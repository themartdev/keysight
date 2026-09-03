package dev.simonmartineau.keysight

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.simonmartineau.keysight.ui.practice.PracticeScreen
import dev.simonmartineau.keysight.ui.theme.KeySightTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A practice session is hands-on-keys; the screen must not dim mid-attempt.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val container = (application as KeySightApplication).container
        setContent {
            KeySightTheme {
                PracticeScreen(container)
            }
        }
    }
}
