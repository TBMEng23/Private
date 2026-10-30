package com.tbm.iptv.subs

import com.tbm.iptv.L

import com.tbm.iptv.data.Http
import com.tbm.iptv.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException

// ======================= Wizdom (עברית, ללא הרשמה) =======================
object WizdomProvider : SubProvider {
    override val name = "Wizdom"
    private const val HOST = "https://wizdom.xyz"
    private val hdr = mapOf("Referer" to "$HOST/")

    override fun enabled() = Prefs.bool("p_wizdom", true) && "he" in Prefs.subLangs()

    override suspend fun search(q: SubQuery): List<SubResult> {
        val imdb = q.imdb ?: throw IOException(L("לא נמצא מזהה IMDb"))
        val txt = try {
            Http.get("$HOST/api/releases/$imdb", hdr)
        } catch (e: IOException) {
            if (e.message?.contains("500") == true || e.message?.contains("404") == true) return emptyList()
            throw e
        }
        val o = JSONObject(txt)
        val subs = o.opt("subs")
        val list: JSONArray? = if (!q.isSeries) {
            subs as? JSONArray
        } else {
            val seasonObj: Any? = when (subs) {
                is JSONArray -> subs.opt(q.season!!)
                is JSONObject -> subs.opt(q.season.toString())
                else -> null
            }
            when (seasonObj) {
                is JSONObject -> seasonObj.optJSONArray(q.episode.toString())
                is JSONArray -> seasonObj.optJSONArray(q.episode!!)
                else -> null
            }
        }
        if (list == null) return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val s = list.optJSONObject(i) ?: return@mapNotNull null
            val id = s.optString("id").ifBlank { return@mapNotNull null }
            SubResult(name, "he", s.optString("version"), 2) {
                RawSub(Http.getBytes("$HOST/api/files/sub/$id", hdr), "wizdom.zip")
            }
        }
    }
}

// ======================= Ktuvit – כתובית (דורש חשבון) =======================
object KtuvitProvider : SubProvider {
    override val name = "Ktuvit"
    private const val BASE = "https://www.ktuvit.me/"
    @Volatile private var cookie: String? = null
    @Volatile private var cookieFor: String? = null

    override fun enabled() = Prefs.bool("p_ktuvit", true) &&
        Prefs.str("ktuvit_email").isNotBlank() && Prefs.str("ktuvit_hash").isNotBlank() &&
        "he" in Prefs.subLangs()

    private fun headers() = mapOf(
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "Cookie" to "Login=${cookie ?: ""}",
        "Referer" to BASE,
    )

    private fun dField(body: String): JSONObject {
        val d = JSONObject(body).opt("d")
        return when (d) {
            is String -> JSONObject(d)
            is JSONObject -> d
            else -> JSONObject()
        }
    }

    private fun login() {
        val email = Prefs.str("ktuvit_email").trim()
        val hash = Prefs.str("ktuvit_hash").trim()
        val body = JSONObject().put("request", JSONObject().put("Email", email).put("Password", hash)).toString()
        val r = Http.post("${BASE}Services/MembershipService.svc/Login", body, emptyMap(), followRedirects = false)
        val ok = runCatching { dField(r.body).optBoolean("IsSuccess") }.getOrDefault(false)
        val c = r.headers.values("Set-Cookie").firstNotNullOfOrNull { h ->
            h.split("Login=").getOrNull(1)?.substringBefore(";")?.ifBlank { null }
        }
        if (!ok || c == null) throw IOException(L("התחברות נכשלה – בדוק אימייל וסיסמה מוצפנת"))
        cookie = c
        cookieFor = email + hash
    }

    private fun searchFilms(req: JSONObject): JSONArray {
        val r = Http.post(
            "${BASE}Services/ContentProvider.svc/SearchPage_search",
            JSONObject().put("request", req).toString(), headers(),
        )
        return dField(r.body).optJSONArray("Films") ?: JSONArray()
    }

