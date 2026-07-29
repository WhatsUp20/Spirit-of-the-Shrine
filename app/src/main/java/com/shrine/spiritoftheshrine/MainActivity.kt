package com.shrine.spiritoftheshrine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shrine.spiritoftheshrine.game.GameScreen
import com.shrine.spiritoftheshrine.ui.theme.SpiritOfTheShrineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpiritOfTheShrineTheme {
                GameScreen()
            }
        }
    }
}
