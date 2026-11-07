package com.tbm.iptv.subs

import com.tbm.iptv.data.Http
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray

/** תרגום כתוביות אוטומטי (Google Translate) – גיבוי כשאין כתובית בעברית */
object MachineTranslate {
    // תרגום – לא מחכים 90 שניות לשרת שלא עונה
    private val fast by lazy {
        Http.client.newBuilder().connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()
    }
    private val tsLine = Regex("""^\d{1,2}:\d{2}:\d{2}[,.]\d{3}\s*-->""")

    /** תרגום רשימת טקסטים קצרים (תיאורים, ז'אנרים, שמות). שורה ריקה נשמרת. */
    fun texts(items: List<String>, to: String, from: String = "auto"): List<String> {
        if (items.isEmpty()) return items
        val clean = items.map { it.replace("\n", " ").trim() }
        val out = ArrayList<String>()
        var k = 0
        while (k < clean.size) {
            var len = 0; var e = k
            while (e < clean.size && len + clean[e].length < 3500) { len += clean[e].length + 1; e++ }
            if (e == k) e = k + 1
            val batch = clean.subList(k, e)
            var res = runCatching { translateBatch(batch, from, to) }.getOrNull()
            if (res == null || res.size != batch.size)
                res = batch.map { t -> if (t.isBlank()) t else runCatching { translateBatch(listOf(t), from, to).joinToString(" ") }.getOrDefault(t) }
            out += res; k = e
        }
        return out
    }

    private fun translateBatch(lines: List<String>, from: String, to: String): List<String> {
        val body = FormBody.Builder().add("q", lines.joinToString("\n")).build()
        val req = Request.Builder()
            .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&dt=t")
            .header("User-Agent", "Mozilla/5.0")
            .post(body).build()
        val txt = fast.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            r.body?.string() ?: ""
        }
        val segs = JSONArray(txt).optJSONArray(0) ?: return lines
        if (lines.size == 1 && segs.length() > 1) {
            val sb1 = StringBuilder(); for (i in 0 until segs.length()) sb1.append(segs.optJSONArray(i)?.optString(0) ?: "")
            return listOf(sb1.toString().replace("\n", " ").trim())
        }
        val sb = StringBuilder()
        for (i in 0 until segs.length()) sb.append(segs.optJSONArray(i)?.optString(0) ?: "")
        return sb.toString().split("\n").map { it.trim() }
    }

    /** מתרגם קובץ SRT/VTT: שומר מספרים וזמנים, מתרגם רק את הטקסט */
    fun srt(text: String, from: String, to: String): String {
        val lines = text.replace("\r\n", "\n").split("\n")
        // אוסף את שורות הטקסט של כל כתובית (מאוחד לשורה אחת)
        data class Cue(val start: Int, val end: Int, val text: String)
        val cues = ArrayList<Cue>()
        var i = 0
        while (i < lines.size) {
            if (tsLine.containsMatchIn(lines[i])) {
                var j = i + 1
                val parts = ArrayList<String>()
                while (j < lines.size && lines[j].isNotBlank()) { parts += lines[j].trim(); j++ }
                if (parts.isNotEmpty()) cues += Cue(i + 1, j, parts.joinToString(" ").replace(Regex("<[^>]+>"), ""))
                i = j
            } else i++
        }
        val translated = arrayOfNulls<String>(cues.size)
        var k = 0
        while (k < cues.size) {
            var len = 0; var e = k
            while (e < cues.size && len + cues[e].text.length < 3500) { len += cues[e].text.length + 1; e++ }
            if (e == k) e = k + 1
            val batch = cues.subList(k, e).map { it.text }
            var res = runCatching { translateBatch(batch, from, to) }.getOrNull()
            if (res == null || res.size != batch.size) {
                // מספר שורות לא תואם – מתרגם אחת-אחת
                res = batch.map { t -> runCatching { translateBatch(listOf(t), from, to).joinToString(" ") }.getOrDefault(t) }
            }
            for (x in batch.indices) translated[k + x] = res[x]
            k = e
        }
        val out = ArrayList<String>()
        var ci = 0
        i = 0
        while (i < lines.size) {
            if (ci < cues.size && i == cues[ci].start) {
                out += translated[ci] ?: cues[ci].text
                i = cues[ci].end
                ci++
            } else { out += lines[i]; i++ }
        }
        return out.joinToString("\n")
    }
}
