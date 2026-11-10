package com.tbm.iptv.data

import com.tbm.iptv.L

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject

data class Recent(val item: PlayItem, val pos: Long, val dur: Long, val ts: Long)

/** רשומה בדוח הצפייה של פרופיל ילדים */
data class WatchEntry(val title: String, val kind: String, val start: Long, val durSec: Long, val child: String)

object Prefs {
    private lateinit var sp: SharedPreferences

    /** מונה שינויים – קומפוזבלים קוראים אותו כדי להתעדכן אחרי שינוי הגדרות */
    val version = mutableIntStateOf(0)

    // זמן השינוי האחרון של כל מפתח – לסנכרון בין מכשירים לפי מפתח (השינוי החדש מנצח, לא כל המסמך)
    private lateinit var mt: SharedPreferences

    fun init(c: Context) {
        sp = c.getSharedPreferences("tbm", Context.MODE_PRIVATE)
        mt = c.getSharedPreferences("tbm_mt", Context.MODE_PRIVATE)
    }

    // זמני שינוי נאספים בזיכרון ונכתבים יחד (פחות כתיבות לדיסק)
    private val pendingMt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val mtFlush = Runnable {
        val e = mt.edit(); pendingMt.keys.toList().forEach { k -> pendingMt.remove(k)?.let { e.putLong(k, it) } }; e.apply()
    }
    private val mtHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private fun touch(k: String) {
        if (isLocalKey(k)) return
        pendingMt[k] = System.currentTimeMillis()
        mtHandler.removeCallbacks(mtFlush); mtHandler.postDelayed(mtFlush, 1500)
    }
    /** לפני ייצוא לענן – לכתוב זמנים שממתינים */
    private fun flushMt() { mtHandler.removeCallbacks(mtFlush); mtFlush.run() }

    /** נקרא בכל שינוי (לסנכרון בענן) */
    @Volatile var onChange: (() -> Unit)? = null

    fun bump() { version.intValue = version.intValue + 1; onChange?.invoke() }

    /** מפתחות ששייכים למכשיר הזה בלבד ולא מסונכרנים */
    private fun isLocalKey(k: String) = k == "profile" || k == "device_id" || k.startsWith("cloud_") ||
        k.startsWith("mini_") || k == "pip_warned" || k == "player_landscape" ||
        k.startsWith("updated_") || k.startsWith("tz_") || k.startsWith("exp_") || k.startsWith("status_") ||
        k.startsWith("kidsel_") || k == "kh_ovr" || k == "upd_last" || k == "upd_fail"

    /** מזהה קבוע למכשיר */
    val deviceId: String get() = str("device_id").ifBlank {
        java.util.UUID.randomUUID().toString().take(12).also { sp.edit().putString("device_id", it).apply() }
    }

    /** ייצוא לסנכרון: בלי מפתחות מקומיים */
    fun exportCloud(): String {
        val o = JSONObject()
        val m = JSONObject()
        pendingMt.forEach { (k, v) -> m.put(k, v) }
        mt.all.forEach { (k, v) -> if (!isLocalKey(k) && v is Long && !m.has(k)) m.put(k, v) }
        o.put("_mt", m)
        sp.all.forEach { (k, v) ->
            if (isLocalKey(k)) return@forEach
            when (v) {
                is Set<*> -> o.put(k, JSONObject().put("\$set", JSONArray(v.map { it.toString() })))
                null -> {}
                else -> o.put(k, v)
            }
        }
        return o.put("_tbm_backup", 1).toString()
    }

