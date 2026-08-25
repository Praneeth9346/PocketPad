package com.aistudio.pocketpad.model

enum class AppScreen {
    HUB,
    CONTROLLER,
    GAMEPAD,
    MEDIA_REMOTE,
    FPS_MOUSE
}

enum class PadMode {
    RACING_WHEEL,
    STANDARD_GAMEPAD,
    MEDIA_REMOTE,
    FPS_MOUSE
}

enum class SpeedUnit {
    MPH,
    KMH
}

enum class PedalMode {
    ANALOG,
    DIGITAL
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED_WIFI,
    CONNECTED_USB,
    ERROR
}

enum class ButtonId(val index: Int) {
    A(0),
    B(1),
    X(2),
    Y(3),
    DPAD_UP(4),
    DPAD_DOWN(5),
    DPAD_LEFT(6),
    DPAD_RIGHT(7),
    START(8),
    BACK(9),
    GUIDE(10),
    LB(11),
    RB(12),
    LS(13),
    RS(14)
}

/**
 * Internal protocol and UI animation state for the racing cockpit.
 * Used for built-in Dash Demo Mode simulations and optional server telemetry streams.
 */
data class TelemetryData(
    val currentRpm: Int = 0,
    val maxRpm: Int = 8500,
    val speedMph: Float = 0f,
    val speedKmh: Float = 0f,
    val gear: Int = 1, // 0 = R, 1..10, 11+ = N
    val shiftPct: Int = 0, // 0..100%
    val slipPct: Int = 0, // 0..100%
    val accel: Int = 0,
    val brake: Int = 0,
    val boostPsi: Float = 0f,
    val isDrifting: Boolean = false,
    val isLive: Boolean = false
) {
    val gearString: String
        get() = when (gear) {
            0 -> "R"
            in 1..10 -> gear.toString()
            else -> "N"
        }
}

data class ButtonConfig(
    val id: String,
    val isVisible: Boolean = true,
    val scale: Float = 1.0f
)

data class LayoutPreset(
    val name: String,
    val buttonConfigs: Map<String, ButtonConfig>
)

data class PocketPadSettings(
    val serverIp: String = "", // Force user to configure real IP
    val serverPort: Int = 8765,
    val authToken: String = "", // Persistent token for authentication handshake
    val maxSteeringAngle: Int = 45, // Degrees for 100% lock (15..90)
    val steeringSensitivity: Float = 2.89f,
    val antiDeadzone: Float = 0.10f, // 10% Forza bypass
    val curveExponent: Float = 1.0f, // 1.0 = linear, >1 = S-curve
    val sensorDeadzone: Float = 0.03f,
    val speedUnit: SpeedUnit = SpeedUnit.MPH,
    val pedalMode: PedalMode = PedalMode.ANALOG,
    val isMotionEnabled: Boolean = true,
    val invertSteering: Boolean = false,
    val manualTrimOffset: Float = 0f,
    val buttonConfigs: Map<String, ButtonConfig> = emptyMap(),
    val isPedalsSwapped: Boolean = false, // If true, throttle on left, brake on right
    val leftClusterOffsetX: Float = 0f, // Draggable horizontal offset (-80dp..80dp)
    val rightClusterOffsetX: Float = 0f, // Draggable horizontal offset (-80dp..80dp)
    val wheelOffsetX: Float = 0f, // Draggable horizontal offset (-60dp..60dp)
    val wheelOffsetY: Float = 0f // Draggable vertical offset (-40dp..40dp)
)

data class PocketPadConnection(
    val host: String,
    val port: Int,
    val token: String,
    val secure: Boolean
)

fun parsePocketPadUrl(rawUrl: String): PocketPadConnection? {
    return try {
        val uri = android.net.Uri.parse(rawUrl.trim())

        val scheme = uri.scheme?.lowercase()
        val secure = scheme == "https" || scheme == "wss"

        if (scheme != "https" && scheme != "http" && scheme != "wss" && scheme != "ws") {
            return null
        }

        val host = uri.host ?: return null
        val token = uri.getQueryParameter("token")
            ?.trim()
            .orEmpty()

        if (token.isEmpty()) return null

        PocketPadConnection(
            host = host,
            port = if (secure) {
                // WebSocket secure port, not HTTPS UI port
                8766
            } else {
                // WebSocket standard port, not HTTP UI port
                8765
            },
            token = token,
            secure = secure
        )
    } catch (_: Exception) {
        null
    }
}
