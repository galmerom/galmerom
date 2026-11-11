package com.galmerom.botbekalut.ui.theme

import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.galmerom.botbekalut.domain.FontSizeId
import com.galmerom.botbekalut.domain.ThemeId

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun lightScheme(
    primary: Color,
    container: Color,
    background: Color,
    surface: Color,
) = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = container,
    onPrimaryContainer = Ink,
    secondary = Muted,
    onSecondary = Color.White,
    secondaryContainer = container,
    onSecondaryContainer = Ink,
    tertiary = primary,
    onTertiary = Color.White,
    background = background,
    onBackground = Ink,
    surface = surface,
    onSurface = Ink,
    surfaceVariant = container,
    onSurfaceVariant = Muted,
    surfaceTint = Color.Transparent,
    outline = OutlineBlue,
    outlineVariant = OutlineSoft,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorSoft,
    onErrorContainer = Color(0xFF5C241E),
)

private fun colorsFor(theme: ThemeId) = when (theme) {
    ThemeId.Blue -> lightScheme(Blue, BlueSoft, BlueBackground, BlueSurface)
    ThemeId.Ocean -> lightScheme(Ocean, OceanSoft, OceanBackground, OceanSurface)
    ThemeId.Sage -> lightScheme(Sage, SageSoft, SageBackground, SageSurface)
    ThemeId.Lilac -> lightScheme(Lilac, LilacSoft, LilacBackground, LilacSurface)
    ThemeId.Warm -> lightScheme(Warm, WarmSoft, WarmBackground, WarmSurface)
}

fun themeSwatch(theme: ThemeId): Color = when (theme) {
    ThemeId.Blue -> Blue
    ThemeId.Ocean -> Ocean
    ThemeId.Sage -> Sage
    ThemeId.Lilac -> Lilac
    ThemeId.Warm -> Warm
}

@Composable
fun BotBekalutTheme(
    theme: ThemeId,
    font: FontSizeId,
    content: @Composable () -> Unit,
) {
    val colors = colorsFor(theme)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<ComponentActivity>()
                .firstOrNull() ?: return@SideEffect
            val background = colors.background.toArgb()
            val surface = colors.surface.toArgb()
            activity.window.setBackgroundDrawable(ColorDrawable(background))
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(background, background),
                navigationBarStyle = SystemBarStyle.light(surface, surface),
            )
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalDensity provides Density(density.density, density.fontScale * font.multiplier),
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