    override suspend fun search(q: SubQuery): List<SubResult> {
        if (cookie == null || cookieFor != Prefs.str("ktuvit_email").trim() + Prefs.str("ktuvit_hash").trim()) login()
        val req = JSONObject()
            .put("FilmName", q.title)
            .put("Actors", JSONArray()).put("Studios", JSONArray()).put("Directors", JSONArray())
            .put("Genres", JSONArray()).put("Countries", JSONArray()).put("Languages", JSONArray())
            .put("Year", q.year?.toString() ?: "")
            .put("Rating", JSONArray())
            .put("Page", 1)
            .put("SearchType", if (q.isSeries) "1" else "0")
            .put("WithSubsOnly", false)
        var films = searchFilms(req)
        if (films.length() == 0 && q.year != null) {
            req.put("Year", ""); films = searchFilms(req)
        }
        if (films.length() == 0) return emptyList()

        var film: JSONObject? = null
        if (q.imdb != null) {
            for (i in 0 until films.length()) {
                val f = films.optJSONObject(i) ?: continue
                val link = f.optString("IMDB_Link").trimEnd('/')
                if (link.substringAfterLast('/') == q.imdb) { film = f; break }
            }
        }
        if (film == null) film = films.optJSONObject(0)
        val filmId = film?.optString("ID")?.ifBlank { null } ?: return emptyList()

        val html = if (q.isSeries) {
            Http.get(
                "${BASE}Services/GetModuleAjax.ashx?moduleName=SubtitlesList&SeriesID=$filmId" +
                    "&Season=${q.season}&Episode=${q.episode}", headers()
            )
        } else {
            Http.get("${BASE}MovieInfo.aspx?ID=$filmId", headers())
        }
        if (html.length < 10) return emptyList()
        val doc = Jsoup.parse(html)
        val rows = if (q.isSeries) doc.select("tr") else doc.select("table#subtitlesList tbody > tr")
        return rows.mapNotNull { tr ->
            val subId = tr.selectFirst("[data-sub-id]")?.attr("data-sub-id")
                ?: tr.selectFirst("[data-subtitle-id]")?.attr("data-subtitle-id")
            if (subId.isNullOrBlank()) return@mapNotNull null
            val rls = tr.selectFirst("td")?.text()?.trim().orEmpty()
            SubResult(name, "he", rls, 3) { download(filmId, subId) }
        }
    }

    private fun download(filmId: String, subId: String): RawSub {
        val body = JSONObject().put(
            "request", JSONObject()
                .put("FilmID", filmId).put("SubtitleID", subId)
                .put("FontSize", 0).put("FontColor", "").put("PredefinedLayout", -1)
        ).toString()
        val id = dField(
            Http.post("${BASE}Services/ContentProvider.svc/RequestSubtitleDownload", body, headers()).body
        ).optString("DownloadIdentifier")
        if (id.isBlank()) throw IOException(L("כתובית סירבה להורדה"))
        val bytes = Http.getBytes("${BASE}Services/DownloadFile.ashx?DownloadIdentifier=$id", headers())
        return RawSub(bytes, "ktuvit.srt")
    }
}

// ======================= OpenSubtitles.com (מפתח API חינמי) =======================
object OpenSubtitlesProvider : SubProvider {
    override val name = "OpenSubtitles"
    private const val API = "https://api.opensubtitles.com/api/v1/"
    @Volatile private var token: String? = null

    private fun key() = Prefs.str("os_key").trim()
    override fun enabled() = Prefs.bool("p_os", true) && key().isNotBlank()

    private fun headers(): Map<String, String> {
        val m = linkedMapOf("Api-Key" to key(), "User-Agent" to "TBMPlayer v1.0", "Accept" to "application/json")
        token?.let { m["Authorization"] = "Bearer $it" }
        return m
    }

    private fun login() {
        val u = Prefs.str("os_user").trim(); val p = Prefs.str("os_pass")
        if (u.isBlank() || p.isBlank()) return
        val r = Http.post("${API}login", JSONObject().put("username", u).put("password", p).toString(), headers())
        token = runCatching { JSONObject(r.body).optString("token").ifBlank { null } }.getOrNull()
    }

    override suspend fun search(q: SubQuery): List<SubResult> {
        if (token == null) runCatching { login() }
        val params = sortedMapOf<String, String>()
        params["languages"] = q.langs.sorted().joinToString(",")
        if (q.imdb != null) {
            val num = q.imdb.removePrefix("tt").trimStart('0')
            if (q.isSeries) params["parent_imdb_id"] = num else params["imdb_id"] = num
        } else {
            params["query"] = q.title.lowercase()
            if (!q.isSeries) q.year?.let { params["year"] = it.toString() }
        }
        if (q.isSeries) {
            params["season_number"] = q.season.toString()
            params["episode_number"] = q.episode.toString()
        }
        val url = "${API}subtitles?" + params.entries.joinToString("&") { "${it.key}=${Http.enc(it.value)}" }
        val data = JSONObject(Http.get(url, headers())).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val a = data.optJSONObject(i)?.optJSONObject("attributes") ?: return@mapNotNull null
            val fileId = a.optJSONArray("files")?.optJSONObject(0)?.optInt("file_id", 0) ?: 0
            if (fileId == 0) return@mapNotNull null
            val lang = a.optString("language").substringBefore('-').lowercase()
            val pop = (a.optInt("download_count", 0) / 2000).coerceAtMost(2)
            SubResult(name, lang, a.optString("release"), pop) { download(fileId) }
        }
    }

    private fun download(id: Int): RawSub {
        val r = Http.post(
            "${API}download",
            JSONObject().put("file_id", id).put("sub_format", "srt").toString(),
            headers(),
        )
        val o = runCatching { JSONObject(r.body) }.getOrDefault(JSONObject())
        val link = o.optString("link")
        if (link.isBlank()) throw IOException(o.optString("message").ifBlank { "HTTP ${r.code}" })
        return RawSub(Http.getBytes(link), o.optString("file_name").ifBlank { "os.srt" })
    }
}

