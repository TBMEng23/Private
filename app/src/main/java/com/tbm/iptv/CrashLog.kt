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
                "TBM Player ${BuildConfigLite.VERSION} | Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT}) | " +
                    "${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "thread=$thread | maxMem=${rt.maxMemory() / 1_048_576}MB\n\n" + sw.toString().take(12000)
            )
        }
    }

    fun read(): String? = file?.takeIf { it.exists() }?.readText()

    fun clear() { file?.delete() }
}

object BuildConfigLite { const val VERSION = "2.3" }
