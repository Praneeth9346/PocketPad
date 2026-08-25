package com.aistudio.pocketpad.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pocketpad.model.ConnectionState
import com.aistudio.pocketpad.model.PocketPadSettings
import com.aistudio.pocketpad.ui.theme.DarkBackground
import com.aistudio.pocketpad.ui.theme.ForzaCyan
import com.aistudio.pocketpad.ui.theme.ForzaGreen
import com.aistudio.pocketpad.ui.theme.ForzaMagenta
import com.aistudio.pocketpad.ui.theme.ForzaOrange
import com.aistudio.pocketpad.ui.theme.ForzaYellow
import com.aistudio.pocketpad.ui.theme.SurfaceCard
import com.aistudio.pocketpad.ui.theme.SurfaceDark
import com.aistudio.pocketpad.ui.theme.TextMuted
import com.aistudio.pocketpad.ui.theme.TextWhite

@Composable
fun HubScreen(
    connectionState: ConnectionState,
    pingMs: Float?,
    settings: PocketPadSettings,
    onConnect: (String, Int) -> Unit,
    onDisconnect: () -> Unit,
    onLaunchCockpit: (testMode: Boolean) -> Unit,
    onLaunchGamepad: () -> Unit,
    onLaunchMediaRemote: () -> Unit,
    onLaunchFPSMouse: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputIp by remember(settings.serverIp) { mutableStateOf(settings.serverIp) }
    var inputPort by remember(settings.serverPort) { mutableStateOf(settings.serverPort.toString()) }
    var isPairingExpanded by remember { mutableStateOf(settings.serverIp.isBlank()) }

    val isConnected = connectionState == ConnectionState.CONNECTED_USB || connectionState == ConnectionState.CONNECTED_WIFI
    val isConnecting = connectionState == ConnectionState.CONNECTING

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. TOP HEADER BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
                .border(1.dp, Color(0xFF1E283C), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand Logo & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "⚡",
                    fontSize = 16.sp,
                    color = ForzaCyan,
                    modifier = Modifier.scale(if (isConnected) pulseScale else 1f)
                )
                Text(
                    text = "POCKETPAD PRO",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.1.sp,
                    color = TextWhite
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(ForzaMagenta, ForzaYellow)))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "VIRTUAL CONTROLLER",
                        fontSize = 6.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                }
            }

            // Connection Status Pill & Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val (statusText, statusBg, statusColor) = when (connectionState) {
                    ConnectionState.CONNECTED_USB -> Triple("● USB (0.2ms)", ForzaGreen.copy(alpha = 0.15f), ForzaGreen)
                    ConnectionState.CONNECTED_WIFI -> Triple(
                        if (pingMs != null) "● ONLINE (${String.format("%.1f", pingMs)} ms)" else "● ONLINE (5GHz)", 
                        ForzaGreen.copy(alpha = 0.15f), 
                        ForzaGreen
                    )
                    ConnectionState.CONNECTING -> Triple("◌ CONNECTING...", ForzaYellow.copy(alpha = 0.15f), ForzaYellow)
                    ConnectionState.AUTHENTICATING -> Triple("◌ AUTHENTICATING...", ForzaYellow.copy(alpha = 0.15f), ForzaYellow)
                    ConnectionState.ERROR -> Triple("✕ AUTH / PROTOCOL ERROR", Color(0xFF3A1818), Color(0xFFFF5252))
                    ConnectionState.DISCONNECTED -> Triple("● OFFLINE / STANDBY", Color(0xFF182234), TextMuted)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable {
                            if (isConnected || isConnecting) onDisconnect()
                            else onConnect(inputIp.ifBlank { "10.0.2.2" }, inputPort.toIntOrNull() ?: 8765)
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("hub_status_pill")
                ) {
                    Text(
                        text = statusText,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                // QR Scanner Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF172338))
                        .border(1.dp, ForzaCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable { onOpenQrScanner() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("hub_btn_qr_scan")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "📷", fontSize = 11.sp)
                        Text(
                            text = "SCAN QR",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForzaCyan
                        )
                    }
                }

                // Settings Button
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("hub_btn_settings")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = ForzaCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 2. QUICK CONNECTION & HOST PAIRING BAR
        if (!isPairingExpanded) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF161F2E))
                    .border(1.dp, Color(0xFF24354F), RoundedCornerShape(6.dp))
                    .clickable { isPairingExpanded = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "🖥️ $inputIp:$inputPort  ✎",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = ForzaCyan
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .border(1.dp, Color(0xFF1E283C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "HOST PAIRING:",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    color = TextWhite
                )

                // IP Input (Compact, robust, no vertical clipping)
                CompactInputField(
                    value = inputIp,
                    onValueChange = { inputIp = it.trim() },
                    placeholder = "192.168.1.xxx",
                    keyboardType = KeyboardType.Ascii,
                    testTag = "hub_input_ip",
                    modifier = Modifier
                        .width(140.dp)
                        .height(28.dp)
                )

                // Port Input (Compact)
                CompactInputField(
                    value = inputPort,
                    onValueChange = { inputPort = it.trim() },
                    placeholder = "8443",
                    keyboardType = KeyboardType.Number,
                    testTag = "hub_input_port",
                    modifier = Modifier
                        .width(65.dp)
                        .height(28.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Quick IP presets
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickIpChip(
                        text = "Emulator", 
                        isActive = inputIp == "10.0.2.2"
                    ) { 
                        inputIp = "10.0.2.2"
                        if (inputPort.isBlank()) inputPort = "8443"
                    }
                    QuickIpChip(
                        text = "USB", 
                        isActive = inputIp == "127.0.0.1"
                    ) { 
                        inputIp = "127.0.0.1"
                        if (inputPort.isBlank()) inputPort = "8443"
                    }
                    QuickIpChip(
                        text = "Port: 8000", 
                        isActive = inputPort == "8000"
                    ) { 
                        inputPort = "8000"
                    }
                }
            }
        }

        // 3. PRESETS & CONTROLLER CARDS
        val pagerState = rememberPagerState(pageCount = { 5 })
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                pageSpacing = 12.dp
            ) { page ->
                when (page) {
                    0 -> LastSessionCard(inputIp, onLaunchGamepad)
                    1 -> ForzaCard(onLaunchCockpit)
                    2 -> XboxCard(onLaunchGamepad)
                    3 -> FpsCard(onLaunchFPSMouse)
                    4 -> MediaCard(onLaunchMediaRemote)
                }
            }

            // Pager Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(5) { i ->
                    val color = if (pagerState.currentPage == i) ForzaOrange else Color(0xFF243550)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}

