package com.tbm.iptv.player

import com.tbm.iptv.data.Http
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.subs.SubtitleFile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * הבנת עלילה ודמויות לקריינות: לפני הסרט שולחים ל-Gemini את כל הכתוביות + תקציר ורשימת שחקנים מ-TMDB,
 * ומקבלים לכל כתובית מי הדמות שמדברת (ולדיאלוג – לכל צד). כל דמות מקבלת קול קבוע לכל הסרט.
 */
object Characters {
    /** gender: 1 אישה, -1 גבר, 0 לא ידוע; age: child / adult / old */
    data class Ch(val name: String, val gender: Int, val age: String, var lines: Int = 0)
    class Cast(val chars: List<Ch>, val byText: Map<String, IntArray>) {
        /** קול ElevenLabs קבוע לכל דמות (לפי האינדקס) */
        val voice = HashMap<Int, String>()
    }

    var key: String
        get() = Prefs.str("gm_key", "")
        set(v) = Prefs.setStr("gm_key", v.trim())
    var on: Boolean
        get() = Prefs.bool("voice_ai_cast", true)
        set(v) = Prefs.setBool("voice_ai_cast", v)
    val enabled get() = key.isNotBlank() && on

    @Volatile var current: Cast? = null
        private set
    @Volatile private var currentFor: String = ""

    fun norm(t: String) = t.replace(Regex("<[^>]+>|\\{[^}]*\\}"), "").lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    fun clear() { current = null; currentFor = "" }

    /** דוברים לכתובית (לפי הטקסט שלה); null אם לא ידוע */
    fun speakersFor(text: String): IntArray? {
        val c = current ?: return null
        return c.byText[norm(text)]
    }

