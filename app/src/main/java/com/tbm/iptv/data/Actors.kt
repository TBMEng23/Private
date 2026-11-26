package com.tbm.iptv.data

import org.json.JSONObject

/** חיפוש לפי שחקן (TMDB): הצעות שמות תוך כדי הקלדה, ולשחקן שנבחר – הסרטים/הסדרות שלו שקיימים אצל הספק */
object Actors {
    data class Person(val id: Int, val name: String, val knownFor: String)
    data class Result(val name: String, val items: List<Entry>)
    private val suggestCache = java.util.concurrent.ConcurrentHashMap<String, List<Person>>()
    private val creditsCache = java.util.concurrent.ConcurrentHashMap<Int, List<Pair<String, Int?>>>()

    private fun get(path: String, key: String): JSONObject? {
        val bearer = key.length > 40
        val url = "https://api.themoviedb.org/3/$path" + (if (bearer) "" else (if ('?' in path) "&" else "?") + "api_key=${Http.enc(key)}")
        val hdr = if (bearer) mapOf("Authorization" to "Bearer $key", "Accept" to "application/json") else emptyMap()
        return runCatching { JSONObject(Http.get(url, hdr)) }.getOrNull()
    }

    /** שחקנים שהשם שלהם מתאים למה שהוקלד (גם חלקי: "jean cl" → Jean-Claude Van Damme), הכי מוכרים קודם */
    fun suggest(q: String): List<Person> {
        val key = Prefs.tmdbKey
        val qq = q.trim()
        if (key.isBlank() || qq.length < 3) return emptyList()
        suggestCache[qq.lowercase()]?.let { return it }
        val lang = if (qq.any { it in 'א'..'ת' }) "he-IL" else "en-US"
        val res = get("search/person?query=${Http.enc(qq)}&language=$lang&include_adult=false", key)?.optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Pair<Double, Person>>()
        for (i in 0 until res.length()) {
            val o = res.optJSONObject(i) ?: continue
            if (o.optString("known_for_department") != "Acting") continue
            val kf = o.optJSONArray("known_for")
            val titles = if (kf == null) "" else (0 until minOf(2, kf.length())).mapNotNull { j ->
                kf.optJSONObject(j)?.let { it.optString("title").ifBlank { it.optString("name") } }?.takeIf { it.isNotBlank() } }.joinToString(", ")
            out += o.optDouble("popularity", 0.0) to Person(o.optInt("id"), o.optString("name"), titles)
        }
        return out.sortedByDescending { it.first }.map { it.second }.take(6).also { suggestCache[qq.lowercase()] = it }
    }

    private fun credits(id: Int, key: String): List<Pair<String, Int?>> {
        creditsCache[id]?.let { return it }
        val titles = ArrayList<Pair<String, Int?>>()
        for (l in listOf("en-US", "he-IL")) {
            val cast = get("person/$id/combined_credits?language=$l", key)?.optJSONArray("cast") ?: continue
            for (i in 0 until cast.length()) {
                val o = cast.optJSONObject(i) ?: continue
                val year = (o.optString("release_date").ifBlank { o.optString("first_air_date") }).take(4).toIntOrNull()
                listOf("title", "original_title", "name", "original_name").forEach { k ->
                    val t = o.optString(k); if (t.isNotBlank()) titles += t to year
                }
            }
        }
        return titles.also { creditsCache[id] = it }
    }

    /** הסרטים והסדרות של השחקן שקיימים אצל הספק, החדשים קודם */
    fun titlesFor(p: Person, movies: List<Entry>, series: List<Entry>): Result {
        val key = Prefs.tmdbKey
        val titles = if (key.isBlank()) emptyList() else credits(p.id, key)
        val wanted = HashMap<String, Int?>()
        titles.forEach { (t, y) -> val k = Epg.norm(t); if (k.length >= 2) wanted.putIfAbsent(k, y) }
        val out = LinkedHashMap<String, Pair<Int, Entry>>()
        for (e in movies + series) {
            val k = Epg.norm(e.name)
            if (k !in wanted || k in out) continue
            val y = wanted[k]; val ey = TitleParser.parse(e.name).year
            if (e !is SeriesItem && y != null && ey != null && kotlin.math.abs(y - ey) > 1) continue
            out[k] = (ey ?: y ?: 0) to e
            if (out.size >= 80) break
        }
        return Result(p.name, out.values.sortedByDescending { it.first }.map { it.second })
    }

    /** תאימות: השחקן הכי מתאים לשאילתה */
    fun search(q: String, movies: List<Entry>, series: List<Entry>): Result? {
        val p = suggest(q).firstOrNull() ?: return null
        return titlesFor(p, movies, series)
    }
}
