package com.galmerom.quickdial.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

enum class SpeechProblem { Failed, Unavailable, Permission }

/**
 * Hebrew speech via the platform SpeechRecognizer. On-device recognition is
 * preferred, and a single online retry covers devices without an offline model.
 */
class HebrewSpeechSession(
    context: Context,
    private val onResult: (List<String>) -> Unit,
    private val onError: (SpeechProblem) -> Unit,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var stopped = false
    private var retried = false

    fun start() {
        stopped = false
        retried = false
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError(SpeechProblem.Unavailable)
            return
        }
        listen(preferOffline = true)
    }

    fun stop() {
        stopped = true
        generation += 1
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun listen(preferOffline: Boolean) {
        val created = runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
        if (created == null) {
            onError(SpeechProblem.Unavailable)
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
        }
        runCatching { created.startListening(intent) }.onFailure {
            if (!stopped && token == generation) onError(SpeechProblem.Failed)
        }
    }

    private fun listener(token: Int, preferOffline: Boolean) = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            onMain {
                if (stopped || token != generation) return@onMain
                stop()
                if (phrases.isEmpty()) onError(SpeechProblem.Failed) else onResult(phrases)
            }
        }

        override fun onError(error: Int) {
            onMain {
                if (stopped || token != generation) return@onMain
                val canRetry = preferOffline && !retried && error in RETRYABLE
                if (canRetry) {
                    retried = true
                    runCatching { recognizer?.destroy() }
                    recognizer = null
                    listen(preferOffline = false)
                    return@onMain
                }
                stop()
                onError(
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

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
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
