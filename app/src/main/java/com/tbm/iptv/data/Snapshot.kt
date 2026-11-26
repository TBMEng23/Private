package com.tbm.iptv.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * תמונת מצב בינארית של הרשימה אחרי טעינה: בפתיחה הבאה התוכן מוצג מיד (שניות בודדות גם בסטרימר חלש)
 * במקום לפענח מחדש קבצי JSON של עשרות מגה. רענון מהספק רץ ברקע רק כשהגיע זמן הרענון.
 */
object Snapshot {
    private const val VER = 1
    private fun file(pid: String) = Repo.cacheFile("snap_$pid.bin")

    /** התוכן תלוי בטעינה חסכונית ובמה שמוסתר – אם אלה השתנו, התמונה לא תקפה */
    fun signature(): Int = if (!Prefs.leanLoad) 1 else
        (Prefs.hidden().hashCode() * 31 + Prefs.adultMode.hashCode()) * 31 + Kind.entries.sumOf { Prefs.allowedCountries(it).hashCode() } + 7

    fun age(pid: String): Long { val f = file(pid); return if (f.exists()) System.currentTimeMillis() - f.lastModified() else Long.MAX_VALUE }

    fun save(pid: String, c: Content) = runCatching {
        val tmp = File(file(pid).path + ".tmp")
        DataOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { o ->
            fun s(v: String?) { if (v == null) o.writeBoolean(false) else { o.writeBoolean(true); o.writeUTF(v.take(20000)) } }
            o.writeInt(VER); o.writeInt(signature())
            s(c.epgUrl)
            fun ch(list: List<Channel>) {
                o.writeInt(list.size)
                list.forEach { x ->
                    o.writeUTF(x.key); o.writeUTF(x.name); s(x.logo); o.writeUTF(x.group); o.writeUTF(x.url)
                    o.writeByte(x.kind.ordinal); o.writeInt(x.streamId ?: Int.MIN_VALUE); s(x.epgId); o.writeInt(x.archiveDays)
                    s(x.catchupType); s(x.catchupSource); o.writeLong(x.added); o.writeFloat(x.rating)
                }
            }
            // Plex נטען בנפרד בכל פעם – לא נשמר
            ch(c.live.filterNot { it.key.startsWith("plex:") }); ch(c.movies.filterNot { it.key.startsWith("plex:") })
            val ser = c.series.filterNot { it.key.startsWith("plex:") }
            o.writeInt(ser.size)
            ser.forEach { x ->
                o.writeUTF(x.key); o.writeUTF(x.name); s(x.logo); o.writeUTF(x.group); o.writeInt(x.seriesId ?: Int.MIN_VALUE)
                s(x.plot); o.writeLong(x.added); o.writeFloat(x.rating)
                val eps = x.episodesM3u
                o.writeInt(eps?.size ?: -1)
                eps?.forEach { e ->
                    o.writeUTF(e.key); o.writeUTF(e.title); o.writeInt(e.season); o.writeInt(e.episode); o.writeUTF(e.url)
                    s(e.plot); s(e.image); o.writeInt(e.durationSec)
                }
            }
            o.writeInt(c.allGroups.size)
            c.allGroups.forEach { (k, gs) -> o.writeByte(k.ordinal); o.writeInt(gs.size); gs.forEach { o.writeUTF(it) } }
        }
        tmp.renameTo(file(pid))
    }

    fun load(pid: String): Content? = runCatching {
        val f = file(pid); if (!f.exists()) return null
        DataInputStream(BufferedInputStream(f.inputStream(), 1 shl 16)).use { i ->
            if (i.readInt() != VER || i.readInt() != signature()) return null
            val pool = HashMap<String, String>()
            fun g(v: String) = pool.getOrPut(v) { v }
            fun s(): String? = if (i.readBoolean()) i.readUTF() else null
            val epg = s()
            val kinds = Kind.entries
            fun ch(): List<Channel> {
                val n = i.readInt(); val l = ArrayList<Channel>(n)
                repeat(n) {
                    val key = i.readUTF(); val name = i.readUTF(); val logo = s(); val group = g(i.readUTF()); val url = i.readUTF()
                    val kind = kinds[i.readByte().toInt()]; val sid = i.readInt().takeIf { it != Int.MIN_VALUE }
                    val epgId = s(); val arch = i.readInt(); val ct = s(); val cs = s(); val added = i.readLong(); val rating = i.readFloat()
                    l += Channel(key, name, logo, group, url, kind, sid, epgId, arch, ct, cs, added, rating)
                }
                return l
            }
            val live = ch(); val movies = ch()
            val n = i.readInt(); val series = ArrayList<SeriesItem>(n)
            repeat(n) {
                val key = i.readUTF(); val name = i.readUTF(); val logo = s(); val group = g(i.readUTF())
                val sid = i.readInt().takeIf { it != Int.MIN_VALUE }; val plot = s(); val added = i.readLong(); val rating = i.readFloat()
                val ne = i.readInt()
                val eps = if (ne < 0) null else List(ne) {
                    Episode(i.readUTF(), i.readUTF(), i.readInt(), i.readInt(), i.readUTF(), s(), s(), i.readInt())
                }
                series += SeriesItem(key, name, logo, group, sid, plot, eps, added, rating)
            }
            val ng = i.readInt()
            val all = HashMap<Kind, List<String>>()
            repeat(ng) { val k = kinds[i.readByte().toInt()]; val c = i.readInt(); all[k] = List(c) { i.readUTF() } }
            Content(live, movies, series, epg, all)
        }
    }.getOrNull()
}
