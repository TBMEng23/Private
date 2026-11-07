package com.tbm.iptv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tbm.iptv.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** עדכון גרסה מתוך האפליקציה – קובץ version.txt ו-APK בקישורי שיתוף (OneDrive / כל קישור ישיר) */
object Updater {
    data class Info(val code: Long, val name: String)

    var available by mutableStateOf<Info?>(null)
    var status by mutableStateOf("")
    var progress by mutableStateOf(-1f)      // -1 = לא מוריד
    var busy by mutableStateOf(false)
    var dismissed by mutableStateOf(false)   // "אחר כך" – עד הפתיחה הבאה
    var needsTap by mutableStateOf(false)    // עדכון אוטומטי לא הצליח לבד – מציגים חלון

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var verUrl: String
        get() = Prefs.str("upd_ver_url")
        set(v) = Prefs.setStr("upd_ver_url", v.trim())
    var apkUrl: String
        get() = Prefs.str("upd_apk_url")
        set(v) = Prefs.setStr("upd_apk_url", v.trim())

    val configured get() = verUrl.isNotBlank() && apkUrl.isNotBlank()

    fun currentCode(ctx: Context): Long = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    }.getOrDefault(0L)

    /** הופך קישור שיתוף לקישור הורדה ישיר */
    fun direct(url: String): String {
        val u = url.trim()
        return when {
            u.contains("1drv.ms") || u.contains("onedrive.live.com") -> {
                val b64 = Base64.encodeToString(u.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
                "https://api.onedrive.com/v1.0/shares/u!$b64/root/content"
            }
            u.contains("sharepoint.com") || u.contains("dropbox.com") ->
                if (Regex("[?&](download|dl)=").containsMatchIn(u)) u.replace("dl=0", "dl=1")
                else u + (if ('?' in u) "&" else "?") + (if (u.contains("dropbox")) "dl=1" else "download=1")
            u.contains("drive.google.com") -> Regex("/d/([^/]+)").find(u)?.let {
                "https://drive.google.com/uc?export=download&id=${it.groupValues[1]}"
            } ?: u
            else -> u
        }
    }

    fun check(ctx: Context, manual: Boolean, then: ((Info?) -> Unit)? = null) {
        if (!configured) { if (manual) status = L("קודם הדבק את שני הקישורים"); return }
        if (busy) return
        busy = true
        if (manual) status = L("בודק…")
        scope.launch {
            try {
                val txt = Http.get(direct(verUrl)).trim()
                if (txt.startsWith("<")) throw IOException(L("הקישור לקובץ הגרסה אינו קישור הורדה ישיר"))
                val lines = txt.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val code = lines.firstNotNullOfOrNull { l -> l.substringAfter("=").trim().toLongOrNull() }
                    ?: throw IOException(L("קובץ הגרסה לא תקין"))
                val name = lines.firstOrNull { it.contains('.') }?.substringAfter("=")?.trim() ?: code.toString()
                if (code > currentCode(ctx)) {
                    available = Info(code, name); dismissed = false
                    status = L("גרסה {0} זמינה", name)
                } else {
                    available = null
                    if (manual) status = L("יש לך את הגרסה האחרונה")
                }
                busy = false
                then?.invoke(available)
            } catch (e: Exception) {
                if (manual) status = L("בדיקת עדכון נכשלה: {0}", e.message ?: e.javaClass.simpleName)
            } finally { busy = false }
        }
    }

    var auto: Boolean
        get() = Prefs.bool("upd_auto", true)
        set(v) = Prefs.setBool("upd_auto", v)

    /** בדיקה אוטומטית בפתיחה: אם יש גרסה חדשה – מוריד ומתקין לבד (באנדרואיד 12+ בלי שאלות) */
    fun autoCheck(ctx: Context) {
        if (!configured || !auto) return
        val last = Prefs.str("upd_last").toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last < 3 * 3_600_000L) return
        Prefs.setStrQuiet("upd_last", System.currentTimeMillis().toString())
        check(ctx, false) { info -> if (info != null) downloadAndInstall(ctx, silent = true) }
    }

    /** מוריד ומתקין דרך PackageInstaller – עדכון עצמי בלי אישור באנדרואיד 12+ */
    fun downloadAndInstall(ctx: Context, silent: Boolean = false) {
        val app = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            if (silent) { needsTap = true; status = L("גרסה {0} זמינה – לחץ עדכן", available?.name ?: ""); return }
            status = L("אשר התקנה ממקור זה ואז לחץ שוב \"עדכן\"")
            runCatching {
                app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        if (busy) return
        busy = true; progress = 0f; status = L("מוריד עדכון…")
        scope.launch {
            try {
                val dir = File(app.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val f = File(dir, "TBMPlayer-update.apk")
                Http.client.newCall(okhttp3.Request.Builder().url(direct(apkUrl)).header("User-Agent", Http.UA).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    val body = r.body ?: throw IOException(L("תגובה ריקה"))
                    val total = body.contentLength()
                    body.byteStream().use { inp ->
                        f.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024); var done = 0L
                            while (true) {
                                val n = inp.read(buf); if (n < 0) break
                                out.write(buf, 0, n); done += n
                                if (total > 0) progress = done.toFloat() / total
                            }
                        }
                    }
                }
                // בדיקה שזה באמת APK (ZIP) ולא דף HTML
                val head = f.inputStream().use { val b = ByteArray(2); it.read(b); b }
                if (head[0] != 'P'.code.toByte() || head[1] != 'K'.code.toByte())
                    throw IOException(L("הקישור לקובץ ה-APK אינו קישור הורדה ישיר"))
                status = L("מתקין…")
                install(app, f)
            } catch (e: Exception) {
                status = L("העדכון נכשל: {0}", e.message ?: e.javaClass.simpleName)
                if (silent) needsTap = true
            } finally { busy = false; progress = -1f }
        }
    }

    private fun install(ctx: Context, f: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        if (Build.VERSION.SDK_INT >= 34) runCatching { params.setRequestUpdateOwnership(true) }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("base.apk", 0, f.length()).use { out -> f.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0)
            val pin = android.app.PendingIntent.getBroadcast(ctx, 7701,
                Intent(ctx, UpdateReceiver::class.java).setPackage(ctx.packageName), flags)
            s.commit(pin.intentSender)
        }
    }
}

/** תוצאת ההתקנה: אם אנדרואיד דורש אישור (גרסאות ישנות / פעם ראשונה) – פותח את מסך האישור */
class UpdateReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -999)) {
            android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            android.content.pm.PackageInstaller.STATUS_SUCCESS -> Updater.status = ""
            else -> Updater.status = L("העדכון נכשל: {0}", intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "")
        }
    }
}
