package com.aistudio.pocketpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aistudio.pocketpad.model.ButtonId
import com.aistudio.pocketpad.ui.theme.DarkBackground

@Composable
fun MediaRemote(
    onMediaKey: (Byte) -> Unit,
    modifier: Modifier = Modifier
) {
    val VK_VOL_UP: Byte = 0xAF.toByte()
    val VK_VOL_DOWN: Byte = 0xAE.toByte()
    val VK_NEXT: Byte = 0xB0.toByte()
    val VK_PREV: Byte = 0xB1.toByte()
    val VK_PLAY_PAUSE: Byte = 0xB3.toByte()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left D-Pad cluster
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TouchButton(
                    title = "Vol +",
                    subtitle = "",
                    isActive = false,
                    onDown = { onMediaKey(VK_VOL_UP) },
                    onUp = { },
                    activeColor = Color(0xFF10B981),
                    modifier = Modifier.size(80.dp, 60.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TouchButton(
                        title = "Prev",
                        subtitle = "",
                        isActive = false,
                        onDown = { onMediaKey(VK_PREV) },
                        onUp = { },
                        activeColor = Color(0xFF10B981),
                        modifier = Modifier.size(80.dp, 60.dp)
                    )
                    Spacer(modifier = Modifier.width(60.dp)) // Middle spacer
                    TouchButton(
                        title = "Next",
                        subtitle = "",
                        isActive = false,
                        onDown = { onMediaKey(VK_NEXT) },
                        onUp = { },
                        activeColor = Color(0xFF10B981),
                        modifier = Modifier.size(80.dp, 60.dp)
                    )
                }
                TouchButton(
                    title = "Vol -",
                    subtitle = "",
                    isActive = false,
                    onDown = { onMediaKey(VK_VOL_DOWN) },
                    onUp = { },
                    activeColor = Color(0xFF10B981),
                    modifier = Modifier.size(80.dp, 60.dp)
                )
            }

            // Right Action Cluster
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TouchButton(
                    title = "Y (N/A)",
                    subtitle = "",
                    isActive = false,
                    onDown = { },
                    onUp = { },
                    activeColor = Color(0xFFFBBF24),
                    modifier = Modifier.size(80.dp, 60.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TouchButton(
                        title = "X (N/A)",
                        subtitle = "",
                        isActive = false,
                        onDown = { },
                        onUp = { },
                        activeColor = Color(0xFF3B82F6),
                        modifier = Modifier.size(80.dp, 60.dp)
                    )
                    Spacer(modifier = Modifier.width(60.dp))
                    TouchButton(
                        title = "B (Back)",
                        subtitle = "",
                        isActive = false,
                        onDown = { },
                        onUp = { },
                        activeColor = Color(0xFFEF4444),
                        modifier = Modifier.size(80.dp, 60.dp)
                    )
                }
                TouchButton(
                    title = "Play/Pause",
                    subtitle = "",
                    isActive = false,
                    onDown = { onMediaKey(VK_PLAY_PAUSE) },
                    onUp = { },
                    activeColor = Color(0xFF10B981),
                    modifier = Modifier.size(80.dp, 60.dp)
                )
            }
        }
    }
}
