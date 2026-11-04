package com.galmerom.outfittracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galmerom.outfittracker.data.UserPrefs
import com.galmerom.outfittracker.ui.LocalAppContainer
import com.galmerom.outfittracker.ui.LocalAppTheme
import com.galmerom.outfittracker.ui.LocalFontScale
import com.galmerom.outfittracker.ui.navigation.OutfitTrackerRoot
import com.galmerom.outfittracker.ui.theme.CardWhite
import com.galmerom.outfittracker.ui.theme.Cream
import com.galmerom.outfittracker.ui.theme.OutfitTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cream = Cream.toArgb()
        val card = CardWhite.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(cream, cream),
            navigationBarStyle = SystemBarStyle.light(card, card),
        )
        val container = (application as OutfitTrackerApplication).container
        setContent {
            val prefs by container.preferences.prefs.collectAsStateWithLifecycle(UserPrefs())
            CompositionLocalProvider(
                LocalAppContainer provides container,
                LocalFontScale provides prefs.fontSize.scale,
                LocalAppTheme provides prefs.theme,
            ) {
                OutfitTrackerTheme {
                    OutfitTrackerRoot()
                }
            }
        }
    }
}
