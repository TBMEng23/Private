package com.tbm.iptv.data

import org.json.JSONObject

/**
 * דילוג על פתיח / סיכום / כתוביות סיום – כמו באפליקציות המובילות:
 * 1. מאגר קהילתי (IntroDB – לפי IMDb + עונה + פרק), כמו Stremio / Jellyfin / Infuse.
 * 2. למידה: כשמדלגים ידנית על הפתיח (חיצים / סרגל) – הזמנים נשמרים לסדרה ולעונה ומוצעים בכל פרק הבא,
 *    בדומה לזיהוי הפתיח המשותף לפרקים של Plex / Netflix. נשמר בחשבון ומסתנכרן בין המכשירים.
 */
object IntroSkip {
    data class Seg(val start: Long, val end: Long)
    data class Info(val intro: Seg?, val recap: Seg?, val outro: Long?, val fromDb: Boolean)

    private val mem = HashMap<String, Info>()

    private fun norm(s: String) = Localize.cleanTitle(s).lowercase()
        .replace(Regex("\\((19|20)\\d\\d\\)|\\b(19|20)\\d\\d\\b"), "")
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun learnKey(series: String, season: Int?) = "intro_l_${norm(series).replace(' ', '_')}_${season ?: 0}"
    private fun learnKeyAny(series: String) = "intro_l_${norm(series).replace(' ', '_')}_any"

    /** פתיח שנלמד מהדילוגים של המשתמש (קודם העונה, אחר כך כל עונה בסדרה) */
    fun learned(series: String, season: Int?): Seg? {
        val v = Prefs.str(learnKey(series, season)).ifBlank { Prefs.str(learnKeyAny(series)) }
        val p = v.split(',').mapNotNull { it.toLongOrNull() }
        return if (p.size == 2 && p[1] > p[0]) Seg(p[0], p[1]) else null
    }

    fun learn(series: String, season: Int?, start: Long, end: Long) {
        val v = "$start,$end"
        Prefs.setStr(learnKey(series, season), v)
        Prefs.setStr(learnKeyAny(series), v)
    }

    fun forget(series: String, season: Int?) {
        Prefs.setStr(learnKey(series, season), "")
        Prefs.setStr(learnKeyAny(series), "")
    }

    /** מזהה IMDb של הסדרה – דרך TMDB (חיפוש לפי שם → external_ids). נשמר במטמון */
    private fun imdbOf(series: String): String? {
        val ck = "local_imdb_" + norm(series).replace(' ', '_')
        Prefs.str(ck).takeIf { it.isNotBlank() }?.let { return it.takeIf { it != "-" } }
        val key = Prefs.tmdbKey
        if (key.isBlank()) return null
        val bearer = key.length > 40
        val hdr = if (bearer) mapOf("Authorization" to "Bearer $key", "Accept" to "application/json") else emptyMap()
        val k = if (bearer) "" else "&api_key=${Http.enc(key)}"
        val q = norm(series)
        if (q.isBlank()) return null
        val res = runCatching {
            JSONObject(Http.get("https://api.themoviedb.org/3/search/tv?query=${Http.enc(q)}$k", hdr)).optJSONArray("results")
        }.getOrNull() ?: return null
        if (res.length() == 0) { Prefs.setStrQuiet(ck, "-"); return null }
        val id = res.getJSONObject(0).optInt("id")
        val imdb = runCatching {
            JSONObject(Http.get("https://api.themoviedb.org/3/tv/$id/external_ids?x=1$k", hdr)).optString("imdb_id")
        }.getOrNull()?.takeIf { it.startsWith("tt") }
        Prefs.setStrQuiet(ck, imdb ?: "-")
        return imdb
    }

    private fun seg(o: JSONObject?, k: String): Seg? {
        val s = o?.optJSONObject(k) ?: return null
        val st = if (s.isNull("start_ms")) 0L else s.optLong("start_ms", -1)
        val en = if (s.isNull("end_ms")) -1L else s.optLong("end_ms", -1)
        return if (st >= 0 && en > st) Seg(st, en) else null
    }

    /** נקרא ברקע (IO) כשמתחיל פרק */
    fun load(series: String?, season: Int?, episode: Int?): Info? {
        if (series.isNullOrBlank() || season == null || episode == null) return null
        val ck = "${norm(series)}|$season|$episode"
        mem[ck]?.let { return it }
        val fromDb = runCatching {
            val imdb = imdbOf(series) ?: return@runCatching null
            val o = JSONObject(Http.get("https://api.introdb.app/segments?imdb_id=$imdb&season=$season&episode=$episode"))
            val outro = o.optJSONObject("outro")?.let { if (it.isNull("start_ms")) null else it.optLong("start_ms") }
            Info(seg(o, "intro") ?: learned(series, season), seg(o, "recap"), outro, true)
        }.getOrNull()
        val info = fromDb ?: Info(learned(series, season), null, null, false)
        mem[ck] = info
        return info
    }
}
