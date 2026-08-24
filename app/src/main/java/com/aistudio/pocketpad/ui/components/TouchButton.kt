package com.aistudio.pocketpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun TouchButton(
    title: String,
    subtitle: String,
    isActive: Boolean,
    activeColor: Color,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    isVisible: Boolean = true
) {
    if (!isVisible) return

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) activeColor else Color(0xFF131A29))
            .border(
                1.5.dp,
                if (isActive) activeColor.copy(alpha = 0.5f) else Color(0xFF243248),
                RoundedCornerShape(8.dp)
            )
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
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = if (isActive) Color.Black else Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color.Black.copy(alpha = 0.7f) else Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}
