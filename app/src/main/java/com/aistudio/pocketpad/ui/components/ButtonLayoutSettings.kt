package com.aistudio.pocketpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pocketpad.model.ButtonConfig
import com.aistudio.pocketpad.model.PocketPadSettings
import com.aistudio.pocketpad.ui.theme.ForzaCyan
import com.aistudio.pocketpad.ui.theme.ForzaGreen
import com.aistudio.pocketpad.ui.theme.ForzaMagenta
import com.aistudio.pocketpad.ui.theme.TextMuted
import com.aistudio.pocketpad.ui.theme.TextWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonLayoutSettings(
    settings: PocketPadSettings,
    onSettingsChanged: ((PocketPadSettings) -> PocketPadSettings) -> Unit
) {
    val buttonConfigs = settings.buttonConfigs
    val buttonIds = listOf(
        "btn_rewind" to "Rewind (Y)",
        "btn_lookback" to "Mirror (RS)",
        "btn_camera" to "Camera",
        "btn_telemetry_demo" to "Dash Demo",
        "btn_horn" to "Horn (LS)",
        "paddle_shift_down" to "Shift Down",
        "paddle_shift_up" to "Shift Up",
        "btn_clutch" to "Clutch (LB)",
        "btn_handbrake" to "Handbrake (A)"
    )

    // Cockpit Grip & Spacing Section
    SettingSection(
        title = "Cockpit Spacing & Pedal Position",
        desc = "Adjust thumb spacing, swap brake/throttle, or pick ergonomics presets."
    ) {
        // Quick Layout Presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PresetPill(
                text = "Default",
                isSelected = settings.leftClusterOffsetX == 0f && settings.rightClusterOffsetX == 0f && !settings.isPedalsSwapped
            ) {
                onSettingsChanged {
                    it.copy(
                        leftClusterOffsetX = 0f,
                        rightClusterOffsetX = 0f,
                        wheelOffsetX = 0f,
                        wheelOffsetY = 0f,
                        isPedalsSwapped = false
                    )
                }
            }

            PresetPill(
                text = "Wide Tablet",
                isSelected = settings.leftClusterOffsetX == -25f && settings.rightClusterOffsetX == 25f
            ) {
                onSettingsChanged {
                    it.copy(
                        leftClusterOffsetX = -25f,
                        rightClusterOffsetX = 25f,
                        wheelOffsetX = 0f,
                        wheelOffsetY = 0f
                    )
                }
            }

            PresetPill(
                text = "Compact",
                isSelected = settings.leftClusterOffsetX == 25f && settings.rightClusterOffsetX == -25f
            ) {
                onSettingsChanged {
                    it.copy(
                        leftClusterOffsetX = 25f,
                        rightClusterOffsetX = -25f,
                        wheelOffsetX = 0f,
                        wheelOffsetY = 0f
                    )
                }
            }

            PresetPill(
                text = if (settings.isPedalsSwapped) "⇄ Pedals: Swapped" else "⇄ Pedals: Standard",
                isSelected = settings.isPedalsSwapped
            ) {
                onSettingsChanged { it.copy(isPedalsSwapped = !it.isPedalsSwapped) }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Swap Brake & Throttle Switch Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF131D2D))
                .clickable { onSettingsChanged { it.copy(isPedalsSwapped = !it.isPedalsSwapped) } }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Swap Brake ⇄ Throttle Position",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Text(
                    text = if (settings.isPedalsSwapped) "Left: Throttle (RT) | Right: Brake (LT)" else "Left: Brake (LT) | Right: Throttle (RT)",
                    fontSize = 9.sp,
                    color = if (settings.isPedalsSwapped) ForzaGreen else TextMuted
                )
            }
            Switch(
                checked = settings.isPedalsSwapped,
                onCheckedChange = { isSwapped -> onSettingsChanged { it.copy(isPedalsSwapped = isSwapped) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ForzaGreen,
                    checkedTrackColor = ForzaGreen.copy(alpha = 0.5f)
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Reset spacing button
        if (settings.leftClusterOffsetX != 0f || settings.rightClusterOffsetX != 0f || settings.wheelOffsetX != 0f || settings.wheelOffsetY != 0f) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E283C))
                    .clickable {
                        onSettingsChanged {
                            it.copy(
                                leftClusterOffsetX = 0f,
                                rightClusterOffsetX = 0f,
                                wheelOffsetX = 0f,
                                wheelOffsetY = 0f
                            )
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "↺ Reset Spacing Offsets (0dp)",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForzaCyan
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Button Sizes & Visibility Section
    SettingSection(
        title = "Button Layout & Size Presets",
        desc = "Enable/disable buttons and change their sizes."
    ) {
        val globalScale = buttonConfigs.values.firstOrNull()?.scale ?: 1f
        Text("Global Button Size: ${String.format("%.1fx", globalScale)}", fontSize = 10.sp, color = TextMuted)
        Slider(
            value = globalScale,
            onValueChange = { s -> 
                val newConfigs = buttonIds.associate { it.first to ButtonConfig(it.first, buttonConfigs[it.first]?.isVisible ?: true, s) }
                onSettingsChanged { it.copy(buttonConfigs = newConfigs) }
            },
            valueRange = 0.5f..1.5f,
            steps = 9,
            colors = SliderDefaults.colors(thumbColor = ForzaCyan, activeTrackColor = ForzaCyan)
        )
        
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            buttonIds.forEach { (id, label) ->
                val config = buttonConfigs[id] ?: ButtonConfig(id, true, globalScale)
                PresetPill(
                    text = label, 
                    isSelected = config.isVisible
                ) {
                    val mutableMap = buttonConfigs.toMutableMap()
                    mutableMap[id] = config.copy(isVisible = !config.isVisible)
                    onSettingsChanged { it.copy(buttonConfigs = mutableMap) }
                }
            }
        }
    }
}

