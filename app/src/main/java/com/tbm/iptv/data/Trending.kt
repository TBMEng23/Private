package com.tbm.iptv.data

import org.json.JSONObject

/** "פופולרי היום" לפי TMDB, מותאם לתוכן שקיים אצל הספק */
object Trending {
    private var cacheDay = ""
    private var movieTitles: List<Pair<String, Int?>> = emptyList()
    private var tvTitles: List<Pair<String, Int?>> = emptyList()

    private fun fetch(type: String, key: String): List<Pair<String, Int?>> {
        val res = ArrayList<Pair<String, Int?>>()
        for ((lang, page) in listOf("en-US" to 1, "en-US" to 2, "he-IL" to 1)) {
            val bearer = key.length > 40
            val url = "https://api.themoviedb.org/3/trending/$type/day?language=$lang&page=$page" +
                (if (bearer) "" else "&api_key=${Http.enc(key)}")
            val hdr = if (bearer) mapOf("Authorization" to "Bearer $key", "Accept" to "application/json") else emptyMap()
            val a = runCatching { JSONObject(Http.get(url, hdr)).optJSONArray("results") }.getOrNull() ?: continue
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
            if (out.size >= 40) break
        }
        return out.values.toList()
    }

    // ---------------------------------------------------------------- ז'אנרים (Netflix-style)
    data class Genre(val he: String, val tmdbMovie: Int?, val tmdbTv: Int?, val rx: Regex)

    val genres = listOf(
        Genre("אקשן", 28, 10759, Regex("(?i)(action|אקשן)")),
        Genre("קומדיה", 35, 35, Regex("(?i)(comed|קומדי)")),
        Genre("דרמה", 18, 18, Regex("(?i)(drama|דרמה)")),
        Genre("מתח ומותחנים", 53, 9648, Regex("(?i)(thriller|suspense|mystery|מתח|מותחן)")),
        Genre("מדע בדיוני ופנטזיה", 878, 10765, Regex("(?i)(sci-?fi|science fiction|fantasy|מדע בדיוני|פנטזיה)")),
        Genre("אנימציה", 16, 16, Regex("(?i)(anim|cartoon|אנימציה)")),
        Genre("משפחה וילדים", 10751, 10762, Regex("(?i)(family|kids|children|משפחה|ילדים)")),
        Genre("פשע", 80, 80, Regex("(?i)(crime|פשע)")),
        Genre("אימה", 27, null, Regex("(?i)(horror|אימה)")),
        Genre("רומנטיקה", 10749, null, Regex("(?i)(romance|romantic|רומנטי)")),
        Genre("דוקומנטרי", 99, 99, Regex("(?i)(docu|documentary|דוקו)")),
        Genre("ריאליטי", null, 10764, Regex("(?i)(reality|ריאליטי)")),
    )

    private val discoverCache = HashMap<String, List<Pair<String, Int?>>>()

    private fun fetchDiscover(type: String, genre: Int, key: String): List<Pair<String, Int?>> {
        val res = ArrayList<Pair<String, Int?>>()
        val bearer = key.length > 40
        for (page in 1..3) {
            val url = "https://api.themoviedb.org/3/discover/$type?language=en-US&sort_by=popularity.desc&vote_count.gte=50&with_genres=$genre&page=$page" +
                (if (bearer) "" else "&api_key=${Http.enc(key)}")
            val hdr = if (bearer) mapOf("Authorization" to "Bearer $key", "Accept" to "application/json") else emptyMap()
            val a = runCatching { JSONObject(Http.get(url, hdr)).optJSONArray("results") }.getOrNull() ?: continue
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val year = o.optString(if (type == "movie") "release_date" else "first_air_date").take(4).toIntOrNull()
                listOf("title", "original_title", "name", "original_name").forEach { k -> o.optString(k).takeIf { it.isNotBlank() }?.let { res += it to year } }
            }
        }
        return res
    }

    /** פריטים מהתוכן שלך לפי ז'אנר: TMDB (אם יש מפתח) + קטגוריות הספק ששמן מתאים. חייב לרוץ ברקע */
    fun byGenre(g: Genre, tv: Boolean, items: List<Entry>): List<Entry> {
        val out = LinkedHashMap<String, Entry>()
        val key = Prefs.tmdbKey
        val id = if (tv) g.tmdbTv else g.tmdbMovie
        if (key.isNotBlank() && id != null) {
            val ck = "${if (tv) "tv" else "movie"}|$id|${System.currentTimeMillis() / 86_400_000L}"
            val titles = synchronized(discoverCache) { discoverCache[ck] } ?: runCatching { fetchDiscover(if (tv) "tv" else "movie", id, key) }
                .getOrDefault(emptyList()).also { synchronized(discoverCache) { discoverCache[ck] = it } }
            match(titles, items).forEach { out[it.key] = it }
        }
        if (out.size < 30) items.asSequence().filter { g.rx.containsMatchIn(it.group) }.take(60).forEach { out.putIfAbsent(it.key, it) }
        return out.values.take(40)
    }

    /** חייב לרוץ ברקע (רשת) */
    fun movies(items: List<Entry>): List<Entry> { ensure(); return match(movieTitles, items) }
    fun series(items: List<Entry>): List<Entry> { ensure(); return match(tvTitles, items) }
}
