package com.galmerom.outfittracker.domain.logic

import com.galmerom.outfittracker.domain.model.ErrorKind

fun Throwable.errorKind(): ErrorKind {
    val text = generateSequence(this) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
    val denied = text.contains("PERMISSION_DENIED", ignoreCase = true) ||
        text.contains("does not have permission", ignoreCase = true)
    return if (denied) ErrorKind.PERMISSION else ErrorKind.OTHER
}
