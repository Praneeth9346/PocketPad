package com.aistudio.pocketpad.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pocketpad.ui.theme.ForzaCyan
import com.aistudio.pocketpad.ui.theme.ForzaMagenta
import com.aistudio.pocketpad.ui.theme.ForzaYellow
import com.aistudio.pocketpad.ui.theme.TextMuted
import com.aistudio.pocketpad.ui.theme.TextWhite
import kotlin.math.roundToInt

@Composable
fun ClusterDragHandle(
    title: String,
    offsetX: Float,
    onOffsetChange: (Float) -> Unit,
    minOffsetX: Float = -70f,
    maxOffsetX: Float = 70f,
    onSwap: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    accentColor: Color = ForzaCyan,
    testTag: String = "cluster_drag_handle"
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }

    val handleBorderColor by animateColorAsState(
        targetValue = if (isDragging) accentColor else Color(0xFF243248),
        label = "handleBorder"
    )

    val handleBgColor by animateColorAsState(
        targetValue = if (isDragging) accentColor.copy(alpha = 0.2f) else Color(0xFF0F1523),
        label = "handleBg"
    )

    Box(
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(8.dp))
            .background(handleBgColor)
            .border(1.dp, handleBorderColor, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val deltaDp = with(density) { dragAmount.x.toDp().value }
                        val newOffset = (offsetX + deltaDp).coerceIn(minOffsetX, maxOffsetX)
                        onOffsetChange(newOffset)
                    }
                )
            }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Drag grip bars icon + title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "⠿",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isDragging) accentColor else ForzaYellow
                )
                Text(
                    text = title,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDragging) TextWhite else TextMuted
                )
                if (offsetX.roundToInt() != 0) {
                    Text(
                        text = "${if (offsetX > 0) "+" else ""}${offsetX.roundToInt()}dp",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = accentColor
                    )
                }
            }

            // Quick Actions: Swap & Reset
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onSwap != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1E283C))
                            .clickable { onSwap() }
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⇄ SWAP",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = ForzaCyan
                        )
                    }
                }

                if (offsetX.roundToInt() != 0 && onReset != null) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF1E283C))
                            .clickable { onReset() }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "↺",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WheelDragHandle(
    offsetX: Float,
    offsetY: Float,
    onOffsetChange: (Float, Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }

    val handleBorderColor by animateColorAsState(
        targetValue = if (isDragging) ForzaCyan else Color(0xFF243248),
        label = "wheelHandleBorder"
    )

    val handleBgColor by animateColorAsState(
        targetValue = if (isDragging) ForzaCyan.copy(alpha = 0.2f) else Color(0xFF0F1523),
        label = "wheelHandleBg"
    )

    Box(
        modifier = modifier
            .testTag("wheel_drag_handle")
            .clip(RoundedCornerShape(8.dp))
            .background(handleBgColor)
            .border(1.dp, handleBorderColor, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val deltaX = with(density) { dragAmount.x.toDp().value }
                        val deltaY = with(density) { dragAmount.y.toDp().value }
                        val newX = (offsetX + deltaX).coerceIn(-60f, 60f)
                        val newY = (offsetY + deltaY).coerceIn(-35f, 35f)
                        onOffsetChange(newX, newY)
                    }
                )
            }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "⠿",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = if (isDragging) ForzaCyan else ForzaYellow
            )
            Text(
                text = "WHEEL POSITION",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDragging) TextWhite else TextMuted
            )
            if (offsetX.roundToInt() != 0 || offsetY.roundToInt() != 0) {
                Text(
                    text = "X:${if (offsetX > 0) "+" else ""}${offsetX.roundToInt()} Y:${if (offsetY > 0) "+" else ""}${offsetY.roundToInt()}",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = ForzaCyan
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF1E283C))
                        .clickable { onReset() }
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "↺",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                }
            }
        }
    }
}