    // ---------------------------------------------------------------- כתוביות → שורות
    private val timeRx = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->""")
    fun blocks(sf: SubtitleFile): List<String> {
        val t = sf.text.replace("\r", "")
        if (sf.ext == "ass" || sf.ext == "ssa") return t.lines().filter { it.startsWith("Dialogue:") }.mapNotNull { l ->
            val parts = l.split(',', limit = 10)
            parts.getOrNull(9)?.replace("\\N", "\n")?.replace(Regex("\\{[^}]*\\}"), "")?.trim()?.takeIf { it.isNotBlank() }
        }
        val out = ArrayList<String>()
        for (b in t.split(Regex("\n\\s*\n"))) {
            val ls = b.lines()
            val ti = ls.indexOfFirst { timeRx.containsMatchIn(it) }
            if (ti < 0) continue
            val txt = ls.drop(ti + 1).joinToString("\n").replace(Regex("<[^>]+>"), "").trim()
            if (txt.isNotBlank()) out += txt
        }
        return out
    }

    // ---------------------------------------------------------------- TMDB: תקציר ושחקנים
    private fun tmdb(title: String, year: Int?, tv: Boolean): Pair<String, List<Ch>> {
        val key = Prefs.tmdbKey
        if (key.isBlank()) return "" to emptyList()
        val bearer = key.length > 40
        val hdr = if (bearer) mapOf("Authorization" to "Bearer $key", "Accept" to "application/json") else emptyMap()
        val k = if (bearer) "" else "&api_key=${Http.enc(key)}"
        val type = if (tv) "tv" else "movie"
        val yq = if (year != null) (if (tv) "&first_air_date_year=$year" else "&year=$year") else ""
        val r = JSONObject(Http.get("https://api.themoviedb.org/3/search/$type?query=${Http.enc(title)}$yq$k", hdr))
            .optJSONArray("results")?.optJSONObject(0) ?: return "" to emptyList()
        val id = r.optInt("id")
        val plot = r.optString("overview")
        val credits = runCatching {
            JSONObject(Http.get("https://api.themoviedb.org/3/$type/$id/${if (tv) "aggregate_credits" else "credits"}?x=1$k", hdr)).optJSONArray("cast")
        }.getOrNull() ?: JSONArray()
        val cast = (0 until minOf(credits.length(), 30)).mapNotNull { i ->
            val o = credits.optJSONObject(i) ?: return@mapNotNull null
            val ch = o.optString("character").ifBlank {
                o.optJSONArray("roles")?.optJSONObject(0)?.optString("character").orEmpty()
            }.substringBefore(" / ").trim()
            if (ch.isBlank()) null else Ch(ch, when (o.optInt("gender")) { 1 -> 1; 2 -> -1; else -> 0 }, "adult")
        }
        return plot to cast
    }

    // ---------------------------------------------------------------- Gemini
    private val client by lazy { Http.client.newBuilder().readTimeout(180, TimeUnit.SECONDS).callTimeout(240, TimeUnit.SECONDS).build() }

    /** בוחר מודל Flash זמין בחשבון (החדש ביותר), עם גיבוי ל-Flash-Lite */
    private fun models(): List<String> {
        val cached = Prefs.str("local_gm_models")
        if (cached.isNotBlank()) return cached.split(',')
        val arr = runCatching {
            JSONObject(Http.get("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200", mapOf("x-goog-api-key" to key))).optJSONArray("models")
        }.getOrNull() ?: JSONArray()
        val names = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val n = o.optString("name").removePrefix("models/")
            val gen = o.optJSONArray("supportedGenerationMethods")?.toString()?.contains("generateContent") == true
            n.takeIf { gen && "flash" in it && listOf("image", "tts", "audio", "live", "embed", "thinking", "exp").none { b -> b in it } }
        }
        fun ver(n: String) = Regex("""gemini-(\d+(?:\.\d+)?)""").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val sorted = names.sortedWith(compareByDescending<String> { ver(it) }.thenBy { "lite" in it }.thenBy { "preview" in it })
        val pick = (sorted.filter { "lite" !in it }.take(2) + sorted.filter { "lite" in it }.take(2)).ifEmpty { listOf("gemini-2.5-flash", "gemini-2.5-flash-lite") }
        Prefs.setStrQuiet("local_gm_models", pick.joinToString(","))
        return pick
    }

    private fun ask(prompt: String): JSONObject {
        var last: Exception? = null
        for (m in models()) {
            try {
                val body = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                    .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.2).put("maxOutputTokens", 60000))
                val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent")
                    .header("x-goog-api-key", key)
                    .post(body.toString().toRequestBody("application/json".toMediaType())).build()
                client.newCall(req).execute().use { r ->
                    val s = r.body?.string().orEmpty()
                    if (!r.isSuccessful) throw RuntimeException("Gemini ${r.code}: " + s.take(200))
                    val parts = JSONObject(s).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                        ?: throw RuntimeException("Gemini: empty")
                    val txt = (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text").orEmpty() }
                    return JSONObject(txt.substring(txt.indexOf('{'), txt.lastIndexOf('}') + 1))
                }
            } catch (e: Exception) { last = e }
        }
        throw last ?: RuntimeException("Gemini")
    }

    private fun cacheFile(id: String) = File(com.tbm.iptv.App.instance.filesDir, "cast_" + java.security.MessageDigest.getInstance("MD5")
        .digest(id.toByteArray()).joinToString("") { "%02x".format(it) } + ".json")

    /** ניתוח (ברקע, IO). id = מפתח הפריט + חתימת הכתובית */
    fun analyze(id: String, title: String, year: Int?, tv: Boolean, episode: String?, sf: SubtitleFile, status: (String) -> Unit): Cast? {
        if (currentFor == id && current != null) return current
        val blocks = blocks(sf)
        if (blocks.size < 20) return null
        val f = cacheFile(id)
        val res: JSONObject = if (f.exists()) JSONObject(f.readText()) else {
            status(com.tbm.iptv.L("🎭 מנתח את העלילה והדמויות…"))
            val (plot, cast) = runCatching { tmdb(title, year, tv) }.getOrDefault("" to emptyList())
            val sb = StringBuilder()
            sb.append("You are casting voice actors for an AI dub of ").append(if (tv) "a TV episode" else "a movie")
                .append(": \"").append(title).append("\"").append(if (year != null) " ($year)" else "")
                .append(if (episode != null) " $episode" else "").append(".\n")
            if (plot.isNotBlank()) sb.append("Plot: ").append(plot).append("\n")
            if (cast.isNotEmpty()) sb.append("Main characters (gender from the actor): ")
                .append(cast.joinToString("; ") { it.name + " (" + when (it.gender) { 1 -> "female"; -1 -> "male"; else -> "?" } + ")" }).append("\n")
            sb.append("""
Below are ALL subtitle cues in order, as "<id>|<text>" (the language may be Hebrew; Hebrew verb/adjective gender is a strong clue for the speaker's gender, e.g. "אני הולכת"=female, "אני הולך"=male; vocatives and names show who is addressed, so the speaker is someone else).
For each cue decide which character SPEAKS it, using the plot, the conversation flow and the scene. Keep speakers consistent: the same person keeps talking across consecutive cues unless the dialogue turns. A cue whose lines start with "-" contains two speakers: give both, first and second.
Return JSON only:
{"characters":[{"name":"...","gender":"m|f|u","age":"child|adult|old"}],"cues":"<id>:<charIndex>[/<charIndex>],<id>:<charIndex>,..."}
charIndex is the 0-based index in "characters"; use -1 for narration, songs, signs or unknown. Include every cue id. Include minor unnamed speakers as characters like "Waiter" or "Old woman" when needed.
""".trimIndent()).append("\n\n")
            blocks.forEachIndexed { i, b -> sb.append(i).append('|').append(b.replace('\n', ' ')).append('\n') }
            val o = ask(sb.toString())
            runCatching { f.writeText(o.toString()) }
            o
        }
        val chArr = res.optJSONArray("characters") ?: JSONArray()
        val chars = (0 until chArr.length()).map { i ->
            val o = chArr.optJSONObject(i) ?: JSONObject()
            Ch(o.optString("name"), when (o.optString("gender")) { "f" -> 1; "m" -> -1; else -> 0 }, o.optString("age", "adult"))
        }
        val map = HashMap<String, IntArray>()
        res.optString("cues").split(',').forEach { p ->
            val idx = p.substringBefore(':').trim().toIntOrNull() ?: return@forEach
            val who = p.substringAfter(':', "").split('/').mapNotNull { it.trim().toIntOrNull() }.toIntArray()
            val b = blocks.getOrNull(idx) ?: return@forEach
            if (who.isEmpty()) return@forEach
            who.forEach { w -> chars.getOrNull(w)?.let { it.lines++ } }
            map[norm(b)] = who
        }
        if (map.isEmpty()) return null
        val cast = Cast(chars, map)
        current = cast; currentFor = id
        status(com.tbm.iptv.L("🎭 זוהו {0} דמויות – לכל אחת קול קבוע", chars.count { it.lines > 0 }))
        return cast
    }

    fun activate(c: Cast?, id: String) { current = c; currentFor = if (c == null) "" else id }
}
