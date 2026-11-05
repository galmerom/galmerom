package com.galmerom.quickdial.domain

/** How long the speak button is allowed to listen. */
object SpeechWindow {
    const val SILENCE_AFTER_SPEECH_MS = 2_000L
    const val MAX_LISTEN_MS = 5_000L

    /**
     * Stop after [SILENCE_AFTER_SPEECH_MS] of silence once speech has started.
     * If nobody speaks, stop at [MAX_LISTEN_MS] from the start.
     */
    fun shouldStop(
        totalElapsedMs: Long,
        speechStarted: Boolean,
        silenceSinceSpeechMs: Long?,
    ): Boolean {
        if (totalElapsedMs >= MAX_LISTEN_MS) return true
        return speechStarted &&
            silenceSinceSpeechMs != null &&
            silenceSinceSpeechMs >= SILENCE_AFTER_SPEECH_MS
    }
}
