package com.hussein.byd.voiceassistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** غلاف بسيط حول TextToSpeech للرد الصوتي. إن لم تتوفر لغة الصوت يبقى الرد نصياً فقط. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: Pair<String, Locale>? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        val waiting = pending
        pending = null
        if (ready && waiting != null) speak(waiting.first, waiting.second)
    }

    fun speak(text: String, locale: Locale) {
        if (!ready) {
            pending = text to locale
            return
        }
        val engine = tts ?: return
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    fun stop() {
        pending = null
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
