package com.aistudio.pocketpad.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pocketpad.model.ButtonId
import com.aistudio.pocketpad.ui.theme.ForzaCyan
import com.aistudio.pocketpad.ui.theme.ForzaGreen
import com.aistudio.pocketpad.ui.theme.ForzaMagenta
import com.aistudio.pocketpad.ui.theme.ForzaYellow
import com.aistudio.pocketpad.ui.theme.SurfaceDark
import com.aistudio.pocketpad.ui.theme.TextMuted
import com.aistudio.pocketpad.ui.theme.TextWhite
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun StandardGamepad(
    activeButtons: Set<ButtonId>,
    throttleVal: Float,
    brakeVal: Float,
    onButtonPress: (ButtonId) -> Unit,
    onButtonRelease: (ButtonId) -> Unit,
    onTriggerChange: (Boolean, Float) -> Unit, // (isLeft, val)
    onStickChange: (Boolean, Float, Float) -> Unit, // (isLeft, x, y)
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. TOP SHOULDER & SYSTEM ROW: [LT] [LB]   [SELECT] (X) [START]   [RB] [RT]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Shoulders (LT, LB)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TriggerShoulderButton(
                    title = "LT",
                    isActive = brakeVal > 0.05f,
                    onDown = { onTriggerChange(true, 1f) },
                    onUp = { onTriggerChange(true, 0f) },
                    testTag = "btn_lt"
                )
                BumperShoulderButton(
                    title = "LB",
                    isActive = activeButtons.contains(ButtonId.LB),
                    onDown = { onButtonPress(ButtonId.LB) },
                    onUp = { onButtonRelease(ButtonId.LB) },
                    testTag = "btn_lb"
                )
            }

            // Center System Buttons: [ SELECT ]  ( X )  [ START ]
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SystemPillButton(
                    title = "SELECT",
                    isActive = activeButtons.contains(ButtonId.BACK),
                    onDown = { onButtonPress(ButtonId.BACK) },
                    onUp = { onButtonRelease(ButtonId.BACK) }
                )

                // Xbox Guide Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (activeButtons.contains(ButtonId.GUIDE)) SolidColor(Color(0xFF388BFD))
                            else Brush.radialGradient(listOf(Color(0xFF238636), Color(0xFF107C11)))
                        )
                        .border(1.dp, Color(0xFF2EA043), CircleShape)
                        .shadow(if (activeButtons.contains(ButtonId.GUIDE)) 14.dp else 10.dp, spotColor = if (activeButtons.contains(ButtonId.GUIDE)) Color(0x99388BFD) else Color(0x662EA043))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onButtonPress(ButtonId.GUIDE)
                                    tryAwaitRelease()
                                    onButtonRelease(ButtonId.GUIDE)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "X",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                SystemPillButton(
                    title = "START",
                    isActive = activeButtons.contains(ButtonId.START),
                    onDown = { onButtonPress(ButtonId.START) },
                    onUp = { onButtonRelease(ButtonId.START) }
                )
            }

            // Right Shoulders (RB, RT)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BumperShoulderButton(
                    title = "RB",
                    isActive = activeButtons.contains(ButtonId.RB),
                    onDown = { onButtonPress(ButtonId.RB) },
                    onUp = { onButtonRelease(ButtonId.RB) },
                    testTag = "btn_rb"
                )
                TriggerShoulderButton(
                    title = "RT",
                    isActive = throttleVal > 0.05f,
                    onDown = { onTriggerChange(false, 1f) },
                    onUp = { onTriggerChange(false, 0f) },
                    testTag = "btn_rt"
                )
            }
        }

        // 2. MAIN QUAD CONTROLS: Left Stick + D-Pad | ABXY + Right Stick
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Quadrant: Left Stick LS + D-Pad
            Row(
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnalogThumbstick(
                    label = "LS",
                    onStickMoved = { x, y -> onStickChange(true, x, y) },
                    modifier = Modifier.size(140.dp),
                    testTag = "stick_left"
                )

                DPadCluster(
                    activeButtons = activeButtons,
                    onButtonPress = onButtonPress,
                    onButtonRelease = onButtonRelease
                )
            }

            // Right Quadrant: ABXY Cluster + Right Stick RS
            Row(
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ABXYCluster(
                    activeButtons = activeButtons,
                    onButtonPress = onButtonPress,
                    onButtonRelease = onButtonRelease
                )

                AnalogThumbstick(
                    label = "RS",
                    onStickMoved = { x, y -> onStickChange(false, x, y) },
                    modifier = Modifier.size(140.dp),
                    testTag = "stick_right"
                )
            }
        }
    }
}

