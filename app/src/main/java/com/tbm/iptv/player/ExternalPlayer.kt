package com.tbm.iptv.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Parcelable
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tbm.iptv.L
import com.tbm.iptv.data.Kind
import com.tbm.iptv.data.PlayItem
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.subs.SubtitleFile
import com.tbm.iptv.subs.SubtitleManager
import java.io.File

/** פתיחה בנגן חיצוני (VLC / MX / Just Player / mpv…) כולל כתוביות עבריות ומיקום המשך */
object ExternalPlayer {
    data class App(val pkg: String, val name: String)

    /** נגן חיצוני לסוג תוכן (ברירת מחדל: ההגדרה הישנה הגלובלית) */
    fun useFor(kind: Kind): Boolean = Prefs.bool("ext_${kind.name}", Prefs.externalPlayer)
    fun setFor(kind: Kind, v: Boolean) = Prefs.setBool("ext_${kind.name}", v)

    /** חבילת הנגן המועדף ("" = לשאול כל פעם) */
    var pkg: String
        get() = Prefs.str("ext_pkg", "")
        set(v) = Prefs.setStr("ext_pkg", v)

    /** נגני וידאו מותקנים */
    fun installed(ctx: Context): List<App> {
        val pm = ctx.packageManager
        val probe = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("http://example.com/v.mp4"), "video/*")
        val list = if (android.os.Build.VERSION.SDK_INT >= 33)
            pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        else @Suppress("DEPRECATION") pm.queryIntentActivities(probe, PackageManager.MATCH_ALL)
        return list.map { App(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }.distinctBy { it.pkg }.sortedBy { it.name.lowercase() }
    }

    fun appName(ctx: Context, p: String): String =
        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(p, 0)).toString() }.getOrDefault(p)

    /** כותב את הכתובית לקובץ ומחזיר Uri שנגן אחר יכול לקרוא */
    private fun subUri(ctx: Context, sf: SubtitleFile): Uri? = runCatching {
        val dir = File(ctx.cacheDir, "extsubs").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "subtitle.${sf.lang}.${sf.ext}")
        f.writeText(sf.text)
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }.getOrNull()

    /**
     * פותח פריט בנגן החיצוני.
     * @param sub כתובית לצרף (אם null – מנסה מהמטמון של הפריט)
     * @param posMs מיקום התחלה (אם null – מהמשך צפייה שמור)
     */
    fun open(ctx: Context, item: PlayItem, sub: SubtitleFile? = null, posMs: Long? = null): Boolean {
        val s = sub ?: if (item.kind != Kind.LIVE) SubtitleManager.loadCache(item.key)?.first else null
        val su = s?.let { subUri(ctx, it) }
        val pos = posMs ?: (if (item.kind != Kind.LIVE) Prefs.resume(item.key)?.first else null) ?: 0L
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(item.url), "video/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra("title", item.title)
        // VLC + MX Player / Just Player / mpv
        i.putExtra("position", pos.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
        i.putExtra("from_start", pos <= 0)
        if (su != null) i.putExtra("subtitles_location", su.toString())
        if (su != null) {
            i.putExtra("subs", arrayOf<Parcelable>(su))
            i.putExtra("subs.enable", arrayOf<Parcelable>(su))
            i.putExtra("subs.name", arrayOf(if (s.lang == "he") "עברית" else s.lang))
            i.clipData = android.content.ClipData.newRawUri("sub", su)
        }
        val p = pkg
        if (p.isNotBlank()) {
            i.setPackage(p)
            if (su != null) ctx.grantUriPermission(p, su, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else if (su != null) {
            installed(ctx).forEach { ctx.grantUriPermission(it.pkg, su, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        return try {
            ctx.startActivity(if (p.isBlank()) Intent.createChooser(i, L("פתח באמצעות")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else i)
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, L("הנגן החיצוני לא נמצא – משתמש בנגן המובנה"), Toast.LENGTH_SHORT).show()
            false
        }
    }
}
