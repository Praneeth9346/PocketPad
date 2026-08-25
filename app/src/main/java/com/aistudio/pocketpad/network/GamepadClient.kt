package com.aistudio.pocketpad.network

import android.content.Context
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
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.math.roundToInt

class GamepadClient(
    private val context: Context,
    private val onConnectionStateChanged: (ConnectionState) -> Unit,
    private val onPingMeasured: (Float) -> Unit,
    private val onTelemetryReceived: (TelemetryData) -> Unit,
    private val onRumbleReceived: ((Float, Float) -> Unit)? = null
) {
    private fun createPocketPadTrustManager(): X509TrustManager {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificate = context.applicationContext.resources
            .openRawResource(com.aistudio.pocketpad.R.raw.pocketpad_ca)
            .use {
                certificateFactory.generateCertificate(it)
            }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("pocketpad-ca", certificate)
        }

        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(keyStore)

        return trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .single()
    }

    private fun createSslContext(trustManager: X509TrustManager): SSLContext {
        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
    }

    private fun createSecureHttpClient(): OkHttpClient {
        val trustManager = createPocketPadTrustManager()
        val sslContext = createSslContext(trustManager)
        val sslSocketFactory = sslContext.socketFactory

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustManager)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Fail visibly if TLS / trust configuration is broken, never fall back to plain client
    private val okHttpClient: OkHttpClient by lazy {
        createSecureHttpClient()
    }

    private var webSocket: WebSocket? = null
    private var authToken: String = ""
    private var authenticated: Boolean = false
    var isConnected: Boolean = false
        private set

    private var connectionGeneration: Long = 0L
    private var lastPingSendTimeNs: Long = 0L

    // Real packet send rate tracking
    private var sentPackets = 0
    private var rateStartNs = System.nanoTime()

    var transmitRateHz: Float = 0f
        private set

    // Pre-allocated ByteBuffers for zero-allocation hot path
    private val steerBuffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
    private val pedalBuffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
    private val buttonBuffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
    private val leftStickBuffer = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
    private val rightStickBuffer = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
    private val pingBuffer = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
    private val latencyProbeBuffer = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
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
        val request = Request.Builder()
            .url("$protocol://$host:$port")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                synchronized(this@GamepadClient) {
                    if (currentGeneration != connectionGeneration) {
                        ws.close(1000, "Stale connection generation")
                        return
                    }
                }

                onConnectionStateChanged(ConnectionState.AUTHENTICATING)

                // Send authentication handshake
                val helloMsg = JSONObject().apply {
                    put("type", "hello")
                    put("token", authToken)
                }
                ws.send(helloMsg.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                synchronized(this@GamepadClient) {
                    if (currentGeneration != connectionGeneration) return
                }

                try {
                    val json = JSONObject(text)
                    when (json.optString("type")) {
                        "hello_ack" -> {
                            val serverVersion = json.optInt("version", -1)
                            if (serverVersion != Protocol.VERSION) {
                                ws.close(4000, "Unsupported protocol version: $serverVersion")
                                onConnectionStateChanged(ConnectionState.ERROR)
                                return
                            }

                            authenticated = true
                            isConnected = true

                            val isUsb = host == "127.0.0.1" || host == "localhost" || host == "10.0.2.2"
                            if (isUsb) {
                                onConnectionStateChanged(ConnectionState.CONNECTED_USB)
                            } else {
                                onConnectionStateChanged(ConnectionState.CONNECTED_WIFI)
                            }
                        }
                        "error" -> {
                            onConnectionStateChanged(ConnectionState.ERROR)
                        }
                    }
                } catch (_: Exception) {}
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                synchronized(this@GamepadClient) {
                    if (currentGeneration != connectionGeneration || !authenticated) return
                }

                val byteArray = bytes.toByteArray()
                if (byteArray.isEmpty()) return

                when (byteArray[0]) {
                    Protocol.PONG -> {
                        if (byteArray.size >= 5 && lastPingSendTimeNs > 0) {
                            val rttMs = (System.nanoTime() - lastPingSendTimeNs) / 1_000_000f
                            onPingMeasured(rttMs)
                        }
                    }
                    Protocol.LATENCY_PROBE -> {
                        if (byteArray.size >= 13) {
                            val buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
                            buffer.get() // Opcode
                            val seq = buffer.int
                            val clientSendNs = buffer.long
                            val roundTripMs = (System.nanoTime() - clientSendNs) / 1_000_000f
                            onPingMeasured(roundTripMs)
                        }
                    }
                    Protocol.RUMBLE -> {
                        if (byteArray.size >= 3) {
                            val large = (byteArray[1].toInt() and 0xFF) / 255f
                            val small = (byteArray[2].toInt() and 0xFF) / 255f
                            onRumbleReceived?.invoke(large, small)
                        }
                    }
                    Protocol.TELEMETRY -> {
                        if (byteArray.size == Protocol.TELEMETRY_PACKET_SIZE) {
                            val buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
                            buffer.get() // Opcode

                            val rpm = buffer.short.toInt() and 0xFFFF
                            val maxRpm = buffer.short.toInt() and 0xFFFF
                            val speedMph = (buffer.short.toInt() and 0xFFFF) / 10f
                            val speedKmh = speedMph * 1.60934f
                            val gear = buffer.get().toInt() and 0xFF
                            val shiftPct = ((buffer.get().toInt() and 0xFF) * 100 / 255)
                            val slipPct = ((buffer.get().toInt() and 0xFF) * 100 / 255)
                            val accel = buffer.get().toInt() and 0xFF
                            val brake = buffer.get().toInt() and 0xFF
                            val boostPsi = (buffer.get().toInt() and 0xFF) / 10f

                            val telem = TelemetryData(
                                currentRpm = rpm,
                                maxRpm = maxRpm,
                                speedMph = speedMph,
                                speedKmh = speedKmh,
                                gear = gear,
                                shiftPct = shiftPct,
                                slipPct = slipPct,
                                accel = accel,
                                brake = brake,
                                boostPsi = boostPsi,
                                isDrifting = slipPct > 30,
                                isLive = true
                            )
                            onTelemetryReceived(telem)
                        }
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                synchronized(this@GamepadClient) {
                    if (currentGeneration != connectionGeneration) return
                    isConnected = false
                    authenticated = false
                }
                onConnectionStateChanged(ConnectionState.DISCONNECTED)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                synchronized(this@GamepadClient) {
                    if (currentGeneration != connectionGeneration) return
                    isConnected = false
                    authenticated = false
                }
                if (code >= 4000) {
                    onConnectionStateChanged(ConnectionState.ERROR)
                } else {
                    onConnectionStateChanged(ConnectionState.DISCONNECTED)
                }
            }
        })
    }

    @Synchronized
    fun disconnect() {
        ++connectionGeneration
        try {
            webSocket?.close(1000, "Client initiated disconnect")
        } catch (_: Exception) {}
        webSocket = null
        isConnected = false
        authenticated = false
    }

    private fun recordPacketSent() {
        sentPackets++
        val now = System.nanoTime()
        val elapsedNs = now - rateStartNs
        if (elapsedNs >= 1_000_000_000L) {
            val elapsedSeconds = elapsedNs / 1_000_000_000f
            transmitRateHz = sentPackets / elapsedSeconds
            sentPackets = 0
            rateStartNs = now
        }
    }

    fun sendSteer(normalized: Float) {
        val clamped = normalized.coerceIn(-1.0f, 1.0f)
        val shortVal = (clamped * 32767f).roundToInt().toShort()
        sendSteering(shortVal)
    }

    fun sendSteering(steeringX: Short) {
        if (!authenticated) return
        steerBuffer.clear()
        steerBuffer.put(Protocol.STEER)
        steerBuffer.putShort(steeringX)
        webSocket?.send(steerBuffer.array().toByteString())
        recordPacketSent()
    }

    fun sendPedals(brake: Float, throttle: Float) {
        val bInt = (brake.coerceIn(0f, 1f) * 255f).roundToInt()
        val tInt = (throttle.coerceIn(0f, 1f) * 255f).roundToInt()
        sendPedals(bInt, tInt)
    }

    fun sendPedals(brake: Int, throttle: Int) {
        if (!authenticated) return
        pedalBuffer.clear()
        pedalBuffer.put(Protocol.PEDALS)
        pedalBuffer.put(brake.coerceIn(0, 255).toByte())
        pedalBuffer.put(throttle.coerceIn(0, 255).toByte())
        webSocket?.send(pedalBuffer.array().toByteString())
        recordPacketSent()
    }

    fun sendButton(buttonId: ButtonId, pressed: Boolean) {
        if (!authenticated) return
        buttonBuffer.clear()
        buttonBuffer.put(Protocol.BUTTON)
        buttonBuffer.put(buttonId.index.toByte())
        buttonBuffer.put(if (pressed) 1.toByte() else 0.toByte())
        webSocket?.send(buttonBuffer.array().toByteString())
        recordPacketSent()
    }

    fun sendStick(isLeft: Boolean, x: Float, y: Float) {
        val xShort = (x.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
        val yShort = (y.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
        if (isLeft) {
            sendLeftStick(xShort, yShort)
        } else {
            sendRightStick(xShort, yShort)
        }
    }

    fun sendLeftStick(x: Short, y: Short) {
        if (!authenticated) return
        leftStickBuffer.clear()
        leftStickBuffer.put(Protocol.LEFT_STICK)
        leftStickBuffer.putShort(x)
        leftStickBuffer.putShort(y)
        webSocket?.send(leftStickBuffer.array().toByteString())
        recordPacketSent()
    }

    fun sendRightStick(x: Short, y: Short) {
        if (!authenticated) return
        rightStickBuffer.clear()
        rightStickBuffer.put(Protocol.RIGHT_STICK)
        rightStickBuffer.putShort(x)
        rightStickBuffer.putShort(y)
        webSocket?.send(rightStickBuffer.array().toByteString())
        recordPacketSent()
    }

    fun sendMouse(dx: Short, dy: Short, buttons: Byte) {
        sendMouseMove(dx, dy, buttons)
    }

    fun sendMouse(dx: Int, dy: Int, buttons: Byte) {
        sendMouseMove(dx.toShort(), dy.toShort(), buttons)
    }

    fun sendMouseMove(dx: Short, dy: Short, buttons: Byte) {
        if (!authenticated) return
        mouseBuffer.clear()
        mouseBuffer.put(Protocol.MOUSE)
        mouseBuffer.putShort(dx)
        mouseBuffer.putShort(dy)
        mouseBuffer.put(buttons)
        webSocket?.send(mouseBuffer.array().toByteString())
        recordPacketSent()
    }

    fun sendMedia(vkCode: Byte) {
        sendMediaKey(vkCode)
    }

    fun sendMediaKey(vkCode: Byte) {
        if (!authenticated) return
        mediaBuffer.clear()
        mediaBuffer.put(Protocol.MEDIA)
        mediaBuffer.put(vkCode)
        webSocket?.send(mediaBuffer.array().toByteString())
        recordPacketSent()
    }

    fun sendPing() {
        if (!authenticated) return
        lastPingSendTimeNs = System.nanoTime()
        val ts = (System.currentTimeMillis() and 0xFFFFFFFFL).toInt()
        pingBuffer.clear()
        pingBuffer.put(Protocol.PING)
        pingBuffer.putInt(ts)
        webSocket?.send(pingBuffer.array().toByteString())
    }

    fun sendLatencyProbe(sequence: Int) {
        if (!authenticated) return
        latencyProbeBuffer.clear()
        latencyProbeBuffer.put(Protocol.LATENCY_PROBE)
        latencyProbeBuffer.putInt(sequence)
        latencyProbeBuffer.putLong(System.nanoTime())
        webSocket?.send(latencyProbeBuffer.array().toByteString())
    }

    fun sendKeepalive() {
        if (!authenticated) return
        webSocket?.send(keepaliveBuffer.array().toByteString())
    }

    fun toggleDemoMode() {
        if (!authenticated) return
        webSocket?.send(demoToggleBuffer.array().toByteString())
    }
}
