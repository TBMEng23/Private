package com.tbm.iptv.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** מצב בקרת הורים בזמן ריצה */
object Parental {
    /** האם הוזן קוד בסשן הנוכחי (פותח תוכן נעול בפרופיל מבוגר) */
    var unlocked by mutableStateOf(false)

    class PinRequest(val title: String, val onOk: () -> Unit)

    var request by mutableStateOf<PinRequest?>(null)

    fun hasPin() = Prefs.pin.length >= 4

    /** מריץ את הפעולה אם אין קוד או שכבר נפתח – אחרת מציג חלון קוד */
    fun require(title: String, onOk: () -> Unit) {
        if (!hasPin() || unlocked) onOk() else request = PinRequest(title, onOk)
    }

    /** כמו require אבל תמיד דורש קוד (מעבר לפרופיל מבוגר) */
    fun requireAlways(title: String, onOk: () -> Unit) {
        if (!hasPin()) onOk() else request = PinRequest(title, onOk)
    }

    fun check(pin: String): Boolean = pin == Prefs.pin

    fun switchProfile(p: Profile) {
        Prefs.profileId = p.id
        unlocked = false
    }
}

/** סינון אוטומטי לפרופיל ילדים לפי גיל: קטגוריות/ערוצי ילדים מותרים, תוכן קשה חסום */
object KidsFilter {
    private val kidsRx = Regex("(?i)(\\bkids?\\b|child|cartoon|toon|junior|\\bjr\\b|bab(y|ies)|preschool|nick|disney|cbeebies|cbbc|boomerang|cartoonito|pbs kids|ילדים|ילד|פעוטות|(^|[^א-ת])הופ|לולי|ניקלודיאון|דיסני|זום|חינוכית|baby ?tv)")
    private val familyRx = Regex("(?i)(anim|anime|family|משפחה|משפחתי|אנימציה|אנימה|pixar|dreamworks)")
    private val teenRx = Regex("(?i)(teen|נוער|youth)")
    private val hardRx = Regex("(?i)(horror|אימה|slasher|gore|thriller|מותחן|crime|פשע|war\\b|מלחמה|erotic|ארוטי)")

    fun matches(s: String, age: Int): Boolean =
        kidsRx.containsMatchIn(s) || (age >= 10 && familyRx.containsMatchIn(s)) || (age >= 13 && teenRx.containsMatchIn(s))

    fun blocked(s: String, age: Int): Boolean = age < 16 && hardRx.containsMatchIn(s)

    /** נושאים נוספים לפרופיל ילדים */
    val topicRx = linkedMapOf(
        "nature" to Regex("(?i)(nat ?geo|national geographic|wild|animal|planet|earth|nature|טבע|חיות|בעלי חיים)"),
        "sport" to Regex("(?i)(sport|ספורט|espn|eurosport|bein|\\bnba\\b|\\bnfl\\b|fifa|uefa|football|soccer|כדורגל|כדורסל)"),
        "docu" to Regex("(?i)(docu|documentary|science|history|discovery|דוקו|מדע|היסטוריה)"),
        "music" to Regex("(?i)(music|\\bmtv\\b|מוזיקה|קליפ)"),
        "edu" to Regex("(?i)(educat|learning|חינוכית|לימוד)"),
    )

    fun topicMatch(topics: Set<String>, s: String): Boolean = topics.any { topicRx[it]?.containsMatchIn(s) == true }

    /** גיל מינימלי לפי סיווג (ארה"ב / ישראל) */
    fun certAge(c: String): Int? = when (c.trim().uppercase()) {
        "G", "TV-Y", "TV-G", "U", "0", "ALL" -> 0
        "TV-Y7", "TV-Y7-FV", "7" -> 7
        "PG", "TV-PG", "8" -> 8
        "12", "12+" -> 12
        "PG-13", "13" -> 13
        "TV-14", "14", "15" -> 14
        "16", "16+" -> 16
        "R", "TV-MA", "17", "NC-17", "18", "18+" -> 18
        else -> null
    }

