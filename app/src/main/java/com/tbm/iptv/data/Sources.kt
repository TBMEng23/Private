package com.tbm.iptv.data

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.tbm.iptv.App

/** זיהוי סוג מכשיר */
object Device {
    val isTv: Boolean by lazy {
        val c = App.instance
        runCatching {
            (c.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                c.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                c.packageManager.hasSystemFeature("android.software.leanback_only")
        }.getOrDefault(false)
    }
}

/** מקורות נוספים לאותו סרט / סדרה (אותו שם אחרי ניקוי קידומות ספק, איכות ושנה) */
object Sources {
    private val prefixRx = Regex("^\\s*(\\|[^|]{1,8}\\||\\[[^\\]]{1,12}]|\\([^)]{1,6}\\)|[A-Za-z0-9+]{2,5}\\s*[-:|•»]+)\\s*")
    private val yearRx = Regex("(?<!\\d)(19[2-9]\\d|20[0-4]\\d)(?!\\d)")
    private val tagRx = Regex("(?i)(?<![\\p{L}\\p{N}])(4k|uhd|2160p?|fhd|1080p?|hd|720p?|sd|480p?|hevc|h\\.?26[45]|x26[45]|hdr10?|dv|dolby|atmos|multi|multisub|sub|subs|dub|dubbed|vip|raw|cam|ts|web-?dl|webrip|bluray|imax)(?![\\p{L}\\p{N}])")
    private val nonAlnum = Regex("[^\\p{L}\\p{N}]+")

    fun year(name: String): String? = yearRx.findAll(name).lastOrNull()?.value

    fun norm(name: String): String {
        var s = name
        repeat(3) { s = s.replace(prefixRx, "") }
        s = s.replace(yearRx, " ").replace(tagRx, " ")
        return s.lowercase().replace(nonAlnum, "")
    }

    private val dubRx = Regex("(?i)מדובב|דיבוב|עברית|(?<![a-z])heb(rew)?(?![a-z])|\\bil\\b|ישראל")
    /** מדובב / בעברית */
    fun hebDub(e: Entry): Boolean = dubRx.containsMatchIn(e.name) || dubRx.containsMatchIn(e.group)

    /** איכות לפי שם / קטגוריה */
    fun quality(e: Entry): String? {
        val t = (e.name + " " + e.group).lowercase()
        return when {
            Regex("4k|uhd|2160").containsMatchIn(t) -> "4K"
            Regex("fhd|1080").containsMatchIn(t) -> "FHD"
            Regex("(?<![a-z])hd(?![a-z])|720").containsMatchIn(t) -> "HD"
            Regex("(?<![a-z])sd(?![a-z])|480").containsMatchIn(t) -> "SD"
            Regex("(?<![a-z])cam(?![a-z])").containsMatchIn(t) -> "CAM"
            else -> null
        }
    }

    // מטמון אינדקס לכל רשימה (סרטים / סדרות) – שומר רק שמות שיש להם יותר ממקור אחד
    private class Idx(val src: List<Entry>, val map: Map<String, List<Entry>>)
    @Volatile private var cache: List<Idx> = emptyList()

    private fun index(list: List<Entry>): Map<String, List<Entry>> {
        cache.firstOrNull { it.src === list }?.let { return it.map }
        val m = HashMap<String, Any>()
        for (e in list) {
            val n = norm(e.name)
            if (n.length < 2) continue
            when (val cur = m[n]) {
                null -> m[n] = e
                is ArrayList<*> -> @Suppress("UNCHECKED_CAST") (cur as ArrayList<Entry>).add(e)
                else -> m[n] = arrayListOf(cur as Entry, e)
            }
        }
        @Suppress("UNCHECKED_CAST")
        val multi = HashMap<String, List<Entry>>().apply { m.forEach { (k, v) -> if (v is ArrayList<*>) put(k, v as List<Entry>) } }
        cache = (cache.filter { c -> c.src.isNotEmpty() } + Idx(list, multi)).takeLast(2)
        return multi
    }

    /** כל המקורות לאותו כותר (כולל הנוכחי, ראשון) – רשימה ריקה אם אין יותר ממקור אחד */
    fun alternatives(e: Entry, list: List<Entry>): List<Entry> {
        val n = norm(e.name)
        if (n.length < 2) return emptyList()
        val y = year(e.name)
        val same = index(list)[n].orEmpty().filter { o -> o.key != e.key && (y == null || year(o.name).let { it == null || it == y }) }
        return if (same.isEmpty()) emptyList() else listOf(e) + same
    }
}


/** שורות לפי דמויות ונושאים בפרופיל ילדים */
object KidsThemes {
    private fun rx(p: String) = Regex("(?i)$p")
    val all: List<Pair<String, Regex>> = listOf(
        "🐾 חיות" to rx("animal|חיות|חיה |dog|puppy|cat|kitten|lion|panda|bear|tiger|elephant|monkey|bunny|כלב|חתול|אריה|פנדה|דוב|נמר|פיל|קוף|ארנב|jungle|ג'ונגל|zoo|גן חיות|madagascar|zootopia|lion king"),
        "🦸 גיבורי על" to rx("super ?hero|superman|spider|batman|avengers|marvel|justice league|incredibles|גיבור|ספיידר|באטמן|סופרמן|נוקמים|pj masks|miraculous"),
        "👑 נסיכות" to rx("princess|frozen|cinderella|rapunzel|tangled|moana|ariel|little mermaid|sofia|נסיכ|לשבור את הקרח|סינדרלה|רפונזל|מואנה|בת הים"),
        "🚗 מכוניות ורכבים" to rx("cars|car |truck|race|racing|blaze|paw patrol|robocar|thomas|מכונ|משאית|מירוץ|כוח פעולה|רובוקאר|רכבת"),
        "🦖 דינוזאורים" to rx("dino|דינוזאור|jurassic|יורה|land before time"),
        "🚀 חלל ומדע" to rx("space|rocket|planet|astronaut|alien|חלל|טיל|כוכב|חייזר|מדע|science"),
        "🎵 שירים ומוזיקה" to rx("sing|song|music|musical|שיר|מוזיק|ריקוד|dance"),
    )
}
