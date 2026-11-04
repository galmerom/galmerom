package com.galmerom.outfittracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D4C41),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEDFD6),
    onPrimaryContainer = Color(0xFF2C1810),
    secondary = Color(0xFF8D6E63),
    onSecondary = Color.White,
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF2C1810),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF2C1810),
)

@Composable
fun OutfitTrackerTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = LightColors,
            content = content,
        )
    }
}
