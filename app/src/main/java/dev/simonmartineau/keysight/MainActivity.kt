package dev.simonmartineau.keysight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.simonmartineau.keysight.ui.home.HomeScreen
import dev.simonmartineau.keysight.ui.theme.KeySightTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KeySightTheme {
                HomeScreen()
            }
        }
    }
}
