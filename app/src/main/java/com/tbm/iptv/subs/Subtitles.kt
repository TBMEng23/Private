package com.tbm.iptv.subs

import androidx.media3.common.MimeTypes
import com.tbm.iptv.data.Http
import com.tbm.iptv.data.PlayItem
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.TitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream
import kotlin.math.abs

data class SubQuery(
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val imdb: String?,
    val langs: List<String>,
    val original: String,
) {
    val isSeries get() = season != null && episode != null
}

class RawSub(val bytes: ByteArray, val name: String)

class SubResult(
    val provider: String,
    val lang: String,
    val release: String,
    val score: Int = 0,
    val fetch: suspend () -> RawSub,
)

class SubtitleFile(val text: String, val ext: String, val lang: String) {
    val mime: String
        get() = when (ext) {
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt" -> MimeTypes.TEXT_VTT
            else -> MimeTypes.APPLICATION_SUBRIP
        }
}

interface SubProvider {
    val name: String
    fun enabled(): Boolean
    suspend fun search(q: SubQuery): List<SubResult>
}

fun langLabel(code: String) = when (code) {
    "he" -> "עברית"; "en" -> "אנגלית"; "ar" -> "ערבית"; "ru" -> "רוסית"; "fr" -> "צרפתית"
    "es" -> "ספרדית"; "de" -> "גרמנית"; "it" -> "איטלקית"; "pt" -> "פורטוגזית"; "tr" -> "טורקית"
    else -> code
}

object SubtitleManager {
    val providers: List<SubProvider> = listOf(WizdomProvider, KtuvitProvider, OpenSubtitlesProvider, SubDlProvider)

    suspend fun buildQuery(item: PlayItem): SubQuery = withContext(Dispatchers.IO) {
        val p = TitleParser.parse(item.seriesName ?: item.title)
        val season = item.season ?: p.season
        val ep = item.episode ?: p.episode
        val imdb = runCatching { Imdb.find(p.title, p.year, season != null) }.getOrNull()
        SubQuery(p.title, p.year, season, ep, imdb, Prefs.subLangs(), item.title)
    }

    /** חיפוש מקבילי בכל האתרים. מחזיר תוצאות ממוינות + רשימת שגיאות לכל אתר */
    suspend fun searchAll(q: SubQuery): Pair<List<SubResult>, List<String>> = coroutineScope {
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val lists = providers.filter { it.enabled() }.map { pr ->
            async(Dispatchers.IO) {
                try {
                    withTimeout(25_000) { pr.search(q) }
                } catch (e: Exception) {
                    errors += "${pr.name}: ${e.message ?: e.javaClass.simpleName}"
                    emptyList()
                }
            }
        }.awaitAll()
        val all = lists.flatten().filter { it.lang in q.langs }
        val sorted = all.sortedWith(
            compareBy<SubResult> { q.langs.indexOf(it.lang) }
                .thenByDescending { it.score + releaseScore(it.release, q.original) }
        )
        sorted to errors.toList()
    }

    private fun tokens(s: String) = s.lowercase().split(Regex("[^\\p{L}0-9]+")).filter { it.length >= 3 }.toSet()

    private fun releaseScore(release: String, original: String): Int {
        val a = tokens(release); val b = tokens(original)
        return a.intersect(b).size
    }

    fun pickBest(results: List<SubResult>): SubResult? = results.firstOrNull()

    suspend fun download(r: SubResult, q: SubQuery): SubtitleFile = withContext(Dispatchers.IO) {
        var raw = r.fetch()
        if (raw.bytes.size > 2 && raw.bytes[0] == 'P'.code.toByte() && raw.bytes[1] == 'K'.code.toByte()) {
            raw = unzip(raw.bytes, q)
        }
        if (raw.bytes.isEmpty()) throw IOException("קובץ ריק")
        var ext = raw.name.substringAfterLast('.', "srt").lowercase()
        if (ext !in setOf("srt", "ass", "ssa", "vtt")) ext = "srt"
        var text = decode(raw.bytes, r.lang)
        if (ext == "srt" || ext == "vtt") {
            text = text.replace("\r\n", "\n").replace('\r', '\n')
            if (r.lang == "he" || r.lang == "ar") text = fixRtl(text)
        }
        SubtitleFile(text, ext, r.lang)
    }

