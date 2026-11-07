package com.tbm.iptv.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tbm.iptv.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** חיבור לשרת Plex: התחברות בקוד (plex.tv/link), ספריות סרטים וסדרות, ניגון ישיר וסנכרון "נצפה" */
object Plex {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http by lazy { Http.client.newBuilder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build() }

    var pinCode by mutableStateOf<String?>(null)
    var status by mutableStateOf("")
    var busy by mutableStateOf(false)

    private var token: String
        get() = Prefs.str("plex_token")
        set(v) = Prefs.setStr("plex_token", v)
    var serverName: String
        get() = Prefs.str("plex_server")
        private set(v) = Prefs.setStr("plex_server", v)
    private var uri: String
        get() = Prefs.str("plex_uri")
        set(v) = Prefs.setStr("plex_uri", v)
    private var srvToken: String
        get() = Prefs.str("plex_srv_token")
        set(v) = Prefs.setStr("plex_srv_token", v)

    val connected get() = token.isNotBlank() && uri.isNotBlank()
    var enabled: Boolean
        get() = Prefs.bool("plex_on", true)
        set(v) = Prefs.setBool("plex_on", v)

    private fun hdr(b: Request.Builder, tok: String? = token): Request.Builder = b
        .header("Accept", "application/json")
        .header("X-Plex-Product", "TBM Player")
        .header("X-Plex-Version", com.tbm.iptv.BuildConfigLite.VERSION)
        .header("X-Plex-Client-Identifier", "tbm-" + Prefs.deviceId)
        .header("X-Plex-Platform", "Android")
        .header("X-Plex-Device-Name", android.os.Build.MODEL ?: "Android")
        .apply { if (!tok.isNullOrBlank()) header("X-Plex-Token", tok) }

    private fun get(url: String, tok: String? = token): String =
        http.newCall(hdr(Request.Builder().url(url), tok).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string() ?: ""
        }

    // ---------------- התחברות ----------------
    /** מתחיל התחברות: מקבל קוד בן 4 תווים להזנה ב-plex.tv/link וממתין לאישור */
    fun startLink(onDone: () -> Unit) {
        if (busy) return
        busy = true; status = L("מקבל קוד…")
        scope.launch {
            try {
                val body = http.newCall(hdr(Request.Builder().url("https://plex.tv/api/v2/pins")
                    .post(ByteArray(0).toRequestBody()), null).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    r.body?.string() ?: ""
                }
                val o = JSONObject(body)
                val id = o.getLong("id")
                pinCode = o.getString("code")
                status = L("היכנס ל-plex.tv/link והזן את הקוד")
                // ממתינים עד 10 דקות
                repeat(200) {
                    delay(3000)
                    val p = JSONObject(get("https://plex.tv/api/v2/pins/$id", null))
                    val t = p.optString("authToken")
                    if (t.isNotBlank() && t != "null") {
                        token = t; pinCode = null
                        status = L("מחובר – מחפש שרת…")
                        findServer()
                        status = L("מחובר לשרת {0}", serverName)
                        busy = false
                        onDone()
                        return@launch
                    }
                }
                pinCode = null; status = L("פג תוקף הקוד – נסה שוב")
            } catch (e: Exception) {
                pinCode = null; status = L("התחברות ל-Plex נכשלה: {0}", e.message ?: e.javaClass.simpleName)
            } finally { busy = false }
        }
    }

    /** בוחר שרת (קודם שרת שבבעלותך) וכתובת שעונה (מקומית → מרוחקת → relay) */
    private fun findServer() {
        val arr = JSONArray(get("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1"))
        val servers = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .filter { it.optString("provides").contains("server") }
            .sortedByDescending { it.optBoolean("owned") }
        if (servers.isEmpty()) throw IOException(L("לא נמצא שרת Plex בחשבון"))
        for (s in servers) {
            val tok = s.optString("accessToken").ifBlank { token }
            val conns = s.optJSONArray("connections") ?: continue
            val list = (0 until conns.length()).mapNotNull { conns.optJSONObject(it) }
                .sortedWith(compareBy({ !it.optBoolean("local") }, { it.optBoolean("relay") }))
            for (c in list) {
                val u = c.optString("uri").trimEnd('/')
                if (u.isBlank()) continue
                val ok = runCatching { get("$u/identity", tok); true }.getOrDefault(false)
                if (ok) { uri = u; srvToken = tok; serverName = s.optString("name"); return }
            }
        }
        throw IOException(L("השרת לא זמין כרגע"))
    }

    fun disconnect() {
        token = ""; uri = ""; srvToken = ""; serverName = ""; status = ""
    }

    // ---------------- תוכן ----------------
    private fun sget(path: String): JSONObject {
        val sep = if ('?' in path) "&" else "?"
        return try {
            JSONObject(get("$uri$path${sep}X-Plex-Token=$srvToken", srvToken))
        } catch (e: IOException) {
            // הכתובת השתנתה (בית / חוץ) – מחפשים שוב
            findServer(); JSONObject(get("$uri$path${sep}X-Plex-Token=$srvToken", srvToken))
        }
    }

