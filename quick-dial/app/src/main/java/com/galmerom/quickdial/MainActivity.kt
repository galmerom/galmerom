package com.galmerom.quickdial

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.galmerom.quickdial.ui.QuickDialApp
import com.galmerom.quickdial.ui.theme.BlueBackground
import com.galmerom.quickdial.ui.theme.BlueSurface
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(hebrew(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val background = BlueBackground.toArgb()
        val surface = BlueSurface.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(background, background),
            navigationBarStyle = SystemBarStyle.light(surface, surface),
        )
        setContent { QuickDialApp() }
    }
}

private fun hebrew(base: Context): Context {
    val locale = Locale.forLanguageTag("he")
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return base.createConfigurationContext(config)
}
