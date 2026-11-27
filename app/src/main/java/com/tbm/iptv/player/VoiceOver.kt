package com.tbm.iptv.player

import android.content.Context
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
    private var elJob: kotlinx.coroutines.Job? = null
    private var mp: android.media.MediaPlayer? = null
    private var elFailToast = 0L
    init { AudioTap.pitchOn = true }
    private val tts = TextToSpeech(ctx.applicationContext, this)
    private var ready = false
    private var lang = "he"
    private var last = ""
    private var speaking = 0
    private var voices: List<Voice> = emptyList()
    /** מגדר אחרון שזוהה לכל צד בדיאלוג (לרציפות כשאין רמז בטקסט) */
    private val lastGender = intArrayOf(0, 0)
    /** הדובר הנוכחי לכל צד – משנים רק כשיש סימן ברור (מונע החלפות קול מיותרות) */
    private val cur = arrayOf(Speaker.NARRATOR, Speaker.NARRATOR)
    private var lastCueAt = 0L

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
        val now = System.currentTimeMillis()
        val cont = segs.size == 1 && now - lastCueAt < 2_500
        lastCueAt = now
        val who = if (VoiceCfg.style == "auto") Characters.speakersFor(flat) else null
        if (Eleven.enabled) { speakEleven(segs, who, cont); return }
        segs.forEachIndexed { i, text ->
            if (text.isBlank()) return@forEachIndexed
            val ci = who?.let { it.getOrNull(i) ?: it[0] } ?: -1
            val sp = if (ci >= 0) charSpeaker(ci) else decide(text, i.coerceAtMost(1), null, cont)
            voiceFor(sp)?.let { runCatching { tts.voice = it } }
            tts.setPitch(sp.pitch)
            tts.setSpeechRate(Prefs.voiceRate * sp.rate)
            tts.speak(text, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "c${System.nanoTime()}")
        }
    }

    /** סיווג לפי גובה הקול שנשמע עכשיו בסרט – רק כשהוא ברור (באזור האפור מחזיר null ושומרים על הדובר הקודם) */
    private fun pitchSpeaker(sinceMs: Long): Speaker? {
        val hz = AudioTap.pitchSince(sinceMs, 6) ?: return null
        return when { hz < 150f -> Speaker.MAN; hz in 190f..270f -> Speaker.WOMAN; hz > 305f -> Speaker.CHILD; else -> null }
    }

    /** דמות שזוהתה בניתוח העלילה → סוג קול */
    private fun charSpeaker(ci: Int): Speaker {
        val ch = Characters.current?.chars?.getOrNull(ci) ?: return Speaker.NARRATOR
        return when { ch.age == "child" -> Speaker.CHILD; ch.gender > 0 -> Speaker.WOMAN; ch.gender < 0 -> Speaker.MAN; else -> Speaker.NARRATOR }
    }

    /** קול ElevenLabs קבוע לדמות: הדמות הראשית מכל מין מקבלת את הקול שנבחר בהגדרות, האחרות – קולות אחרים מאותו מין */
    private fun charVoice(ci: Int): String {
        val cast = Characters.current ?: return ""
        cast.voice[ci]?.let { return it }
        val ch = cast.chars.getOrNull(ci) ?: return ""
        fun grp(c: Characters.Ch) = when { c.age == "child" -> "child"; c.gender > 0 -> "f"; c.gender < 0 -> "m"; else -> "u" }
        val g = grp(ch)
        val role = when (g) { "child" -> "child"; "f" -> "woman"; "m" -> "man"; else -> "narrator" }
        val roleV = Eleven.voiceFor(role)
        val pool = Eleven.cached().filter { v -> when (g) { "f", "child" -> v.gender == "female"; "m" -> v.gender == "male"; else -> false } }
            .map { it.id }.filter { it != roleV && it != Eleven.voiceFor("narrator") }
        val ordered = (listOf(roleV) + pool).filter { it.isNotBlank() }.distinct()
        val rank = cast.chars.withIndex().filter { grp(it.value) == g }.sortedByDescending { it.value.lines }.indexOfFirst { it.index == ci }
        val v = if (ordered.isEmpty()) "" else ordered[rank.coerceAtLeast(0) % ordered.size]
        cast.voice[ci] = v
        return v
    }

    /** ייצוב: דקדוק ברור → מגדר; צד שני בדיאלוג → הפוך מהראשון; קול מקורי ברור ושונה → מחליף; אחרת – אותו דובר */
    private fun decide(text: String, slot: Int, heard: Speaker?, cont: Boolean): Speaker {
        when (VoiceCfg.style) {
            "narrator" -> return Speaker.NARRATOR; "man" -> return Speaker.MAN
            "woman" -> return Speaker.WOMAN; "child" -> return Speaker.CHILD
        }
        val g = HebGender.detect(text)
        val sp = when {
            g > 0 -> if (cur[slot] == Speaker.CHILD) Speaker.CHILD else Speaker.WOMAN
            g < 0 -> Speaker.MAN
            slot == 1 -> when (cur[0]) { Speaker.MAN -> Speaker.WOMAN; Speaker.WOMAN, Speaker.CHILD -> Speaker.MAN; else -> if (cur[1] == Speaker.NARRATOR) Speaker.WOMAN else cur[1] }
            heard != null && heard != cur[slot] -> heard
            cont -> cur[slot]
            heard != null -> heard
            else -> cur[slot]
        }
        cur[slot] = sp
        return sp
    }

    /** ElevenLabs: מחכים רגע לשמוע את הקול המקורי (גובה → גבר/אישה/ילד), מייצרים ומנגנים */
    private fun speakEleven(segs: List<String>, who: IntArray?, cont: Boolean) {
        elJob?.cancel(); stopMp()
        val cueAt = System.currentTimeMillis()
        elJob = scope.launch {
            val auto = VoiceCfg.style == "auto"
            // כשהדמויות ידועות מהניתוח – לא צריך להקשיב לקול המקורי
            if (auto && who == null) kotlinx.coroutines.delay(450)
            val heard = if (auto && who == null) pitchSpeaker(cueAt - 150) else null
            for ((i, text) in segs.withIndex()) {
                if (text.isBlank()) continue
                val ci = who?.let { it.getOrNull(i) ?: it[0] } ?: -1
                val sp = if (ci >= 0) charSpeaker(ci) else decide(text, i.coerceAtMost(1), if (i == 0) heard else null, cont)
                val role = when (sp) { Speaker.MAN -> "man"; Speaker.WOMAN -> "woman"; Speaker.CHILD -> "child"; else -> "narrator" }
                val vid = (if (ci >= 0) charVoice(ci) else "").ifBlank { Eleven.voiceFor(role) }.ifBlank { Eleven.voiceFor("narrator") }
                if (vid.isBlank()) { fallbackDevice(text, sp); continue }
                val f = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Eleven.synth(text, vid, lang) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (System.currentTimeMillis() - elFailToast > 60_000) { elFailToast = System.currentTimeMillis(); onMissingVoice("ElevenLabs: " + (e.message ?: "")) }
                    fallbackDevice(text, sp); continue
                }
                playFile(f)
            }
        }
    }

    private fun fallbackDevice(text: String, sp: Speaker) {
        voiceFor(sp)?.let { runCatching { tts.voice = it } }
        tts.setPitch(sp.pitch); tts.setSpeechRate(Prefs.voiceRate * sp.rate)
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "c${System.nanoTime()}")
    }

    private suspend fun playFile(f: java.io.File) = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
        val p = android.media.MediaPlayer()
        mp = p
        runCatching {
            p.setDataSource(f.path)
            p.setOnPreparedListener {
                runCatching { it.playbackParams = it.playbackParams.setSpeed(Prefs.voiceRate.coerceIn(0.7f, 1.6f)) }
                speaking++; onDuck(true); it.start()
            }
            p.setOnCompletionListener {
                speaking = (speaking - 1).coerceAtLeast(0); if (speaking == 0) onDuck(false)
                it.release(); if (mp === it) mp = null
                if (cont.isActive) cont.resumeWith(Result.success(Unit))
            }
            p.setOnErrorListener { mp2, _, _ ->
                speaking = 0; onDuck(false); mp2.release(); if (mp === mp2) mp = null
                if (cont.isActive) cont.resumeWith(Result.success(Unit)); true
            }
            p.prepareAsync()
        }.onFailure { if (cont.isActive) cont.resumeWith(Result.success(Unit)) }
        cont.invokeOnCancellation { main.post { stopMp() } }
    }

    private fun stopMp() {
        mp?.let { runCatching { it.stop() }; runCatching { it.release() } }
        mp = null
        if (speaking > 0) { speaking = 0; onDuck(false) }
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
        cur[0] = Speaker.NARRATOR; cur[1] = Speaker.NARRATOR
        elJob?.cancel(); stopMp()
        if (ready) tts.stop()
        speaking = 0
        onDuck(false)
    }

    fun shutdown() {
        AudioTap.pitchOn = false
        elJob?.cancel(); stopMp(); scope.cancel()
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
