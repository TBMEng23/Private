package com.tbm.iptv.data

import org.json.JSONObject

enum class Kind(private val he: String) {
    LIVE("טלוויזיה"), MOVIE("סרטים"), SERIES("סדרות");

    val label: String get() = com.tbm.iptv.L(he)
}

/** פריט שמוצג ברשימות – ערוץ, סרט או סדרה */
interface Entry {
    val key: String
    val name: String
    val group: String
    val logo: String?
}

data class Channel(
    override val key: String,
    override val name: String,
    override val logo: String?,
    override val group: String,
    val url: String,
    val kind: Kind,
    val streamId: Int? = null,
    val epgId: String? = null,
    /** ימי ארכיון (catch-up). 0 = אין */
    val archiveDays: Int = 0,
    /** M3U: catchup="default|append|shift|flussonic|xc" */
    val catchupType: String? = null,
    val catchupSource: String? = null,
    /** זמן הוספה לשרת (Xtream, מילישניות) – ל"נוספו לאחרונה" */
    val added: Long = 0,
    /** דירוג 0–10 (סרטים, 0 = אין) */
    val rating: Float = 0f,
) : Entry {
    val hasCatchup get() = archiveDays > 0 || catchupType != null
}

data class SeriesItem(
    override val key: String,
    override val name: String,
    override val logo: String?,
    override val group: String,
    val seriesId: Int? = null,
    val plot: String? = null,
    val episodesM3u: List<Episode>? = null,
    val added: Long = 0,
    val rating: Float = 0f,
) : Entry

data class Episode(
    val key: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val url: String,
    val plot: String? = null,
    val image: String? = null,
    /** אורך בשניות (0 = לא ידוע) */
    val durationSec: Int = 0,
)

/** פרטים מלאים לדף סדרה/סרט (כמו נטפליקס) */
data class MediaInfo(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val rating: Double = 0.0,
    val backdrops: List<String> = emptyList(),
    val trailer: String? = null,
    val durationSec: Int = 0,
    val tmdbId: String? = null,
)

data class Content(
    val live: List<Channel>,
    val movies: List<Channel>,
    val series: List<SeriesItem>,
    /** כתובת XMLTV מתוך כותרת ה-M3U (url-tvg) */
    val epgUrl: String? = null,
)

data class EpgEntry(val title: String, val start: Long, val end: Long, val desc: String = "")

data class Playlist(
    val id: String,
    val name: String,
    val type: String, // "m3u" | "xtream"
    val url: String,
    val user: String = "",
    val pass: String = "",
) {
    val isXtream get() = type == "xtream"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("type", type)
        .put("url", url).put("user", user).put("pass", pass)

    companion object {
        fun fromJson(o: JSONObject) = Playlist(
            o.optString("id"), o.optString("name"), o.optString("type", "m3u"),
            o.optString("url"), o.optString("user"), o.optString("pass"),
        )
    }
}

/**
 * פרופיל צופה. ילדים: age = גיל מקסימלי לתוכן, auto = סינון אוטומטי לפי קטגוריות ילדים (בנוסף לאישורים ידניים)
 */
data class Profile(val id: String, val name: String, val kids: Boolean, val age: Int = 7, val auto: Boolean = true, val color: Int = 0) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("kids", kids)
        .put("age", age).put("auto", auto).put("color", color)

    companion object {
        fun fromJson(o: JSONObject) = Profile(o.optString("id"), o.optString("name"), o.optBoolean("kids"),
            o.optInt("age", 7), o.optBoolean("auto", true), o.optInt("color", 0))
    }
}

/** מה שנשלח לנגן */
data class PlayItem(
    val key: String,
    val title: String,
    val url: String,
    val kind: Kind,
    val logo: String? = null,
    val seriesName: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val streamId: Int? = null,
    val epgId: String? = null,
    val channelName: String? = null,
    /** צפייה בתוכנית שכבר שודרה – בלי שמירת המשך צפייה ובלי כתוביות אוטומטיות */
    val isCatchup: Boolean = false,
    /** הקלטה מקומית */
    val isLocal: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key); put("title", title); put("url", url); put("kind", kind.name)
        logo?.let { put("logo", it) }
        seriesName?.let { put("seriesName", it) }
        season?.let { put("season", it) }
        episode?.let { put("episode", it) }
        streamId?.let { put("streamId", it) }
        epgId?.let { put("epgId", it) }
        channelName?.let { put("channelName", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = PlayItem(
            key = o.optString("key"),
            title = o.optString("title"),
            url = o.optString("url"),
            kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrDefault(Kind.MOVIE),
            logo = o.optString("logo").ifBlank { null },
            seriesName = o.optString("seriesName").ifBlank { null },
            season = if (o.has("season")) o.optInt("season") else null,
            episode = if (o.has("episode")) o.optInt("episode") else null,
            streamId = if (o.has("streamId")) o.optInt("streamId") else null,
            epgId = o.optString("epgId").ifBlank { null },
            channelName = o.optString("channelName").ifBlank { null },
        )
    }
}

fun Channel.toPlay() = PlayItem(
    key, name, url, kind, logo, streamId = streamId,
    epgId = epgId, channelName = name,
)

fun Episode.toPlay(s: SeriesItem) = PlayItem(
    key = key,
    title = "${s.name} – S%02dE%02d".format(season, episode) +
        (if (title.isNotBlank() && !title.contains(s.name, true)) " · $title" else ""),
    url = url,
    kind = Kind.SERIES,
    logo = image ?: s.logo,
    seriesName = s.name,
    season = season,
    episode = episode,
)
