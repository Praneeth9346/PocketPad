package com.aistudio.pocketpad.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aistudio.pocketpad.model.PedalMode
import com.aistudio.pocketpad.model.PocketPadSettings
import com.aistudio.pocketpad.model.SpeedUnit
import com.aistudio.pocketpad.ui.theme.*

@Composable
fun SettingsDialog(
    settings: PocketPadSettings,
    rawTiltDeg: Float,
    onSettingsChanged: ((PocketPadSettings) -> PocketPadSettings) -> Unit,
    onMakeDefault: () -> Unit,
    onTrimLeft: () -> Unit,
    onTrimRight: () -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("settings_modal"),
            color = SurfaceDark
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚙️ PocketPad Pro Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = TextWhite
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF243248))
                            .clickable { onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextWhite, modifier = Modifier.size(16.dp))
                        Text("Done", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1E283C)))

                // Scrollable Content Area
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (isLandscape) {
                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                            // Left Column (Steering)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(end = 12.dp, top = 16.dp, bottom = 16.dp)
                            ) {
                                SteeringSettingsColumn(settings, onSettingsChanged)
                            }
                            // Right Column (Pedals, Units, Diagnostics)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(start = 12.dp, top = 16.dp, bottom = 16.dp)
                            ) {
                                RightSettingsColumn(settings, rawTiltDeg, onSettingsChanged, onTrimLeft, onTrimRight)
                            }
                        }
                    } else {
                        // Portrait fallback
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            SteeringSettingsColumn(settings, onSettingsChanged)
                            Spacer(modifier = Modifier.height(24.dp))
                            RightSettingsColumn(settings, rawTiltDeg, onSettingsChanged, onTrimLeft, onTrimRight)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1E283C)))
                
                // Sticky Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { 
                            onSettingsChanged { PocketPadSettings() } 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C263B), contentColor = ForzaOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(0.4f).height(48.dp)
                    ) {
                        Text("Reset to Defaults", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { 
                            onMakeDefault()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForzaOrange, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(0.6f).height(48.dp)
                    ) {
                        Text("Save as Default (FORZA)", fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun SteeringSettingsColumn(
    settings: PocketPadSettings,
    onSettingsChanged: ((PocketPadSettings) -> PocketPadSettings) -> Unit
) {
    // 1. Max Steering Angle Lock
    SettingSection(
        title = "Physical Tilt for 100% Lock: ${settings.maxSteeringAngle}°",
        desc = "Sets exact wrist tilt needed to reach full in-game lock.",
        visualization = { TiltDiagram(settings.maxSteeringAngle.toFloat()) }
    ) {
        SettingsSlider(
            value = settings.maxSteeringAngle.toFloat(),
            valueRange = 15f..90f,
            steps = 14,
            onValueChange = { onSettingsChanged { s -> s.copy(maxSteeringAngle = it.toInt()) } },
            stepSize = 5f
        )
        PresetRow {
            PresetPill("30°", settings.maxSteeringAngle == 30) { onSettingsChanged { it.copy(maxSteeringAngle = 30) } }
            PresetPill("45°", settings.maxSteeringAngle == 45) { onSettingsChanged { it.copy(maxSteeringAngle = 45) } }
            PresetPill("60°", settings.maxSteeringAngle == 60) { onSettingsChanged { it.copy(maxSteeringAngle = 60) } }
            PresetPill("90°", settings.maxSteeringAngle == 90) { onSettingsChanged { it.copy(maxSteeringAngle = 90) } }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // 2. Steering Multiplier
    val inGameDeg = (90f * settings.steeringSensitivity).toInt()
    SettingSection(
        title = "Steering Multiplier: ${String.format("%.2f", settings.steeringSensitivity)}x",
        desc = "Increases wheel turning rate per tilt. ${String.format("%.2f", settings.steeringSensitivity)}x turns in-game wheel $inGameDeg° at 90° physical tilt."
    ) {
        SettingsSlider(
            value = settings.steeringSensitivity,
            valueRange = 0.5f..5.0f,
            onValueChange = { onSettingsChanged { s -> s.copy(steeringSensitivity = it) } },
            stepSize = 0.05f
        )
        PresetRow {
            PresetPill("1.00x", (settings.steeringSensitivity - 1.0f) in -0.01f..0.01f) { onSettingsChanged { it.copy(steeringSensitivity = 1.0f) } }
            PresetPill("1.50x", (settings.steeringSensitivity - 1.5f) in -0.01f..0.01f) { onSettingsChanged { it.copy(steeringSensitivity = 1.5f) } }
            PresetPill("2.50x", (settings.steeringSensitivity - 2.5f) in -0.01f..0.01f) { onSettingsChanged { it.copy(steeringSensitivity = 2.5f) } }
            PresetPill("2.89x", (settings.steeringSensitivity - 2.89f) in -0.01f..0.01f) { onSettingsChanged { it.copy(steeringSensitivity = 2.89f) } }
            PresetPill("4.00x", (settings.steeringSensitivity - 4.0f) in -0.01f..0.01f) { onSettingsChanged { it.copy(steeringSensitivity = 4.0f) } }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // 3. Anti-Deadzone
    SettingSection(
        title = "Anti-Deadzone: ${(settings.antiDeadzone * 100).toInt()}%",
        desc = "Bypasses the game's built-in controller deadzone for immediate turn-in."
    ) {
        SettingsSlider(
            value = settings.antiDeadzone,
            valueRange = 0f..0.35f,
            onValueChange = { onSettingsChanged { s -> s.copy(antiDeadzone = it) } },
            stepSize = 0.01f
        )
        PresetRow {
            PresetPill("20%", (settings.antiDeadzone - 0.20f) in -0.01f..0.01f) { onSettingsChanged { it.copy(antiDeadzone = 0.20f) } }
            PresetPill("25%", (settings.antiDeadzone - 0.25f) in -0.01f..0.01f) { onSettingsChanged { it.copy(antiDeadzone = 0.25f) } }
            PresetPill("12%", (settings.antiDeadzone - 0.12f) in -0.01f..0.01f) { onSettingsChanged { it.copy(antiDeadzone = 0.12f) } }
            PresetPill("0%", settings.antiDeadzone == 0f) { onSettingsChanged { it.copy(antiDeadzone = 0f) } }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // 4. Linearity
    SettingSection(
        title = "Steering Linearity (S-Curve): ${String.format("%.1f", settings.curveExponent)}x",
        desc = "Adjusts curve shape: higher values increase straight-line stability while preserving full lock response.",
        visualization = { LinearityCurve(settings.curveExponent) }
    ) {
        SettingsSlider(
            value = settings.curveExponent,
            valueRange = 1.0f..3.0f,
            steps = 19,
            onValueChange = { onSettingsChanged { s -> s.copy(curveExponent = it) } },
            stepSize = 0.1f
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // 5. Tremor Guard
    val smoothingDeg = String.format("%.1f", settings.sensorDeadzone * 10f)
    val latencyMs = (settings.sensorDeadzone * 200).toInt()
    SettingSection(
        title = "1€ Filter Tremor Guard: ${(settings.sensorDeadzone * 100).toInt()}%",
        desc = if (settings.sensorDeadzone == 0f) "Raw sensor response with 0 added latency." else "${(settings.sensorDeadzone * 100).toInt()}% ≈ ±$smoothingDeg° jitter smoothing (Adds ~$latencyMs ms latency)."
    ) {
        SettingsSlider(
            value = settings.sensorDeadzone,
            valueRange = 0f..0.15f,
            steps = 14,
            onValueChange = { onSettingsChanged { s -> s.copy(sensorDeadzone = it) } },
            stepSize = 0.01f
        )
    }
}

@Composable
private fun RightSettingsColumn(
    settings: PocketPadSettings,
    rawTiltDeg: Float,
    onSettingsChanged: ((PocketPadSettings) -> PocketPadSettings) -> Unit,
    onTrimLeft: () -> Unit,
    onTrimRight: () -> Unit
) {
    // Steering Orientation
    SettingSection(
        title = "Steering Orientation & Live Tuning",
        desc = "Toggle motion, fine-tune physical center (Trim), and reverse steering axis."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            SegmentedControl(
                options = listOf("Motion ON", "Motion OFF"),
                selectedIndex = if (settings.isMotionEnabled) 0 else 1,
                onOptionSelected = { onSettingsChanged { s -> s.copy(isMotionEnabled = it == 0) } }
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onSettingsChanged { it.copy(invertSteering = !it.invertSteering) } },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (settings.invertSteering) ForzaOrange else Color(0xFF1E283C),
                        contentColor = if (settings.invertSteering) Color.Black else TextWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text(if (settings.invertSteering) "Invert: ON" else "Invert: OFF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E283C)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable { onTrimLeft() }, contentAlignment = Alignment.Center) {
                        Text("◀ Trim L", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    }
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight(0.6f).background(Color(0xFF2C3E5A)))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable { onTrimRight() }, contentAlignment = Alignment.Center) {
                        Text("Trim R ▶", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Pedal Mode
    SettingSection(
        title = "Pedal Input Mode",
        desc = "Choose between progressive analog feathering or direct digital instant-action."
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        SegmentedControl(
            options = listOf("Analog (Slide)", "Digital (Tap)"),
            selectedIndex = if (settings.pedalMode == PedalMode.ANALOG) 0 else 1,
            onOptionSelected = { onSettingsChanged { s -> s.copy(pedalMode = if (it == 0) PedalMode.ANALOG else PedalMode.DIGITAL) } }
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Speedometer Unit
    SettingSection(
        title = "Speedometer Unit",
        desc = "Choose between Imperial or Metric readout for live HUD telemetry."
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        SegmentedControl(
            options = listOf("MPH", "KM/H"),
            selectedIndex = if (settings.speedUnit == SpeedUnit.MPH) 0 else 1,
            onOptionSelected = { onSettingsChanged { s -> s.copy(speedUnit = if (it == 0) SpeedUnit.MPH else SpeedUnit.KMH) } }
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Diagnostics
    SettingSection(
        title = "Hardware & Diagnostics",
        desc = "Live sensor telemetry and hardware protocol readout."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "Sensor: Tilt ${String.format("%.1f", rawTiltDeg)}° | Lock: ${settings.maxSteeringAngle}° | One-Euro Filter Active",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextWhite
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DiagnosticItem("GYRO", "ROTATION VECTOR", ForzaOrange)
                DiagnosticItem("HAPTICS", "15ms TACTILE", ForzaOrange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DiagnosticItem("PROTOCOL", "7-BYTE BINARY", ForzaOrange)
                DiagnosticItem("TELEMETRY", "13-BYTE STREAM", ForzaOrange)
            }
        }
    }
}

@Composable
fun SettingSection(
    title: String,
    desc: String,
    visualization: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = if (visualization != null) 16.dp else 0.dp)) {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ForzaOrange)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = desc, fontSize = 11.sp, color = Color(0xFFA0AAB5), lineHeight = 14.sp)
            }
            if (visualization != null) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    visualization()
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
fun PresetPill(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) ForzaOrange else Color(0xFF1E283C))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.Black else TextWhite
        )
    }
}

@Composable
private fun SettingsSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    stepSize: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF1E283C)).clickable { 
                onValueChange((value - stepSize).coerceIn(valueRange))
            },
            contentAlignment = Alignment.Center
        ) {
            Text("-", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            colors = SliderDefaults.colors(
                thumbColor = ForzaOrange,
                activeTrackColor = ForzaOrange,
                inactiveTrackColor = Color(0xFF2C3E5A),
                activeTickColor = Color(0xFF1A263C),
                inactiveTickColor = ForzaOrange.copy(alpha = 0.5f)
            )
        )
        
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF1E283C)).clickable { 
                onValueChange((value + stepSize).coerceIn(valueRange))
            },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E283C))
            .border(1.dp, Color(0xFF2C3E5A), RoundedCornerShape(8.dp))
    ) {
        options.forEachIndexed { index, text ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isSelected) ForzaOrange else Color.Transparent)
                    .clickable { onOptionSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = if (isSelected) Color.Black else TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun TiltDiagram(tiltAngle: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val w = size.width * 0.4f
        val h = size.height * 0.7f
        
        // Draw reference dashed circle/arc
        drawCircle(
            color = Color(0xFF2C3E5A),
            radius = size.width * 0.45f,
            style = Stroke(width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        )
        
        rotate(degrees = tiltAngle, pivot = center) {
            drawRoundRect(
                color = ForzaOrange,
                topLeft = Offset(center.x - w / 2, center.y - h / 2),
                size = androidx.compose.ui.geometry.Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
            // Screen dot
            drawCircle(
                color = ForzaOrange,
                radius = 2.dp.toPx(),
                center = Offset(center.x, center.y - h / 2 + 6.dp.toPx())
            )
        }
    }
}

@Composable
private fun LinearityCurve(exponent: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 2.dp.toPx()
        val path = Path()
        val steps = 20
        
        // Draw axes
        drawLine(Color(0xFF2C3E5A), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth)
        drawLine(Color(0xFF2C3E5A), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth)

        // Draw curve
        for (i in 0..steps) {
            val t = i / steps.toFloat() // 0 to 1
            // map t to -1 to 1
            val x = (t * 2) - 1
            // apply exponent: sign(x) * |x|^exponent
            val y = kotlin.math.sign(x) * kotlin.math.abs(x).toDouble().let { Math.pow(it, exponent.toDouble()) }.toFloat()
            
            // Map back to canvas coords
            val px = t * size.width
            val py = size.height / 2 - (y * size.height / 2)
            
            if (i == 0) path.moveTo(px, py)
            else path.lineTo(px, py)
        }
        
        drawPath(
            path = path,
            color = ForzaOrange,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun DiagnosticItem(label: String, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text = "$label: ", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA0AAB5))
        Text(text = value, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = color)
    }
}
