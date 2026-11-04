package com.galmerom.outfittracker.domain

data class CutoutResult(
    val original: ByteArray,
    val processed: ByteArray?,
)

interface BackgroundRemover {
    suspend fun prepare(jpeg: ByteArray): CutoutResult
}
