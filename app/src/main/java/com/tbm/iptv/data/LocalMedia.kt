package com.tbm.iptv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray

/** קובץ וידאו במכשיר (מתיקייה שנבחרה או מבחירת קובץ) */
data class LocalVideo(
    val uri: Uri, val name: String, val size: Long, val folder: String,
    val show: String?, val season: Int?, val episode: Int?, val subtitle: Uri?,
) {
    val key get() = "file:" + uri.toString()
    val title get() = name.substringBeforeLast('.')
}

object LocalMedia {
    private val videoExt = setOf("mp4", "mkv", "avi", "mov", "m4v", "ts", "m2ts", "webm", "wmv", "flv", "mpg", "mpeg", "3gp", "vob")
    private val subExt = setOf("srt", "vtt", "ass", "ssa")
    private val epRx = Regex("(?i)^(.*?)[ ._\\-\\[(]*s(\\d{1,2})[ ._\\-]?e(\\d{1,3})")
    private val epRx2 = Regex("(?i)^(.*?)[ ._\\-]+(\\d{1,2})x(\\d{2,3})")

    /** כתוביות מקומיות שנמצאו ליד הסרט (מפתח הנגן → קובץ כתוביות) */
    val sidecars = java.util.concurrent.ConcurrentHashMap<String, Uri>()

    // ---- תיקיות שמורות (במכשיר הזה בלבד) ----
    fun folders(): List<Uri> = runCatching {
        val a = JSONArray(Prefs.str("local_dirs", "[]")); (0 until a.length()).map { Uri.parse(a.getString(it)) }
    }.getOrDefault(emptyList())

    fun addFolder(ctx: Context, uri: Uri) {
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val l = (folders().map { it.toString() } + uri.toString()).distinct()
        Prefs.setStr("local_dirs", JSONArray(l).toString())
    }

    fun removeFolder(ctx: Context, uri: Uri) {
        runCatching { ctx.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        Prefs.setStr("local_dirs", JSONArray(folders().map { it.toString() }.filter { it != uri.toString() }).toString())
    }

    fun folderName(uri: Uri): String = runCatching {
        DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').ifBlank { DocumentsContract.getTreeDocumentId(uri) }
    }.getOrDefault(uri.lastPathSegment ?: "?")

    /** סריקת תיקייה (כולל תת-תיקיות, עד 4 רמות) */
    fun scan(ctx: Context, tree: Uri): List<LocalVideo> {
        val out = ArrayList<LocalVideo>()
        val root = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return out
        fun walk(docId: String, folder: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val vids = ArrayList<Triple<String, String, Long>>()   // id, name, size
            val subs = HashMap<String, String>()                    // base name → id
            runCatching {
                ctx.contentResolver.query(children, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
                ), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2) ?: ""
                        val size = if (c.isNull(3)) 0L else c.getLong(3)
                        val ext = name.substringAfterLast('.', "").lowercase()
                        when {
                            mime == DocumentsContract.Document.MIME_TYPE_DIR -> if (depth < 4) walk(id, name, depth + 1)
                            ext in videoExt || mime.startsWith("video/") -> vids += Triple(id, name, size)
                            ext in subExt -> subs[name.substringBeforeLast('.').lowercase()] = id
                        }
                    }
                }
            }
            for ((id, name, size) in vids) {
                val base = name.substringBeforeLast('.').lowercase()
                // כתובית באותו שם (גם "Movie.he.srt" / "Movie.heb.srt")
                val subId = subs[base] ?: subs.entries.firstOrNull { it.key.startsWith("$base.") }?.value
                val m = epRx.find(name) ?: epRx2.find(name)
                val show = m?.groupValues?.get(1)?.replace(Regex("[._]"), " ")?.trim()?.ifBlank { folder }
                out += LocalVideo(
                    DocumentsContract.buildDocumentUriUsingTree(tree, id), name, size, folder,
                    show, m?.groupValues?.get(2)?.toIntOrNull(), m?.groupValues?.get(3)?.toIntOrNull(),
                    subId?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it) },
                )
            }
        }
        walk(root, folderName(tree), 0)
        return out
    }

    /** מהקובץ לפריט לנגן (כולל כתובית מקומית אם יש) */
    fun toPlay(v: LocalVideo): PlayItem {
        v.subtitle?.let { sidecars[v.key] = it }
        return PlayItem(v.key, v.title, v.uri.toString(), if (v.show != null) Kind.SERIES else Kind.MOVIE,
            seriesName = v.show, season = v.season, episode = v.episode, isLocal = true)
    }

    /** קובץ בודד שנבחר */
    fun fromPicked(ctx: Context, uri: Uri): PlayItem {
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "video"
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
        }
        val m = epRx.find(name) ?: epRx2.find(name)
        val show = m?.groupValues?.get(1)?.replace(Regex("[._]"), " ")?.trim()?.ifBlank { null }
        return PlayItem("file:$uri", name.substringBeforeLast('.'), uri.toString(), if (show != null) Kind.SERIES else Kind.MOVIE,
            seriesName = show, season = m?.groupValues?.get(2)?.toIntOrNull(), episode = m?.groupValues?.get(3)?.toIntOrNull(), isLocal = true)
    }

    /** הקלטה (לא קובץ מדיה) – בלי כתוביות/המשך צפייה */
    fun isRecording(p: PlayItem) = p.isLocal && !p.key.startsWith("file:")
}
