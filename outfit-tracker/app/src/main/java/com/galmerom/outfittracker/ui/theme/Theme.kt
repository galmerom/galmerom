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
import com.galmerom.outfittracker.data.AppTheme
import com.galmerom.outfittracker.ui.LocalAppTheme
import com.galmerom.outfittracker.ui.LocalFontScale
import androidx.core.view.WindowCompat

private fun lightScheme(primary: Color, container: Color) = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = container,
    onPrimaryContainer = Charcoal,
    secondary = WarmGray,
    onSecondary = Color.White,
    secondaryContainer = CreamDeep,
    onSecondaryContainer = Charcoal,
    tertiary = primary,
    onTertiary = Color.White,
    tertiaryContainer = container,
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

private val NightColors = androidx.compose.material3.darkColorScheme(
    primary = NightAccent,
    onPrimary = NightBackground,
    primaryContainer = NightAccentSoft,
    onPrimaryContainer = NightText,
    secondary = NightMuted,
    onSecondary = NightBackground,
    secondaryContainer = NightSurface,
    onSecondaryContainer = NightText,
    tertiary = NightAccent,
    onTertiary = NightBackground,
    tertiaryContainer = NightAccentSoft,
    onTertiaryContainer = NightText,
    background = NightBackground,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = Color(0xFF3A332F),
    onSurfaceVariant = NightMuted,
    surfaceTint = Color.Transparent,
    outline = Color(0xFF6E625A),
    outlineVariant = Color(0xFF4A433E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private fun colorsFor(theme: AppTheme) = when (theme) {
    AppTheme.WARM -> lightScheme(Accent, AccentSoft)
    AppTheme.OCEAN -> lightScheme(Ocean, OceanSoft)
    AppTheme.SAGE -> lightScheme(Sage, SageSoft)
    AppTheme.LILAC -> lightScheme(Lilac, LilacSoft)
    AppTheme.NIGHT -> NightColors
}

@Composable
fun OutfitTrackerTheme(content: @Composable () -> Unit) {
    val theme = LocalAppTheme.current
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            val lightBars = !theme.dark
            controller.isAppearanceLightStatusBars = lightBars
            controller.isAppearanceLightNavigationBars = lightBars
        }
    }
    val density = LocalDensity.current
    val fontScale = LocalFontScale.current
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalDensity provides Density(density.density, density.fontScale * fontScale),
    ) {
        MaterialTheme(
            colorScheme = colorsFor(theme),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
