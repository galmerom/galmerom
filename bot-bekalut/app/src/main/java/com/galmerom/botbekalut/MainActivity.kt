package com.galmerom.botbekalut

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.galmerom.botbekalut.ui.BotBekalutApp
import com.galmerom.botbekalut.ui.theme.BlueBackground
import com.galmerom.botbekalut.ui.theme.BlueSurface
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var launch by mutableStateOf(LaunchArgs())

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
        launch = LaunchArgs.from(intent)
        setContent { BotBekalutApp(launch) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launch = LaunchArgs.from(intent)
    }

    companion object {
        const val EXTRA_BOT_ID = "botId"
        const val EXTRA_OPEN_UNREAD = "openUnread"
        const val EXTRA_ISSUED_AT = "issuedAt"
    }
}

data class LaunchArgs(
    val botId: String? = null,
    val openUnread: Boolean = false,
    val issuedAt: Long = 0L,
) {
    companion object {
        fun from(intent: Intent?): LaunchArgs {
            if (intent == null) return LaunchArgs()
            val botId = intent.getStringExtra(MainActivity.EXTRA_BOT_ID)?.takeIf { it.isNotBlank() }
            val openUnread = intent.getBooleanExtra(MainActivity.EXTRA_OPEN_UNREAD, false) ||
                intent.getStringExtra(MainActivity.EXTRA_OPEN_UNREAD) == "true"
            return LaunchArgs(
                botId = botId,
                openUnread = openUnread,
                issuedAt = intent.getLongExtra(MainActivity.EXTRA_ISSUED_AT, 0L),
            )
        }
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
