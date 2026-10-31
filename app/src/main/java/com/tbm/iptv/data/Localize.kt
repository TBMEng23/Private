package com.tbm.iptv.data

import com.tbm.iptv.I18n
import com.tbm.iptv.subs.MachineTranslate
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * תרגום תוכן (שם, תקציר, ז'אנר, שחקנים, פרקים) לשפת הממשק.
 * עדיפות: TMDB בעברית (אם יש מפתח ומזהה) → Google Translate. התוצאות נשמרות במטמון במכשיר.
 */
object Localize {
    private val heb = Regex("[\\u0590-\\u05FF]")
    private val mem = ConcurrentHashMap<String, String>()
    private var loaded = false

    val enabled get() = Prefs.bool("translate_meta", true)
    val target get() = if (I18n.isEn) "en" else "he"

    private val genresHe = mapOf(
        "action" to "אקשן", "adventure" to "הרפתקאות", "animation" to "אנימציה", "comedy" to "קומדיה", "crime" to "פשע",
        "documentary" to "דוקומנטרי", "drama" to "דרמה", "family" to "משפחה", "fantasy" to "פנטזיה", "history" to "היסטוריה",
        "horror" to "אימה", "music" to "מוזיקה", "musical" to "מחזמר", "mystery" to "מסתורין", "romance" to "רומנטיקה",
        "science fiction" to "מדע בדיוני", "sci-fi" to "מדע בדיוני", "thriller" to "מותחן", "war" to "מלחמה",
        "western" to "מערבון", "tv movie" to "סרט טלוויזיה", "action & adventure" to "אקשן והרפתקאות", "kids" to "ילדים",
        "news" to "חדשות", "reality" to "ריאליטי", "soap" to "אופרת סבון", "talk" to "אירוח", "war & politics" to "מלחמה ופוליטיקה",
        "sci-fi & fantasy" to "מדע בדיוני ופנטזיה", "biography" to "ביוגרפיה", "sport" to "ספורט", "sports" to "ספורט",
        "suspense" to "מתח", "anime" to "אנימה", "short" to "קצר", "superhero" to "גיבורי על",
    )
    private val genresEn by lazy { genresHe.entries.associate { (k, v) -> v to k.replaceFirstChar { it.uppercase() } } }

    private fun file() = File(com.tbm.iptv.App.instance.filesDir, "meta_tr.json")

    @Synchronized private fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val o = JSONObject(file().readText())
            o.keys().forEach { k -> mem[k] = o.getString(k) }
        }
    }

    @Synchronized private fun save() {
        runCatching {
            // שומר עד 3000 רשומות אחרונות
            val o = JSONObject()
            mem.entries.toList().takeLast(3000).forEach { (k, v) -> o.put(k, v) }
            file().writeText(o.toString())
        }
    }

    private fun needs(text: String, to: String) = text.isNotBlank() && (if (to == "he") !heb.containsMatchIn(text) else heb.containsMatchIn(text))

    /** מתרגם רשימה (עם מטמון). חוזר מיד עם המקור אם התרגום כבוי או לא נדרש. */
    fun texts(items: List<String?>, to: String = target): List<String?> {
        if (!enabled) return items
        load()
        val todo = items.filterNotNull().filter { needs(it, to) && mem["$to|$it"] == null }.distinct()
        if (todo.isNotEmpty()) {
            val res = runCatching { MachineTranslate.texts(todo, to) }.getOrNull()
            if (res != null && res.size == todo.size) {
                todo.forEachIndexed { i, s -> mem["$to|$s"] = res[i] }
                save()
            }
        }
        return items.map { s -> if (s == null || !needs(s, to)) s else mem["$to|$s"] ?: s }
    }

    fun genre(g: String?, to: String = target): String? {
        if (g.isNullOrBlank() || !enabled) return g
        val parts = g.split(',', '/', '|').map { it.trim() }.filter { it.isNotEmpty() }
        val mapped = parts.map { p -> if (to == "he") genresHe[p.lowercase()] else genresEn[p] }
        val unknown = parts.filterIndexed { i, _ -> mapped[i] == null }
        val tr = if (unknown.isEmpty()) emptyList() else texts(unknown, to)
        var u = 0
        return parts.mapIndexed { i, p -> mapped[i] ?: (tr.getOrNull(u++) ?: p) }.joinToString(" / ")
    }

    /** כותרת ותקציר מ-TMDB בשפת היעד (movie/tv) */
    fun tmdb(id: String?, tv: Boolean, to: String = target): Pair<String?, String?>? {
        val key = Prefs.tmdbKey
        if (id.isNullOrBlank() || key.isBlank() || !enabled) return null
        load()
        val ck = "tmdb|$to|$tv|$id"
        mem[ck]?.let { v -> return v.substringBefore('\u0001').ifBlank { null } to v.substringAfter('\u0001', "").ifBlank { null } }
        return runCatching {
            val bearer = key.length > 40
            val lang = if (to == "he") "he-IL" else "en-US"
            val url = "https://api.themoviedb.org/3/${if (tv) "tv" else "movie"}/$id?language=$lang" +
                (if (bearer) "" else "&api_key=${Http.enc(key)}")
            val hdr = if (bearer) mapOf("Authorization" to "Bearer $key", "Accept" to "application/json") else emptyMap()
            val o = JSONObject(Http.get(url, hdr))
            val title = o.optString(if (tv) "name" else "title").ifBlank { null }
            val ov = o.optString("overview").ifBlank { null }
            mem[ck] = (title ?: "") + '\u0001' + (ov ?: ""); save()
            title to ov
        }.getOrNull()
    }

    /** מסיר קידומות ספק כמו "NF - ", "EN - ", "|HE| " ושנה בסוף לצורך תרגום */
    fun cleanTitle(t: String): String =
        t.replace(Regex("^\\s*(\\|[^|]{1,6}\\||\\[[^\\]]{1,8}]|[A-Z0-9]{2,4}\\s*[-:|])\\s*"), "").trim()
}
