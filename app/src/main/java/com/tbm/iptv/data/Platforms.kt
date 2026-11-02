package com.tbm.iptv.data

import org.json.JSONObject

/** זיהוי פלטפורמת הסטרימינג (נטפליקס, פריים, דיסני+…) מתוך שם הקטגוריה/הכותר של הספק, ומ-TMDB */
object Platforms {
    data class P(val id: String, val name: String, val short: String, val color: Long, val rx: Regex)

    val all = listOf(
        P("netflix", "Netflix", "NETFLIX", 0xFFE50914, Regex("(?i)(netflix|\\bnf\\b|nflx)")),
        P("prime", "Amazon Prime Video", "prime", 0xFF00A8E1, Regex("(?i)(amazon|prime video|\\bamz\\b|\\bamzn\\b|\\bprime\\b)")),
        P("disney", "Disney+", "Disney+", 0xFF113CCF, Regex("(?i)(disney\\s*\\+|disney plus|\\bdsnp\\b|\\bdsny\\b)")),
        P("apple", "Apple TV+", "tv+", 0xFF555555, Regex("(?i)(apple\\s*tv|\\batvp\\b)")),
        P("hbo", "HBO Max", "max", 0xFF5822B4, Regex("(?i)(hbo|\\bmax\\b)")),
        P("paramount", "Paramount+", "P+", 0xFF0064FF, Regex("(?i)(paramount|\\bpmtp\\b)")),
        P("hulu", "Hulu", "hulu", 0xFF1CE783, Regex("(?i)(\\bhulu\\b)")),
        P("peacock", "Peacock", "peacock", 0xFF222222, Regex("(?i)(peacock|\\bpcok\\b)")),
        P("showtime", "Showtime", "SHO", 0xFFB00000, Regex("(?i)(showtime|\\bsho\\b)")),
        P("starz", "Starz", "STARZ", 0xFF111111, Regex("(?i)(\\bstarz\\b)")),
        P("yes", "yes", "yes", 0xFF7AC143, Regex("(?i)(\\byes\\b|yes\\s*vod)")),
        P("hot", "HOT", "HOT", 0xFFE4002B, Regex("(?i)(\\bhot\\b\\s*(vod|tv)?)")),
        P("cellcom", "Cellcom tv", "cellcom", 0xFF6C2DC7, Regex("(?i)(cellcom)")),
    )
    private val byName = all.associateBy { it.name.lowercase() }
    private val cache = HashMap<String, P?>()

    /** מזהה לפי שם הקבוצה ואז לפי שם הכותר (קידומת כמו "4K-AMZ -") */
    fun detect(e: Entry): P? = synchronized(cache) {
        cache.getOrPut(e.key) {
            all.firstOrNull { it.rx.containsMatchIn(e.group) } ?: run {
                val prefix = e.name.substringBefore(" - ", "").take(14)
                if (prefix.isNotBlank()) all.firstOrNull { it.rx.containsMatchIn(prefix) } else null
            }
        }
    }

    /** שמות הפלטפורמות שבהן הכותר זמין (TMDB watch providers – ישראל, אחרת ארה"ב) */
    fun tmdbProviders(tmdbId: String?, tv: Boolean): List<String> {
        val key = Prefs.tmdbKey
        if (tmdbId.isNullOrBlank() || key.isBlank()) return emptyList()
        return runCatching {
            val bearer = key.length > 40
            val url = "https://api.themoviedb.org/3/${if (tv) "tv" else "movie"}/$tmdbId/watch/providers" +
                (if (bearer) "" else "?api_key=${Http.enc(key)}")
            val hdr = if (bearer) mapOf("Authorization" to "Bearer $key") else emptyMap()
            val res = JSONObject(Http.get(url, hdr)).optJSONObject("results") ?: return emptyList()
            val c = res.optJSONObject("IL") ?: res.optJSONObject("US") ?: return emptyList()
            val a = c.optJSONArray("flatrate") ?: c.optJSONArray("ads") ?: return emptyList()
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("provider_name") }.distinct().take(5)
        }.getOrDefault(emptyList())
    }

    fun find(name: String): P? = byName[name.lowercase()] ?: all.firstOrNull { it.rx.containsMatchIn(name) }
}
