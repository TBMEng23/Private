package com.tbm.iptv.data

import org.json.JSONObject

/** "פופולרי היום" לפי TMDB, מותאם לתוכן שקיים אצל הספק */
object Trending {
    private var cacheDay = ""
    private var movieTitles: List<Pair<String, Int?>> = emptyList()
    private var tvTitles: List<Pair<String, Int?>> = emptyList()

    private fun fetch(type: String, key: String): List<Pair<String, Int?>> {
        val res = ArrayList<Pair<String, Int?>>()
        for (lang in listOf("en-US", "he-IL")) {
            val bearer = key.length > 40
            val url = "https://api.themoviedb.org/3/trending/$type/day?language=$lang" +
                (if (bearer) "" else "&api_key=${Http.enc(key)}")
            val hdr = if (bearer) mapOf("Authorization" to "Bearer $key", "Accept" to "application/json") else emptyMap()
            val a = JSONObject(Http.get(url, hdr)).optJSONArray("results") ?: continue
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val date = o.optString(if (type == "movie") "release_date" else "first_air_date")
                val year = date.take(4).toIntOrNull()
                listOf("title", "original_title", "name", "original_name").forEach { k ->
                    val t = o.optString(k)
                    if (t.isNotBlank()) res += t to year
                }
            }
        }
        return res
    }

    private fun ensure() {
        val key = Prefs.tmdbKey
        if (key.isBlank()) { movieTitles = emptyList(); tvTitles = emptyList(); return }
        val day = (System.currentTimeMillis() / 86_400_000L).toString() + key.take(6)
        if (day == cacheDay && (movieTitles.isNotEmpty() || tvTitles.isNotEmpty())) return
        movieTitles = runCatching { fetch("movie", key) }.getOrDefault(emptyList())
        tvTitles = runCatching { fetch("tv", key) }.getOrDefault(emptyList())
        cacheDay = day
    }

    private fun match(titles: List<Pair<String, Int?>>, items: List<Entry>): List<Entry> {
        if (titles.isEmpty()) return emptyList()
        val index = HashMap<String, MutableList<Entry>>()
        items.forEach { e ->
            val k = Epg.norm(e.name)
            if (k.isNotEmpty()) index.getOrPut(k) { ArrayList() } += e
        }
        val out = LinkedHashMap<String, Entry>()
        for ((t, year) in titles) {
            val cands = index[Epg.norm(t)] ?: continue
            val best = cands.firstOrNull { year == null || TitleParser.parse(it.name).year == null || TitleParser.parse(it.name).year == year }
                ?: continue
            out.putIfAbsent(Epg.norm(t), best)
            if (out.size >= 30) break
        }
        return out.values.toList()
    }

    /** חייב לרוץ ברקע (רשת) */
    fun movies(items: List<Entry>): List<Entry> { ensure(); return match(movieTitles, items) }
    fun series(items: List<Entry>): List<Entry> { ensure(); return match(tvTitles, items) }
}
