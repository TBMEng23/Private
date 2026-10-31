package com.tbm.iptv.data

import com.tbm.iptv.L

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader

/** לקוח Xtream Codes API (player_api.php) */
class Xtream(private val p: Playlist) {
    val base: String = normalize(p.url)
    private val u = Http.enc(p.user)
    private val pw = Http.enc(p.pass)

    private fun api(action: String, extra: String = "") =
        "$base/player_api.php?username=$u&password=$pw" +
            (if (action.isNotEmpty()) "&action=$action" else "") + extra

    /** מוריד את התשובה לקובץ (סטרימינג, בלי לטעון לזיכרון) – עם מטמון */
    private fun file(action: String, force: Boolean): File {
        val f = Repo.cacheFile("x_${p.id}_$action.json")
        val fresh = f.exists() && f.length() > 2 &&
            System.currentTimeMillis() - f.lastModified() < Prefs.refreshHours * 3_600_000L
        if (force || !fresh) {
            try { Http.download(api(action), f) } catch (e: Exception) { if (!f.exists()) throw e }
        }
        return f
    }

    /** קריאת מערך JSON אובייקט-אובייקט מהקובץ. שומר רק את השדות המבוקשים */
    private fun eachObject(f: File, fields: Set<String>, block: (Map<String, String>) -> Unit) {
        if (!f.exists() || f.length() < 2) return
        f.inputStream().buffered(65536).use { ins ->
            val r = JsonReader(InputStreamReader(ins, Charsets.UTF_8))
            r.isLenient = true
            try {
                if (r.peek() != JsonToken.BEGIN_ARRAY) return
            } catch (e: Exception) { return }
            r.beginArray()
            while (r.hasNext()) {
                if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                r.beginObject()
                val m = HashMap<String, String>(16)
                while (r.hasNext()) {
                    val name = r.nextName()
                    if (name in fields) {
                        when (r.peek()) {
                            JsonToken.STRING, JsonToken.NUMBER -> m[name] = r.nextString()
                            JsonToken.BOOLEAN -> m[name] = if (r.nextBoolean()) "1" else "0"
                            JsonToken.NULL -> r.nextNull()
                            else -> r.skipValue()
                        }
                    } else r.skipValue()
                }
                r.endObject()
                block(m)
            }
        }
    }

    private fun cats(action: String, force: Boolean): Map<String, String> {
        val m = HashMap<String, String>()
        runCatching {
            eachObject(file(action, force), setOf("category_id", "category_name")) { o ->
                m[o["category_id"] ?: return@eachObject] = o["category_name"] ?: ""
            }
        }
        return m
    }

    /** פרטי חשבון: אזור זמן של השרת (ל-catch-up) ותאריך תפוגה */
    private fun accountInfo() {
        runCatching {
            val o = JSONObject(Http.get(api("")))
            o.optJSONObject("server_info")?.optString("timezone")?.ifBlank { null }?.let {
                Prefs.setStrQuiet("tz_${p.id}", it)
            }
            o.optJSONObject("user_info")?.let { ui ->
                Prefs.setStrQuiet("exp_${p.id}", ui.optString("exp_date"))
                Prefs.setStrQuiet("status_${p.id}", ui.optString("status"))
            }
        }
    }