@Composable
fun TriggerShoulderButton(
    title: String,
    isActive: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .size(width = 78.dp, height = 38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) SolidColor(Color(0xFF388BFD)) else Brush.verticalGradient(listOf(Color(0xFF21262D), Color(0xFF161B22))))
            .border(1.dp, if (isActive) Color(0xFF388BFD) else Color(0xFF30363D), RoundedCornerShape(12.dp))
            .shadow(if (isActive) 14.dp else 6.dp, spotColor = if (isActive) Color(0x99388BFD) else Color(0x4D000000))
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
        if (!isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(Color(0xFF58A6FF))
            )
        }
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (isActive) Color.White else Color(0xFFF0F6FC))
    }
}

@Composable
fun BumperShoulderButton(
    title: String,
    isActive: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .size(width = 68.dp, height = 38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) SolidColor(Color(0xFF388BFD)) else Brush.verticalGradient(listOf(Color(0xFF21262D), Color(0xFF161B22))))
            .border(1.dp, if (isActive) Color(0xFF388BFD) else Color(0xFF30363D), RoundedCornerShape(12.dp))
            .shadow(if (isActive) 14.dp else 6.dp, spotColor = if (isActive) Color(0x99388BFD) else Color(0x4D000000))
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
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (isActive) Color.White else Color(0xFFF0F6FC))
    }
}

@Composable
fun SystemPillButton(
    title: String,
    isActive: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) Color(0xFF388BFD) else Color(0x0DFFFFFF))
            .border(1.dp, if (isActive) Color(0xFF388BFD) else Color(0xFF30363D), RoundedCornerShape(12.dp))
            .shadow(if (isActive) 14.dp else 0.dp, spotColor = if (isActive) Color(0x99388BFD) else Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onDown()
                        tryAwaitRelease()
                        onUp()
                    }
                )
            }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = if (isActive) Color.White else Color(0xFFF0F6FC))
    }
}

@Composable
fun AnalogThumbstick(
    label: String,
    onStickMoved: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    var thumbOffsetX by remember { mutableFloatStateOf(0f) }
    var thumbOffsetY by remember { mutableFloatStateOf(0f) }
    val isActive = thumbOffsetX != 0f || thumbOffsetY != 0f

    Box(
        modifier = modifier
            .testTag(testTag)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF1A2230), Color(0xFF0D1117))))
            .border(2.dp, Color(0xFF30363D), CircleShape)
            .shadow(12.dp, spotColor = Color(0x66000000))
            .pointerInput(Unit) {
                val radiusPx = size.width / 2f - 31.dp.toPx()
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newX = thumbOffsetX + dragAmount.x
                        val newY = thumbOffsetY + dragAmount.y
                        val dist = hypot(newX, newY)
                        if (dist <= radiusPx) {
                            thumbOffsetX = newX
                            thumbOffsetY = newY
                        } else {
                            val ratio = radiusPx / dist
                            thumbOffsetX = newX * ratio
                            thumbOffsetY = newY * ratio
                        }
                        onStickMoved(thumbOffsetX / radiusPx, -thumbOffsetY / radiusPx)
                    },
                    onDragEnd = {
                        thumbOffsetX = 0f
                        thumbOffsetY = 0f
                        onStickMoved(0f, 0f)
                    },
                    onDragCancel = {
                        thumbOffsetX = 0f
                        thumbOffsetY = 0f
                        onStickMoved(0f, 0f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Draggable Knob
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffsetX.roundToInt(), thumbOffsetY.roundToInt()) }
                .size(62.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF2D333B), Color(0xFF1C2128))))
                .border(2.dp, if (isActive) Color(0xFF58A6FF) else Color(0xFF444C56), CircleShape)
                .shadow(10.dp, spotColor = if (isActive) Color(0x8058A6FF) else Color(0x80000000)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B949E))
        }
    }
}