// ======================= SubDL (מפתח API חינמי) =======================
object SubDlProvider : SubProvider {
    override val name = "SubDL"
    private val langMap = mapOf(
        "hebrew" to "he", "english" to "en", "arabic" to "ar", "russian" to "ru", "french" to "fr",
        "spanish" to "es", "german" to "de", "italian" to "it", "portuguese" to "pt", "turkish" to "tr",
    )

    private fun key() = Prefs.str("subdl_key").trim()
    override fun enabled() = Prefs.bool("p_subdl", true) && key().isNotBlank()

    override suspend fun search(q: SubQuery): List<SubResult> {
        val sb = StringBuilder("https://api.subdl.com/api/v1/subtitles?api_key=${Http.enc(key())}")
        sb.append("&subs_per_page=30&languages=").append(q.langs.joinToString(",") { it.uppercase() })
        if (q.imdb != null) sb.append("&imdb_id=").append(q.imdb) else sb.append("&film_name=").append(Http.enc(q.title))
        sb.append("&type=").append(if (q.isSeries) "tv" else "movie")
        if (q.isSeries) sb.append("&season_number=${q.season}&episode_number=${q.episode}")
        else if (q.imdb == null && q.year != null) sb.append("&year=${q.year}")

        val o = JSONObject(Http.get(sb.toString()))
        if (!o.optBoolean("status", false)) {
            val err = o.optString("error")
            if (err.isNotBlank() && !err.contains("not found", true) && !err.contains("can't find", true))
                throw IOException(err)
            return emptyList()
        }
        val subs = o.optJSONArray("subtitles") ?: return emptyList()
        return (0 until subs.length()).mapNotNull { i ->
            val s = subs.optJSONObject(i) ?: return@mapNotNull null
            val url = s.optString("url").ifBlank { return@mapNotNull null }
            val code = s.optString("language").lowercase()
            val lang = if (code.length == 2) code
            else langMap[s.optString("lang").lowercase()] ?: s.optString("lang").lowercase()
            if (q.isSeries) {
                val se = s.optInt("season", -1); val ep = s.optInt("episode", -1)
                if (se > 0 && se != q.season) return@mapNotNull null
                if (ep > 0 && ep != q.episode) return@mapNotNull null
            }
            val full = if (url.startsWith("http")) url else "https://dl.subdl.com$url"
            SubResult(name, lang, s.optString("release_name"), 1) { RawSub(Http.getBytes(full), "subdl.zip") }
        }
    }
}

// ======================= Podnapisi (ללא הרשמה, יש עברית) =======================
object PodnapisiProvider : SubProvider {
    override val name = "Podnapisi"
    override fun enabled() = Prefs.bool("p_podnapisi", true)

    override suspend fun search(q: SubQuery): List<SubResult> {
        val out = ArrayList<SubResult>()
        for (lang in q.langs.take(3)) {
            val sb = StringBuilder("https://www.podnapisi.net/subtitles/search/old?sXML=1")
            sb.append("&sL=").append(lang).append("&sK=").append(Http.enc(q.title))
            if (q.isSeries) sb.append("&sTS=${q.season}&sTE=${q.episode}")
            q.year?.let { if (!q.isSeries) sb.append("&sY=$it") }
            val xml = Http.get(sb.toString())
            val doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser())
            doc.select("subtitle").forEach { s ->
                val pid = s.selectFirst("pid")?.text()?.trim().orEmpty()
                if (pid.isBlank()) return@forEach
                val l = s.selectFirst("language")?.text()?.trim()?.lowercase()?.substringBefore('-') ?: lang
                val rls = s.selectFirst("release")?.text()?.trim().orEmpty()
                    .ifBlank { s.selectFirst("title")?.text().orEmpty() }
                out += SubResult(name, l, rls, 1) {
                    RawSub(Http.getBytes("https://www.podnapisi.net/subtitles/$pid/download?container=zip"), "podnapisi.zip")
                }
            }
        }
        return out
    }
}

