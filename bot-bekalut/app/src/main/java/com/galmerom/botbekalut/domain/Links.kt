package com.galmerom.botbekalut.domain

import java.net.URI

fun isXLink(url: String): Boolean {
    val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
    val bare = host.removePrefix("www.")
    return bare == "x.com" || bare == "twitter.com" || bare.endsWith(".x.com") || bare.endsWith(".twitter.com")
}

fun isHttpUrl(url: String): Boolean {
    val scheme = runCatching { URI(url).scheme }.getOrNull()?.lowercase() ?: return false
    return scheme == "https" || scheme == "http"
}
