package com.tbm.iptv

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** שומר את סיבת הקריסה לקובץ, ומציג אותה בפתיחה הבאה כדי שאפשר לשלוח אותה */
object CrashLog {
    private var file: File? = null

    fun install(ctx: Context) {
        file = File(ctx.filesDir, "last_crash.txt")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            save(e, t.name)
            prev?.uncaughtException(t, e)
        }
    }

    fun save(e: Throwable, thread: String = Thread.currentThread().name) {
        runCatching {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val rt = Runtime.getRuntime()
            file?.writeText(
                "TBM Player ${appVersion(App.instance)} | Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT}) | " +
                    "${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "thread=$thread | maxMem=${rt.maxMemory() / 1_048_576}MB\n\n" + sw.toString().take(12000)
            )
        }
    }

    fun read(): String? = file?.takeIf { it.exists() }?.readText()

    fun clear() { file?.delete() }
}

object BuildConfigLite { const val VERSION = "3.9" }

/** הגרסה האמיתית מתוך ה-APK (versionName + versionCode) */
fun appVersion(ctx: android.content.Context): String = runCatching {
    val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    @Suppress("DEPRECATION")
    val code = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    "${pi.versionName} (build $code)"
}.getOrDefault(BuildConfigLite.VERSION)
