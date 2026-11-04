package com.galmerom.outfittracker.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.galmerom.outfittracker.ui.LocalFontScale
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Charcoal,
    secondary = WarmGray,
    onSecondary = Color.White,
    secondaryContainer = CreamDeep,
    onSecondaryContainer = Charcoal,
    tertiary = Accent,
    onTertiary = Color.White,
    tertiaryContainer = AccentSoft,
    onTertiaryContainer = Charcoal,
    background = Cream,
    onBackground = Charcoal,
    surface = CardWhite,
    onSurface = Charcoal,
    surfaceVariant = CreamDeep,
    onSurfaceVariant = WarmGray,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = Cream,
    surfaceContainer = Cream,
    surfaceContainerHigh = CreamDeep,
    surfaceContainerHighest = CreamDeep,
    outline = OutlineWarm,
    outlineVariant = OutlineSoft,
    error = ErrorWarm,
    onError = Color.White,
    errorContainer = ErrorSoft,
    onErrorContainer = Color(0xFF5C241E),
    inverseSurface = Charcoal,
    inverseOnSurface = Cream,
    inversePrimary = Color(0xFFE7C7C0),
    scrim = Color(0xFF2A2420),
)

@Composable
fun OutfitTrackerTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }
    val density = LocalDensity.current
    val fontScale = LocalFontScale.current
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalDensity provides Density(density.density, density.fontScale * fontScale),
    ) {
        MaterialTheme(
            colorScheme = LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