    /** בודק ב-TMDB את סיווג הגיל של סרט/סדרה. null = לא ידוע */
    fun tmdbAge(tmdbId: String?, tv: Boolean): Int? {
        val key = Prefs.tmdbKey
        if (tmdbId.isNullOrBlank() || key.isBlank()) return null
        return runCatching {
            val bearer = key.length > 40
            val path = if (tv) "tv/$tmdbId/content_ratings" else "movie/$tmdbId/release_dates"
            val url = "https://api.themoviedb.org/3/$path" + (if (bearer) "" else "?api_key=${Http.enc(key)}")
            val hdr = if (bearer) mapOf("Authorization" to "Bearer $key") else emptyMap()
            val arr = org.json.JSONObject(Http.get(url, hdr)).optJSONArray("results") ?: return null
            val found = HashMap<String, Int>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val cc = o.optString("iso_3166_1")
                if (tv) certAge(o.optString("rating"))?.let { found[cc] = it }
                else o.optJSONArray("release_dates")?.let { rd ->
                    for (j in 0 until rd.length()) certAge(rd.optJSONObject(j)?.optString("certification") ?: "")?.let { found[cc] = maxOf(found[cc] ?: 0, it) }
                }
            }
            found["IL"] ?: found["US"] ?: found["GB"] ?: found.values.maxOrNull()
        }.getOrNull()
    }
}

/** בניית התצוגה של כל סוג תוכן לפי פרופיל, סינונים, נעילות, שמות וסדר */
object Catalog {
    val adultRx = Regex("(?i)(adult|xxx|18\\+|\\+18|porn|for adults|למבוגרים|ארוטי)")

    class View(val items: List<Entry>, val groups: List<String>, val lockedGroups: Set<String>)

    fun isAdult(group: String) = adultRx.containsMatchIn(group)

    fun isGroupLocked(kind: Kind, group: String): Boolean =
        Parental.hasPin() && ("${kind.name}|$group" in Prefs.lockedGroups() ||
            (Prefs.adultMode == "lock" && isAdult(group)))

    fun isItemLocked(kind: Kind, e: Entry): Boolean =
        Parental.hasPin() && (e.key in Prefs.lockedItems() || isGroupLocked(kind, e.group))

    fun isKidsApproved(kind: Kind, e: Entry): Boolean =
        e.key in Prefs.kidsItems() || "${kind.name}|${e.group}" in Prefs.kidsGroups()

    fun rename(e: Entry, renames: Map<String, String>): Entry {
        val n = renames[e.key] ?: return e
        return when (e) {
            is Channel -> e.copy(name = n)
            is SeriesItem -> e.copy(name = n)
            else -> e
        }
    }

    fun build(kind: Kind, all: List<Entry>): View {
        val renames = Prefs.renames()
        val order = Prefs.order()
        val base: List<Entry>
        val locked = HashSet<String>()

        if (Prefs.isKids) {
            val kg = Prefs.kidsGroups(); val ki = Prefs.kidsItems()
            val prof = Prefs.profile()
            val inc = Prefs.kidsInc(prof.id); val exc = Prefs.kidsExc(prof.id)
            val autoOk = HashMap<String, Boolean>()
            base = all.filter { e ->
                val gk = "${kind.name}|${e.group}"
                if (gk in exc || isAdult(e.group)) return@filter false
                if (e.key in ki || gk in inc) return@filter true
                if (KidsFilter.blocked(e.group, prof.age) || KidsFilter.blocked(e.name, prof.age)) return@filter false
                if (prof.countries.isNotEmpty() && Countries.of(e.group) !in prof.countries) return@filter false
                gk in kg ||
                    (prof.auto && (autoOk.getOrPut(e.group) { KidsFilter.matches(e.group, prof.age) || KidsFilter.topicMatch(prof.topics, e.group) } ||
                        (kind == Kind.LIVE && (KidsFilter.matches(e.name, prof.age) || KidsFilter.topicMatch(prof.topics, e.name)))))
            }
        } else {
            val hidden = Prefs.hidden()
            val hiddenItems = Prefs.hiddenItems()
            val allowed = Prefs.allowedCountries(kind)
            val adultMode = Prefs.adultMode
            val ok = HashMap<String, Boolean>()
            val filtered = all.filter { e ->
                e.key !in hiddenItems && ok.getOrPut(e.group) {
                    "${kind.name}|${e.group}" !in hidden &&
                        (allowed.isEmpty() || Countries.of(e.group) in allowed) &&
                        !(adultMode == "hide" && isAdult(e.group))
                }
            }
            filtered.map { it.group }.distinct().forEach { g -> if (isGroupLocked(kind, g)) locked += g }
            base = if (Parental.hasPin() && !Parental.unlocked) {
                val li = Prefs.lockedItems()
                filtered.filter { it.group !in locked && it.key !in li }
            } else filtered
            // קבוצות נעולות נשארות ברשימת הקבוצות (מוצגות עם מנעול)
            val groups = orderGroups(kind, filtered.map { it.group }.distinct())
            return View(sortAndRename(base, order, renames, groups), groups, locked)
        }
        val groups = orderGroups(kind, base.map { it.group }.distinct())
        return View(sortAndRename(base, order, renames, groups), groups, locked)
    }

