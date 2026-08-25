package com.aistudio.pocketpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pocketpad.model.ConnectionState
import com.aistudio.pocketpad.model.PadMode
import com.aistudio.pocketpad.ui.theme.ForzaCyan
import com.aistudio.pocketpad.ui.theme.ForzaGreen
import com.aistudio.pocketpad.ui.theme.ForzaMagenta
import com.aistudio.pocketpad.ui.theme.ForzaOrange
import com.aistudio.pocketpad.ui.theme.ForzaYellow
import com.aistudio.pocketpad.ui.theme.TextMuted
import com.aistudio.pocketpad.ui.theme.TextWhite

@Composable
fun TopBar(
    padMode: PadMode,
    connectionState: ConnectionState,
    pingMs: Float?,
    onPadModeChange: (PadMode) -> Unit,
    onOpenConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFullscreen: () -> Unit = {},
    onNavigateToHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFF070C16))
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. LEFT BRAND TITLE WITH HUB SHORTCUT: [ 🏠 PRESETS FORZA ] 🏎️ PocketPad
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable { onNavigateToHub() }
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF162338))
                    .border(1.dp, ForzaOrange.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "🏠", fontSize = 11.sp)
                    Text(
                        text = "PRESETS",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        color = ForzaOrange
                    )
                    
                    if (padMode == PadMode.RACING_WHEEL) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF4A1010))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "FORZA",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp,
                                color = Color(0xFFFA5A5A)
                            )
                        }
                    }
                }
            }

            Text(text = "🏎️", fontSize = 13.sp)
            Text(
                text = "PocketPad",
                fontSize = 12.sp, // shrunk
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                color = TextWhite
            )
        }

        // 2. CENTER MODE SWITCHER CAPSULE: [ 🏎️ Motion Wheel | 🎮 Standard ]
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0C1322))
                .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(20.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Motion Wheel Tab
            val isMotionWheel = padMode == PadMode.RACING_WHEEL
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isMotionWheel) Color(0xFF1E100A) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isMotionWheel) Color(0xFF802D00) else Color.Transparent,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onPadModeChange(PadMode.RACING_WHEEL) }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .testTag("tab_racing")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(text = "🏎️", fontSize = 11.sp)
                    Text(
                        text = "Motion Wheel",
                        fontSize = 10.5.sp,
                        fontWeight = if (isMotionWheel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isMotionWheel) Color(0xFFFF5B00) else TextMuted
                    )
                }
            }

            // Standard Gamepad Tab
            val isStandard = padMode == PadMode.STANDARD_GAMEPAD
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isStandard) Color(0xFF1C2B42) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isStandard) Color(0xFF2E4264) else Color.Transparent,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onPadModeChange(PadMode.STANDARD_GAMEPAD) }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .testTag("tab_standard")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(text = "🎮", fontSize = 11.sp)
                    Text(
                        text = "Standard",
                        fontSize = 10.5.sp,
                        fontWeight = if (isStandard) FontWeight.Bold else FontWeight.Medium,
                        color = if (isStandard) TextWhite else TextMuted
                    )
                }
            }
        }

        // 3. RIGHT STATUS PANEL
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Connection Pill
            val (connIcon, connLabel) = when (connectionState) {
                ConnectionState.CONNECTED_USB -> Pair("⚡", "USB")
                ConnectionState.CONNECTED_WIFI -> Pair("📶", "Wi-Fi")
                ConnectionState.CONNECTING -> Pair("◌", "Connecting...")
                ConnectionState.AUTHENTICATING -> Pair("🔒", "Authenticating...")
                ConnectionState.ERROR -> Pair("✕", "Auth Error")
                ConnectionState.DISCONNECTED -> Pair("📶", "Disconnected")
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1726))
                    .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(8.dp))
                    .clickable { onOpenConnect() }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .testTag("conn_badge")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = connIcon,
                        fontSize = 11.sp,
                        color = if (connectionState == ConnectionState.CONNECTED_WIFI || connectionState == ConnectionState.CONNECTED_USB) ForzaGreen else ForzaYellow
                    )
                    Text(
                        text = connLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (connectionState == ConnectionState.CONNECTED_WIFI || connectionState == ConnectionState.CONNECTED_USB) ForzaGreen else Color(0xFF94A3B8)
                    )
                }
            }

            // Live Latency Pill
            val displayPing = pingMs?.let { String.format("%.1f ms", it) } ?: "-- ms"
            val pingColor = when {
                pingMs == null -> TextMuted
                pingMs < 20f -> ForzaGreen
                pingMs < 60f -> ForzaYellow
                else -> Color(0xFFEF4444)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1726))
                    .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("ping_badge")
            ) {
                Text(
                    text = displayPing,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = pingColor
                )
            }

            // Settings Icon Button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1726))
                    .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(8.dp))
                    .testTag("btn_settings")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = if (padMode == PadMode.RACING_WHEEL) Color(0xFF94A3B8) else ForzaOrange,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Fullscreen Icon Button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1726))
                    .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(8.dp))
                    .clickable { onToggleFullscreen() }
                    .testTag("btn_fullscreen"),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⛶", fontSize = 14.sp, color = TextWhite)
            }
        }
    }
}
