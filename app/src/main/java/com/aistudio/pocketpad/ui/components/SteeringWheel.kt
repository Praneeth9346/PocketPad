package com.aistudio.pocketpad.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SteeringWheel(
    visualAngleDeg: Float,
    gearString: String,
    isMotionEnabled: Boolean,
    onManualSteer: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragAngleOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .pointerInput(isMotionEnabled) {
                    if (!isMotionEnabled) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val initialAngle = (atan2(
                                    (offset.y - center.y).toDouble(),
                                    (offset.x - center.x).toDouble()
                                ) * (180.0 / PI)).toFloat()
                                dragAngleOffset = initialAngle
                            },
                            onDrag = { change, _ ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val currentAngle = (atan2(
                                    (change.position.y - center.y).toDouble(),
                                    (change.position.x - center.x).toDouble()
                                ) * (180.0 / PI)).toFloat()
                                val delta = currentAngle - dragAngleOffset
                                val newAngle = (visualAngleDeg + delta).coerceIn(-180f, 180f)
                                dragAngleOffset = currentAngle
                                onManualSteer(newAngle)
                            },
                            onDragEnd = { onManualSteer(0f) },
                            onDragCancel = { onManualSteer(0f) }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Background Canvas: Outer ring + Honeycomb inner
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(visualAngleDeg)
                    .testTag("steering_wheel_graphic")
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.minDimension / 2f - 2.dp.toPx()
                val innerRadius = outerRadius * 0.95f

                // Outer guide circle - ForzaOrange
                drawCircle(
                    color = Color(0xFFFF5B00),
                    radius = outerRadius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Honeycomb grid inside
                val clipCircle = Path().apply {
                    addOval(Rect(
                        left = center.x - innerRadius,
                        top = center.y - innerRadius,
                        right = center.x + innerRadius,
                        bottom = center.y + innerRadius
                    ))
                }

                clipPath(clipCircle) {
                    drawRect(color = Color(0xFF030303))

                    val hexRadius = 14.dp.toPx()
                    val hexWidth = Math.sqrt(3.0).toFloat() * hexRadius
                    val hexHeight = 2f * hexRadius

                    val cols = (innerRadius * 2 / hexWidth).toInt() + 3
                    val rows = (innerRadius * 2 / (hexHeight * 0.75f)).toInt() + 3

                    val path = Path()
                    for (row in -rows / 2..rows / 2) {
                        for (col in -cols / 2..cols / 2) {
                            val xOffset = col * hexWidth + if (row % 2 != 0) hexWidth / 2f else 0f
                            val yOffset = row * hexHeight * 0.75f

                            val cx = center.x + xOffset
                            val cy = center.y + yOffset

                            for (i in 0 until 6) {
                                val angle = i * 60f * (PI.toFloat() / 180f) + (30f * PI.toFloat() / 180f)
                                val px = cx + hexRadius * cos(angle)
                                val py = cy + hexRadius * sin(angle)
                                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                            }
                            path.close()
                        }
                    }

                    drawPath(path, color = Color(0xFF1E100A), style = Stroke(width = 1.dp.toPx()))
                }
            }

            // Fixed Center: Gear Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "GEAR",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFFFF5B00)
                )
                Text(
                    text = gearString,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
            }
        }
    }
}

