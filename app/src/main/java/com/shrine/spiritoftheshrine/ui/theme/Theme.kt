package com.shrine.spiritoftheshrine.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ShrineColorScheme = darkColorScheme(
    primary = Color(0xFFC9A227),
    secondary = Color(0xFF4A7C3A),
    background = Color(0xFF000000),
)

@Composable
fun SpiritOfTheShrineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShrineColorScheme,
        content = content,
    )
}
