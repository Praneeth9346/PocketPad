package com.aistudio.pocketpad.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aistudio.pocketpad.ui.theme.ForzaGreen
import com.aistudio.pocketpad.ui.theme.ForzaMagenta
import com.aistudio.pocketpad.ui.theme.ForzaYellow

@Composable
fun ShiftLightBar(
    shiftPct: Int,
    modifier: Modifier = Modifier
) {
    val isRedline = shiftPct >= 96

    val infiniteTransition = rememberInfiniteTransition(label = "strobe")
    val strobeAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(60, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "strobeAlpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF090D15))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (idx in 0 until 10) {
                val baseColor = when {
                    idx < 4 -> ForzaGreen // 0..3: Green LEDs
                    idx < 7 -> ForzaYellow // 4..6: Yellow LEDs
                    else -> ForzaMagenta // 7..9: Red/Magenta LEDs
                }

                val threshold = when {
                    idx < 4 -> 50 + idx * 4
                    idx < 7 -> 68 + ((idx - 4) * 4.5).toInt()
                    else -> 82 + (idx - 7) * 4
                }

                val isActive = shiftPct >= threshold

                val dotColor by animateColorAsState(
                    targetValue = when {
                        isRedline -> ForzaMagenta.copy(alpha = strobeAlpha)
                        isActive -> baseColor
                        else -> Color(0xFF1A2234)
                    },
                    animationSpec = tween(50),
                    label = "ledColor"
                )

                val glowModifier = if (isActive || isRedline) {
                    Modifier.shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = dotColor,
                        spotColor = dotColor
                    )
                } else Modifier

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .then(glowModifier)
                        .clip(CircleShape)
                        .background(dotColor)
                        .border(
                            0.5.dp,
                            if (isActive || isRedline) Color.White.copy(alpha = 0.6f) else Color.Transparent,
                            CircleShape
                        )
                )
            }
        }
    }
}
