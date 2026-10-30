package com.tbm.iptv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject

data class Recent(val item: PlayItem, val pos: Long, val dur: Long, val ts: Long)

object Prefs {
    private lateinit var sp: SharedPreferences

    /** מונה שינויים – קומפוזבלים קוראים אותו כדי להתעדכן אחרי שינוי הגדרות */
    val version = mutableIntStateOf(0)

    fun init(c: Context) {
        sp = c.getSharedPreferences("tbm", Context.MODE_PRIVATE)
    }

    private fun bump() { version.intValue = version.intValue + 1 }

    fun str(k: String, d: String = ""): String = sp.getString(k, d) ?: d
    fun setStr(k: String, v: String) { sp.edit().putString(k, v).apply(); bump() }
    fun bool(k: String, d: Boolean): Boolean = sp.getBoolean(k, d)
    fun setBool(k: String, v: Boolean) { sp.edit().putBoolean(k, v).apply(); bump() }
    fun float(k: String, d: Float): Float = sp.getFloat(k, d)
    fun setFloat(k: String, v: Float) { sp.edit().putFloat(k, v).apply(); bump() }

    private fun set(k: String): Set<String> = sp.getStringSet(k, emptySet())?.toSet() ?: emptySet()
    private fun putSet(k: String, s: Set<String>) { sp.edit().putStringSet(k, HashSet(s)).apply(); bump() }

    // ---------- רשימות השמעה ----------
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

    // ---------- מועדפים ----------
    fun favs(): Set<String> = set("fav")
    fun isFav(key: String) = key in favs()
    fun toggleFav(key: String) {
        val s = favs().toMutableSet()
        if (!s.add(key)) s.remove(key)
        putSet("fav", s)
    }

    // ---------- סינון קבוצות וארצות ----------
    fun hidden(): Set<String> = set("hidden")
    fun setHidden(kind: Kind, groups: Collection<String>, hide: Boolean) {
        val s = hidden().toMutableSet()
        groups.forEach { g -> val k = "${kind.name}|$g"; if (hide) s.add(k) else s.remove(k) }
        putSet("hidden", s)
    }

    fun allowedCountries(kind: Kind): Set<String> = set("countries_${kind.name}")
    fun setAllowedCountries(kind: Kind, s: Set<String>) = putSet("countries_${kind.name}", s)

    var hideAdult: Boolean
        get() = bool("hide_adult", true)
        set(v) = setBool("hide_adult", v)

    // ---------- נגן ----------
    var externalPlayer: Boolean
        get() = bool("external_player", false)
        set(v) = setBool("external_player", v)

    var autoNext: Boolean
        get() = bool("auto_next", true)
        set(v) = setBool("auto_next", v)

    var userAgent: String
        get() = str("ua", "VLC/3.0.20 LibVLC/3.0.20").ifBlank { "VLC/3.0.20 LibVLC/3.0.20" }
        set(v) = setStr("ua", v)

    // ---------- כתוביות ----------
    var autoSubs: Boolean
        get() = bool("auto_subs", true)
        set(v) = setBool("auto_subs", v)

    fun subLangs(): List<String> = str("sub_langs", "he,en")
        .split(',', ' ', ';').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        .ifEmpty { listOf("he", "en") }

    var subSize: Float
        get() = float("sub_size", 1.0f)
        set(v) = setFloat("sub_size", v)

    // ---------- המשך צפייה ----------
    private fun resumeObj(): JSONObject = runCatching { JSONObject(str("resume", "{}")) }.getOrDefault(JSONObject())

    fun resume(key: String): Pair<Long, Long>? {
        val o = resumeObj().optJSONObject(key) ?: return null
        return o.optLong("pos") to o.optLong("dur")
    }

    fun saveResume(item: PlayItem, pos: Long, dur: Long) {
        val all = resumeObj()
        all.put(item.key, JSONObject().put("item", item.toJson()).put("pos", pos).put("dur", dur)
            .put("ts", System.currentTimeMillis()))
        // שמירה על עד 300 פריטים
        if (all.length() > 300) {
            val keys = all.keys().asSequence().toList()
                .sortedBy { all.optJSONObject(it)?.optLong("ts") ?: 0L }
            keys.take(all.length() - 300).forEach { all.remove(it) }
        }
        sp.edit().putString("resume", all.toString()).apply()
        bump()
    }

    fun removeResume(key: String) {
        val all = resumeObj(); all.remove(key)
        setStr("resume", all.toString())
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
}
