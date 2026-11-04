package com.galmerom.outfittracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.galmerom.outfittracker.ui.LocalAppContainer
import com.galmerom.outfittracker.ui.navigation.OutfitTrackerRoot
import com.galmerom.outfittracker.ui.theme.OutfitTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OutfitTrackerApplication).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                OutfitTrackerTheme {
                    OutfitTrackerRoot()
                }
            }
        }
    }
}
