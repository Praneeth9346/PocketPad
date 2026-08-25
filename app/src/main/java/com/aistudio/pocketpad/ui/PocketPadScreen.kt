package com.aistudio.pocketpad.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.pocketpad.model.AppScreen
import com.aistudio.pocketpad.model.ButtonId
import com.aistudio.pocketpad.model.ConnectionState
import com.aistudio.pocketpad.model.PadMode
import com.aistudio.pocketpad.ui.components.ConnectionDialog
import com.aistudio.pocketpad.ui.components.LeftBrakeCluster
import com.aistudio.pocketpad.ui.components.QRScannerScreen
import com.aistudio.pocketpad.ui.components.RightThrottleCluster
import com.aistudio.pocketpad.ui.components.SettingsDialog
import com.aistudio.pocketpad.ui.components.StandardGamepad
import com.aistudio.pocketpad.ui.components.SteeringWheel
import com.aistudio.pocketpad.ui.components.TopBar
import com.aistudio.pocketpad.ui.components.MediaRemote
import com.aistudio.pocketpad.ui.components.FPSMouse
import com.aistudio.pocketpad.ui.HubScreen
import com.aistudio.pocketpad.ui.theme.DarkBackground
import com.aistudio.pocketpad.ui.theme.ForzaCyan
import com.aistudio.pocketpad.ui.theme.ForzaMagenta
import com.aistudio.pocketpad.ui.theme.ForzaYellow
import com.aistudio.pocketpad.ui.theme.ForzaGreen
import com.aistudio.pocketpad.ui.theme.ForzaOrange
import com.aistudio.pocketpad.ui.theme.ForzaBorder
import com.aistudio.pocketpad.ui.theme.TextMuted
import com.aistudio.pocketpad.ui.theme.TextWhite
import com.aistudio.pocketpad.viewmodel.PocketPadViewModel