    /** פרטי חשבון לתצוגה (מסך "מידע") */
    fun accountDetails(): List<Pair<String, String>> {
        val o = JSONObject(Http.get(api("")))
        val ui = o.optJSONObject("user_info") ?: JSONObject()
        val si = o.optJSONObject("server_info") ?: JSONObject()
        val df = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US)
        fun ts(v: String) = v.toLongOrNull()?.takeIf { it > 0 }?.let { df.format(java.util.Date(it * 1000)) } ?: L("ללא הגבלה")
        return listOf(
            L("סטטוס") to ui.optString("status"),
            L("שם משתמש") to ui.optString("username"),
            L("תוקף") to ts(ui.optString("exp_date")),
            L("נוצר") to ts(ui.optString("created_at")),
            L("ניסיון") to (if (ui.optString("is_trial") == "1") L("כן") else L("לא")),
            L("חיבורים פעילים") to "${ui.optString("active_cons")} / ${ui.optString("max_connections")}",
            L("פורמטים") to (ui.optJSONArray("allowed_output_formats")?.let { a -> (0 until a.length()).joinToString(", ") { a.optString(it) } } ?: ""),
            L("שרת") to (si.optString("url") + (si.optString("port").takeIf { it.isNotBlank() }?.let { ":$it" } ?: "")),
            L("אזור זמן") to si.optString("timezone"),
            L("שעת שרת") to si.optString("time_now"),
        ).filter { it.second.isNotBlank() }
    }

    fun load(force: Boolean): Content {
        if (force || Prefs.str("tz_${p.id}").isBlank()) accountInfo()
        val liveCats = cats("get_live_categories", force)
        val vodCats = cats("get_vod_categories", force)
        val serCats = cats("get_series_categories", force)
        val noGroup = "ללא קבוצה"
        // אותה מחרוזת קבוצה לכל הפריטים – חוסך זיכרון
        val pool = HashMap<String, String>()
        fun grp(cats: Map<String, String>, id: String?) = (cats[id] ?: noGroup).let { pool.getOrPut(it) { it } }
        val pfx = "$base/live/${p.user}/${p.pass}/"

        val live = ArrayList<Channel>()
        eachObject(file("get_live_streams", force),
            setOf("stream_id", "name", "stream_icon", "category_id", "epg_channel_id", "tv_archive", "tv_archive_duration")) { o ->
            val id = o["stream_id"]?.toIntOrNull() ?: return@eachObject
            live += Channel(
                key = "x:${p.id}:l:$id",
                name = o["name"] ?: "",
                logo = o["stream_icon"]?.ifBlank { null },
                group = grp(liveCats, o["category_id"]),
                url = "$pfx$id.ts", kind = Kind.LIVE, streamId = id,
                epgId = o["epg_channel_id"]?.ifBlank { null },
                archiveDays = if (o["tv_archive"] == "1")
                    (o["tv_archive_duration"]?.toIntOrNull() ?: 1).coerceAtLeast(1) else 0,
            )
        }

        val movies = ArrayList<Channel>()
        eachObject(file("get_vod_streams", force),
            setOf("stream_id", "name", "stream_icon", "category_id", "container_extension", "added", "rating")) { o ->
            val id = o["stream_id"]?.toIntOrNull() ?: return@eachObject
            val ext = o["container_extension"]?.ifBlank { null } ?: "mp4"
            movies += Channel(
                key = "x:${p.id}:m:$id",
                name = o["name"] ?: "",
                logo = o["stream_icon"]?.ifBlank { null },
                group = grp(vodCats, o["category_id"]),
                url = "$base/movie/${p.user}/${p.pass}/$id.$ext",
                kind = Kind.MOVIE, streamId = id,
                added = (o["added"]?.toLongOrNull() ?: 0L) * 1000,
                rating = o["rating"]?.toFloatOrNull() ?: 0f,
            )
        }

        val series = ArrayList<SeriesItem>()
        eachObject(file("get_series", force),
            setOf("series_id", "name", "cover", "category_id", "plot", "last_modified", "rating")) { o ->
            val id = o["series_id"]?.toIntOrNull() ?: return@eachObject
            series += SeriesItem(
                key = "x:${p.id}:s:$id",
                name = o["name"] ?: "",
                logo = o["cover"]?.ifBlank { null },
                group = grp(serCats, o["category_id"]),
                seriesId = id,
                plot = o["plot"]?.ifBlank { null }?.take(600),
                added = (o["last_modified"]?.toLongOrNull() ?: 0L) * 1000,
                rating = o["rating"]?.toFloatOrNull() ?: 0f,
            )
        }

        if (live.isEmpty() && movies.isEmpty() && series.isEmpty())
            throw Exception(L("לא התקבל תוכן מהשרת – בדוק כתובת, שם משתמש וסיסמה"))
        return Content(live, movies, series)
    }

    fun seriesEpisodes(seriesId: Int): List<Episode> = seriesDetails(seriesId).second

    private fun parseInfo(i: JSONObject?): MediaInfo {
        if (i == null) return MediaInfo()
        val bd = ArrayList<String>()
        when (val b = i.opt("backdrop_path")) {
            is JSONArray -> for (k in 0 until b.length()) b.optString(k).ifBlank { null }?.let { bd += it }
            is String -> if (b.isNotBlank()) bd += b
        }
        val dur = i.optString("duration_secs").toIntOrNull()
            ?: i.optString("episode_run_time").toIntOrNull()?.times(60) ?: 0
        val date = i.optString("releaseDate").ifBlank { i.optString("releasedate") }.ifBlank { i.optString("release_date") }
        return MediaInfo(
            plot = i.optString("plot").ifBlank { i.optString("description") }.ifBlank { null },
            cast = i.optString("cast").ifBlank { i.optString("actors") }.ifBlank { null },
            director = i.optString("director").ifBlank { null },
            genre = i.optString("genre").ifBlank { null },
            year = date.take(4).ifBlank { null },
            rating = i.optString("rating").toDoubleOrNull() ?: 0.0,
            backdrops = bd,
            trailer = i.optString("youtube_trailer").ifBlank { null },
            durationSec = dur,
            tmdbId = (i.optString("tmdb_id").ifBlank { i.optString("tmdb") }).takeIf { it.isNotBlank() && it != "0" && it != "null" },
        )
    }

    /** פרטי סרט (get_vod_info) */
    fun vodInfo(streamId: Int): MediaInfo = runCatching {
        parseInfo(JSONObject(Http.get(api("get_vod_info", "&vod_id=$streamId"))).optJSONObject("info"))
    }.getOrDefault(MediaInfo())

    /** פרטי סדרה + פרקים (get_series_info) */
    fun seriesDetails(seriesId: Int): Pair<MediaInfo, List<Episode>> {
        val o = JSONObject(Http.get(api("get_series_info", "&series_id=$seriesId")))
        val info = parseInfo(o.optJSONObject("info"))
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
                    durationSec = info?.optString("duration_secs")?.toIntOrNull()
                        ?: info?.optString("duration")?.let { d ->
                            val p = d.split(":").mapNotNull { it.toIntOrNull() }
                            if (p.size == 3) p[0] * 3600 + p[1] * 60 + p[2] else null
                        } ?: 0,
                )
            }
        }

        when (val eps = o.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { k -> eps.optJSONArray(k)?.let { addArr(it, k.toIntOrNull()) } }
            is JSONArray -> for (i in 0 until eps.length()) eps.optJSONArray(i)?.let { addArr(it, null) }
        }
        return info to res.sortedWith(compareBy({ it.season }, { it.episode }))
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
