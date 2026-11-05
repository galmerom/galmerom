package com.galmerom.quickdial.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.galmerom.quickdial.domain.SpeechWindow

enum class SpeechProblem { Failed, Unavailable, Permission }

/**
 * Hebrew speech via the platform SpeechRecognizer.
 * Listening ends after two seconds of silence once speech has started, and
 * always within five seconds of the start. Every finish path returns to idle.
 */
class HebrewSpeechSession(
    context: Context,
    private val onResult: (List<String>) -> Unit,
    private val onError: (SpeechProblem) -> Unit,
    private val onIdle: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var closed = false
    private var delivered = false
    private var retried = false
    private var startedAt = 0L
    private val cap = Runnable { onCap() }
    private val giveUp = Runnable { finishWithError(SpeechProblem.Failed) }

    fun start() {
        closed = false
        delivered = false
        retried = false
        startedAt = SystemClock.elapsedRealtime()
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onIdle()
            onError(SpeechProblem.Unavailable)
            return
        }
        listen(preferOffline = true)
        main.removeCallbacks(cap)
        main.postDelayed(cap, SpeechWindow.MAX_LISTEN_MS)
    }

    /** User cancelled. Late recognizer callbacks must not show an error or place a call. */
    fun stop() {
        closed = true
        delivered = true
        main.removeCallbacks(cap)
        main.removeCallbacks(giveUp)
        generation += 1
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        onIdle()
    }

    private fun listen(preferOffline: Boolean) {
        val created = runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
        if (created == null) {
            finishWithError(SpeechProblem.Unavailable)
            return
        }
        val token = ++generation
        recognizer = created
        created.setRecognitionListener(listener(token, preferOffline))
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                SpeechWindow.SILENCE_AFTER_SPEECH_MS,
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                SpeechWindow.SILENCE_AFTER_SPEECH_MS,
            )
        }
        runCatching { created.startListening(intent) }.onFailure {
            finishWithError(SpeechProblem.Failed)
        }
    }

    private fun onCap() {
        if (closed || delivered) return
        onIdle()
        runCatching { recognizer?.stopListening() }
        main.removeCallbacks(giveUp)
        main.postDelayed(giveUp, 1_200)
    }

    private fun listener(token: Int, preferOffline: Boolean) = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            onMain {
                if (closed || delivered || token != generation) return@onMain
                if (phrases.isEmpty()) finishWithError(SpeechProblem.Failed)
                else finishWithResult(phrases)
            }
        }

        override fun onError(error: Int) {
            onMain {
                if (closed || delivered || token != generation) return@onMain
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val canRetry = preferOffline && !retried && error in RETRYABLE && elapsed < SpeechWindow.MAX_LISTEN_MS
                if (canRetry) {
                    retried = true
                    runCatching { recognizer?.destroy() }
                    recognizer = null
                    listen(preferOffline = false)
                    return@onMain
                }
                finishWithError(
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechProblem.Permission
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                        -> SpeechProblem.Unavailable
                        else -> SpeechProblem.Failed
                    },
                )
            }
        }

        override fun onEndOfSpeech() {
            onMain {
                if (closed || token != generation) return@onMain
                onIdle()
                runCatching { recognizer?.stopListening() }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun finishWithResult(phrases: List<String>) {
        if (closed || delivered) return
        delivered = true
        releaseRecognizer()
        onIdle()
        onResult(phrases)
    }

    private fun finishWithError(problem: SpeechProblem) {
        if (closed || delivered) return
        delivered = true
        releaseRecognizer()
        onIdle()
        onError(problem)
    }

    private fun releaseRecognizer() {
        main.removeCallbacks(cap)
        main.removeCallbacks(giveUp)
        generation += 1
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private companion object {
        val RETRYABLE = setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        )
    }
}
