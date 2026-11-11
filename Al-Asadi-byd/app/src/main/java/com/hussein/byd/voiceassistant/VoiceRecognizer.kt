package com.hussein.byd.voiceassistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.StringRes

/** غلاف حول SpeechRecognizer (تحويل الكلام إلى نص). */
class VoiceRecognizer(
    private val context: Context,
    private val callback: Callback
) {

    interface Callback {
        fun onPartial(text: String)
        fun onResults(candidates: List<String>)
        fun onError(@StringRes message: Int)
    }

    private var recognizer: SpeechRecognizer? = null
    private var cancelled = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String) {
        cancelled = false
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        r.startListening(intent)
    }

    /** إنهاء التسجيل ومعالجة ما قيل حتى الآن. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** إلغاء التسجيل بدون نتيجة. */
    fun cancel() {
        cancelled = true
        recognizer?.cancel()
    }

    fun destroy() {
        cancelled = true
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            if (cancelled) return
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) callback.onPartial(text)
        }

        override fun onResults(results: Bundle?) {
            if (cancelled) return
            val list = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.filter { it.isNotBlank() }
                .orEmpty()
            if (list.isEmpty()) callback.onError(R.string.err_no_match) else callback.onResults(list)
        }

        override fun onError(error: Int) {
            if (cancelled) return
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> R.string.err_no_match
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.err_timeout
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> R.string.err_network
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.err_busy
                SpeechRecognizer.ERROR_AUDIO -> R.string.err_audio
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.err_permission
                else -> R.string.err_generic
            }
            callback.onError(message)
        }
    }
}
