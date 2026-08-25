package com.aistudio.pocketpad.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.pocketpad.model.AppScreen
import com.aistudio.pocketpad.model.ButtonId
import com.aistudio.pocketpad.model.ConnectionState
import com.aistudio.pocketpad.model.PadMode
import com.aistudio.pocketpad.model.PedalMode
import com.aistudio.pocketpad.model.PocketPadSettings
import com.aistudio.pocketpad.model.SpeedUnit
import com.aistudio.pocketpad.model.TelemetryData
import com.aistudio.pocketpad.model.parsePocketPadUrl
import com.aistudio.pocketpad.network.GamepadClient
import com.aistudio.pocketpad.sensor.MotionSensorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PocketPadViewModel(application: Application) : AndroidViewModel(application) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val _currentScreen = MutableStateFlow(AppScreen.HUB)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _padMode = MutableStateFlow(PadMode.RACING_WHEEL)
    val padMode: StateFlow<PadMode> = _padMode.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _pingMs = MutableStateFlow<Float?>(null)
    val pingMs: StateFlow<Float?> = _pingMs.asStateFlow()

    private val _settings = MutableStateFlow(PocketPadSettings())
    val settings: StateFlow<PocketPadSettings> = _settings.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    private val _steerNormalized = MutableStateFlow(0f)
    val steerNormalized: StateFlow<Float> = _steerNormalized.asStateFlow()

    private val _visualSteerAngle = MutableStateFlow(0f)
    val visualSteerAngle: StateFlow<Float> = _visualSteerAngle.asStateFlow()

    private val _rawTiltDeg = MutableStateFlow(0f)
    val rawTiltDeg: StateFlow<Float> = _rawTiltDeg.asStateFlow()

    private val _throttle = MutableStateFlow(0f)
    val throttle: StateFlow<Float> = _throttle.asStateFlow()

    private val _brake = MutableStateFlow(0f)
    val brake: StateFlow<Float> = _brake.asStateFlow()

    private val _activeButtons = MutableStateFlow<Set<ButtonId>>(emptySet())
    val activeButtons: StateFlow<Set<ButtonId>> = _activeButtons.asStateFlow()

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _showConnectDialog = MutableStateFlow(false)
    val showConnectDialog: StateFlow<Boolean> = _showConnectDialog.asStateFlow()

    private val prefs = application.getSharedPreferences("PocketPadSettings", Context.MODE_PRIVATE)

    private var isIntentionalDisconnect = false
    private var reconnectJob: Job? = null
    private var retryDelayMs = 1000L

    private val client = GamepadClient(
        context = application.applicationContext,
        onConnectionStateChanged = { state ->
            viewModelScope.launch(Dispatchers.Main) {
                handleConnectionState(state)
            }
        },
        onPingMeasured = { rtt ->
            viewModelScope.launch(Dispatchers.Main) {
                _pingMs.value = rtt
            }
        },
        onTelemetryReceived = { telem ->
            viewModelScope.launch(Dispatchers.Main) {
                if (!_isDemoMode.value) {
                    _telemetry.value = telem
                    lastTelemetryTime = System.currentTimeMillis()
                }
            }
        },
        onRumbleReceived = { large, small ->
            val intensity = max(large, small)
            if (intensity > 0.05f) {
                val duration = (intensity * 100).toLong().coerceIn(15, 100)
                val amplitude = (intensity * 255).toInt().coerceIn(1, 255)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(duration)
                    }
                } catch (_: Exception) {}
            }
        }
    )

    private var previousSampleNs = 0L
    private val _measuredSensorHz = MutableStateFlow(0f)
    val measuredSensorHz: StateFlow<Float> = _measuredSensorHz.asStateFlow()

    private var previousTxNs = 0L
    private val _measuredTxHz = MutableStateFlow(0f)
    val measuredTxHz: StateFlow<Float> = _measuredTxHz.asStateFlow()

    private fun recordSensorSample() {
        val now = System.nanoTime()
        if (previousSampleNs != 0L) {
            val deltaNs = now - previousSampleNs
            if (deltaNs > 0) {
                val instantHz = 1_000_000_000f / deltaNs.toFloat()
                _measuredSensorHz.value = _measuredSensorHz.value * 0.9f + instantHz * 0.1f
            }
        }
        previousSampleNs = now
    }

    private fun recordTxSample() {
        val now = System.nanoTime()
        if (previousTxNs != 0L) {
            val deltaNs = now - previousTxNs
            if (deltaNs > 0) {
                val instantHz = 1_000_000_000f / deltaNs.toFloat()
                _measuredTxHz.value = _measuredTxHz.value * 0.9f + instantHz * 0.1f
            }
        }
        previousTxNs = now
    }

    private var lastSteerSend = 0L
    private val motionManager = MotionSensorManager(application) { norm, visualDeg, rawDeg ->
        recordSensorSample()
        _steerNormalized.value = norm
        _visualSteerAngle.value = visualDeg
        _rawTiltDeg.value = rawDeg

        val now = System.currentTimeMillis()
        if (now - lastSteerSend >= 16) { // ~60Hz
            lastSteerSend = now
            recordTxSample()
            client.sendSteer(norm)
        }
    }

    private var pingJob: Job? = null
    private var heartbeatJob: Job? = null
    private var demoSimulationJob: Job? = null
    private var telemetryTimeoutJob: Job? = null
    private var fpsMouseJob: Job? = null
    private var lastTelemetryTime = 0L

    private var fpsMouseRightX = 0f
    private var fpsMouseRightY = 0f

    init {
        loadSettings()
        syncSettingsToSensor()
        startTelemetryTimeoutMonitor()
        startHeartbeatLoop()
    }

    private fun handleConnectionState(state: ConnectionState) {
        _connectionState.value = state
        if (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) {
            _pingMs.value = null
            if (!isIntentionalDisconnect && _settings.value.serverIp.isNotEmpty()) {
                startAutoReconnect()
            }
        } else if (state == ConnectionState.CONNECTED_WIFI || state == ConnectionState.CONNECTED_USB) {
            reconnectJob?.cancel()
            reconnectJob = null
            retryDelayMs = 1000L
        }
    }

    private fun startAutoReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            while (isActive && !isIntentionalDisconnect &&
                   (_connectionState.value == ConnectionState.DISCONNECTED || _connectionState.value == ConnectionState.ERROR)) {
                delay(retryDelayMs)
                if (isActive && !isIntentionalDisconnect) {
                    val s = _settings.value
                    client.connect(s.serverIp, s.serverPort, s.authToken)
                    retryDelayMs = min(retryDelayMs * 2, 15000L) // exponential backoff up to 15s
                }
            }
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(2000)
                if (client.isConnected) {
                    client.sendKeepalive()
                }
            }
        }
    }

    private fun loadSettings() {
        val maxAngle = prefs.getInt("maxSteeringAngle", 45)
        val ip = prefs.getString("serverIp", "") ?: ""
        val port = prefs.getInt("serverPort", 8765)
        val token = prefs.getString("authToken", "") ?: ""
        _settings.update {
            it.copy(
                maxSteeringAngle = maxAngle,
                serverIp = ip,
                serverPort = port,
                authToken = token
            )
        }
    }

    private fun saveSettings(s: PocketPadSettings) {
        prefs.edit()
            .putInt("maxSteeringAngle", s.maxSteeringAngle)
            .putString("serverIp", s.serverIp)
            .putInt("serverPort", s.serverPort)
            .putString("authToken", s.authToken)
            .apply()
    }

    private fun startTelemetryTimeoutMonitor() {
        telemetryTimeoutJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (!_isDemoMode.value && _telemetry.value.isLive) {
                    val now = System.currentTimeMillis()
                    if (now - lastTelemetryTime > 3000) {
                        _telemetry.update { it.copy(isLive = false) }
                    }
                }
            }
        }
    }

    private fun syncSettingsToSensor() {
        val s = _settings.value
        motionManager.maxSteeringAngle = s.maxSteeringAngle
        motionManager.steeringSensitivity = s.steeringSensitivity
        motionManager.antiDeadzone = s.antiDeadzone
        motionManager.curveExponent = s.curveExponent
        motionManager.sensorDeadzone = s.sensorDeadzone
        motionManager.invertSteering = s.invertSteering
        motionManager.manualTrimOffset = s.manualTrimOffset
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        if (screen == AppScreen.CONTROLLER) {
            _padMode.value = PadMode.RACING_WHEEL
            if (_settings.value.isMotionEnabled) {
                motionManager.start()
                motionManager.calibrateCenter()
            }
        } else {
            motionManager.stop()
        }

        if (screen == AppScreen.FPS_MOUSE) {
            _padMode.value = PadMode.FPS_MOUSE
            startFPSMouseLoop()
        } else {
            stopFPSMouseLoop()
        }
        triggerHaptic(20)
    }

    fun launchController(testMode: Boolean = false) {
        _padMode.value = PadMode.RACING_WHEEL
        if (testMode) {
            _isDemoMode.value = true
            startDemoSimulation()
        } else {
            _isDemoMode.value = false
        }
        _settings.update { it.copy(isMotionEnabled = true) }
        motionManager.start()
        motionManager.calibrateCenter()
        _currentScreen.value = AppScreen.CONTROLLER
        triggerHaptic(30)
    }

    fun launchGamepad() {
        _padMode.value = PadMode.STANDARD_GAMEPAD
        _currentScreen.value = AppScreen.GAMEPAD
        triggerHaptic(30)
    }

    fun setPadMode(mode: PadMode) {
        _padMode.value = mode
        if (mode == PadMode.RACING_WHEEL && _settings.value.isMotionEnabled) {
            motionManager.start()
            motionManager.calibrateCenter()
        } else {
            motionManager.stop()
            _steerNormalized.value = 0f
            _visualSteerAngle.value = 0f
            _rawTiltDeg.value = 0f
            client.sendSteer(0f)
        }
        triggerHaptic(15)
    }

    fun setShowSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
        triggerHaptic(15)
    }

    fun setShowConnectDialog(show: Boolean) {
        _showConnectDialog.value = show
        triggerHaptic(15)
    }

    fun toggleMotion() {
        val newEnabled = !_settings.value.isMotionEnabled
        _settings.update { it.copy(isMotionEnabled = newEnabled) }

        if (newEnabled) {
            motionManager.start()
            motionManager.calibrateCenter()
            triggerHaptic(30)
        } else {
            motionManager.stop()
            _steerNormalized.value = 0f
            _visualSteerAngle.value = 0f
            _rawTiltDeg.value = 0f
            client.sendSteer(0f)
            triggerHaptic(20)
        }
    }

    fun centerWheel() {
        motionManager.calibrateCenter()
        _settings.update { it.copy(manualTrimOffset = 0f) }
        _steerNormalized.value = 0f
        _visualSteerAngle.value = 0f
        _rawTiltDeg.value = 0f
        client.sendSteer(0f)
        triggerHaptic(35)
    }

    fun applyTrim(deltaDeg: Float) {
        motionManager.applyTrim(deltaDeg)
        _settings.update { it.copy(manualTrimOffset = motionManager.manualTrimOffset) }
        triggerHaptic(20)
    }

    fun cycleMaxSteeringAngle() {
        val angles = listOf(45, 60, 90, 30, 25)
        val current = _settings.value.maxSteeringAngle
        val nextIndex = (angles.indexOf(current) + 1).takeIf { it in angles.indices } ?: 0
        val nextAngle = angles[nextIndex]

        updateSettings { it.copy(maxSteeringAngle = nextAngle) }
        triggerHaptic(25)
    }

    fun toggleInvertSteer() {
        val newInvert = !_settings.value.invertSteering
        updateSettings { it.copy(invertSteering = newInvert) }
        triggerHaptic(20)
    }

    fun setManualSteerAngle(angleDeg: Float) {
        if (_settings.value.isMotionEnabled) return
        val clamped = angleDeg.coerceIn(-260f, 260f)
        _visualSteerAngle.value = clamped
        val norm = (clamped / 260f).coerceIn(-1f, 1f)
        _steerNormalized.value = norm
        client.sendSteer(norm)
    }

    fun updateSettings(transform: (PocketPadSettings) -> PocketPadSettings) {
        _settings.update(transform)
        syncSettingsToSensor()
    }

    fun makeSettingsDefault() {
        saveSettings(_settings.value)
        triggerHaptic(30)
    }

    fun setThrottle(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _throttle.value = clamped
        client.sendPedals(_brake.value, clamped)
    }

    fun setBrake(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _brake.value = clamped
        client.sendPedals(clamped, _throttle.value)
    }

    fun pressButton(button: ButtonId) {
        val newButtons = _activeButtons.value + button
        _activeButtons.value = newButtons

        if (_padMode.value == PadMode.FPS_MOUSE && (button == ButtonId.RB || button == ButtonId.LB)) {
            client.sendMouse(0, 0, getFPSMouseButtons(newButtons))
        } else {
            client.sendButton(button, true)
        }
        triggerHaptic(25)

        if (_isDemoMode.value) {
            handleDemoButtonPress(button)
        }
    }

    fun releaseButton(button: ButtonId) {
        val newButtons = _activeButtons.value - button
        _activeButtons.value = newButtons

        if (_padMode.value == PadMode.FPS_MOUSE && (button == ButtonId.RB || button == ButtonId.LB)) {
            client.sendMouse(0, 0, getFPSMouseButtons(newButtons))
        } else {
            client.sendButton(button, false)
        }
    }

    fun setStick(isLeft: Boolean, x: Float, y: Float) {
        if (_padMode.value == PadMode.FPS_MOUSE) {
            if (isLeft) {
                client.sendStick(true, x, y)
            } else {
                fpsMouseRightX = x
                fpsMouseRightY = y
            }
        } else {
            client.sendStick(isLeft, x, y)
        }
    }

    fun toggleDemoMode() {
        val newDemo = !_isDemoMode.value
        _isDemoMode.value = newDemo
        triggerHaptic(30)

        if (client.isConnected) {
            client.toggleDemoMode()
        }

        if (newDemo) {
            startDemoSimulation()
        } else {
            stopDemoSimulation()
            if (!client.isConnected) {
                _telemetry.value = TelemetryData()
            }
        }
    }

    private fun startDemoSimulation() {
        demoSimulationJob?.cancel()
        demoSimulationJob = viewModelScope.launch {
            var simSpeed = 45f
            var simRpm = 3200
            var simGear = 3
            var simBoost = 8.5f
            var simSlip = 5

            while (isActive && _isDemoMode.value) {
                val t = _throttle.value
                val b = _brake.value

                if (t > 0.05f) {
                    simSpeed += t * 3.5f
                    simRpm += (t * 220).toInt()
                    simBoost = min(22f, simBoost + t * 0.8f)
                    if (simRpm > 8200) {
                        if (simGear < 6) {
                            simGear++
                            simRpm = 4500
                            triggerHaptic(20)
                        } else {
                            simRpm = 8400
                        }
                    }
                } else if (b > 0.05f) {
                    simSpeed = max(0f, simSpeed - b * 4.2f)
                    simRpm = max(900, simRpm - (b * 280).toInt())
                    simBoost = max(0f, simBoost - 1.2f)
                    if (simRpm < 2500 && simGear > 1 && simSpeed > 5f) {
                        simGear--
                        simRpm = 5200
                        triggerHaptic(15)
                    }
                } else {
                    simSpeed = max(20f, simSpeed - 0.2f)
                    simRpm = (simRpm + (-30..30).random()).coerceIn(2800, 7200)
                    simBoost = max(2f, simBoost - 0.1f)
                }

                val steerMag = abs(_steerNormalized.value)
                if (steerMag > 0.5f && simSpeed > 30f) {
                    simSlip = (steerMag * 85).toInt().coerceIn(10, 95)
                } else {
                    simSlip = max(3, simSlip - 4)
                }

                val shiftPct = ((simRpm.toFloat() / 8500f) * 100f).toInt().coerceIn(0, 100)

                _telemetry.value = TelemetryData(
                    currentRpm = simRpm,
                    maxRpm = 8500,
                    speedMph = simSpeed,
                    speedKmh = simSpeed * 1.60934f,
                    gear = simGear,
                    shiftPct = shiftPct,
                    slipPct = simSlip,
                    boostPsi = simBoost,
                    isDrifting = simSlip > 22,
                    isLive = true
                )

                delay(50)
            }
        }
    }

    private fun handleDemoButtonPress(button: ButtonId) {
        val current = _telemetry.value
        when (button) {
            ButtonId.B -> {
                val nextGear = min(7, current.gear + 1)
                _telemetry.update { it.copy(gear = nextGear, currentRpm = max(3000, it.currentRpm - 2000)) }
            }
            ButtonId.X -> {
                val prevGear = max(1, current.gear - 1)
                _telemetry.update { it.copy(gear = prevGear, currentRpm = min(8200, it.currentRpm + 2200)) }
            }
            ButtonId.A -> {
                _telemetry.update { it.copy(slipPct = 88, isDrifting = true) }
            }
            else -> {}
        }
    }

    private fun stopDemoSimulation() {
        demoSimulationJob?.cancel()
        demoSimulationJob = null
    }

    fun connectToServer(ip: String, port: Int = 8765, token: String = "") {
        val parsed = parsePocketPadUrl(ip.trim())
        if (parsed != null) {
            isIntentionalDisconnect = false
            updateSettings { it.copy(serverIp = parsed.host, serverPort = parsed.port, authToken = parsed.token) }
            client.connect(
                host = parsed.host,
                port = parsed.port,
                token = parsed.token,
                isHttps = parsed.secure
            )
            startPingJob()
            return
        }

        var rawInput = ip.trim()
        var extractedToken = token.trim()

        if (rawInput.contains("token=")) {
            val tokenPart = rawInput.substringAfter("token=").substringBefore("&").substringBefore("#")
            if (tokenPart.isNotEmpty()) {
                extractedToken = tokenPart
            }
            rawInput = rawInput.substringBefore("?")
        }

        if (extractedToken.isEmpty()) {
            extractedToken = _settings.value.authToken
        }

        var cleanIp = rawInput.lowercase()
        val isHttpsScheme = cleanIp.startsWith("https://") || cleanIp.startsWith("wss://")
        cleanIp = cleanIp.removePrefix("ws://")
                         .removePrefix("wss://")
                         .removePrefix("http://")
                         .removePrefix("https://")
                         .trimEnd('/')

        var finalIp = cleanIp
        var finalPort = port
        if (cleanIp.contains(":")) {
            val parts = cleanIp.split(":")
            finalIp = parts[0]
            val parsedPort = parts[1].split("/")[0].toIntOrNull()
            if (parsedPort != null) {
                finalPort = parsedPort
            }
        }

        isIntentionalDisconnect = false
        updateSettings { it.copy(serverIp = finalIp, serverPort = finalPort, authToken = extractedToken) }

        val isHttps = isHttpsScheme || finalPort == 8443 || finalPort == 8766
        val wsPort = when (finalPort) {
            8000 -> 8765
            8443 -> 8766
            else -> finalPort
        }

        client.connect(finalIp, wsPort, extractedToken, isHttps)
        startPingJob()
    }

    private fun startPingJob() {
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            while (isActive) {
                if (client.isConnected) {
                    client.sendPing()
                }
                delay(1000)
            }
        }
    }

    fun disconnectServer() {
        isIntentionalDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        pingJob?.cancel()
        pingJob = null
        client.disconnect()
    }

    fun triggerHaptic(durationMs: Long = 25) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    fun sendMouse(dx: Short, dy: Short, buttons: Byte) {
        client.sendMouse(dx, dy, buttons)
    }

    fun sendMediaKey(keyCode: Byte) {
        client.sendMediaKey(keyCode)
        triggerHaptic(20)
    }

    private fun startFPSMouseLoop() {
        fpsMouseJob?.cancel()
        fpsMouseJob = viewModelScope.launch {
            while (isActive) {
                if (fpsMouseRightX != 0f || fpsMouseRightY != 0f) {
                    val dx = (fpsMouseRightX * 25f).toInt().toShort()
                    val dy = (fpsMouseRightY * 25f).toInt().toShort()
                    client.sendMouse(dx, dy, getFPSMouseButtons(_activeButtons.value))
                }
                delay(16) // ~60Hz
            }
        }
    }

    private fun stopFPSMouseLoop() {
        fpsMouseJob?.cancel()
        fpsMouseJob = null
        fpsMouseRightX = 0f
        fpsMouseRightY = 0f
    }

    private fun getFPSMouseButtons(active: Set<ButtonId>): Byte {
        var b = 0
        if (active.contains(ButtonId.RB)) b = b or 1 // Left click
        if (active.contains(ButtonId.LB)) b = b or 2 // Right click
        return b.toByte()
    }

    override fun onCleared() {
        super.onCleared()
        isIntentionalDisconnect = true
        motionManager.stop()
        client.disconnect()
        pingJob?.cancel()
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        demoSimulationJob?.cancel()
        telemetryTimeoutJob?.cancel()
        fpsMouseJob?.cancel()
    }
}
