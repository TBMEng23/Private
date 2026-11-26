package com.tbm.iptv.data

import org.json.JSONObject

/** חיפוש לפי שם שחקן (TMDB): מוצאים את השחקן, את הסרטים/הסדרות שלו, ומציגים רק מה שקיים אצל הספק */
object Actors {
    data class Result(val name: String, val items: List<Entry>)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, List<Pair<String, Int?>>>>()

    private fun get(path: String, key: String): JSONObject? {
        val bearer = key.length > 40
        val url = "https://api.themoviedb.org/3/$path" + (if (bearer) "" else (if ('?' in path) "&" else "?") + "api_key=${Http.enc(key)}")
        val hdr = if (bearer) mapOf("Authorization" to "Bearer $key", "Accept" to "application/json") else emptyMap()
        return runCatching { JSONObject(Http.get(url, hdr)) }.getOrNull()
    }

    /** שם השחקן + רשימת כותרים (כל השמות: עברית/מקור) עם שנה */
    private fun credits(q: String, key: String): Pair<String, List<Pair<String, Int?>>>? {
        cache[q.lowercase()]?.let { return it }
        // עברית או אנגלית – TMDB מחפש בשני השמות
        val lang = if (q.any { it in 'א'..'ת' }) "he-IL" else "en-US"
        val res = get("search/person?query=${Http.enc(q)}&language=$lang", key)?.optJSONArray("results") ?: return null
        var person: JSONObject? = null
        for (i in 0 until res.length()) {
            val o = res.optJSONObject(i) ?: continue
            if (o.optString("known_for_department") == "Acting" || person == null) { person = o; if (o.optString("known_for_department") == "Acting") break }
        }
        val p = person ?: return null
        val name = p.optString("name").ifBlank { q }
        val titles = ArrayList<Pair<String, Int?>>()
        for (l in listOf("en-US", "he-IL")) {
            val cast = get("person/${p.optInt("id")}/combined_credits?language=$l", key)?.optJSONArray("cast") ?: continue
            for (i in 0 until cast.length()) {
                val o = cast.optJSONObject(i) ?: continue
                val year = (o.optString("release_date").ifBlank { o.optString("first_air_date") }).take(4).toIntOrNull()
                listOf("title", "original_title", "name", "original_name").forEach { k ->
                    val t = o.optString(k); if (t.isNotBlank()) titles += t to year
                }
            }
        }
        return (name to titles).also { cache[q.lowercase()] = it }
    }

    fun search(q: String, movies: List<Entry>, series: List<Entry>): Result? {
        val key = Prefs.tmdbKey
        if (key.isBlank() || q.trim().length < 3) return null
        val (name, titles) = credits(q.trim(), key) ?: return null
        if (titles.isEmpty()) return null
        val wanted = HashMap<String, Int?>()
        titles.forEach { (t, y) -> val k = Epg.norm(t); if (k.length >= 2) wanted.putIfAbsent(k, y) }
        val out = LinkedHashMap<String, Entry>()
        for (e in movies + series) {
            val k = Epg.norm(e.name)
            if (k !in wanted || k in out) continue
            // סרטים: גם השנה צריכה להתאים (±1) כשידועה – כדי לא לתפוס סרט אחר עם אותו שם
            val y = wanted[k]; val ey = TitleParser.parse(e.name).year
            if (e !is SeriesItem && y != null && ey != null && kotlin.math.abs(y - ey) > 1) continue
            out[k] = e
            if (out.size >= 60) break
        }
        return Result(name, out.values.toList())
    }
}
