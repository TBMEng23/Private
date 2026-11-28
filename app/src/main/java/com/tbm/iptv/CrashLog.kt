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

    /** גלאי תקיעות: אם ה-UI לא מגיב 4 שניות – שומר את מצב ה-thread הראשי לדוח (מוצג בפתיחה הבאה) */
    fun watchdog() {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        Thread({
            var reported = false
            while (true) {
                val done = java.util.concurrent.atomic.AtomicBoolean(false)
                main.post { done.set(true) }
                Thread.sleep(4000)
                // דוח קודם עוד לא נקרא – לא דורסים אותו (אחרת הדוח מתאר רק את מה שקרה הכי מאוחר)
                if (!done.get() && !reported && file?.exists() != true) {
                    reported = true
                    runCatching {
                        val tr = android.os.Looper.getMainLooper().thread.stackTrace
                        // קודם השורות מהקוד שלנו (כדי שיראו בדוח איזה מסך/פעולה תקעו), אחר כך כל השאר
                        val ours = tr.filter { it.className.startsWith("com.tbm") }.joinToString("\n") { "  > $it" }
                        val st = tr.joinToString("\n") { "  at $it" }
                        file?.writeText("ANR (UI frozen >4s) – TBM Player ${BuildConfigLite.VERSION}\n$ours\n---\n$st")
                    }
                }
                if (done.get()) reported = false
            }
        }, "tbm-watchdog").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    /** שגיאה שנבלעה ברקע (למשל בניית רשימת התוכן) – נשמרת לדוח רק אם אין דוח אחר */
    fun saveIfNone(e: Throwable, where: String) { if (file?.exists() != true) save(e, where) }

    fun read(): String? = file?.takeIf { it.exists() }?.readText()

    fun clear() { file?.delete() }
}

object BuildConfigLite { const val VERSION = "5.0.3" }

/** הגרסה האמיתית מתוך ה-APK (versionName + versionCode) */
fun appVersion(ctx: android.content.Context): String = runCatching {
    val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    @Suppress("DEPRECATION")
    val code = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    "${pi.versionName} (build $code)"
}.getOrDefault(BuildConfigLite.VERSION)
