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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
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
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val sslSocketFactory = sslContext.socketFactory

        OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    } catch (e: Exception) {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private var webSocket: WebSocket? = null
    var isConnected: Boolean = false
        private set

    private var lastPingSendTime: Long = 0L

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
        keepaliveBuffer.put(0x00.toByte())
        demoToggleBuffer.put(0x11.toByte())
    }

    fun connect(host: String, port: Int, isHttps: Boolean = false) {
        disconnect()
        onConnectionStateChanged(ConnectionState.CONNECTING)

        val protocol = if (isHttps) "wss" else "ws"
        val url = "$protocol://$host:$port"

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                val isUsb = host == "127.0.0.1" || host == "localhost" || host.startsWith("10.18.")
                onConnectionStateChanged(
                    if (isUsb) ConnectionState.CONNECTED_USB else ConnectionState.CONNECTED_WIFI
                )
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                handleIncomingBinary(data)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // JSON message fallback if needed
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                onConnectionStateChanged(ConnectionState.DISCONNECTED)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                onConnectionStateChanged(ConnectionState.DISCONNECTED)
            }
        })
    }

    fun disconnect() {
        isConnected = false
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        webSocket = null
        onConnectionStateChanged(ConnectionState.DISCONNECTED)
    }

    fun sendSteer(normX: Float) {
        if (!isConnected) return
        val clamped = normX.coerceIn(-1.0f, 1.0f)
        val intVal = (clamped * 32767f).toInt().toShort()

        synchronized(steerBuffer) {
            steerBuffer.clear()
            steerBuffer.put(0x01.toByte())
            steerBuffer.putShort(intVal)
            webSocket?.send(steerBuffer.array().toByteString(0, 3))
        }
    }

    fun sendPedals(brake: Float, throttle: Float) {
        if (!isConnected) return
        val ltByte = (brake.coerceIn(0f, 1f) * 255f).toInt().toByte()
        val rtByte = (throttle.coerceIn(0f, 1f) * 255f).toInt().toByte()

        synchronized(pedalBuffer) {
            pedalBuffer.clear()
            pedalBuffer.put(0x02.toByte())
            pedalBuffer.put(ltByte)
            pedalBuffer.put(rtByte)
            webSocket?.send(pedalBuffer.array().toByteString(0, 3))
        }
    }

    fun sendButton(button: ButtonId, pressed: Boolean) {
        if (!isConnected) return
        synchronized(buttonBuffer) {
            buttonBuffer.clear()
            buttonBuffer.put(0x03.toByte())
            buttonBuffer.put(button.index.toByte())
            buttonBuffer.put(if (pressed) 1.toByte() else 0.toByte())
            webSocket?.send(buttonBuffer.array().toByteString(0, 3))
        }
    }

    fun sendStick(isLeft: Boolean, x: Float, y: Float) {
        if (!isConnected) return
        val intX = (x.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        val intY = (y.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

        val buf = if (isLeft) leftStickBuffer else rightStickBuffer
        val opcode = if (isLeft) 0x05.toByte() else 0x06.toByte()

        synchronized(buf) {
            buf.clear()
            buf.put(opcode)
            buf.putShort(intX)
            buf.putShort(intY)
            webSocket?.send(buf.array().toByteString(0, 5))
        }
    }

    fun sendMouse(dx: Short, dy: Short, buttons: Byte) {
        if (!isConnected) return
        synchronized(mouseBuffer) {
            mouseBuffer.clear()
            mouseBuffer.put(0x07.toByte())
            mouseBuffer.putShort(dx)
            mouseBuffer.putShort(dy)
            mouseBuffer.put(buttons)
            webSocket?.send(mouseBuffer.array().toByteString(0, 6))
        }
    }

    fun sendMediaKey(keyCode: Byte) {
        if (!isConnected) return
        synchronized(mediaBuffer) {
            mediaBuffer.clear()
            mediaBuffer.put(0x08.toByte())
            mediaBuffer.put(keyCode)
            webSocket?.send(mediaBuffer.array().toByteString(0, 2))
        }
    }

    fun sendPing() {
        if (!isConnected) return
        lastPingSendTime = System.currentTimeMillis()
        synchronized(pingBuffer) {
            pingBuffer.clear()
            pingBuffer.put(0x09.toByte())
            pingBuffer.putInt((lastPingSendTime and 0xFFFFFFFFL).toInt())
            webSocket?.send(pingBuffer.array().toByteString(0, 5))
        }
    }

    fun sendKeepalive() {
        if (!isConnected) return
        webSocket?.send(keepaliveBuffer.array().toByteString(0, 1))
    }

    fun toggleDemoMode() {
        if (!isConnected) return
        webSocket?.send(demoToggleBuffer.array().toByteString(0, 1))
    }

    private fun handleIncomingBinary(data: ByteArray) {
        if (data.isEmpty()) return
        val opcode = data[0].toInt() and 0xFF

        if (opcode == 0x0A) { // Pong
            val rtt = (System.currentTimeMillis() - lastPingSendTime).toFloat().coerceAtLeast(0.2f)
            onPingMeasured(rtt)
        } else if (opcode == 0x0B && data.size >= 3) { // Rumble
            val large = (data[1].toInt() and 0xFF) / 255f
            val small = (data[2].toInt() and 0xFF) / 255f
            onRumbleReceived?.invoke(large, small)
        } else if (opcode == 0x10 && data.size >= 13) { // Telemetry packet
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
