package com.galmerom.botbekalut

import android.app.Application
import com.galmerom.botbekalut.fcm.NotificationCenter

class BotBekalutApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationCenter.ensureChannel(this)
    }
}