@Composable
private fun LastSessionCard(
    lastIp: String,
    onLaunch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.5.dp, ForzaOrange, RoundedCornerShape(12.dp))
            .clickable { onLaunch() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🔄", fontSize = 18.sp)
                Column {
                    Text(
                        text = "LAST SESSION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = TextWhite,
                        maxLines = 1
                    )
                    Text(
                        text = "$lastIp • Gamepad",
                        fontSize = 9.sp,
                        color = ForzaOrange,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
            
            Text(
                text = "Resume your previous connection and controller layout instantly.",
                fontSize = 9.sp,
                lineHeight = 12.sp,
                color = Color(0xFFA0AAB5),
                maxLines = 2
            )
            
            Button(
                onClick = onLaunch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForzaOrange,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Text(
                    text = "▶ RESUME",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun ForzaCard(onLaunchCockpit: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF101728), Color(0xFF152238), Color(0xFF0F1928))))
            .border(1.dp, Color(0xFF243248), RoundedCornerShape(12.dp))
            .clickable { onLaunchCockpit(false) }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(text = "🏎️", fontSize = 18.sp)
                    Column {
                        Text(
                            text = "FORZA RACING",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Gyro Steering + HUD",
                            fontSize = 9.sp,
                            color = Color(0xFFA0AAB5),
                            maxLines = 1
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ForzaOrange)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(text = "PRO", fontSize = 7.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
            }

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FeaturePill("GYRO STEER")
                FeaturePill("PEDALS")
                FeaturePill("LIVE HUD")
            }

            Text(
                text = "Hardware gyroscope steering with progressive pedals and live Forza telemetry HUD.",
                fontSize = 9.sp,
                lineHeight = 12.sp,
                color = Color(0xFFA0AAB5),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { onLaunchCockpit(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = ForzaOrange, contentColor = Color.Black),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.weight(1.25f).height(36.dp)
                ) {
                    Text(text = "▶ LAUNCH", fontSize = 10.sp, fontWeight = FontWeight.Black)
                }

                Button(
                    onClick = { onLaunchCockpit(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A263C), contentColor = ForzaOrange),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.weight(0.9f).height(36.dp)
                ) {
                    Text(text = "🧪 DEMO", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun XboxCard(onLaunchGamepad: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, Color(0xFF243248), RoundedCornerShape(12.dp))
            .clickable { onLaunchGamepad() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🎮", fontSize = 18.sp)
                Column {
                    Text(
                        text = "XBOX GAMEPAD",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Dual Sticks + D-Pad",
                        fontSize = 9.sp,
                        color = Color(0xFFA0AAB5),
                        maxLines = 1
                    )
                }
            }

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FeaturePill("DUAL STICKS")
                FeaturePill("XINPUT")
                FeaturePill("HAPTICS")
            }

            Text(
                text = "Xbox 360 controller with auto-centering analog thumbsticks and triggers.",
                fontSize = 9.sp,
                lineHeight = 12.sp,
                color = Color(0xFFA0AAB5),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Button(
                onClick = onLaunchGamepad,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23324C), contentColor = ForzaOrange),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Text(text = "▶ LAUNCH GAMEPAD", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FpsCard(onLaunchFPSMouse: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, Color(0xFF243248), RoundedCornerShape(12.dp))
            .clickable { onLaunchFPSMouse() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🖱️", fontSize = 18.sp)
                Column {
                    Text(
                        text = "FPS MOUSE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Keyboard & Mouse",
                        fontSize = 9.sp,
                        color = Color(0xFFA0AAB5),
                        maxLines = 1
                    )
                }
            }

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FeaturePill("WASD STICK")
                FeaturePill("AIM TRACKPAD")
            }

            Text(
                text = "Play first-person shooters with a touch trackpad for aiming and WASD stick.",
                fontSize = 9.sp,
                lineHeight = 12.sp,
                color = Color(0xFFA0AAB5),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Button(
                onClick = { onLaunchFPSMouse() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23324C), contentColor = ForzaOrange),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Text(text = "▶ LAUNCH FPS MOUSE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MediaCard(onLaunchMediaRemote: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, Color(0xFF243248), RoundedCornerShape(12.dp))
            .clickable { onLaunchMediaRemote() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "📺", fontSize = 18.sp)
                Column {
                    Text(
                        text = "MEDIA REMOTE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Movies & Music",
                        fontSize = 9.sp,
                        color = Color(0xFFA0AAB5),
                        maxLines = 1
                    )
                }
            }

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FeaturePill("VOLUME")
                FeaturePill("PLAYBACK")
            }

            Text(
                text = "Perfect for couch viewing. Easy access to play/pause, volume, and media keys.",
                fontSize = 9.sp,
                lineHeight = 12.sp,
                color = Color(0xFFA0AAB5),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Button(
                onClick = { onLaunchMediaRemote() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23324C), contentColor = ForzaOrange),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Text(text = "▶ LAUNCH REMOTE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CompactInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Ascii,
    testTag: String = ""
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = TextWhite,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(ForzaCyan),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF121927))
                    .border(1.dp, Color(0xFF24354F), RoundedCornerShape(5.dp))
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                innerTextField()
            }
        },
        modifier = modifier.testTag(testTag)
    )
}

@Composable
private fun QuickIpChip(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) Color(0xFF1E2F4C) else Color(0xFF172338)
    val borderColor = if (isActive) ForzaOrange else Color(0xFF243550)
    val textColor = if (isActive) TextWhite else ForzaCyan
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun FeaturePill(
    text: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF1E283C))
            .border(1.dp, Color(0xFF2E3D5C), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFA0AAB5)
        )
    }
}
