package com.tbm.iptv.data

import com.tbm.iptv.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

sealed interface LoadState {
    data object Idle : LoadState
    data class Loading(val msg: String) : LoadState
    data class Ready(val content: Content) : LoadState
    data class Error(val msg: String) : LoadState
}

object Repo {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val state = MutableStateFlow<LoadState>(LoadState.Idle)

    /** תור הניגון הנוכחי – ערוצים בקבוצה / פרקי סדרה */
    @Volatile var queue: List<PlayItem> = emptyList()
    @Volatile var queueIndex = 0

    private val seriesCache = ConcurrentHashMap<String, List<Episode>>()

    val content: Content? get() = (state.value as? LoadState.Ready)?.content

    fun loadAsync(p: Playlist, force: Boolean = false) {
        scope.launch { load(p, force) }
    }

    suspend fun load(p: Playlist, force: Boolean = false) = withContext(Dispatchers.IO) {
        state.value = LoadState.Loading("טוען את \"${p.name}\"…")
        try {
            val c = if (p.isXtream) Xtream(p).load(force) else loadM3u(p, force)
            seriesCache.clear()
            state.value = LoadState.Ready(c)
        } catch (e: Exception) {
            state.value = LoadState.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun loadM3u(p: Playlist, force: Boolean): Content {
        val f = if (p.url.startsWith("file://")) {
            File(p.url.removePrefix("file://"))
        } else {
            val cf = cacheFile("m3u_${p.id}.m3u")
            if (force || !fresh(cf)) {
                try {
                    Http.download(p.url, cf)
                } catch (e: Exception) {
                    if (!cf.exists()) throw e
                }
            }
            cf
        }
        if (!f.exists()) throw Exception("קובץ הרשימה לא נמצא")
        val c = f.inputStream().use { M3uParser.parse(it, p.id) }
        if (c.live.isEmpty() && c.movies.isEmpty() && c.series.isEmpty())
            throw Exception("הרשימה ריקה או שאינה בפורמט M3U")
        return c
    }

    fun cacheFile(name: String) = File(App.instance.filesDir, name)

    private fun fresh(f: File, hours: Int = 12) =
        f.exists() && System.currentTimeMillis() - f.lastModified() < hours * 3_600_000L

    fun fetchCached(url: String, name: String, force: Boolean): String {
        val f = cacheFile(name)
        if (!force && fresh(f)) return f.readText()
        return try {
            Http.get(url).also { f.writeText(it) }
        } catch (e: Exception) {
            if (f.exists()) f.readText() else throw e
        }
    }

    suspend fun episodes(p: Playlist, s: SeriesItem): List<Episode> = withContext(Dispatchers.IO) {
        s.episodesM3u ?: seriesCache[s.key] ?: run {
            val id = s.seriesId ?: return@run emptyList()
            Xtream(p).seriesEpisodes(id).also { seriesCache[s.key] = it }
        }
    }

    fun deletePlaylistCache(p: Playlist) {
        App.instance.filesDir.listFiles()?.forEach { f ->
            if (f.name.startsWith("m3u_${p.id}") || f.name.startsWith("x_${p.id}_") ||
                (p.url.startsWith("file://") && f.absolutePath == p.url.removePrefix("file://"))
            ) f.delete()
        }
    }
}
