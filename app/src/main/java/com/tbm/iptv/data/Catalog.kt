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
            base = all.filter { (it.key in ki || "${kind.name}|${it.group}" in kg) && !isAdult(it.group) }
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
