package com.aistudio.pocketpad.network

import com.aistudio.pocketpad.model.ButtonId
import com.aistudio.pocketpad.model.ConnectionState
import com.aistudio.pocketpad.model.TelemetryData
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class GamepadClient(
    private val onConnectionStateChanged: (ConnectionState) -> Unit,
    private val onPingMeasured: (Float) -> Unit,
    private val onTelemetryReceived: (TelemetryData) -> Unit,
    private val onRumbleReceived: ((Float, Float) -> Unit)? = null
) {
    private val okHttpClient: OkHttpClient = try {
        val pocketPadTrustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain == null || chain.isEmpty()) {
                    throw CertificateException("Server certificate chain is empty")
                }
                val leafCert = chain[0]
                leafCert.checkValidity()

                val subject = leafCert.subjectX500Principal.name
                val issuer = leafCert.issuerX500Principal.name
                val isPocketPadCert = subject.contains("PocketPad", ignoreCase = true) ||
                                     issuer.contains("PocketPad", ignoreCase = true)

                if (!isPocketPadCert) {
                    throw CertificateException("Untrusted certificate: Not issued for PocketPad server ($subject)")
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(pocketPadTrustManager), SecureRandom())
        val sslSocketFactory = sslContext.socketFactory

        OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, pocketPadTrustManager)
            .hostnameVerifier { hostname, _ ->
                if (hostname.isNullOrBlank()) return@hostnameVerifier false
                val isLocalHost = hostname == "localhost" ||
                                 hostname == "127.0.0.1" ||
                                 hostname == "10.0.2.2" ||
                                 hostname.startsWith("192.168.") ||
                                 hostname.startsWith("10.") ||
                                 hostname.startsWith("172.")
                isLocalHost
            }
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    } catch (_: Exception) {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private var webSocket: WebSocket? = null
    private var authToken: String = ""
    private var authenticated: Boolean = false
    var isConnected: Boolean = false
        private set

    private var connectionGeneration: Long = 0L
    private var lastPingSendTimeNs: Long = 0L

    // Pre-allocated ByteBuffers for zero-allocation hot path
    private val steerBuffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
    private val pedalBuffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
    private val buttonBuffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
    private val leftStickBuffer = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
    private val rightStickBuffer = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
    private val pingBuffer = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
    private val mouseBuffer = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
    private val mediaBuffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
    private val keepaliveBuffer = ByteBuffer.allocate(1)
    private val demoToggleBuffer = ByteBuffer.allocate(1)

    init {
        keepaliveBuffer.put(Protocol.KEEPALIVE)
        demoToggleBuffer.put(0x11.toByte())
    }

    fun connect(
        host: String,
        port: Int,
        token: String,
        isHttps: Boolean = false
    ) {
        disconnect()

        authToken = token.trim()
        authenticated = false

        if (authToken.isEmpty()) {
            onConnectionStateChanged(ConnectionState.DISCONNECTED)
            return
        }

        val currentGeneration = synchronized(this) {
            ++connectionGeneration
        }

        onConnectionStateChanged(ConnectionState.CONNECTING)

        val protocol = if (isHttps) "wss" else "ws"
        val url = "$protocol://$host:$port"

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {
                    if (currentGeneration != connectionGeneration) return

                    onConnectionStateChanged(ConnectionState.AUTHENTICATING)

                    val hello = """
                        {
                          "type":"hello",
                          "token":${JSONObject.quote(authToken)}
                        }
                    """.trimIndent()

                    if (!webSocket.send(hello)) {
                        webSocket.close(1011, "Failed to send authentication")
                    }
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {
                    if (currentGeneration != connectionGeneration) return
                    handleIncomingText(text, host, webSocket)
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    bytes: ByteString
                ) {
                    if (currentGeneration != connectionGeneration) return
                    if (!authenticated) {
                        webSocket.close(4003, "Binary data before authentication")
                        return
                    }

                    handleIncomingBinary(bytes.toByteArray())
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    if (currentGeneration != connectionGeneration) return
                    isConnected = false
                    authenticated = false
                    onConnectionStateChanged(ConnectionState.DISCONNECTED)
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?
                ) {
                    if (currentGeneration != connectionGeneration) return
                    isConnected = false
                    authenticated = false
                    onConnectionStateChanged(ConnectionState.DISCONNECTED)
                }
            }
        )
    }

    private fun handleIncomingText(text: String, host: String, ws: WebSocket) {
        try {
            val json = JSONObject(text)

            when (json.optString("type")) {
                "hello_ack" -> {
                    val serverVersion = json.optInt("version", -1)
                    if (serverVersion != Protocol.VERSION) {
                        ws.close(4004, "Protocol mismatch: server=$serverVersion, client=${Protocol.VERSION}")
                        onConnectionStateChanged(ConnectionState.ERROR)
                        return
                    }

                    authenticated = true
                    isConnected = true

                    val isUsb =
                        host == "127.0.0.1" ||
                        host == "localhost" ||
                        host.startsWith("10.18.")

                    onConnectionStateChanged(
                        if (isUsb) {
                            ConnectionState.CONNECTED_USB
                        } else {
                            ConnectionState.CONNECTED_WIFI
                        }
                    )
                }

                "error" -> {
                    authenticated = false
                    isConnected = false
                    onConnectionStateChanged(ConnectionState.ERROR)
                }
            }
        } catch (_: Exception) {
            // Ignore malformed control messages.
        }
    }

    @Synchronized
    fun disconnect() {
        isConnected = false
        authenticated = false
        val ws = webSocket
        webSocket = null
        try {
            ws?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        onConnectionStateChanged(ConnectionState.DISCONNECTED)
    }

    fun sendSteer(normX: Float) {
        if (!isConnected || !authenticated) return
        val clamped = normX.coerceIn(-1.0f, 1.0f)
        val intVal = (clamped * 32767f).toInt().toShort()

        synchronized(steerBuffer) {
            steerBuffer.clear()
            steerBuffer.put(Protocol.STEER)
            steerBuffer.putShort(intVal)
            webSocket?.send(steerBuffer.array().toByteString(0, 3))
        }
    }

    fun sendPedals(brake: Float, throttle: Float) {
        if (!isConnected || !authenticated) return
        val ltByte = (brake.coerceIn(0f, 1f) * 255f).toInt().toByte()
        val rtByte = (throttle.coerceIn(0f, 1f) * 255f).toInt().toByte()

        synchronized(pedalBuffer) {
            pedalBuffer.clear()
            pedalBuffer.put(Protocol.PEDALS)
            pedalBuffer.put(ltByte)
            pedalBuffer.put(rtByte)
            webSocket?.send(pedalBuffer.array().toByteString(0, 3))
        }
    }

    fun sendButton(button: ButtonId, pressed: Boolean) {
        if (!isConnected || !authenticated) return
        synchronized(buttonBuffer) {
            buttonBuffer.clear()
            buttonBuffer.put(Protocol.BUTTON)
            buttonBuffer.put(button.index.toByte())
            buttonBuffer.put(if (pressed) 1.toByte() else 0.toByte())
            webSocket?.send(buttonBuffer.array().toByteString(0, 3))
        }
    }

    fun sendStick(isLeft: Boolean, x: Float, y: Float) {
        if (!isConnected || !authenticated) return
        val intX = (x.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        val intY = (y.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

        val buf = if (isLeft) leftStickBuffer else rightStickBuffer
        val opcode = if (isLeft) Protocol.LEFT_STICK else Protocol.RIGHT_STICK

        synchronized(buf) {
            buf.clear()
            buf.put(opcode)
            buf.putShort(intX)
            buf.putShort(intY)
            webSocket?.send(buf.array().toByteString(0, 5))
        }
    }

    fun sendMouse(dx: Short, dy: Short, buttons: Byte) {
        if (!isConnected || !authenticated) return
        synchronized(mouseBuffer) {
            mouseBuffer.clear()
            mouseBuffer.put(Protocol.MOUSE)
            mouseBuffer.putShort(dx)
            mouseBuffer.putShort(dy)
            mouseBuffer.put(buttons)
            webSocket?.send(mouseBuffer.array().toByteString(0, 6))
        }
    }

    fun sendMediaKey(keyCode: Byte) {
        if (!isConnected || !authenticated) return
        synchronized(mediaBuffer) {
            mediaBuffer.clear()
            mediaBuffer.put(Protocol.MEDIA)
            mediaBuffer.put(keyCode)
            webSocket?.send(mediaBuffer.array().toByteString(0, 2))
        }
    }

    fun sendPing() {
        if (!isConnected || !authenticated) return
        lastPingSendTimeNs = System.nanoTime()
        synchronized(pingBuffer) {
            pingBuffer.clear()
            pingBuffer.put(Protocol.PING)
            pingBuffer.putInt((System.currentTimeMillis() and 0xFFFFFFFFL).toInt())
            webSocket?.send(pingBuffer.array().toByteString(0, 5))
        }
    }

    fun sendKeepalive() {
        if (!isConnected || !authenticated) return
        webSocket?.send(keepaliveBuffer.array().toByteString(0, 1))
    }

    fun toggleDemoMode() {
        if (!isConnected || !authenticated) return
        webSocket?.send(demoToggleBuffer.array().toByteString(0, 1))
    }

    private fun handleIncomingBinary(data: ByteArray) {
        if (data.isEmpty()) return
        val opcode = data[0]

        if (opcode == Protocol.PONG) {
            val elapsedNs = System.nanoTime() - lastPingSendTimeNs
            val rttMs = (elapsedNs / 1_000_000f).coerceAtLeast(0.1f)
            onPingMeasured(rttMs)
        } else if (opcode == Protocol.RUMBLE && data.size >= 3) {
            val large = (data[1].toInt() and 0xFF) / 255f
            val small = (data[2].toInt() and 0xFF) / 255f
            onRumbleReceived?.invoke(large, small)
        } else if (opcode == Protocol.TELEMETRY && data.size == Protocol.TELEMETRY_PACKET_SIZE) {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(1)
            val currentRpm = buffer.short.toInt() and 0xFFFF
            val maxRpm = buffer.short.toInt() and 0xFFFF
            val speedMphX10 = buffer.short.toInt() and 0xFFFF
            val gear = buffer.get().toInt() and 0xFF
            val shiftPct = buffer.get().toInt() and 0xFF
            val slipPct = buffer.get().toInt() and 0xFF
            val accel = buffer.get().toInt() and 0xFF
            val brake = buffer.get().toInt() and 0xFF
            val boost = buffer.get().toInt() and 0xFF

            val speedMph = speedMphX10 / 10.0f
            val speedKmh = speedMph * 1.60934f

            val telemetry = TelemetryData(
                currentRpm = currentRpm,
                maxRpm = if (maxRpm > 0) maxRpm else 8500,
                speedMph = speedMph,
                speedKmh = speedKmh,
                gear = gear,
                shiftPct = shiftPct,
                slipPct = slipPct,
                accel = accel,
                brake = brake,
                boostPsi = boost.toFloat(),
                isDrifting = slipPct > 22,
                isLive = true
            )
            onTelemetryReceived(telemetry)
        }
    }
}
