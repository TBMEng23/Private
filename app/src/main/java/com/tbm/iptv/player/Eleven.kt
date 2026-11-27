package com.tbm.iptv.player

import com.tbm.iptv.data.Http
import com.tbm.iptv.data.Prefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** קולות AI של ElevenLabs (מודל eleven_v4_turbo – תומך עברית, השהיה נמוכה) */
object Eleven {
    data class V(val id: String, val name: String, val gender: String, val age: String, val desc: String) {
        val label: String get() = buildString {
            append(name)
            val g = when (gender) { "male" -> com.tbm.iptv.L("גבר"); "female" -> com.tbm.iptv.L("אישה"); else -> "" }
            val a = when (age) { "young" -> com.tbm.iptv.L("צעיר"); "middle_aged", "middle aged" -> com.tbm.iptv.L("מבוגר"); "old" -> com.tbm.iptv.L("זקן"); else -> "" }
            val t = listOf(g, a).filter { it.isNotBlank() }.joinToString(" · ")
            if (t.isNotBlank()) append(" ($t)")
        }
    }
    const val MODEL = "eleven_v4_turbo"

    var key: String
        get() = Prefs.str("el_key", "")
        set(v) = Prefs.setStr("el_key", v.trim())
    val enabled: Boolean get() = key.isNotBlank() && Prefs.str("voice_engine", "device") == "eleven"

    /** קול לכל תפקיד: man / woman / child / old_man / old_woman / narrator */
    fun voiceFor(role: String): String = Prefs.str("el_v_$role", "").ifBlank { Prefs.str("el_v_narrator", "") }
    fun setVoice(role: String, id: String) = Prefs.setStr("el_v_$role", id)

    fun voices(): List<V> {
        val body = Http.get("https://api.elevenlabs.io/v1/voices", mapOf("xi-api-key" to key))
        val arr = JSONObject(body).optJSONArray("voices") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val l = o.optJSONObject("labels")
            V(o.optString("voice_id"), o.optString("name"), l?.optString("gender").orEmpty(), l?.optString("age").orEmpty(),
                l?.optString("description").orEmpty().ifBlank { o.optString("description") })
        }
    }

    /** רשימת הקולות האחרונה שנטענה (לחלוקת קולות לדמויות בלי לפנות לרשת) */
    fun cached(): List<V> = Prefs.str("local_el_voices").split('\n').mapNotNull { l ->
        val p = l.split('|'); if (p.size < 3) null else V(p[0], p.getOrElse(3) { "" }, p[1], p[2], "")
    }
    fun cache(vs: List<V>) = Prefs.setStrQuiet("local_el_voices", vs.joinToString("\n") { "${it.id}|${it.gender}|${it.age}|${it.name.replace('|', ' ')}" })

    /** בחירה אוטומטית ראשונית לפי התוויות (מין/גיל) */
    fun autoAssign(vs: List<V>) {
        cache(vs)
        fun pick(gender: String, ages: List<String>) = vs.firstOrNull { it.gender == gender && it.age in ages } ?: vs.firstOrNull { it.gender == gender }
        val man = pick("male", listOf("middle_aged", "middle aged", "young")); val woman = pick("female", listOf("young", "middle_aged", "middle aged"))
        val oldM = pick("male", listOf("old")); val oldW = pick("female", listOf("old"))
        val child = vs.firstOrNull { "child" in it.desc.lowercase() || "kid" in it.desc.lowercase() } ?: pick("female", listOf("young"))
        listOf("man" to man, "woman" to woman, "child" to child, "old_man" to (oldM ?: man), "old_woman" to (oldW ?: woman), "narrator" to (man ?: woman))
            .forEach { (r, v) -> if (v != null && Prefs.str("el_v_$r", "").isBlank()) setVoice(r, v.id) }
    }

    private fun cacheDir() = File(com.tbm.iptv.App.instance.cacheDir, "el_tts").apply { mkdirs() }

    /** קובץ MP3 של הטקסט בקול הנתון (עם מטמון – שורה שחוזרת לא עולה שוב) */
    fun synth(text: String, voiceId: String, lang: String): File {
        val h = MessageDigest.getInstance("MD5").digest("$voiceId|$lang|$text".toByteArray()).joinToString("") { "%02x".format(it) }
        val f = File(cacheDir(), "$h.mp3")
        if (f.exists() && f.length() > 0) return f
        val json = JSONObject().put("text", text).put("model_id", MODEL).put("language_code", if (lang == "iw") "he" else lang)
        val req = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_64")
            .header("xi-api-key", key).header("Accept", "audio/mpeg")
            .post(json.toString().toRequestBody("application/json".toMediaType())).build()
        Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("ElevenLabs ${r.code}: " + (r.body?.string()?.take(160) ?: ""))
            val tmp = File(f.path + ".tmp")
            r.body!!.byteStream().use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            tmp.renameTo(f)
        }
        // מטמון מוגבל: 400 הקבצים האחרונים
        cacheDir().listFiles()?.sortedBy { it.lastModified() }?.let { l -> if (l.size > 400) l.take(l.size - 400).forEach { it.delete() } }
        return f
    }
}
