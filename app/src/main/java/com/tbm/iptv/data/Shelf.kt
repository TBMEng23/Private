package com.tbm.iptv.data

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch
import java.io.File

/**
 * "מה חדש על המדף": עוקב אחרי הסדרות של הספק בין טעינה לטעינה
 * – סדרה שלא הייתה קודם = סדרה חדשה; סדרה שתאריך העדכון שלה השתנה = פרקים / עונה חדשה.
 * הנתונים נשמרים בקובץ מקומי (לא בענן) כדי לא להכביד.
 */
object Shelf {
    private class Rec(var lastMod: Long, val firstSeen: Long, var updated: Long, var seasons: Int)

    val ready = mutableIntStateOf(0)
    @Volatile var newSeries: List<String> = emptyList(); private set
    /** מפתח סדרה → תווית ("עונה 3 חדשה" / "פרקים חדשים") */
    @Volatile var updated: List<Pair<String, String>> = emptyList(); private set

    private const val WINDOW = 21L * 86_400_000L   // 3 שבועות על המדף

    private fun file(p: Playlist) = Repo.cacheFile("shelf_${p.id}.tsv")

    private fun read(f: File): HashMap<String, Rec> {
        val m = HashMap<String, Rec>()
        if (!f.exists()) return m
        runCatching {
            f.forEachLine { l ->
                val a = l.split('\t'); if (a.size < 5) return@forEachLine
                m[a[0]] = Rec(a[1].toLongOrNull() ?: 0, a[2].toLongOrNull() ?: 0, a[3].toLongOrNull() ?: 0, a[4].toIntOrNull() ?: 0)
            }
        }
        return m
    }

    private fun write(f: File, m: Map<String, Rec>) = runCatching {
        f.bufferedWriter().use { w -> m.forEach { (k, r) -> w.append(k).append('\t').append(r.lastMod.toString()).append('\t')
            .append(r.firstSeen.toString()).append('\t').append(r.updated.toString()).append('\t').append(r.seasons.toString()).append('\n') } }
    }

    fun update(p: Playlist, c: Content) {
        Repo.scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val f = file(p)
                val m = read(f)
                val baseline = m.isEmpty()          // טעינה ראשונה – רק רושמים, בלי לסמן הכל כחדש
                val now = System.currentTimeMillis()
                val byKey = HashMap<String, SeriesItem>(c.series.size * 2)
                for (s in c.series) {
                    if (s.key.startsWith("plex:")) continue
                    byKey[s.key] = s
                    val r = m[s.key]
                    if (r == null) m[s.key] = Rec(s.added, if (baseline) 0 else now, 0, 0)
                    else if (s.added > r.lastMod + 60_000 && r.lastMod > 0) { r.updated = now; r.lastMod = s.added }
                    else if (r.lastMod == 0L) r.lastMod = s.added
                }
                // מנקים סדרות שהוסרו מהספק
                m.keys.retainAll(byKey.keys)
                val fresh = m.filter { it.value.firstSeen > 0 && now - it.value.firstSeen < WINDOW }
                    .entries.sortedByDescending { it.value.firstSeen }.map { it.key }.take(60)
                val upd = m.filter { (_, r) -> r.updated > 0 && now - r.updated < WINDOW && (r.firstSeen == 0L || r.updated > r.firstSeen + 60_000) }
                    .entries.sortedByDescending { it.value.updated }.map { it.key }.take(60)
                newSeries = fresh
                updated = upd.map { it to "פרקים חדשים" }
                write(f, m)
                ready.intValue++
                // בדיקת מספר עונות ל-25 הראשונות – כדי לכתוב "עונה X חדשה"
                val labels = ArrayList<Pair<String, String>>()
                for (k in upd) {
                    val r = m[k] ?: continue
                    val s = byKey[k] ?: continue
                    var label = "פרקים חדשים"
                    if (labels.size < 25) runCatching {
                        val seasons = Repo.details(p, s).second.maxOfOrNull { it.season } ?: 0
                        if (r.seasons in 1 until seasons) label = "עונה $seasons חדשה"
                        if (seasons > 0) r.seasons = seasons
                    }
                    labels += k to label
                }
                updated = labels
                write(f, m)
                ready.intValue++
            }
        }
    }

    /** תווית "פרקים חדשים"/"עונה X חדשה" לסדרה, אם היא על המדף */
    fun tag(key: String): String? = updated.firstOrNull { it.first == key }?.second ?: if (key in newSeries) "חדש" else null
}
