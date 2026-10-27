package app.n_zik.android.components

import app.n_zik.android.privacy.PermissionUsageJournal
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import app.n_zik.android.R
import timber.log.Timber
import java.util.Locale

class VoiceSearchUtils(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartialResult: (String) -> Unit = {},
    private val onError: () -> Unit = {},
    private val onListeningStateChanged: (Boolean) -> Unit = {},
    private val onSpeechDetected: () -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var hasReceivedResults = false
    private var isCancelled = false

    // The SpeechRecognizer is created inside startListening() on the main thread
    // (SpeechRecognizer.createSpeechRecognizer binds its callbacks to the caller's looper),
//
    // so every RecognitionListener callback below already runs on Main — no Handler needed.
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            PermissionUsageJournal.record(context, PermissionFeature.VOICE_SEARCH, JournalPermission.MICROPHONE, PermissionEventKind.USED)
            Timber.tag("VoiceSearchUtils").d("Ready")
            hasReceivedResults = false
            isCancelled = false
            isListening = true
            onListeningStateChanged(true)
        }

        override fun onBeginningOfSpeech() {
            Timber.tag("VoiceSearchUtils").d("Speech detected")
            onSpeechDetected()
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Timber.tag("VoiceSearchUtils").d("End of speech")
            if (!isCancelled) {
                onListeningStateChanged(false)
            }
            speechRecognizer?.stopListening()
        }

        override fun onError(error: Int) {
            if (hasReceivedResults || isCancelled) {
                Timber.tag("VoiceSearchUtils").d("Ignoring error %d after results/cancel", error)
                return
            }
            Timber.tag("VoiceSearchUtils").e("Error: %d", error)
            isListening = false
            onListeningStateChanged(false)
            onError()
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }

        override fun onResults(results: Bundle?) {
            Timber.tag("VoiceSearchUtils").d("Results")
            hasReceivedResults = true
            isListening = false
            onListeningStateChanged(false)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                Timber.tag("VoiceSearchUtils").d("Result: %s, Language: %s", matches[0], Locale.getDefault())
                onResult(matches[0])
            }
            speechRecognizer?.stopListening()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                onPartialResult(matches[0])
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        if (isListening) {
            stopListening()
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError()
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }

        Timber.tag("VoiceSearchUtils").d("Starting (auto-detect language)")

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        isCancelled = true
        isListening = false
        onListeningStateChanged(false)
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Timber.tag("VoiceSearchUtils").e(e, "Error stopping")
        }
        speechRecognizer = null
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }
}
