package com.aistudio.pocketpad

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aistudio.pocketpad.ui.PocketPadScreen
import com.aistudio.pocketpad.ui.theme.PocketPadTheme
import com.aistudio.pocketpad.viewmodel.PocketPadViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PocketPadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen awake for active racing cockpit session
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Immersive sticky mode to hide nav and status bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        enableEdgeToEdge()

        setContent {
            PocketPadTheme {
                PocketPadScreen(viewModel = viewModel)
            }
        }
    }
}