@Composable
fun DPadCluster(
    activeButtons: Set<ButtonId>,
    onButtonPress: (ButtonId) -> Unit,
    onButtonRelease: (ButtonId) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(130.dp),
        contentAlignment = Alignment.Center
    ) {
        // Cross Arms
        // Up
        DPadArmButton(
            symbol = "▲",
            buttonId = ButtonId.DPAD_UP,
            isActive = activeButtons.contains(ButtonId.DPAD_UP),
            onPress = onButtonPress,
            onRelease = onButtonRelease,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 40.dp, height = 46.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
        )
        // Down
        DPadArmButton(
            symbol = "▼",
            buttonId = ButtonId.DPAD_DOWN,
            isActive = activeButtons.contains(ButtonId.DPAD_DOWN),
            onPress = onButtonPress,
            onRelease = onButtonRelease,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 40.dp, height = 46.dp)
                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
        )
        // Left
        DPadArmButton(
            symbol = "◀",
            buttonId = ButtonId.DPAD_LEFT,
            isActive = activeButtons.contains(ButtonId.DPAD_LEFT),
            onPress = onButtonPress,
            onRelease = onButtonRelease,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 46.dp, height = 40.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
        )
        // Right
        DPadArmButton(
            symbol = "▶",
            buttonId = ButtonId.DPAD_RIGHT,
            isActive = activeButtons.contains(ButtonId.DPAD_RIGHT),
            onPress = onButtonPress,
            onRelease = onButtonRelease,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = 46.dp, height = 40.dp)
                .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
        )

        // Center hub box
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF161B22))
        )
    }
}

@Composable
fun DPadArmButton(
    symbol: String,
    buttonId: ButtonId,
    isActive: Boolean,
    onPress: (ButtonId) -> Unit,
    onRelease: (ButtonId) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(if (isActive) Color(0xFF388BFD) else Color(0xFF161B22))
            .border(1.dp, if (isActive) Color(0xFF388BFD) else Color(0xFF30363D))
            .shadow(if (isActive) 12.dp else 0.dp, spotColor = if (isActive) Color(0xB3388BFD) else Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress(buttonId)
                        tryAwaitRelease()
                        onRelease(buttonId)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, fontSize = 16.sp, color = if (isActive) Color.White else Color(0xFFF0F6FC))
    }
}

@Composable
fun ABXYCluster(
    activeButtons: Set<ButtonId>,
    onButtonPress: (ButtonId) -> Unit,
    onButtonRelease: (ButtonId) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(130.dp),
        contentAlignment = Alignment.Center
    ) {
        // Y Button (Top - Yellow)
        ActionRoundButton(
            label = "Y",
            ringColor = Color(0xFFF1C40F),
            buttonId = ButtonId.Y,
            isActive = activeButtons.contains(ButtonId.Y),
            onPress = onButtonPress,
            onRelease = onButtonRelease,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        // A Button (Bottom - Green)
        ActionRoundButton(
            label = "A",
            ringColor = Color(0xFF2ECC71),
            buttonId = ButtonId.A,
            isActive = activeButtons.contains(ButtonId.A),
            onPress = onButtonPress,
            onRelease = onButtonRelease,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        // X Button (Left - Blue)
        ActionRoundButton(
            label = "X",
            ringColor = Color(0xFF3498DB),
            buttonId = ButtonId.X,
            isActive = activeButtons.contains(ButtonId.X),
            onPress = onButtonPress,
            onRelease = onButtonRelease,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        // B Button (Right - Red)
        ActionRoundButton(
            label = "B",
            ringColor = Color(0xFFE74C3C),
            buttonId = ButtonId.B,
            isActive = activeButtons.contains(ButtonId.B),
            onPress = onButtonPress,
            onRelease = onButtonRelease,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
fun ActionRoundButton(
    label: String,
    ringColor: Color,
    buttonId: ButtonId,
    isActive: Boolean,
    onPress: (ButtonId) -> Unit,
    onRelease: (ButtonId) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (isActive) ringColor else Color(0xFF161B22))
            .border(2.dp, ringColor, CircleShape)
            .shadow(if (isActive) 16.dp else 8.dp, spotColor = if (isActive) ringColor else Color(0x66000000))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress(buttonId)
                        tryAwaitRelease()
                        onRelease(buttonId)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isActive) Color.White else ringColor
        )
    }
}
