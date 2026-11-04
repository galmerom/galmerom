package com.galmerom.outfittracker.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.galmerom.outfittracker.data.AppContainer
import com.galmerom.outfittracker.data.AppTheme

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not available")
}

val LocalFontScale = compositionLocalOf { 1f }

val LocalAppTheme = compositionLocalOf { AppTheme.WARM }

inline fun <reified T : ViewModel> viewModelFactory(crossinline create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = build()

        override fun <VM : ViewModel> create(modelClass: Class<VM>, extras: CreationExtras): VM = build()

        @Suppress("UNCHECKED_CAST")
        private fun <VM : ViewModel> build(): VM = create() as VM
    }
