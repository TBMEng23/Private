package com.tbm.iptv.data

import com.tbm.iptv.L

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** לקוח Xtream Codes API (player_api.php) */
class Xtream(private val p: Playlist) {
    val base: String = normalize(p.url)
    private val u = Http.enc(p.user)
    private val pw = Http.enc(p.pass)

    private fun api(action: String, extra: String = "") =
        "$base/player_api.php?username=$u&password=$pw" +
            (if (action.isNotEmpty()) "&action=$action" else "") + extra

    private fun arr(action: String, force: Boolean): JSONArray {
        val t = Repo.fetchCached(api(action), "x_${p.id}_$action.json", force).trim()
        return if (t.startsWith("[")) JSONArray(t) else JSONArray()
    }

    private fun cats(action: String, force: Boolean): Map<String, String> {
        val a = runCatching { arr(action, force) }.getOrDefault(JSONArray())
        val m = HashMap<String, String>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            m[o.optString("category_id")] = o.optString("category_name")
        }
        return m
    }

    private fun JSONObject.idInt(k: String): Int? = optString(k).toIntOrNull()

    /** פרטי חשבון: אזור זמן של השרת (ל-catch-up) ותאריך תפוגה */
    private fun accountInfo() {
        runCatching {
            val o = JSONObject(Http.get(api("")))
            o.optJSONObject("server_info")?.optString("timezone")?.ifBlank { null }?.let {
                Prefs.setStrQuiet("tz_${p.id}", it)
            }
            o.optJSONObject("user_info")?.let { ui ->
                if (ui.optString("auth") == "0") throw Exception("auth")
                Prefs.setStrQuiet("exp_${p.id}", ui.optString("exp_date"))
                Prefs.setStrQuiet("status_${p.id}", ui.optString("status"))
            }
        }
    }

    fun load(force: Boolean): Content {
        if (force || Prefs.str("tz_${p.id}").isBlank()) accountInfo()
        val liveCats = cats("get_live_categories", force)
        val vodCats = cats("get_vod_categories", force)
        val serCats = cats("get_series_categories", force)

        val live = ArrayList<Channel>()
        arr("get_live_streams", force).let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.idInt("stream_id") ?: continue
                val url = "$base/live/${p.user}/${p.pass}/$id.ts"
                live += Channel(
                    key = "x:${p.id}:l:$id",
                    name = o.optString("name"),
                    logo = o.optString("stream_icon").ifBlank { null },
                    group = liveCats[o.optString("category_id")] ?: "ללא קבוצה",
                    url = url, kind = Kind.LIVE, streamId = id,
                    epgId = o.optString("epg_channel_id").ifBlank { null },
                    archiveDays = if (o.optString("tv_archive") == "1")
                        (o.optString("tv_archive_duration").toIntOrNull() ?: 1).coerceAtLeast(1) else 0,
                )
            }
        }

        val movies = ArrayList<Channel>()
        arr("get_vod_streams", force).let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.idInt("stream_id") ?: continue
                val ext = o.optString("container_extension").ifBlank { "mp4" }
                movies += Channel(
                    key = "x:${p.id}:m:$id",
                    name = o.optString("name"),
                    logo = o.optString("stream_icon").ifBlank { null },
                    group = vodCats[o.optString("category_id")] ?: "ללא קבוצה",
                    url = "$base/movie/${p.user}/${p.pass}/$id.$ext",
                    kind = Kind.MOVIE, streamId = id,
                    added = (o.optString("added").toLongOrNull() ?: 0L) * 1000,
                )
            }
        }

        val series = ArrayList<SeriesItem>()
        arr("get_series", force).let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.idInt("series_id") ?: continue
                series += SeriesItem(
                    key = "x:${p.id}:s:$id",
                    name = o.optString("name"),
                    logo = o.optString("cover").ifBlank { null },
                    group = serCats[o.optString("category_id")] ?: "ללא קבוצה",
                    seriesId = id,
                    plot = o.optString("plot").ifBlank { null },
                    added = (o.optString("last_modified").toLongOrNull() ?: 0L) * 1000,
                )
            }
        }

        if (live.isEmpty() && movies.isEmpty() && series.isEmpty())
            throw Exception(L("לא התקבל תוכן מהשרת – בדוק כתובת, שם משתמש וסיסמה"))
        return Content(live, movies, series)
    }

    fun seriesEpisodes(seriesId: Int): List<Episode> {
        val o = JSONObject(Http.get(api("get_series_info", "&series_id=$seriesId")))
        val res = ArrayList<Episode>()

        fun addArr(a: JSONArray, seasonKey: Int?) {
            for (i in 0 until a.length()) {
                val e = a.optJSONObject(i) ?: continue
                val epId = e.optString("id")
                if (epId.isBlank()) continue
                val ext = e.optString("container_extension").ifBlank { "mp4" }
                val info = e.optJSONObject("info")
                val url = "$base/series/${p.user}/${p.pass}/$epId.$ext"
                res += Episode(
                    key = url,
                    title = e.optString("title"),
                    season = e.optString("season").toIntOrNull() ?: seasonKey ?: 1,
                    episode = e.optString("episode_num").toIntOrNull() ?: (i + 1),
                    url = url,
                    plot = info?.optString("plot")?.ifBlank { null },
                    image = info?.optString("movie_image")?.ifBlank { null },
                )
            }
        }

        when (val eps = o.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { k -> eps.optJSONArray(k)?.let { addArr(it, k.toIntOrNull()) } }
            is JSONArray -> for (i in 0 until eps.length()) eps.optJSONArray(i)?.let { addArr(it, null) }
        }
        return res.sortedWith(compareBy({ it.season }, { it.episode }))
    }

    fun shortEpg(streamId: Int): List<EpgEntry> {
        val o = JSONObject(Http.get(api("get_short_epg", "&stream_id=$streamId&limit=5")))
        val a = o.optJSONArray("epg_listings") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val e = a.optJSONObject(i) ?: return@mapNotNull null
            val title = runCatching {
                String(Base64.decode(e.optString("title"), Base64.DEFAULT), Charsets.UTF_8)
            }.getOrDefault(e.optString("title"))
            EpgEntry(title, e.optLong("start_timestamp") * 1000, e.optLong("stop_timestamp") * 1000)
        }
    }

    companion object {
        fun normalize(url: String): String {
            var s = url.trim()
            if (!s.startsWith("http", true)) s = "http://$s"
            listOf("/player_api.php", "/get.php", "/panel_api.php").forEach { k ->
                val i = s.indexOf(k)
                if (i > 0) s = s.substring(0, i)
            }
            return s.trimEnd('/')
        }
    }
}
