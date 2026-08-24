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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aistudio.pocketpad.model.ButtonId
import com.aistudio.pocketpad.ui.theme.DarkBackground

@Composable
fun FPSMouse(
    activeButtons: Set<ButtonId>,
    onButtonPress: (ButtonId) -> Unit,
    onButtonRelease: (ButtonId) -> Unit,
    onStickChange: (Boolean, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top triggers (Left Click = RT, Right Click = LT, wait, in typical gamepads RT is primary fire)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TriggerShoulderButton(
                title = "LT (Aim)",
                isActive = activeButtons.contains(ButtonId.LB),
                onDown = { onButtonPress(ButtonId.LB) },
                onUp = { onButtonRelease(ButtonId.LB) },
                testTag = "btn_lt_aim",
                modifier = Modifier.size(100.dp, 40.dp)
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SystemPillButton(
                    title = "RELOAD (X)",
                    isActive = activeButtons.contains(ButtonId.X),
                    onDown = { onButtonPress(ButtonId.X) },
                    onUp = { onButtonRelease(ButtonId.X) }
                )
                SystemPillButton(
                    title = "JUMP (A)",
                    isActive = activeButtons.contains(ButtonId.A),
                    onDown = { onButtonPress(ButtonId.A) },
                    onUp = { onButtonRelease(ButtonId.A) }
                )
            }

            TriggerShoulderButton(
                title = "RT (Fire)",
                isActive = activeButtons.contains(ButtonId.RB),
                onDown = { onButtonPress(ButtonId.RB) },
                onUp = { onButtonRelease(ButtonId.RB) },
                testTag = "btn_rt_fire",
                modifier = Modifier.size(100.dp, 40.dp)
            )
        }

        // Main controls: Left Stick (Movement) and Right Stick (Aiming)
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Movement Stick
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnalogThumbstick(
                    label = "MOVE",
                    onStickMoved = { x, y -> onStickChange(true, x, y) },
                    modifier = Modifier.size(160.dp),
                    testTag = "fps_stick_left"
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))

            // Aiming Stick
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnalogThumbstick(
                    label = "AIM",
                    onStickMoved = { x, y -> onStickChange(false, x, y) },
                    modifier = Modifier.size(160.dp),
                    testTag = "fps_stick_right"
                )
            }
        }
    }
}
