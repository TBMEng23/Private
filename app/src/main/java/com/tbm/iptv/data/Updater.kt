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

    // קישורי השיתוף הקבועים של תיקיית העדכונים ב-OneDrive (אפשר לשנות בהגדרות)
    private const val DEF_VER = "https://tbmeng-my.sharepoint.com/:t:/g/personal/tamir_tbmeng_co_il/IQByidkxOdy8RIA3NX3DA9REAVa_TePgB7Z8ULnrAhKvOZw?e=mG2Pj3"
    private const val DEF_APK = "https://tbmeng-my.sharepoint.com/:u:/g/personal/tamir_tbmeng_co_il/IQCkhZFQKUqGR7iBUf0A154VAfVXUxmklB5aNFxnPaeshl8?e=3es29a"

    var verUrl: String
        get() = Prefs.str("upd_ver_url").ifBlank { DEF_VER }
        set(v) = Prefs.setStr("upd_ver_url", v.trim())
    var apkUrl: String
        get() = Prefs.str("upd_apk_url").ifBlank { DEF_APK }
        set(v) = Prefs.setStr("upd_apk_url", v.trim())

    val configured get() = verUrl.isNotBlank() && apkUrl.isNotBlank()

    fun currentCode(ctx: Context): Long = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    }.getOrDefault(0L)

    // SharePoint מעביר דרך הפניה שמגדירה עוגייה (FedAuth) – בלי שמירת עוגיות מתקבל 403
    private val net by lazy {
        val store = java.util.concurrent.ConcurrentHashMap<String, MutableList<okhttp3.Cookie>>()
        Http.client.newBuilder()
            .cookieJar(object : okhttp3.CookieJar {
                override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                    val l = store.getOrPut(url.host) { mutableListOf() }
                    synchronized(l) { cookies.forEach { c -> l.removeAll { it.name == c.name }; l += c } }
                }
                override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> =
                    store[url.host]?.let { l -> synchronized(l) { l.toList() } } ?: emptyList()
            })
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
    private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** כל צורות ההורדה האפשריות לקישור (ננסה לפי הסדר) */
    private fun candidates(url: String): List<String> {
        val u = url.trim()
        val out = mutableListOf(direct(u))
        // https://x-my.sharepoint.com/:t:/g/personal/USER/TOKEN?e=.. → .../personal/USER/_layouts/15/download.aspx?share=TOKEN
        Regex("""^(https://[^/]+)/:\w:/[gr]/(personal/[^/]+)/([^/?]+)""").find(u)?.let { m ->
            val (host, site, token) = m.destructured
            out += "$host/$site/_layouts/15/download.aspx?share=$token"
        }
        out += u
        return out.distinct()
    }

    private fun open(url: String): okhttp3.Response {
        var last: Exception? = null
        for (c in candidates(url)) {
            try {
                val r = net.newCall(okhttp3.Request.Builder().url(c).header("User-Agent", BROWSER_UA).build()).execute()
                val type = r.header("Content-Type") ?: ""
                if (r.isSuccessful && !type.contains("text/html")) return r
                r.close(); last = IOException("HTTP ${r.code}")
            } catch (e: Exception) { last = e }
        }
        throw last ?: IOException(L("ההורדה נכשלה"))
    }

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
                val txt = open(verUrl).use { it.body?.string() ?: "" }.trim()
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
        check(ctx, false) { info ->
            if (info == null) return@check
            // גרסה שכבר נכשלה – לא מנסים שוב לבד (מונע לולאת סגירות)
            if (Prefs.str("upd_fail").toLongOrNull() == info.code) { needsTap = true; return@check }
            // הכל אוטומטי: מוריד, מוודא שהקובץ חדש, מתקין (האפליקציה נסגרת) ונפתחת מחדש בגרסה החדשה.
            // אם אנדרואיד דורש אישור (פעם ראשונה / מכשיר ישן) – מסך האישור נפתח לבד, לחיצה אחת.
            status = L("מעדכן לגרסה {0}…", info.name)
            downloadAndInstall(ctx, silent = true)
        }
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
                open(apkUrl).use { r ->
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
                // בודקים שהקובץ שהורד באמת חדש יותר (OneDrive לפעמים עוד לא סנכרן את ה-APK החדש)
                val pi = app.packageManager.getPackageArchiveInfo(f.path, 0)
                @Suppress("DEPRECATION")
                val fileCode = pi?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() } ?: 0L
                if (fileCode <= currentCode(app)) {
                    Prefs.setStrQuiet("upd_fail", (available?.code ?: 0L).toString())
                    throw IOException(L("קובץ העדכון עדיין לא התעדכן בענן – ננסה שוב מאוחר יותר"))
                }
                status = L("מתקין…")
                Prefs.setStrQuiet("upd_fail", (available?.code ?: 0L).toString())   // יימחק אם ההתקנה תצליח (גרסה חדשה)
                try { install(app, f) } catch (e: Exception) {
                    if (!systemInstall(app)) throw e
                }
            } catch (e: Exception) {
                status = L("העדכון נכשל: {0}", e.message ?: e.javaClass.simpleName)
                if (silent) needsTap = true
            } finally { busy = false; progress = -1f }
        }
    }

    private fun apkFile(ctx: Context) = File(File(ctx.cacheDir, "updates"), "TBMPlayer-update.apk")

    /** פתיחת מסך ההתקנה הרגיל של המערכת לקובץ שהורד. מחזיר false אם אין קובץ */
    fun systemInstall(ctx: Context): Boolean {
        val f = apkFile(ctx)
        if (!f.exists() || f.length() == 0L) return false
        return runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            status = L("אשר את ההתקנה במסך שנפתח")
            true
        }.getOrDefault(false)
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
            else -> {
                // HyperOS ואחרים חוסמים התקנה דרך session – עוברים למסך ההתקנה הרגיל של המערכת
                val msg = intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                if (!Updater.systemInstall(ctx)) Updater.status = L("העדכון נכשל: {0}", msg)
            }
        }
    }
}

/** אחרי שהאפליקציה עודכנה – פותחים אותה מחדש (ואם אנדרואיד חוסם פתיחה מהרקע – התראה ללחיצה) */
class UpdatedReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) ?: return
        runCatching { ctx.startActivity(launch) }
        // גיבוי: התראה (נסגרת כשהאפליקציה נפתחת)
        runCatching {
            val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26)
                nm.createNotificationChannel(android.app.NotificationChannel(UPDATED_CH, L("עדכוני אפליקציה"), android.app.NotificationManager.IMPORTANCE_HIGH))
            val pi = android.app.PendingIntent.getActivity(ctx, 7702, launch,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            val n = androidx.core.app.NotificationCompat.Builder(ctx, UPDATED_CH)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(L("TBM Player עודכן – לחץ לפתיחה"))
                .setContentIntent(pi).setAutoCancel(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH).build()
            nm.notify(UPDATED_ID, n)
        }
    }

    companion object {
        const val UPDATED_CH = "app_updated"
        const val UPDATED_ID = 7702
        fun clear(ctx: Context) = runCatching {
            ctx.getSystemService(android.app.NotificationManager::class.java).cancel(UPDATED_ID)
        }
    }
}