    private fun img(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let {
        "$uri/photo/:/transcode?width=400&height=600&minSize=1&url=${Http.enc(it)}&X-Plex-Token=$srvToken"
    }
    private fun art(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let { "$uri$it?X-Plex-Token=$srvToken" }
    private fun part(o: JSONObject): String? = o.optJSONArray("Media")?.optJSONObject(0)?.optJSONArray("Part")?.optJSONObject(0)
        ?.optString("key")?.takeIf { it.isNotBlank() }?.let { "$uri$it?X-Plex-Token=$srvToken" }
    private fun meta(o: JSONObject): List<JSONObject> = o.optJSONObject("MediaContainer")?.optJSONArray("Metadata")
        ?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()

    /** כל הסרטים והסדרות מכל הספריות */
    fun load(): Pair<List<Channel>, List<SeriesItem>> {
        if (!connected || !enabled) return emptyList<Channel>() to emptyList()
        val movies = ArrayList<Channel>(); val series = ArrayList<SeriesItem>()
        val dirs = sget("/library/sections").optJSONObject("MediaContainer")?.optJSONArray("Directory") ?: return movies to series
        for (i in 0 until dirs.length()) {
            val d = dirs.optJSONObject(i) ?: continue
            val type = d.optString("type"); if (type != "movie" && type != "show") continue
            val group = "Plex · " + d.optString("title")
            meta(sget("/library/sections/${d.optString("key")}/all")).forEach { m ->
                val rk = m.optString("ratingKey"); if (rk.isBlank()) return@forEach
                val title = m.optString("title") + (m.optInt("year").takeIf { it > 0 }?.let { " ($it)" } ?: "")
                val rating = (m.optDouble("audienceRating").takeIf { !it.isNaN() } ?: m.optDouble("rating").takeIf { !it.isNaN() } ?: 0.0).toFloat()
                val added = m.optLong("addedAt") * 1000
                if (type == "movie") {
                    val url = part(m) ?: return@forEach
                    movies += Channel(key = "plex:$rk", name = title, logo = img(m.optString("thumb")), group = group,
                        url = url, kind = Kind.MOVIE, added = added, rating = rating)
                } else {
                    series += SeriesItem(key = "plex:s:$rk", name = title, logo = img(m.optString("thumb")), group = group,
                        plot = m.optString("summary").ifBlank { null }?.take(600), added = added, rating = rating)
                }
            }
        }
        return movies to series
    }

    private fun rk(key: String) = key.substringAfterLast(':')

    private fun info(m: JSONObject): MediaInfo {
        fun names(k: String, n: Int = 8) = m.optJSONArray(k)?.let { a -> (0 until minOf(a.length(), n)).mapNotNull { a.optJSONObject(it)?.optString("tag") } }
            ?.filter { it.isNotBlank() }?.joinToString(", ")?.ifBlank { null }
        val tmdb = m.optJSONArray("Guid")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("id") } }
            ?.firstOrNull { it.startsWith("tmdb://") }?.removePrefix("tmdb://")
        return MediaInfo(plot = m.optString("summary").ifBlank { null }, cast = names("Role"), director = names("Director", 3),
            genre = names("Genre", 4)?.replace(", ", " / "), year = m.optInt("year").takeIf { it > 0 }?.toString(),
            rating = m.optDouble("audienceRating").takeIf { !it.isNaN() } ?: 0.0,
            backdrops = listOfNotNull(art(m.optString("art"))), durationSec = (m.optLong("duration") / 1000).toInt(), tmdbId = tmdb)
    }

    fun movieInfo(c: Channel): MediaInfo = runCatching { meta(sget("/library/metadata/${rk(c.key)}")).firstOrNull()?.let { info(it) } }
        .getOrNull() ?: MediaInfo()

    fun seriesDetails(s: SeriesItem): Pair<MediaInfo, List<Episode>> {
        val id = rk(s.key)
        val inf = runCatching { meta(sget("/library/metadata/$id")).firstOrNull()?.let { info(it) } }.getOrNull() ?: MediaInfo(plot = s.plot)
        val eps = meta(sget("/library/metadata/$id/allLeaves")).mapNotNull { e ->
            val url = part(e) ?: return@mapNotNull null
            Episode(key = "plex:${e.optString("ratingKey")}", title = e.optString("title"), season = e.optInt("parentIndex", 1),
                episode = e.optInt("index", 0), url = url, plot = e.optString("summary").ifBlank { null },
                image = art(e.optString("thumb")), durationSec = (e.optLong("duration") / 1000).toInt())
        }.sortedWith(compareBy({ it.season }, { it.episode }))
        return inf to eps
    }

    /** מדווח ל-Plex מיקום צפייה, ומסמן "נצפה" כשמגיעים לסוף */
    fun report(key: String, pos: Long, dur: Long) {
        if (!key.startsWith("plex:") || !connected || dur <= 0) return
        val id = rk(key)
        scope.launch {
            runCatching {
                if (pos >= dur * 0.9) sget("/:/scrobble?key=$id&identifier=com.plexapp.plugins.library")
                else sget("/:/progress?key=$id&identifier=com.plexapp.plugins.library&time=$pos&state=stopped")
            }
        }
    }
}
