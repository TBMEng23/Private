package com.tbm.iptv

import android.util.Log
import com.tbm.iptv.data.Prefs
import org.json.JSONObject

/**
 * כלי בדיקה לאמולטור בלבד – פעיל רק כשמגדירים מאפייני מערכת debug.tbm.* דרך adb
 * (במכשיר רגיל אי אפשר להגדיר אותם בלי חיבור מחשב, ולכן אין לזה השפעה על משתמשים).
 *  debug.tbm.seed=<url>  – טוען הגדרות התחלה (רשימה, פרופילים) מכתובת, לפני שהאפליקציה עולה
 *  debug.tbm.perf=1      – רושם ללוג (TBMPERF) כל תקיעה של המסך הראשי מעל 250ms עם המקום בקוד
 */
object Perf {
    private fun prop(name: String): String = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
    }.getOrDefault("")

    val testMode: Boolean by lazy { prop("debug.tbm.perf") == "1" || prop("debug.tbm.seed").isNotBlank() }

    fun seedIfRequested() {
        val url = prop("debug.tbm.seed").ifBlank { return }
        runCatching {
            val t = Thread {
                runCatching {
                    val txt = java.net.URL(url).openStream().use { it.readBytes().toString(Charsets.UTF_8) }
                    val o = JSONObject(txt)
                    o.keys().forEach { k ->
                        when (val v = o.get(k)) {
                            is Boolean -> Prefs.setBool(k, v)
                            else -> Prefs.setStrQuiet(k, v.toString())
                        }
                    }
                    Log.w("TBMPERF", "seed applied: ${o.length()} keys")
                }.onFailure { Log.w("TBMPERF", "seed failed: $it") }
            }
            t.start(); t.join(15_000)
        }
    }

    fun startMonitor() {
        if (prop("debug.tbm.perf") != "1") return
        val thr = prop("debug.tbm.perfms").toLongOrNull() ?: 250L
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        Thread({
            while (true) {
                val done = java.util.concurrent.atomic.AtomicBoolean(false)
                val t0 = System.nanoTime()
                main.post { done.set(true) }
                Thread.sleep(thr)
                if (!done.get()) {
                    val st = android.os.Looper.getMainLooper().thread.stackTrace
                    while (!done.get()) Thread.sleep(20)
                    val ms = (System.nanoTime() - t0) / 1_000_000
                    val ours = st.filter { it.className.startsWith("com.tbm") }.take(12).joinToString(" <- ") {
                        "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
                    }
                    val top = st.take(4).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}" }
                    Log.w("TBMPERF", "BLOCK ${ms}ms | ours: ${ours.ifBlank { "-" }} | top: $top")
                } else Thread.sleep(50)
            }
        }, "tbm-perf").apply { isDaemon = true }.start()
    }
}
