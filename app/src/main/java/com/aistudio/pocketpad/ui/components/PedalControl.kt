package com.aistudio.pocketpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pocketpad.model.ButtonId
import com.aistudio.pocketpad.ui.theme.ForzaCyan
import com.aistudio.pocketpad.ui.theme.ForzaGreen
import com.aistudio.pocketpad.ui.theme.ForzaMagenta
import com.aistudio.pocketpad.ui.theme.ForzaYellow
import com.aistudio.pocketpad.ui.theme.TextMuted
import com.aistudio.pocketpad.ui.theme.TextWhite

@Composable
fun LeftBrakeCluster(
    brakeVal: Float,
    activeButtons: Set<ButtonId>,
    onBrakeChange: (Float) -> Unit,
    onButtonPress: (ButtonId) -> Unit,
    onButtonRelease: (ButtonId) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shift Down & Clutch Column
        Column(
            modifier = Modifier.width(90.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Shift Down Button
            ShiftDownButton(
                isActive = activeButtons.contains(ButtonId.X),
                onDown = { onButtonPress(ButtonId.X) },
                onUp = { onButtonRelease(ButtonId.X) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .testTag("paddle_shift_down")
            )

            // Clutch Button
            ClutchButton(
                isActive = activeButtons.contains(ButtonId.LB),
                onDown = { onButtonPress(ButtonId.LB) },
                onUp = { onButtonRelease(ButtonId.LB) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .testTag("btn_clutch")
            )
        }

        // Brake Pedal Plate
        CockpitPedalPlate(
            title = "BRAKE",
            hint = "SLIDE / TAP (LT)",
            value = brakeVal,
            titleColor = Color(0xFFFF5B00), // ForzaOrange
            onValueChange = onBrakeChange,
            testTag = "brake_pedal_zone",
            modifier = Modifier
                .width(96.dp)
                .height(160.dp)
        )
    }
}

@Composable
fun RightThrottleCluster(
    throttleVal: Float,
    activeButtons: Set<ButtonId>,
    onThrottleChange: (Float) -> Unit,
    onButtonPress: (ButtonId) -> Unit,
    onButtonRelease: (ButtonId) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Throttle Pedal Plate
        CockpitPedalPlate(
            title = "THROTTLE",
            hint = "SLIDE / TAP (RT)",
            value = throttleVal,
            titleColor = Color(0xFFFF5B00), // ForzaOrange
            onValueChange = onThrottleChange,
            testTag = "throttle_pedal_zone",
            modifier = Modifier
                .width(96.dp)
                .height(160.dp)
        )

        // Shift Up & E-Brake Column
        Column(
            modifier = Modifier.width(90.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Shift Up Button
            ShiftUpButton(
                isActive = activeButtons.contains(ButtonId.B),
                onDown = { onButtonPress(ButtonId.B) },
                onUp = { onButtonRelease(ButtonId.B) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .testTag("paddle_shift_up")
            )

            // E-Brake Button
            EBrakeButton(
                isActive = activeButtons.contains(ButtonId.A),
                onDown = { onButtonPress(ButtonId.A) },
                onUp = { onButtonRelease(ButtonId.A) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .testTag("btn_handbrake")
            )
        }
    }
}

@Composable
fun CockpitPedalPlate(
    title: String,
    hint: String,
    value: Float,
    titleColor: Color,
    onValueChange: (Float) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Transparent)
            .border(1.dp, Color(0xFFFF5B00), RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var pct = 1.0f - (down.position.y / size.height).coerceIn(0f, 1f)
                    if (pct >= 0.99f) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onValueChange(pct)

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null && change.pressed) {
                            val newPct = 1.0f - (change.position.y / size.height).coerceIn(0f, 1f)
                            if (newPct >= 0.99f && pct < 0.99f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            pct = newPct
                            onValueChange(pct)
                        }
                    } while (event.changes.any { it.id == down.id && it.pressed })

                    onValueChange(0f)
                }
            }
    ) {
        // Dynamic bottom-to-top gradient fill based on input percentage
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(value.coerceIn(0f, 1f))
                .background(titleColor.copy(alpha = 0.8f + (0.2f * value))) // Active glowing fill color
        )

        // Pedal layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Title & Value
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    color = titleColor
                )
                Text(
                    text = "${(value * 100).toInt()}%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = TextWhite
                )
            }

            // Bottom Hint
            Text(
                text = hint,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun ShiftDownButton(
    isActive: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) Color(0xFFEA580C) else Color.Transparent)
            .border(1.dp, if (isActive) Color(0xFFFF5B00) else Color(0xFF401804), RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onDown()
                        tryAwaitRelease()
                        onUp()
                    }
                )
            }
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "▼ DOWN",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = if (isActive) Color.Black else TextWhite
            )
            Text(
                text = "(X)",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color.Black.copy(alpha=0.7f) else Color(0xFFA0AAB5)
            )
        }
    }
}

@Composable
private fun ShiftUpButton(
    isActive: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) Color(0xFFEA580C) else Color.Transparent)
            .border(1.dp, if (isActive) Color(0xFFFF5B00) else Color(0xFF401804), RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onDown()
                        tryAwaitRelease()
                        onUp()
                    }
                )
            }
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "▲ UP",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = if (isActive) Color.Black else TextWhite
            )
            Text(
                text = "(B)",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color.Black.copy(alpha=0.7f) else Color(0xFFA0AAB5)
            )
        }
    }
}

@Composable
private fun ClutchButton(
    isActive: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) Color(0xFFEA580C) else Color.Transparent)
            .border(1.dp, if (isActive) Color(0xFFFF5B00) else Color(0xFF401804), RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onDown()
                        tryAwaitRelease()
                        onUp()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "CLUTCH (LB)",
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color.Black else Color(0xFFA0AAB5)
        )
    }
}

@Composable
private fun EBrakeButton(
    isActive: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) Color(0xFFEA580C) else Color.Transparent)
            .border(1.dp, if (isActive) Color(0xFFFF5B00) else Color(0xFF401804), RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onDown()
                        tryAwaitRelease()
                        onUp()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "E-BRAKE",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Black,
                color = if (isActive) Color.Black else TextWhite
            )
            Text(
                text = "(A - DRIFT)",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color.Black.copy(alpha=0.7f) else Color(0xFFF97316)
            )
        }
    }
}
