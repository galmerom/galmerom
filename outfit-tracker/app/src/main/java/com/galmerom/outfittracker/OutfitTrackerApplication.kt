package com.galmerom.outfittracker

import android.app.Application
import com.galmerom.outfittracker.data.AppContainer

class OutfitTrackerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
