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
            val manual = ((Prefs.str("epg_off_${p.id}", "0").toFloatOrNull() ?: 0f) * 3_600_000L).toLong()
            val auto = Prefs.str("epg_auto_${p.id}", "0").toLongOrNull() ?: 0L
            parse(f, c, manual + if (manual == 0L) auto else 0L)
            // בדיקה עצמית מול השעון של הספק (Xtream נותן זמני Unix מדויקים) – תיקון הזזת שעות אוטומטי
            if (manual == 0L && p.isXtream) runCatching {
                val d = calibrate(p, c)
                if (d != null && d != auto) {
                    Prefs.setStrQuiet("epg_auto_${p.id}", d.toString())
                    parse(f, c, d)
                }
            }
            status = if (programs.isEmpty()) L("המדריך ריק") else ""
        } catch (e: OutOfMemoryError) {
            programs = emptyMap(); nameIndex = emptyMap()
            status = L("המדריך גדול מדי לזיכרון המכשיר")
        } catch (e: Throwable) {
            status = L("שגיאה בטעינת EPG: {0}", e.message ?: "")
        }
        version.intValue = version.intValue + 1
    }

    /** תיקון אחרון שזוהה (מילישניות) – להצגה בהגדרות */
    fun autoOffsetHours(p: Playlist): Float = (Prefs.str("epg_auto_${p.id}", "0").toLongOrNull() ?: 0L) / 3_600_000f

    /**
     * משווה את זמני המדריך לזמני ה-API של הספק לכמה ערוצים.
     * מחזיר את ההזזה הכוללת הנכונה (כולל מה שכבר הוחל), מעוגלת לחצי שעה, או null אם אין מספיק נתונים.
     */
    private fun calibrate(p: Playlist, c: Content): Long? {
        val x = Xtream(p)
        val applied = Prefs.str("epg_auto_${p.id}", "0").toLongOrNull() ?: 0L
        val now = System.currentTimeMillis()
        val deltas = ArrayList<Long>()
        for (ch in c.live.asSequence().filter { it.streamId != null && it.epgId != null }.filter { forChannel(it).size > 3 }.take(8)) {
            val ref = runCatching { x.shortEpg(ch.streamId!!) }.getOrNull().orEmpty().filter { it.start > 0 }
            val mine = forChannel(ch)
            for (r in ref) {
                val t = r.title.trim().lowercase()
                val m = mine.firstOrNull { it.title.trim().lowercase() == t && kotlin.math.abs(it.start - r.start) < 14 * 3_600_000L } ?: continue
                deltas += r.start - m.start
            }
            if (deltas.size >= 6) break
        }
        if (deltas.size < 3) return null
        val med = deltas.sorted()[deltas.size / 2]
        // רוב ההתאמות צריכות להסכים (±10 דק')
        if (deltas.count { kotlin.math.abs(it - med) <= 10 * 60_000L } < deltas.size * 0.6) return null
        val half = 30 * 60_000L
        val rounded = Math.round(med.toDouble() / half) * half
        return applied + rounded
    }

    private fun open(f: File): InputStream {
        val bis = BufferedInputStream(f.inputStream(), 65536)
        bis.mark(2)
        val b1 = bis.read(); val b2 = bis.read()
        bis.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(bis, 65536) else bis
    }

    /** מקסימום תוכניות בזיכרון – מונע קריסה בספקים עם אלפי ערוצים */
    private const val MAX_ENTRIES = 500_000

    /** מחיקת המדריך של מקור (קובץ מטמון + זיכרון) */
    fun clear(p: Playlist) {
        runCatching { Repo.cacheFile("epg_${p.id}.xml").delete() }
        programs = emptyMap(); nameIndex = emptyMap()
        status = L("המדריך נמחק")
        version.intValue = version.intValue + 1
    }

    private fun parse(f: File, c: Content, offsetMs: Long = 0L) {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        val day = 86_400_000L
        // לכל ערוץ: מאיזה זמן לשמור (ארכיון רק לערוצים עם catch-up)
        val fromById = HashMap<String, Long>()
        val wantNames = HashMap<String, Long>()
        val vis = if (Prefs.epgVisibleOnly) Catalog.parentGroupFilter(Kind.LIVE) else null
        val hiddenItems = if (vis != null) Prefs.hiddenItems() else emptySet()
        c.live.forEach { ch ->
            if (vis != null && (!vis(ch.group) || ch.key in hiddenItems)) return@forEach
            val days = if (ch.archiveDays > 0) minOf(ch.archiveDays, 7) else if (ch.catchupType != null) 7 else 0
            val from = if (days > 0) now - days * day else now - 3 * hour
            ch.epgId?.lowercase()?.let { id -> if ((fromById[id] ?: Long.MAX_VALUE) > from) fromById[id] = from }
            val n = norm(ch.name)
            if (n.isNotEmpty() && (wantNames[n] ?: Long.MAX_VALUE) > from) wantNames[n] = from
        }
        val to = now + 2 * day
        val descFrom = now - 3 * hour
        val descTo = now + day

        val chNames = HashMap<String, String>() // normName -> channel id
        val progs = HashMap<String, ArrayList<EpgEntry>>()
        val titles = HashMap<String, String>()  // מחרוזות כותרת משותפות
        var count = 0

        open(f).use { input ->
            val xp = Xml.newPullParser()
            xp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            xp.setInput(input, null)
            var ev = xp.eventType
            var curChannel: String? = null
            var pStart = 0L; var pEnd = 0L; var pCh: String? = null
            var pTitle = ""; var pDesc = ""
            var inProg = false
            var keep = false
            while (ev != XmlPullParser.END_DOCUMENT && count < MAX_ENTRIES) {
                if (ev == XmlPullParser.START_TAG) {
                    when (xp.name) {
                        "channel" -> curChannel = xp.getAttributeValue(null, "id")?.lowercase()
                        "display-name" -> if (curChannel != null && !inProg) {
                            val n = norm(xp.nextText())
                            val fr = wantNames[n]
                            if (fr != null && chNames.putIfAbsent(n, curChannel!!) == null) {
                                if ((fromById[curChannel!!] ?: Long.MAX_VALUE) > fr) fromById[curChannel!!] = fr
                            }
                        }
                        "programme" -> {
                            pCh = xp.getAttributeValue(null, "channel")?.lowercase()
                            pStart = parseTime(xp.getAttributeValue(null, "start")) + offsetMs
                            pEnd = parseTime(xp.getAttributeValue(null, "stop")) + offsetMs
                            val fr = pCh?.let { fromById[it] }
                            keep = fr != null && pEnd > fr && pStart < to && pEnd > pStart
                            pTitle = ""; pDesc = ""; inProg = true
                        }
                        "title" -> if (inProg && keep && pTitle.isEmpty()) pTitle = xp.nextText().trim()
                        "desc" -> if (inProg && keep && pDesc.isEmpty() && pStart in descFrom..descTo)
                            pDesc = xp.nextText().trim().take(220)
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    when (xp.name) {
                        "channel" -> curChannel = null
                        "programme" -> {
                            inProg = false
                            val ch = pCh
                            if (keep && ch != null) {
                                val t = titles.getOrPut(pTitle) { pTitle }
                                progs.getOrPut(ch) { ArrayList() } += EpgEntry(t, pStart, pEnd, pDesc)
                                count++
                            }
                        }
                    }
                }
                ev = xp.next()
            }
        }
        progs.values.forEach { it.sortBy { e -> e.start }; it.trimToSize() }
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
