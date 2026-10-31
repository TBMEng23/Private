package com.tbm.iptv.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.tbm.iptv.data.Prefs
import java.util.Locale

/** קריינות AI: מקריא את הכתובית המוצגת בקול (TTS מובנה של אנדרואיד) ומנמיך את הקול המקורי בזמן הדיבור */
class VoiceOver(
    ctx: Context,
    private val onDuck: (Boolean) -> Unit,
    private val onMissingVoice: (String) -> Unit,
) : TextToSpeech.OnInitListener {
    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(ctx.applicationContext, this)
    private var ready = false
    private var lang = "he"
    private var last = ""
    private var speaking = 0

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) { main.post { onMissingVoice("TTS") }; return }
        ready = true
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { main.post { speaking++; onDuck(true) } }
            override fun onDone(id: String?) { main.post { speaking = (speaking - 1).coerceAtLeast(0); if (speaking == 0) onDuck(false) } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { main.post { speaking = 0; onDuck(false) } }
            override fun onStop(id: String?, interrupted: Boolean) { main.post { speaking = 0; onDuck(false) } }
        })
        applyLang()
    }

    private fun locale(l: String): Locale = when (l) {
        "he", "iw" -> Locale("iw", "IL")
        "ar" -> Locale("ar")
        "en" -> Locale.US
        else -> Locale(l)
    }

    private fun applyLang() {
        if (!ready) return
        val r = tts.setLanguage(locale(lang))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) main.post { onMissingVoice(lang) }
    }

    fun setLanguage(l: String) {
        if (l == lang) return
        lang = l; applyLang()
    }

    /** נקרא בכל שינוי כתובית */
    fun speak(raw: String) {
        if (!ready) return
        val text = raw.replace(Regex("[\\u200E\\u200F\\u202A-\\u202E]"), "")
            .replace(Regex("<[^>]+>"), "").replace(Regex("\\{[^}]*\\}"), "")
            .replace(Regex("^\\s*-\\s*", RegexOption.MULTILINE), "")
            .replace('\n', ' ').trim()
        if (text.isBlank() || text == last) return
        last = text
        tts.setSpeechRate(Prefs.voiceRate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "c${System.nanoTime()}")
    }

    fun stop() {
        last = ""
        if (ready) tts.stop()
        speaking = 0
        onDuck(false)
    }

    fun shutdown() {
        runCatching { tts.stop(); tts.shutdown() }
    }
}