    private fun unzip(bytes: ByteArray, q: SubQuery): RawSub {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val ext = e.name.substringAfterLast('.', "").lowercase()
                    if (ext in setOf("srt", "ass", "ssa", "vtt")) entries += e.name to z.readBytes()
                }
                e = z.nextEntry
            }
        }
        if (entries.isEmpty()) throw IOException("אין קובץ כתוביות בארכיון")
        if (q.isSeries && entries.size > 1) {
            val s = q.season ?: 1; val ep = q.episode ?: 1
            val pats = listOf("s%02de%02d".format(s, ep), "%dx%02d".format(s, ep), "e%02d".format(ep))
            for (p in pats) {
                entries.firstOrNull { it.first.lowercase().contains(p) }?.let { return RawSub(it.second, it.first) }
            }
        }
        val best = entries.sortedBy {
            when (it.first.substringAfterLast('.').lowercase()) { "srt" -> 0; "vtt" -> 1; else -> 2 }
        }.first()
        return RawSub(best.second, best.first)
    }

    /** UTF-8 אם תקין, אחרת קידוד אזורי (windows-1255 לעברית) */
    fun decode(b: ByteArray, lang: String): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte())
            return String(b, 3, b.size - 3, Charsets.UTF_8)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte())
            return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte())
            return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        try {
            return Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
        } catch (e: CharacterCodingException) {
            // לא UTF-8 – ננסה קידוד אזורי
        }
        val cs = when (lang) {
            "he" -> "windows-1255"; "ar" -> "windows-1256"; "ru" -> "windows-1251"; "tr" -> "windows-1254"
            else -> "windows-1252"
        }
        return String(b, Charset.forName(cs))
    }

    private val rtlChars = Regex("[\\u0590-\\u05FF\\u0600-\\u06FF]")

    /** מתקן פיסוק בכתוביות עבריות (נקודה בתחילת שורה וכו') ע"י סימון כיווניות RTL */
    private fun fixRtl(text: String): String = text.lines().joinToString("\n") { line ->
        if (rtlChars.containsMatchIn(line) && !line.startsWith("‫")) "‫$line‬" else line
    }

    private val tsRx = Regex("""(\d{1,2}):(\d{2}):(\d{2})([,.])(\d{3})""")

    private fun shift(text: String, ms: Long): String = tsRx.replace(text) { m ->
        val g = m.groupValues
        val t = ((g[1].toLong() * 3600 + g[2].toLong() * 60 + g[3].toLong()) * 1000 + g[5].toLong() + ms)
            .coerceAtLeast(0)
        "%02d:%02d:%02d%s%03d".format(t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, g[4], t % 1000)
    }

    /** כותב את הכתובית לקובץ זמני (עם היסט זמן אם נדרש) */
    fun writeFile(sf: SubtitleFile, offsetMs: Long, dir: File): File {
        dir.listFiles()?.filter { it.name.startsWith("sub_") }?.forEach { it.delete() }
        val text = if (offsetMs != 0L && sf.ext != "ass" && sf.ext != "ssa") shift(sf.text, offsetMs) else sf.text
        return File(dir, "sub_${System.nanoTime()}.${sf.ext}").apply { writeText(text, Charsets.UTF_8) }
    }
}

/** חיפוש מזהה IMDb ללא מפתח API (שירות ההשלמה האוטומטית של IMDb) */
object Imdb {
    private val cache = ConcurrentHashMap<String, String>()

    fun find(title: String, year: Int?, series: Boolean): String? {
        if (title.isBlank()) return null
        val key = "$title|$year|$series"
        cache[key]?.let { return it }
        val q = title.lowercase().trim()
        val first = q.first().let { if (it in 'a'..'z') it else 'x' }
        val url = "https://v3.sg.media-imdb.com/suggestion/$first/${Http.enc(q)}.json"
        val arr = JSONObject(Http.get(url)).optJSONArray("d") ?: return null
        var best: String? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (!id.startsWith("tt")) continue
            val type = o.optString("q").lowercase()
            val isSer = "series" in type
            val y = o.optInt("y", 0)
            var sc = 0
            if (isSer == series) sc += 40
            if (year != null && y != 0 && abs(y - year) <= 1) sc += 30
            if (o.optString("l").equals(title, true)) sc += 20
            sc -= i
            if (sc > bestScore) { bestScore = sc; best = id }
        }
        best?.let { cache[key] = it }
        return best
    }
}
