package com.tbm.iptv.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.tbm.iptv.data.Prefs
import java.util.Locale

/** הגדרות קריינות (שלב א': קולות מותקנים, סגנונות, דוברים שונים) */
object VoiceCfg {
    /** auto = לפי הדובר (גבר/אישה/ילד לפי הטקסט), narrator = קול אחד רגיל, man / woman / child = סגנון קבוע */
    var style: String
        get() = Prefs.str("voice_style", "auto")
        set(v) = Prefs.setStr("voice_style", v)
    /** קול שונה לכל צד בדיאלוג (שורות שמתחילות במקף) */
    var multi: Boolean
        get() = Prefs.bool("voice_multi", true)
        set(v) = Prefs.setBool("voice_multi", v)
    /** שם הקול לגבר / לאישה ("" = ברירת מחדל) */
    var maleVoice: String
        get() = Prefs.str("voice_male", "")
        set(v) = Prefs.setStr("voice_male", v)
    var femaleVoice: String
        get() = Prefs.str("voice_female", "")
        set(v) = Prefs.setStr("voice_female", v)
}

enum class Speaker(val pitch: Float, val rate: Float) {
    NARRATOR(1.0f, 1.0f), MAN(0.82f, 0.98f), WOMAN(1.18f, 1.0f), CHILD(1.55f, 1.08f)
}

/** זיהוי מגדר הדובר מתוך הדקדוק העברי ("אני הולכת" / "אני הולך", "אני בטוחה" / "אני בטוח") */
object HebGender {
    private val neutralH = setOf("רוצה", "רואה", "עושה", "קונה", "שותה", "בוכה", "עונה", "נראה", "מנסה", "מקווה", "מצפה",
        "מודה", "נהנה", "שונה", "מסכים", "פה", "כאן", "שם", "זה", "זאת", "לה", "לו", "בה", "מה", "מתה")
    private val skip = setOf("לא", "כבר", "עוד", "גם", "רק", "פשוט", "באמת", "ממש", "בכלל", "עדיין", "כן", "די", "כל", "כך", "מאוד", "נורא")
    private val femOnly = setOf("יכולה", "צריכה", "חייבת", "מוכנה", "בטוחה", "עייפה", "שמחה", "מצטערת", "אחראית", "לבד")
    private val femVerbsPast = Regex("(?:^|\\s)(?:אני\\s+)?(?:\\S+)תי\\s+(?:לבד|מוכנה|בטוחה)")

    /** +1 אישה, -1 גבר, 0 לא ידוע */
    fun detect(text: String): Int {
        val words = text.replace(Regex("[^\\u0590-\\u05FF\\s]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        var score = 0
        for (i in words.indices) {
            if (words[i] != "אני" && words[i] != "שאני" && words[i] != "כשאני") continue
            var j = i + 1
            while (j < words.size && words[j] in skip) j++
            val w = words.getOrNull(j)?.removePrefix("ה") ?: continue
            if (w.length < 3) continue
            when {
                w in femOnly -> score += 2
                w in neutralH -> {}
                w.endsWith("ת") && !w.endsWith("ות") -> score += 1
                w.endsWith("ה") -> score += 1
                w.startsWith("מ") || w.length >= 4 -> score -= 1
            }
        }
        if (femVerbsPast.containsMatchIn(text)) score += 1
        return Integer.signum(score)
    }
}

/** קריינות AI: מקריא את הכתובית בקול ומנמיך את הקול המקורי בזמן הדיבור */
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
    private var voices: List<Voice> = emptyList()
    /** מגדר אחרון שזוהה לכל צד בדיאלוג (לרציפות כשאין רמז בטקסט) */
    private val lastGender = intArrayOf(0, 0)

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

    private fun applyLang() {
        if (!ready) return
        val r = tts.setLanguage(localeOf(lang))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) main.post { onMissingVoice(lang) }
        voices = voicesFor(tts, lang)
    }