@Composable
fun PocketPadScreen(
    viewModel: PocketPadViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val padMode by viewModel.padMode.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val pingMs by viewModel.pingMs.collectAsStateWithLifecycle()
    val measuredSensorHz by viewModel.measuredSensorHz.collectAsStateWithLifecycle()
    val measuredTxHz by viewModel.measuredTxHz.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val visualSteerAngle by viewModel.visualSteerAngle.collectAsStateWithLifecycle()
    val rawTiltDeg by viewModel.rawTiltDeg.collectAsStateWithLifecycle()
    val throttleVal by viewModel.throttle.collectAsStateWithLifecycle()
    val brakeVal by viewModel.brake.collectAsStateWithLifecycle()
    val activeButtons by viewModel.activeButtons.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()
    val showSettingsDialog by viewModel.showSettingsDialog.collectAsStateWithLifecycle()
    val showConnectDialog by viewModel.showConnectDialog.collectAsStateWithLifecycle()

    var showQRScanner by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
        ) {
            // Top Navigation & Status Bar (Only shown in active gamepad/controller screens, NOT on Hub screen)
            if (currentScreen != AppScreen.HUB) {
                TopBar(
                    padMode = padMode,
                    connectionState = connectionState,
                    pingMs = pingMs,
                    measuredSensorHz = measuredSensorHz,
                    measuredTxHz = measuredTxHz,
                    onPadModeChange = { mode ->
                        viewModel.setPadMode(mode)
                        when (mode) {
                            PadMode.RACING_WHEEL -> viewModel.navigateTo(AppScreen.CONTROLLER)
                            else -> viewModel.navigateTo(AppScreen.GAMEPAD)
                        }
                    },
                    onOpenConnect = { viewModel.setShowConnectDialog(true) },
                    onOpenSettings = { viewModel.setShowSettingsDialog(true) },
                    onNavigateToHub = { viewModel.navigateTo(AppScreen.HUB) },
                    onToggleFullscreen = {
                        (context as? Activity)?.window?.let { window ->
                            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                            insetsController.hide(WindowInsetsCompat.Type.systemBars())
                            insetsController.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    }
                )
            }

            // Main Content Area
            when (currentScreen) {
                AppScreen.HUB -> {
                    HubScreen(
                        connectionState = connectionState,
                        pingMs = pingMs,
                        settings = settings,
                        onConnect = { ip, port -> viewModel.connectToServer(ip, port) },
                        onDisconnect = { viewModel.disconnectServer() },
                        onLaunchCockpit = { testMode ->
                            viewModel.launchController(testMode)
                        },
                        onLaunchGamepad = {
                            viewModel.setPadMode(PadMode.STANDARD_GAMEPAD)
                            viewModel.navigateTo(AppScreen.GAMEPAD)
                        },
                        onLaunchMediaRemote = {
                            viewModel.navigateTo(AppScreen.MEDIA_REMOTE)
                        },
                        onLaunchFPSMouse = {
                            viewModel.navigateTo(AppScreen.FPS_MOUSE)
                        },
                        onOpenQrScanner = { showQRScanner = true },
                        onOpenSettings = { viewModel.setShowSettingsDialog(true) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AppScreen.CONTROLLER -> {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        RacingCockpitView(
                            telemetry = telemetry,
                            settings = settings,
                            visualSteerAngle = visualSteerAngle,
                            rawTiltDeg = rawTiltDeg,
                            throttleVal = throttleVal,
                            brakeVal = brakeVal,
                            activeButtons = activeButtons,
                            isDemoMode = isDemoMode,
                            onToggleDemo = { viewModel.toggleDemoMode() },
                            onToggleMotion = { viewModel.toggleMotion() },
                            onCenterWheel = { viewModel.centerWheel() },
                            onTrimLeft = { viewModel.applyTrim(-0.5f) },
                            onTrimRight = { viewModel.applyTrim(0.5f) },
                            onCycleAngle = { viewModel.cycleMaxSteeringAngle() },
                            onToggleInvert = { viewModel.toggleInvertSteer() },
                            onManualSteer = { viewModel.setManualSteerAngle(it) },
                            onThrottleChange = { viewModel.setThrottle(it) },
                            onBrakeChange = { viewModel.setBrake(it) },
                            onButtonPress = { viewModel.pressButton(it) },
                            onButtonRelease = { viewModel.releaseButton(it) },
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (connectionState == ConnectionState.DISCONNECTED) 0.3f else 1f)
                        )
                        if (connectionState == ConnectionState.DISCONNECTED) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F1726))
                                    .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "DISCONNECTED - RECONNECTING...",
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
                AppScreen.GAMEPAD -> {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        StandardGamepad(
                            activeButtons = activeButtons,
                            throttleVal = throttleVal,
                            brakeVal = brakeVal,
                            onButtonPress = { viewModel.pressButton(it) },
                            onButtonRelease = { viewModel.releaseButton(it) },
                            onTriggerChange = { isLeft, valAmount ->
                                if (isLeft) viewModel.setBrake(valAmount) else viewModel.setThrottle(valAmount)
                            },
                            onStickChange = { isLeft, x, y ->
                                viewModel.setStick(isLeft, x, y)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (connectionState == ConnectionState.DISCONNECTED) 0.3f else 1f)
                        )
                        if (connectionState == ConnectionState.DISCONNECTED) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F1726))
                                    .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "DISCONNECTED - RECONNECTING...",
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
                AppScreen.MEDIA_REMOTE -> {
                    MediaRemote(
                        onMediaKey = { viewModel.sendMediaKey(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                AppScreen.FPS_MOUSE -> {
                    FPSMouse(
                        activeButtons = activeButtons,
                        onButtonPress = { viewModel.pressButton(it) },
                        onButtonRelease = { viewModel.releaseButton(it) },
                        onStickChange = { isLeft, x, y ->
                            viewModel.setStick(isLeft, x, y)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }

        // Settings Dialog Modal
        if (showSettingsDialog) {
            SettingsDialog(
                settings = settings,
                rawTiltDeg = rawTiltDeg,
                onSettingsChanged = { viewModel.updateSettings(it) },
                onMakeDefault = { viewModel.makeSettingsDefault() },
                onTrimLeft = { viewModel.applyTrim(-0.5f) },
                onTrimRight = { viewModel.applyTrim(0.5f) },
                onDismiss = { viewModel.setShowSettingsDialog(false) }
            )
        }

        // Connection Dialog Modal
        if (showConnectDialog) {
            ConnectionDialog(
                initialIp = settings.serverIp,
                initialPort = settings.serverPort,
                initialToken = settings.authToken,
                connectionState = connectionState,
                onConnect = { ip, port, token ->
                    viewModel.connectToServer(ip, port, token)
                    viewModel.setShowConnectDialog(false)
                },
                onDisconnect = { viewModel.disconnectServer() },
                onDismiss = { viewModel.setShowConnectDialog(false) },
                onScanQrClick = { showQRScanner = true }
            )
        }

        if (showQRScanner) {
            QRScannerScreen(
                onQRCodeScanned = { qrText ->
                    viewModel.connectToServer(qrText, settings.serverPort)
                    viewModel.setShowConnectDialog(false)
                    showQRScanner = false
                },
                onDismiss = { showQRScanner = false }
            )
        }
    }
}

@Composable
fun RacingCockpitView(
    telemetry: com.aistudio.pocketpad.model.TelemetryData,
    settings: com.aistudio.pocketpad.model.PocketPadSettings,
    visualSteerAngle: Float,
    rawTiltDeg: Float,
    throttleVal: Float,
    brakeVal: Float,
    activeButtons: Set<ButtonId>,
    isDemoMode: Boolean,
    onToggleDemo: () -> Unit,
    onToggleMotion: () -> Unit,
    onCenterWheel: () -> Unit,
    onTrimLeft: () -> Unit,
    onTrimRight: () -> Unit,
    onCycleAngle: () -> Unit,
    onToggleInvert: () -> Unit,
    onManualSteer: (Float) -> Unit,
    onThrottleChange: (Float) -> Unit,
    onBrakeChange: (Float) -> Unit,
    onButtonPress: (ButtonId) -> Unit,
    onButtonRelease: (ButtonId) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Racing Aux Row: Left Quick Actions | Center Telemetry HUD | Right Quick Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Top Left Actions: [⏮ REWIND (Y)]
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CockpitAuxButton(
                    icon = "⏮",
                    label = "REWIND (Y)",
                    accentColor = Color.Black,
                    isActive = activeButtons.contains(ButtonId.Y),
                    onDown = { onButtonPress(ButtonId.Y) },
                    onUp = { onButtonRelease(ButtonId.Y) },
                    testTag = "btn_rewind",
                    height = 36.dp, // Enlarged
                    bgColor = Color(0xFFEA580C) // High contrast Orange
                )
            }

            // Center: Sleek Dynamic Steering Angle Display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val isLeft = visualSteerAngle < -1.5f
                val isRight = visualSteerAngle > 1.5f

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "◀",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isLeft) ForzaCyan else Color(0xFF30150A)
                    )
                    Text(
                        text = "${String.format("%.1f", kotlin.math.abs(visualSteerAngle))}\u00B0",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = Color.White
                    )
                    Text(
                        text = "▶",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isRight) ForzaCyan else Color(0xFF30150A)
                    )
                }
                Text(
                    text = "STEERING ANGLE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = ForzaOrange
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Center Wheel Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Transparent)
                        .border(1.dp, Color(0xFF401804), RoundedCornerShape(6.dp))
                        .clickable { onCenterWheel() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("btn_calibrate_motion")
                ) {
                    Text(text = "🎯 ZERO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ForzaOrange)
                }
            }

            // Top Right Actions: [🎥 CAMERA]  [🏁 DASH DEMO]  [📢 HORN]
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CockpitAuxButton(
                    icon = "🎥",
                    label = "CAMERA",
                    accentColor = Color.White,
                    isActive = activeButtons.contains(ButtonId.BACK),
                    onDown = { onButtonPress(ButtonId.BACK) },
                    onUp = { onButtonRelease(ButtonId.BACK) },
                    testTag = "btn_camera"
                )

                CockpitAuxButton(
                    icon = "📢",
                    label = "HORN",
                    accentColor = Color.White,
                    isActive = activeButtons.contains(ButtonId.LS),
                    onDown = { onButtonPress(ButtonId.LS) },
                    onUp = { onButtonRelease(ButtonId.LS) },
                    testTag = "btn_horn"
                )
            }
        }



        // Main Cockpit Row: Left Pedal Cluster | Center Steering Wheel | Right Pedal Cluster
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Cluster: Shift Down (X) + Clutch (LB) + Brake Pedal (LT)
            LeftBrakeCluster(
                brakeVal = brakeVal,
                activeButtons = activeButtons,
                onBrakeChange = onBrakeChange,
                onButtonPress = onButtonPress,
                onButtonRelease = onButtonRelease,
                modifier = Modifier.testTag("left_pedal_cluster")
            )

            // Center Steering Wheel Section
            SteeringWheel(
                visualAngleDeg = visualSteerAngle,
                gearString = telemetry.gearString,
                isMotionEnabled = settings.isMotionEnabled,
                onManualSteer = onManualSteer,
                modifier = Modifier.testTag("center_wheel_cluster")
            )

            // Right Cluster: Throttle Pedal (RT) + Shift Up (B) + E-Brake (A)
            RightThrottleCluster(
                throttleVal = throttleVal,
                activeButtons = activeButtons,
                onThrottleChange = onThrottleChange,
                onButtonPress = onButtonPress,
                onButtonRelease = onButtonRelease,
                modifier = Modifier.testTag("right_pedal_cluster")
            )
        }
    }
}

@Composable
fun CockpitAuxButton(
    icon: String,
    label: String,
    accentColor: Color,
    isActive: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 28.dp,
    bgColor: Color = Color.Transparent
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) Color(0xFFEA580C) else bgColor)
            .border(1.dp, if (isActive) ForzaBorder else Color(0xFF401804), RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onDown()
                        tryAwaitRelease()
                        onUp()
                    }
                )
            }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = icon, fontSize = 10.sp)
            Text(
                text = label,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color.White else accentColor
            )
        }
    }
}