    private fun putAny(e: SharedPreferences.Editor, k: String, v: Any?) {
        when (v) {
            is JSONObject -> v.optJSONArray("\$set")?.let { a -> e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Double -> e.putFloat(k, v.toFloat())
            is String -> e.putString(k, v)
            else -> {}
        }
    }

    /**
     * ייבוא מהענן. replace=true (חשבון אחר / ראשון) מחליף הכל.
     * אחרת – מיזוג לפי מפתח: לכל הגדרה נלקח השינוי החדש ביותר (מכל מכשיר).
     * מחזיר true אם במכשיר יש שינויים חדשים יותר שצריך להעלות.
     */
    fun importCloud(text: String, replace: Boolean = false): Boolean {
        flushMt()
        val o = JSONObject(text)
        val rmt = o.optJSONObject("_mt")
        val e = sp.edit(); val me = mt.edit()
        var localNewer = false
        if (replace || rmt == null) {
            sp.all.keys.filter { !isLocalKey(it) }.forEach { e.remove(it) }
            mt.all.keys.filter { !isLocalKey(it) }.forEach { me.remove(it) }
            o.keys().forEach { k -> if (k != "_tbm_backup" && k != "_mt" && !isLocalKey(k)) putAny(e, k, o.get(k)) }
            rmt?.keys()?.forEach { k -> me.putLong(k, rmt.optLong(k)) }
        } else {
            val keys = HashSet<String>()
            o.keys().forEach { keys += it }; rmt.keys().forEach { keys += it }; keys += sp.all.keys; keys += mt.all.keys
            for (k in keys) {
                if (k == "_tbm_backup" || k == "_mt" || isLocalKey(k)) continue
                val r = rmt.optLong(k, 0L); val l = mt.getLong(k, 0L)
                when {
                    r > l -> { if (o.has(k)) putAny(e, k, o.get(k)) else e.remove(k); me.putLong(k, r) }
                    l > r -> localNewer = true
                    // מפתח ישן שלא נרשם לו זמן: אם רק בענן – לוקחים
                    l == 0L && r == 0L && o.has(k) && !sp.contains(k) -> putAny(e, k, o.get(k))
                }
            }
        }
        e.commit(); me.commit()
        version.intValue = version.intValue + 1
        return localNewer
    }

    /** מחיקת כל הנתונים המסונכרנים במכשיר (יציאה מחשבון) */
    fun wipeSynced() {
        val e = sp.edit(); sp.all.keys.filter { !isLocalKey(it) }.forEach { e.remove(it) }; e.commit()
        val me = mt.edit(); mt.all.keys.filter { !isLocalKey(it) }.forEach { me.remove(it) }; me.commit()
        version.intValue = version.intValue + 1
    }

    fun str(k: String, d: String = ""): String = sp.getString(k, d) ?: d
    fun setStr(k: String, v: String) { sp.edit().putString(k, v).apply(); touch(k); bump() }
    fun setStrQuiet(k: String, v: String) { sp.edit().putString(k, v).apply(); touch(k) }
    fun bool(k: String, d: Boolean): Boolean = sp.getBoolean(k, d)
    fun setBool(k: String, v: Boolean) { sp.edit().putBoolean(k, v).apply(); touch(k); bump() }
    fun float(k: String, d: Float): Float = sp.getFloat(k, d)
    fun setFloat(k: String, v: Float) { sp.edit().putFloat(k, v).apply(); touch(k); bump() }
    fun int(k: String, d: Int): Int = sp.getInt(k, d)
    fun setInt(k: String, v: Int) { sp.edit().putInt(k, v).apply(); touch(k); bump() }

    private fun set(k: String): Set<String> = sp.getStringSet(k, emptySet())?.toSet() ?: emptySet()
    private fun putSet(k: String, s: Set<String>) { sp.edit().putStringSet(k, HashSet(s)).apply(); touch(k); bump() }
    private fun toggle(k: String, item: String, on: Boolean) {
        val s = set(k).toMutableSet(); if (on) s.add(item) else s.remove(item); putSet(k, s)
    }

    /** תוכן לפרופיל ילדים מסוים: קבוצות שאושרו / נחסמו ידנית ("KIND|group") */
    fun kidsInc(pid: String): Set<String> = set("kinc_$pid")
    fun kidsExc(pid: String): Set<String> = set("kexc_$pid")
    /** ערוצים / סרטים / סדרות בודדים שנחסמו לפרופיל ילדים */
    fun kidsItemExc(pid: String): Set<String> = set("kxi_$pid")
    fun setKidsItemBlocked(pid: String, key: String, on: Boolean) = toggle("kxi_$pid", key, on)
    fun setKidsGroupState(pid: String, item: String, state: Int) {   // 1 = מאושר, -1 = חסום, 0 = אוטומטי
        toggle("kinc_$pid", item, state == 1)
        toggle("kexc_$pid", item, state == -1)
    }

    private fun obj(k: String): JSONObject = runCatching { JSONObject(str(k, "{}")) }.getOrDefault(JSONObject())

    // ================= רשימות השמעה =================
    fun playlists(): List<Playlist> = runCatching {
        val a = JSONArray(str("playlists", "[]"))
        (0 until a.length()).map { Playlist.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun savePlaylists(l: List<Playlist>) =
        setStr("playlists", JSONArray().apply { l.forEach { put(it.toJson()) } }.toString())

    var activeId: String
        get() = str("active")
        set(v) = setStr("active", v)

    fun activePlaylist(): Playlist? {
        val l = playlists()
        return l.firstOrNull { it.id == activeId } ?: l.firstOrNull()
    }

    fun epgUrl(p: Playlist) = str("epg_url_${p.id}")
    fun setEpgUrl(p: Playlist, v: String) = setStr("epg_url_${p.id}", v)

    /** כל כמה שעות לרענן רשימה ו-EPG */
    var refreshHours: Int
        get() = int("refresh_hours", 12)
        set(v) = setInt("refresh_hours", v)

    // ================= פרופילים =================
    fun profiles(): List<Profile> {
        val l = runCatching {
            val a = JSONArray(str("profiles", "[]"))
            (0 until a.length()).map { Profile.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        return l.ifEmpty { listOf(Profile("main", L("ראשי"), false)) }
    }

    fun saveProfiles(l: List<Profile>) =
        setStr("profiles", JSONArray().apply { l.forEach { put(it.toJson()) } }.toString())

    var profileId: String
        get() = str("profile", "main")
        set(v) = setStr("profile", v)

    fun profile(): Profile = profiles().let { l -> l.firstOrNull { it.id == profileId } ?: l.first() }
    val isKids get() = profile().kids
    private fun pk(k: String) = "${k}_${profileId}"

    // ================= בקרת הורים =================
    var pin: String
        get() = str("pin")
        set(v) = setStr("pin", v)

    /** תוכן למבוגרים בפרופיל מבוגר: hide | lock | show */
    var adultMode: String
        get() = str("adult_mode", "lock")
        set(v) = setStr("adult_mode", v)

    fun lockedGroups(): Set<String> = set("locked_groups")
    fun setGroupLocked(kind: Kind, g: String, on: Boolean) = toggle("locked_groups", "${kind.name}|$g", on)
    fun lockedItems(): Set<String> = set("locked_items")
    fun setItemLocked(key: String, on: Boolean) = toggle("locked_items", key, on)

    /** אישורים לפרופיל ילדים – קבוצות שלמות או פריטים בודדים */
    fun kidsGroups(): Set<String> = set("kids_groups")
    fun setKidsGroup(kind: Kind, g: String, on: Boolean) = toggle("kids_groups", "${kind.name}|$g", on)
    fun kidsItems(): Set<String> = set("kids_items")
    fun setKidsItem(key: String, on: Boolean) = toggle("kids_items", key, on)

    // ================= ניהול ערוצים =================
    fun hidden(): Set<String> = set("hidden")
    fun setHidden(kind: Kind, groups: Collection<String>, hide: Boolean) {
        val s = hidden().toMutableSet()
        groups.forEach { g -> val k = "${kind.name}|$g"; if (hide) s.add(k) else s.remove(k) }
        putSet("hidden", s)
    }

    fun hiddenItems(): Set<String> = set("hidden_items")
    fun setItemHidden(key: String, on: Boolean) = toggle("hidden_items", key, on)

    fun allowedCountries(kind: Kind): Set<String> = set("countries_${kind.name}")
    fun setAllowedCountries(kind: Kind, s: Set<String>) = putSet("countries_${kind.name}", s)

    fun renames(): Map<String, String> {
        val o = obj("renames")
        return o.keys().asSequence().associateWith { o.optString(it) }
    }

    fun setRename(key: String, name: String?) {
        val o = obj("renames")
        if (name.isNullOrBlank()) o.remove(key) else o.put(key, name)
        setStr("renames", o.toString())
    }

    fun order(): Map<String, Int> {
        val o = obj("order")
        return o.keys().asSequence().associateWith { o.optInt(it) }
    }

    fun setOrder(m: Map<String, Int>) {
        val o = obj("order")
        m.forEach { (k, v) -> o.put(k, v) }
        setStr("order", o.toString())
    }

    // ================= מועדפים (לכל פרופיל) =================
    // בפרופיל ילדים עם כמה ילדים – מועדפים נפרדים לכל ילד
    private fun favKey(): String {
        val c = if (isKids) kidChild.takeIf { it.isNotBlank() && it in kidsNames() } else null
        return if (c != null) "fav_${profileId}_$c" else pk("fav")
    }
    fun favs(): Set<String> = set(favKey())
    fun isFav(key: String) = key in favs()
    fun toggleFav(key: String) {
        val k = favKey()
        val s = set(k).toMutableSet()
        if (!s.add(key)) s.remove(key)
        putSet(k, s)
    }

    fun stringSet(k: String): Set<String> = set(k)
    fun toggleIn(k: String, v: String, on: Boolean) = toggle(k, v, on)

    // ================= ילדים: שמות, ילד נבחר, שעות מותרות, דוח צפייה =================
    /** שמות הילדים בפרופיל (עד 5). אם לא הוגדרו – מפרקים את שם הפרופיל ("פלג ושחף") */
    fun kidsNames(pid: String = profileId): List<String> {
        val saved = runCatching { JSONArray(str("kidnames_$pid", "[]")).let { a -> (0 until a.length()).map { a.optString(it).trim() } } }
            .getOrDefault(emptyList()).filter { it.isNotEmpty() }
        if (saved.isNotEmpty()) return saved.take(5)
        val p = profiles().firstOrNull { it.id == pid } ?: return emptyList()
        if (!p.kids) return emptyList()
        return p.name.split(Regex("""\s+ו(?=\S)|,|&|\s+and\s+""")).map { it.trim() }.filter { it.isNotEmpty() && it != "ילדים" }.take(5)
    }
    fun setKidsNames(pid: String, names: List<String>) =
        setStr("kidnames_$pid", JSONArray(names.map { it.trim() }.filter { it.isNotEmpty() }.take(5)).toString())

    /** הילד שצופה עכשיו (במכשיר הזה) – למועדפים ולדוח */
    var kidChild: String
        get() = str("kidsel_$profileId")
        set(v) = setStr("kidsel_$profileId", v)

    fun kidsHoursOn(pid: String) = bool("kh_on_$pid", false)
    fun setKidsHoursOn(pid: String, v: Boolean) = setBool("kh_on_$pid", v)
    fun kidsFrom(pid: String) = str("kh_from_$pid", "07:00")
    fun kidsTo(pid: String) = str("kh_to_$pid", "20:00")
    fun setKidsFrom(pid: String, v: String) = setStr("kh_from_$pid", v)
    fun setKidsTo(pid: String, v: String) = setStr("kh_to_$pid", v)
    private fun mins(hm: String) = hm.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }

    /** האם מותר לצפות עכשיו בפרופיל הנוכחי (מבוגר – תמיד) */
    fun kidsAllowedNow(): Boolean {
        if (!isKids) return true
        val pid = profileId
        if (!kidsHoursOn(pid)) return true
        if (System.currentTimeMillis() < (str("kh_ovr").toLongOrNull() ?: 0L)) return true
        val c = java.util.Calendar.getInstance()
        val now = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
        val f = mins(kidsFrom(pid)); val t = mins(kidsTo(pid))
        return if (f <= t) now in f until t else now >= f || now < t
    }
    /** הורה אישר המשך צפייה – לשעה */
    fun kidsOverride(hours: Int = 1) = setStrQuiet("kh_ovr", (System.currentTimeMillis() + hours * 3_600_000L).toString())

    fun logWatch(title: String, kind: Kind, start: Long, durSec: Long) {
        if (!isKids || durSec < 30) return
        val k = "wlog_$profileId"
        val a = runCatching { JSONArray(str(k, "[]")) }.getOrDefault(JSONArray())
        a.put(JSONObject().put("t", title).put("k", kind.name).put("s", start).put("d", durSec).put("c", kidChild))
        val keep = JSONArray(); val from = maxOf(0, a.length() - 400)
        for (i in from until a.length()) keep.put(a.get(i))
        setStr(k, keep.toString())
    }
    fun watchLog(pid: String): List<WatchEntry> = runCatching {
        val a = JSONArray(str("wlog_$pid", "[]"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
            WatchEntry(it.optString("t"), it.optString("k"), it.optLong("s"), it.optLong("d"), it.optString("c"))
        }.sortedByDescending { it.start }
    }.getOrDefault(emptyList())
    fun clearWatchLog(pid: String) = setStr("wlog_$pid", "[]")

    // ================= ערוצים אחרונים (לכל פרופיל) =================
    fun recentChannels(): List<String> = runCatching {
        val a = JSONArray(str(pk("recent_ch"), "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    fun addRecentChannel(key: String) {
        val l = (listOf(key) + recentChannels().filter { it != key }).take(30)
        setStr(pk("recent_ch"), JSONArray(l).toString())
    }

    // ================= נגן =================
    var externalPlayer: Boolean
        get() = bool("external_player", false)
        set(v) = setBool("external_player", v)

    var autoNext: Boolean
        get() = bool("auto_next", true)
        set(v) = setBool("auto_next", v)

    var userAgent: String
        get() = str("ua", "VLC/3.0.20 LibVLC/3.0.20").ifBlank { "VLC/3.0.20 LibVLC/3.0.20" }
        set(v) = setStr("ua", v)

    var tmdbKey: String
        get() = str("tmdb_key").trim()
        set(v) = setStr("tmdb_key", v)

    // ================= כתוביות =================
    var autoSubs: Boolean
        get() = bool("auto_subs", true)
        set(v) = setBool("auto_subs", v)

    fun subLangs(): List<String> = str("sub_langs", "he,en")
        .split(',', ' ', ';').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        .ifEmpty { listOf("he", "en") }

    var subSize: Float
        get() = float("sub_size", 1.0f)
        set(v) = setFloat("sub_size", v)

    /** רקע שחור מלא מאחורי הכתובית – מכסה כתוביות צרובות בתמונה */
    var subBox: Boolean
        get() = bool("sub_box", false)
        set(v) = setBool("sub_box", v)

    /** גובה הכתובית מתחתית המסך (0–0.5) */
    var subPos: Float
        get() = float("sub_pos", 0.08f)
        set(v) = setFloat("sub_pos", v.coerceIn(0f, 0.5f))

    /** כשנטענת כתובית חיצונית – לכבות כתוביות מובנות בקובץ */
    var replaceEmbedded: Boolean
        get() = bool("replace_embedded", true)
        set(v) = setBool("replace_embedded", v)

    // ================= המשך צפייה (לכל פרופיל) =================
    private fun resumeObj(): JSONObject = obj(pk("resume"))

    // מטמון: פענוח ה-JSON פעם אחת בלבד עד שהוא משתנה (נקרא לכל פריט ברשימות של אלפי סרטים)
    @Volatile private var resumeCache: Triple<String, String, Map<String, Pair<Long, Long>>>? = null

    fun resumeMap(): Map<String, Pair<Long, Long>> {
        val k = pk("resume")
        val raw = str(k, "{}")
        resumeCache?.let { if (it.first == k && it.second == raw) return it.third }
        val o = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        val m = HashMap<String, Pair<Long, Long>>(o.length() * 2)
        o.keys().forEach { key -> o.optJSONObject(key)?.let { m[key] = it.optLong("pos") to it.optLong("dur") } }
        resumeCache = Triple(k, raw, m)
        return m
    }

    fun resume(key: String): Pair<Long, Long>? = resumeMap()[key]

    fun saveResume(item: PlayItem, pos: Long, dur: Long) {
        Plex.report(item.key, pos, dur)
        val all = resumeObj()
        all.put(item.key, JSONObject().put("item", item.toJson()).put("pos", pos).put("dur", dur)
            .put("ts", System.currentTimeMillis()))
        if (all.length() > 300) {
            val keys = all.keys().asSequence().toList()
                .sortedBy { all.optJSONObject(it)?.optLong("ts") ?: 0L }
            keys.take(all.length() - 300).forEach { all.remove(it) }
        }
        setStr(pk("resume"), all.toString())
    }

    fun removeResume(key: String) {
        val all = resumeObj(); all.remove(key)
        setStr(pk("resume"), all.toString())
    }

    fun recent(): List<Recent> {
        val all = resumeObj()
        return all.keys().asSequence().mapNotNull { k ->
            val o = all.optJSONObject(k) ?: return@mapNotNull null
            val item = o.optJSONObject("item") ?: return@mapNotNull null
            Recent(PlayItem.fromJson(item), o.optLong("pos"), o.optLong("dur"), o.optLong("ts"))
        }.filter { it.dur <= 0 || it.pos < it.dur * 0.95 }
            .sortedByDescending { it.ts }.toList()
    }
    // ================= ניהול קבוצות: נעיצה וסדר =================
    fun pinnedGroups(kind: Kind): List<String> = runCatching {
        val a = JSONArray(str("pinned_${kind.name}", "[]")); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    fun setPinnedGroups(kind: Kind, l: List<String>) = setStr("pinned_${kind.name}", JSONArray(l).toString())

    fun togglePinned(kind: Kind, g: String) {
        val l = pinnedGroups(kind).toMutableList()
        if (!l.remove(g)) l.add(g)
        setPinnedGroups(kind, l)
    }

    fun groupOrder(kind: Kind): List<String> = runCatching {
        val a = JSONArray(str("gorder_${kind.name}", "[]")); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    fun setGroupOrder(kind: Kind, l: List<String>) = setStr("gorder_${kind.name}", JSONArray(l).toString())

    /** לוח שידורים נסרק רק לערוצים בקטגוריות גלויות */
    var epgVisibleOnly: Boolean
        get() = bool("epg_visible_only", true)
        set(v) = setBool("epg_visible_only", v)

    /** טעינה חסכונית – קטגוריות מוסתרות/מסוננות לא נטענות לזיכרון כלל */
    var leanLoad: Boolean
        get() = bool("lean_load", false)
        set(v) = setBool("lean_load", v)

    // ================= אפשרויות נגן =================
    var inAppPip: Boolean
        get() = bool("inapp_pip", true)
        set(v) = setBool("inapp_pip", v)

    var systemPip: Boolean
        get() = bool("system_pip", true)
        set(v) = setBool("system_pip", v)

    var osdTimeout: Int
        get() = int("osd_timeout", 5)
        set(v) = setInt("osd_timeout", v)

    var audioLang: String
        get() = str("audio_lang")
        set(v) = setStr("audio_lang", v)

    // ================= הקלטות =================
    /** הקלטת הערוץ הנצפה: לצפות מתוך ההקלטה (חיבור אחד לספק + השהיית שידור חי) */
    var recWatchFromFile: Boolean
        get() = bool("rec_watch_file", true)
        set(v) = setBool("rec_watch_file", v)

    /** אורך הקלטה ברירת מחדל כשאין לוח שידורים (דקות) */
    var recDefaultMin: Int
        get() = int("rec_default_min", 120)
        set(v) = setInt("rec_default_min", v)

    // ================= קריינות AI =================
    var voiceDefault: Boolean
        get() = bool("voice_default", false)
        set(v) = setBool("voice_default", v)

    var voiceRate: Float
        get() = float("voice_rate", 1.1f)
        set(v) = setFloat("voice_rate", v)

    /** עוצמת הקול המקורי בזמן הקריינות (0–1) */
    var voiceDuck: Float
        get() = float("voice_duck", 0.25f)
        set(v) = setFloat("voice_duck", v)

    // ================= אפשרויות הורים =================
    var settingsLock: Boolean
        get() = bool("settings_lock", false)
        set(v) = setBool("settings_lock", v)

    var clearHistoryOnExit: Boolean
        get() = bool("clear_history_exit", false)
        set(v) = setBool("clear_history_exit", v)

    var biometric: Boolean
        get() = bool("biometric", false)
        set(v) = setBool("biometric", v)

    fun clearHistory() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("resume_") || it.startsWith("recent_ch_") }.forEach { e.remove(it); touch(it) }
        e.apply(); bump()
    }

    // ================= גיבוי ושחזור =================
    fun exportJson(): String {
        val o = JSONObject()
        sp.all.forEach { (k, v) ->
            when (v) {
                is Set<*> -> o.put(k, JSONObject().put("\$set", JSONArray(v.map { it.toString() })))
                null -> {}
                else -> o.put(k, v)
            }
        }
        return o.put("_tbm_backup", 1).toString(2)
    }

    fun importJson(text: String) {
        val o = JSONObject(text)
        if (!o.has("_tbm_backup")) throw IllegalArgumentException(L("זה לא קובץ גיבוי של TBM Player"))
        val e = sp.edit().clear()
        o.keys().forEach { k ->
            if (k == "_tbm_backup") return@forEach
            when (val v = o.get(k)) {
                is JSONObject -> v.optJSONArray("\$set")?.let { a ->
                    e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                }
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Double -> e.putFloat(k, v.toFloat())
                is String -> e.putString(k, v)
                else -> {}
            }
            touch(k)
        }
        e.apply(); bump()
    }

    /** איפוס העדפות משתמש – משאיר רשימות ערוצים ומפתחות */
    fun resetPreferences() {
        val keep = setOf("playlists", "active", "os_key", "os_user", "os_pass", "subdl_key",
            "ktuvit_email", "ktuvit_hash", "tmdb_key")
        val e = sp.edit()
        sp.all.keys.filter { k -> k !in keep && !k.startsWith("epg_url_") && !k.startsWith("tz_") }.forEach { e.remove(it); touch(it) }
        e.apply(); bump()
    }
}