    fun setLanguage(l: String) {
        if (l == lang) return
        lang = l; applyLang()
    }

    private fun voiceFor(s: Speaker): Voice? {
        val name = when (s) { Speaker.MAN -> VoiceCfg.maleVoice; Speaker.WOMAN, Speaker.CHILD -> VoiceCfg.femaleVoice; else -> "" }
        return voices.firstOrNull { it.name == name } ?: voices.firstOrNull { !it.isNetworkConnectionRequired } ?: voices.firstOrNull()
    }

    private fun clean(s: String) = s.replace(Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]"), "")
        .replace(Regex("<[^>]+>"), "").replace(Regex("\\{[^}]*\\}"), "")
        .replace(Regex("\\[[^\\]]*]|\\([^)]*\\)"), "")   // תיאורי קול לכבדי שמיעה
        .trim()

    /** נקרא בכל שינוי כתובית */
    fun speak(raw: String) {
        if (!ready) return
        val body = clean(raw)
        val flat = body.replace('\n', ' ').trim()
        if (flat.isBlank() || flat == last) return
        last = flat
        // פיצול לדוברים: כל שורה שמתחילה במקף היא דובר אחר
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val segs = ArrayList<String>()
        if (VoiceCfg.multi && lines.count { it.startsWith("-") || it.startsWith("–") } >= 1 && lines.size > 1) {
            lines.forEach { l -> if (l.startsWith("-") || l.startsWith("–") || segs.isEmpty()) segs += l.trimStart('-', '–', ' ') else segs[segs.lastIndex] += " $l" }
        } else segs += flat.trimStart('-', '–', ' ')
        tts.stop()
        segs.forEachIndexed { i, text ->
            if (text.isBlank()) return@forEachIndexed
            val sp = speakerFor(text, i.coerceAtMost(1))
            voiceFor(sp)?.let { runCatching { tts.voice = it } }
            tts.setPitch(sp.pitch)
            tts.setSpeechRate(Prefs.voiceRate * sp.rate)
            tts.speak(text, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "c${System.nanoTime()}")
        }
    }

    private fun speakerFor(text: String, slot: Int): Speaker = when (VoiceCfg.style) {
        "narrator" -> Speaker.NARRATOR
        "man" -> Speaker.MAN
        "woman" -> Speaker.WOMAN
        "child" -> Speaker.CHILD
        else -> {
            val g = HebGender.detect(text).let { if (it == 0) lastGender[slot] else it }
            lastGender[slot] = g
            when {
                g > 0 -> Speaker.WOMAN
                g < 0 -> Speaker.MAN
                // בלי רמז: בדיאלוג – הצד השני מקבל קול שונה מהראשון
                slot == 1 -> if (lastGender[0] < 0) Speaker.WOMAN else Speaker.MAN
                else -> Speaker.NARRATOR
            }
        }
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

    companion object {
        fun localeOf(l: String): Locale = when (l) {
            "he", "iw" -> Locale("iw", "IL")
            "ar" -> Locale("ar")
            "en" -> Locale.US
            else -> Locale(l)
        }

        /** הקולות המותקנים לשפה (עברית: iw/he) */
        fun voicesFor(tts: TextToSpeech, l: String): List<Voice> {
            val want = if (l == "he" || l == "iw") setOf("iw", "he", "heb") else setOf(l, localeOf(l).isO3Language)
            return runCatching {
                tts.voices.orEmpty().filter { v ->
                    (v.locale.language in want || runCatching { v.locale.isO3Language }.getOrNull() in want) &&
                        !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                }.sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
            }.getOrDefault(emptyList())
        }

        /** שם ידידותי לקול */
        fun label(v: Voice, i: Int): String {
            val net = if (v.isNetworkConnectionRequired) " · ☁ " + com.tbm.iptv.L("רשת (איכותי יותר)") else ""
            return com.tbm.iptv.L("קול {0}", i + 1) + " (" + v.name.substringAfter("-x-", v.name).take(16) + ")" + net
        }
    }
}