// ======================= SubSource (מפתח API חינמי, יש עברית) =======================
object SubSourceProvider : SubProvider {
    override val name = "SubSource"
    private const val API = "https://api.subsource.net/api/v1"
    private fun key() = Prefs.str("subsource_key").trim()
    override fun enabled() = Prefs.bool("p_subsource", true) && key().isNotBlank()
    private fun hdr() = mapOf("X-API-Key" to key(), "Accept" to "application/json")
    private val names = mapOf("he" to "hebrew", "en" to "english", "ar" to "arabic", "ru" to "russian",
        "fr" to "french", "es" to "spanish", "de" to "german")

    override suspend fun search(q: SubQuery): List<SubResult> {
        val url = if (q.imdb != null) "$API/movies/search?searchType=imdb&imdb=${q.imdb}"
        else "$API/movies/search?searchType=text&q=${Http.enc(q.title)}"
        val movies = JSONObject(Http.get(url + (if (q.isSeries) "&season=${q.season}" else ""), hdr()))
            .optJSONArray("data") ?: return emptyList()
        var movieId: String? = null
        for (i in 0 until movies.length()) {
            val m = movies.optJSONObject(i) ?: continue
            if (q.isSeries && m.has("season") && m.optInt("season", -1) != q.season) continue
            movieId = m.optString("movieId").ifBlank { null } ?: continue
            break
        }
        if (movieId == null) return emptyList()
        val out = ArrayList<SubResult>()
        val epRx = if (q.isSeries) Regex("(?i)s0?${q.season}\\s*e0?${q.episode}(?!\\d)|\\b${q.season}x0?${q.episode}\\b") else null
        for (lang in q.langs.take(3)) {
            val ln = names[lang] ?: continue
            val a = JSONObject(Http.get("$API/subtitles?movieId=$movieId&language=$ln&limit=50", hdr()))
                .optJSONArray("data") ?: continue
            for (i in 0 until a.length()) {
                val s = a.optJSONObject(i) ?: continue
                val id = s.optString("subtitleId").ifBlank { null } ?: continue
                val rel = when (val r = s.opt("releaseInfo")) {
                    is JSONArray -> (0 until r.length()).joinToString(" / ") { r.optString(it) }
                    null -> ""
                    else -> r.toString()
                }
                if (epRx != null && rel.isNotBlank() && Regex("(?i)s\\d+\\s*e\\d+").containsMatchIn(rel) && !epRx.containsMatchIn(rel)) continue
                out += SubResult(name, lang, rel, 2) {
                    RawSub(Http.getBytes("$API/subtitles/$id/download", hdr()), "subsource.zip")
                }
            }
        }
        return out
    }
}

// ======================= YIFY Subtitles (סרטים, ללא הרשמה) =======================
object YifyProvider : SubProvider {
    override val name = "YIFY"
    private const val HOST = "https://yts-subs.com"
    private val names = mapOf("he" to "hebrew", "en" to "english", "ar" to "arabic", "ru" to "russian",
        "fr" to "french", "es" to "spanish", "de" to "german")
    override fun enabled() = Prefs.bool("p_yify", true)

    override suspend fun search(q: SubQuery): List<SubResult> {
        if (q.isSeries) return emptyList()
        val imdb = q.imdb ?: return emptyList()
        val doc = Jsoup.parse(Http.get("$HOST/movie-imdb/$imdb"), HOST)
        val out = ArrayList<SubResult>()
        doc.select("tr").forEach { tr ->
            val link = tr.selectFirst("a[href*=/subtitles/]") ?: return@forEach
            val langName = (tr.selectFirst(".sub-lang")?.text() ?: tr.text()).lowercase()
            val code = q.langs.firstOrNull { l -> names[l]?.let { langName.contains(it) } == true } ?: return@forEach
            val page = link.absUrl("href")
            val rls = link.text().removePrefix("subtitle").trim()
            out += SubResult(name, code, rls, 0) {
                val p = Jsoup.parse(Http.get(page), page)
                val zip = p.selectFirst("a.download-subtitle, a[href$=.zip]")?.absUrl("href")
                    ?: throw java.io.IOException(L("קישור הורדה לא נמצא"))
                RawSub(Http.getBytes(zip), "yify.zip")
            }
        }
        return out
    }
}
