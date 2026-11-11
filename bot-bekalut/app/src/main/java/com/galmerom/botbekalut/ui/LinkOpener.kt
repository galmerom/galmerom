package com.galmerom.botbekalut.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.galmerom.botbekalut.domain.isHttpUrl
import com.galmerom.botbekalut.domain.isXLink

fun openChatLink(context: Context, url: String) {
    if (!isHttpUrl(url)) return
    val uri = Uri.parse(url)
    if (isXLink(url)) {
        val xIntent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.twitter.android")
        if (xIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(xIntent)
            return
        }
    }
    val tabs = CustomTabsIntent.Builder().setShowTitle(true).build()
    tabs.intent.setPackage("com.android.chrome")
    try {
        tabs.launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        openExternal(context, uri)
    } catch (_: Exception) {
        openExternal(context, uri)
    }
}

fun openPageUrl(context: Context, url: String) {
    if (!isHttpUrl(url)) return
    openExternal(context, Uri.parse(url))
}

private fun openExternal(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // No handler installed.
    }
}