    private fun sortAndRename(
        list: List<Entry>, order: Map<String, Int>, renames: Map<String, String>, groups: List<String>,
    ): List<Entry> {
        val gi = HashMap<String, Int>(); groups.forEachIndexed { i, g -> gi[g] = i }
        val sorted = list.withIndex().sortedWith(
            compareBy<IndexedValue<Entry>>({ gi[it.value.group] ?: Int.MAX_VALUE },
                { order[it.value.key] ?: Int.MAX_VALUE }, { it.index })
        ).map { it.value }
        return if (renames.isEmpty()) sorted else sorted.map { rename(it, renames) }
    }

    /** קבוצות נעוצות קודם, אחר כך סדר ידני, אחר כך הסדר המקורי */
    fun orderGroups(kind: Kind, groups: List<String>): List<String> {
        val pinned = Prefs.pinnedGroups(kind)
        val custom = Prefs.groupOrder(kind)
        if (pinned.isEmpty() && custom.isEmpty()) return groups
        val pi = HashMap<String, Int>(); pinned.forEachIndexed { i, g -> pi[g] = i }
        val ci = HashMap<String, Int>(); custom.forEachIndexed { i, g -> ci[g] = i }
        return groups.withIndex().sortedWith(
            compareBy<IndexedValue<String>>({ pi[it.value] ?: Int.MAX_VALUE },
                { ci[it.value] ?: Int.MAX_VALUE }, { it.index })
        ).map { it.value }
    }

    /** הזזת קבוצה למעלה/למטה */
    fun moveGroup(kind: Kind, groups: List<String>, g: String, delta: Int) {
        val l = orderGroups(kind, groups).toMutableList()
        val i = l.indexOf(g); val j = i + delta
        if (i < 0 || j !in l.indices) return
        val t = l[i]; l[i] = l[j]; l[j] = t
        Prefs.setGroupOrder(kind, l)
    }

    /** הזזת פריט בתוך הקבוצה שלו */
    fun move(kind: Kind, all: List<Entry>, e: Entry, delta: Int) {
        val inGroup = build(kind, all).items.filter { it.group == e.group }
        val i = inGroup.indexOfFirst { it.key == e.key }
        val j = i + delta
        if (i < 0 || j !in inGroup.indices) return
        val keys = inGroup.map { it.key }.toMutableList()
        val tmp = keys[i]; keys[i] = keys[j]; keys[j] = tmp
        Prefs.setOrder(keys.withIndex().associate { it.value to it.index })
    }

    /** תור ניגון – בלי פריטים נעולים אם הקוד לא הוזן */
    fun playable(kind: Kind, list: List<Entry>): List<Entry> =
        if (!Parental.hasPin() || Parental.unlocked) list else list.filter { !isItemLocked(kind, it) }
}
