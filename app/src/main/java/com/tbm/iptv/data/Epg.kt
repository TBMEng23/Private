package com.tbm.iptv.data

import com.tbm.iptv.L

import android.util.Xml
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/** מדריך שידורים מלא מ-XMLTV (Xtream xmltv.php, url-tvg של M3U, או כתובת ידנית) */
object Epg {
    @Volatile private var programs: Map<String, List<EpgEntry>> = emptyMap()
    @Volatile private var nameIndex: Map<String, String> = emptyMap()

    val version = mutableIntStateOf(0)
    var status by mutableStateOf("")

    val isEmpty get() = programs.isEmpty()

    fun norm(s: String): String = TitleParser.parse(s).title.lowercase()
        .replace(Regex("[^\\p{L}0-9]"), "")

    private fun keyFor(epgId: String?, name: String?): String? {
        if (!epgId.isNullOrBlank()) {
            val k = epgId.lowercase()
            if (programs.containsKey(k)) return k
        }
        if (!name.isNullOrBlank()) return nameIndex[norm(name)]
        return null
    }

    fun forChannel(epgId: String?, name: String?): List<EpgEntry> =
        keyFor(epgId, name)?.let { programs[it] } ?: emptyList()

    fun forChannel(c: Channel) = forChannel(c.epgId, c.name)

    fun nowNext(epgId: String?, name: String?, now: Long = System.currentTimeMillis()): Pair<EpgEntry?, EpgEntry?> {
        val l = forChannel(epgId, name)
        val i = l.indexOfFirst { now >= it.start && now < it.end }
        if (i < 0) return null to l.firstOrNull { it.start > now }
        return l[i] to l.getOrNull(i + 1)
    }

    fun sourceUrl(p: Playlist, c: Content): String? {
        Prefs.epgUrl(p).trim().ifBlank { null }?.let { return it }
        if (p.isXtream) {
            val x = Xtream(p)
            return "${x.base}/xmltv.php?username=${Http.enc(p.user)}&password=${Http.enc(p.pass)}"
        }
        return c.epgUrl?.split(',')?.firstOrNull()?.trim()?.ifBlank { null }
    }

    fun load(p: Playlist, c: Content, force: Boolean) {
        val url = sourceUrl(p, c)
        if (url == null) { status = L("אין מקור EPG – אפשר להוסיף כתובת XMLTV בהגדרות"); return }
        status = L("טוען מדריך שידורים…")
        try {
            val f = Repo.cacheFile("epg_${p.id}.xml")
            val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < Prefs.refreshHours * 3_600_000L
            if (force || !fresh) {
                try { Http.download(url, f) } catch (e: Exception) { if (!f.exists()) throw e }
            }
            parse(f, c)
            status = if (programs.isEmpty()) L("המדריך ריק") else ""
        } catch (e: Exception) {
            status = L("שגיאה בטעינת EPG: {0}", e.message ?: "")
        }
        version.intValue = version.intValue + 1
    }

    private fun open(f: File): InputStream {
        val bis = BufferedInputStream(f.inputStream(), 65536)
        bis.mark(2)
        val b1 = bis.read(); val b2 = bis.read()
        bis.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(bis, 65536) else bis
    }

    private fun parse(f: File, c: Content) {
        val wantIds = HashSet<String>()
        val wantNames = HashSet<String>()
        var maxArchive = 0
        c.live.forEach { ch ->
            ch.epgId?.let { wantIds += it.lowercase() }
            wantNames += norm(ch.name)
            if (ch.archiveDays > maxArchive) maxArchive = ch.archiveDays
            if (ch.catchupType != null && maxArchive < 7) maxArchive = 7
        }
        val now = System.currentTimeMillis()
        val from = now - (if (maxArchive > 0) minOf(maxArchive, 7) * 86_400_000L else 6 * 3_600_000L)
        val to = now + 3 * 86_400_000L

        val chNames = HashMap<String, String>() // normName -> channel id
        val progs = HashMap<String, ArrayList<EpgEntry>>()

        open(f).use { input ->
            val xp = Xml.newPullParser()
            xp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            xp.setInput(input, null)
            var ev = xp.eventType
            var curChannel: String? = null
            var pStart = 0L; var pEnd = 0L; var pCh: String? = null
            var pTitle = ""; var pDesc = ""
            var inProg = false
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (xp.name) {
                        "channel" -> curChannel = xp.getAttributeValue(null, "id")?.lowercase()
                        "display-name" -> if (curChannel != null && !inProg) {
                            val n = norm(xp.nextText())
                            if (n.isNotEmpty() && n in wantNames) chNames.putIfAbsent(n, curChannel)
                        }
                        "programme" -> {
                            pCh = xp.getAttributeValue(null, "channel")?.lowercase()
                            pStart = parseTime(xp.getAttributeValue(null, "start"))
                            pEnd = parseTime(xp.getAttributeValue(null, "stop"))
                            pTitle = ""; pDesc = ""; inProg = true
                        }
                        "title" -> if (inProg && pTitle.isEmpty()) pTitle = xp.nextText().trim()
                        "desc" -> if (inProg && pDesc.isEmpty()) pDesc = xp.nextText().trim().take(400)
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    when (xp.name) {
                        "channel" -> curChannel = null
                        "programme" -> {
                            inProg = false
                            val ch = pCh
                            if (ch != null && pEnd > from && pStart < to && pEnd > pStart &&
                                (ch in wantIds || ch in chNames.values || wantIds.isEmpty())
                            ) {
                                progs.getOrPut(ch) { ArrayList() } += EpgEntry(pTitle, pStart, pEnd, pDesc)
                            }
                        }
                    }
                }
                ev = xp.next()
            }
        }
        progs.values.forEach { it.sortBy { e -> e.start } }
        programs = progs
        nameIndex = chNames
    }

    /** "20231015120000 +0300" */
    fun parseTime(s: String?): Long {
        if (s == null || s.length < 12) return 0
        return try {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.clear()
            cal.set(
                s.substring(0, 4).toInt(), s.substring(4, 6).toInt() - 1, s.substring(6, 8).toInt(),
                s.substring(8, 10).toInt(), s.substring(10, 12).toInt(),
                if (s.length >= 14) s.substring(12, 14).toIntOrNull() ?: 0 else 0,
            )
            var t = cal.timeInMillis
            val tz = s.substringAfter(' ', "").trim()
            if (tz.length >= 5 && (tz[0] == '+' || tz[0] == '-')) {
                val sign = if (tz[0] == '-') -1 else 1
                val off = (tz.substring(1, 3).toInt() * 60 + tz.substring(3, 5).toInt()) * 60_000L
                t -= sign * off
            }
            t
        } catch (e: Exception) { 0 }
    }
}
