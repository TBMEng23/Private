package com.tbm.iptv.data

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** בניית כתובת צפייה לתוכנית שכבר שודרה (Catch-up / Archive) */
object Catchup {

    fun available(c: Channel, e: EpgEntry, now: Long = System.currentTimeMillis()): Boolean {
        if (!c.hasCatchup || e.start >= now) return false
        val days = if (c.archiveDays > 0) c.archiveDays else 7
        return e.start >= now - days * 86_400_000L
    }

    private fun fmt(pattern: String, t: Long, tz: TimeZone): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = tz }.format(t)

    fun url(p: Playlist?, c: Channel, start: Long, end: Long): String? {
        val now = System.currentTimeMillis()
        val endT = minOf(end, now)
        val durSec = ((endT - start) / 1000).coerceAtLeast(60)
        val durMin = (durSec + 59) / 60

        // ---------- Xtream ----------
        if (p != null && p.isXtream && c.streamId != null && c.archiveDays > 0) {
            val base = Xtream.normalize(p.url)
            val tz = Prefs.str("tz_${p.id}").ifBlank { null }?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
            val s = fmt("yyyy-MM-dd:HH-mm", start, tz)
            return "$base/timeshift/${p.user}/${p.pass}/$durMin/$s/${c.streamId}.ts"
        }

        val type = c.catchupType?.lowercase() ?: return null
        val src = c.catchupSource
        val utc = TimeZone.getTimeZone("UTC")
        val local = TimeZone.getDefault()

        fun fill(t: String): String {
            var r = t
            val map = linkedMapOf(
                "{utc}" to "${start / 1000}", "\${start}" to "${start / 1000}", "{start}" to "${start / 1000}",
                "{utcend}" to "${endT / 1000}", "\${end}" to "${endT / 1000}", "{end}" to "${endT / 1000}",
                "{lutc}" to "${now / 1000}", "{now}" to "${now / 1000}", "\${timestamp}" to "${now / 1000}",
                "{timestamp}" to "${now / 1000}",
                "{duration}" to "$durSec", "\${duration}" to "$durSec",
                "{offset}" to "${(now - start) / 1000}", "\${offset}" to "${(now - start) / 1000}",
                "{Y}" to fmt("yyyy", start, local), "{m}" to fmt("MM", start, local), "{d}" to fmt("dd", start, local),
                "{H}" to fmt("HH", start, local), "{M}" to fmt("mm", start, local), "{S}" to fmt("ss", start, local),
            )
            map.forEach { (k, v) -> r = r.replace(k, v) }
            // {utc:YmdHMS} / {start:Y-m-d} וכו'
            r = Regex("""\{(utc|start|utcend|end):([^}]+)\}""").replace(r) { m ->
                val t0 = if (m.groupValues[1].startsWith("utcend") || m.groupValues[1] == "end") endT else start
                val pat = m.groupValues[2].replace("Y", "yyyy").replace("m", "MM").replace("d", "dd")
                    .replace("H", "HH").replace("M", "mm").replace("S", "ss")
                runCatching { fmt(pat, t0, utc) }.getOrDefault(m.value)
            }
            r = Regex("""\{duration:(\d+)\}""").replace(r) { m ->
                "${durSec / (m.groupValues[1].toLongOrNull() ?: 1L).coerceAtLeast(1)}"
            }
            return r
        }

        return when (type) {
            "default" -> src?.let { fill(it) }
            "append" -> src?.let { c.url + fill(it) }
            "shift", "timeshift" -> c.url + (if ('?' in c.url) "&" else "?") + "utc=${start / 1000}&lutc=${now / 1000}"
            "flussonic", "flussonic-hls", "flussonic-ts", "fs" -> {
                val u = c.url
                when {
                    u.endsWith("mpegts") || u.contains("/mpegts") ->
                        u.replace(Regex("""mpegts(\?.*)?$"""), "timeshift_abs-${start / 1000}.ts$1")
                    u.contains(".m3u8") ->
                        u.replace(Regex("""/[^/]+\.m3u8"""), "/archive-${start / 1000}-$durSec.m3u8")
                    else -> null
                }
            }
            "xc" -> {
                val m = Regex("""^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/(\d+)(\.\w+)?$""").find(c.url) ?: return null
                val (host, u, pw, id) = m.destructured
                "$host/timeshift/$u/$pw/$durMin/${fmt("yyyy-MM-dd:HH-mm", start, local)}/$id.ts"
            }
            else -> src?.let { fill(it) }
        }
    }
}
