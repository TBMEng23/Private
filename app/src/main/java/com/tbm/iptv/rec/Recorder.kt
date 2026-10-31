package com.tbm.iptv.rec

import com.tbm.iptv.L

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tbm.iptv.data.Http
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

object Notif {
    const val CH_REC = "rec"
    const val CH_REMIND = "remind"

    fun init(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_REC, L("הקלטות"), NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CH_REMIND, L("תזכורות לתוכניות"), NotificationManager.IMPORTANCE_HIGH))
        }
    }
}

// ============================================================ שירות הקלטה
class RecordingService : Service() {
    data class RecInfo(val id: String, val title: String, val file: String, val endsAt: Long, val bytes: Long = 0, val url: String = "")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val NOTIF_ID = 4201

        val active = MutableStateFlow<Map<String, RecInfo>>(emptyMap())

        fun dir(ctx: Context): File =
            File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Recordings").apply { mkdirs() }

        /** זמן הסיום של כל הקלטה – ניתן לשינוי תוך כדי הקלטה */
        val endTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

        private fun safeName(s: String) = s.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60)

        /** מתחיל הקלטה ומחזיר את נתיב הקובץ */
        fun start(ctx: Context, title: String, url: String, durationMs: Long): String {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(dir(ctx), "${safeName(title)}__$stamp.ts")
            endTimes[file.name] = System.currentTimeMillis() + durationMs
            val i = Intent(ctx, RecordingService::class.java).setAction(ACTION_START)
                .putExtra("title", title).putExtra("url", url).putExtra("dur", durationMs)
                .putExtra("file", file.absolutePath)
            ContextCompat.startForegroundService(ctx, i)
            return file.absolutePath
        }

        /** שינוי זמן הסיום של הקלטה פעילה */
        fun setEnd(id: String, endAt: Long) {
            endTimes[id] = endAt
            active.value[id]?.let { active.value = active.value + (id to it.copy(endsAt = endAt)) }
        }

        fun endOf(id: String): Long = endTimes[id] ?: 0L

        fun isRecordingFile(path: String) = active.value.values.any { it.file == path }

        fun activeFor(url: String) = active.value.values.firstOrNull { it.url == url }

        fun stop(ctx: Context, id: String) {
            ctx.startService(Intent(ctx, RecordingService::class.java).setAction(ACTION_STOP).putExtra("id", id))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(text: String) = NotificationCompat.Builder(this, Notif.CH_REC)
        .setSmallIcon(android.R.drawable.presence_video_online)
        .setContentTitle(L("TBM Player מקליט"))
        .setContentText(text)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java).putExtra("open", "recordings"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notif.init(this)
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification(L("מכין הקלטה…")),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra("title") ?: L("הקלטה")
                val url = intent.getStringExtra("url") ?: return START_NOT_STICKY
                val dur = intent.getLongExtra("dur", 3_600_000L)
                val path = intent.getStringExtra("file")
                startJob(title, url, dur, path)
            }
            ACTION_STOP -> {
                val id = intent.getStringExtra("id")
                if (id != null) jobs.remove(id)?.cancel()
                active.value = active.value - (id ?: "")
                checkDone()
            }
            else -> checkDone()
        }
        return START_NOT_STICKY
    }

    private fun safe(s: String) = s.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60)

    private fun startJob(title: String, url: String, dur: Long, path: String?) {
        val file = if (path != null) File(path) else {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            File(dir(this), "${safe(title)}__$stamp.ts")
        }
        val id = file.name
        if (endTimes[id] == null) endTimes[id] = System.currentTimeMillis() + dur
        active.value = active.value + (id to RecInfo(id, title, file.absolutePath, endOf(id), url = url))
        updateNotif()
        jobs[id] = scope.launch {
            try {
                FileOutputStream(file, true).use { out ->
                    var tries = 0
                    while (System.currentTimeMillis() < endOf(id) && tries < 20) {
                        try {
                            if (url.contains(".m3u8", true)) recordHls(url, out, id)
                            else recordDirect(url, out, id)
                            if (!url.contains(".m3u8", true)) tries++ else break
                        } catch (e: Exception) {
                            coroutineContext.ensureActive()
                            tries++
                            delay(3000)
                        }
                    }
                }
            } finally {
                jobs.remove(id)
                endTimes.remove(id)
                active.value = active.value - id
                if (file.exists() && file.length() < 1024) file.delete()
                scope.launch(Dispatchers.Main) { checkDone() }
            }
        }
    }

    private fun bump(id: String, n: Int) {
        val cur = active.value[id] ?: return
        active.value = active.value + (id to cur.copy(bytes = cur.bytes + n))
    }

    private suspend fun recordDirect(url: String, out: FileOutputStream, id: String) {
        val req = Request.Builder().url(url).header("User-Agent", Prefs.userAgent).build()
        val call = Http.client.newCall(req)
        call.execute().use { r ->
            if (!r.isSuccessful) throw Exception("HTTP ${r.code}")
            val input = r.body?.byteStream() ?: throw Exception("empty")
            val buf = ByteArray(64 * 1024)
            var lastBump = 0
            while (System.currentTimeMillis() < endOf(id)) {
                coroutineContext.ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                out.flush()
                lastBump += n
                if (lastBump > 1_000_000) { bump(id, lastBump); lastBump = 0 }
            }
            bump(id, lastBump)
        }
    }

    private suspend fun recordHls(url: String, out: FileOutputStream, id: String) {
        val hdr = mapOf("User-Agent" to Prefs.userAgent)
        var media = url
        var text = Http.get(media, hdr)
        if (text.contains("#EXT-X-STREAM-INF")) {
            val lines = text.lines()
            var best: String? = null; var bw = -1L
            lines.forEachIndexed { i, l ->
                if (l.startsWith("#EXT-X-STREAM-INF")) {
                    val b = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                    val u = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    if (u != null && b > bw) { bw = b; best = u.trim() }
                }
            }
            val chosen = best ?: throw Exception("no variant")
            media = URL(URL(url), chosen).toString()
            text = Http.get(media, hdr)
        }
        val seen = LinkedHashSet<String>()
        while (System.currentTimeMillis() < endOf(id)) {
            coroutineContext.ensureActive()
            var target = 6
            text.lines().forEach { l ->
                if (l.startsWith("#EXT-X-TARGETDURATION:")) target = l.substringAfter(':').trim().toIntOrNull() ?: 6
            }
            val segs = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.trim() }
            for (s in segs) {
                if (s in seen) continue
                coroutineContext.ensureActive()
                val bytes = Http.getBytes(URL(URL(media), s).toString(), hdr)
                out.write(bytes)
                bump(id, bytes.size)
                seen += s
                if (seen.size > 500) seen.remove(seen.first())
            }
            if (text.contains("#EXT-X-ENDLIST")) break
            delay((target * 1000L / 2).coerceAtLeast(1000))
            text = Http.get(media, hdr)
        }
    }

    private fun updateNotif() {
        val n = active.value.size
        val txt = if (n == 1) active.value.values.first().title else L("{0} הקלטות פעילות", n)
        runCatching { NotificationManagerCompat.from(this).notify(NOTIF_ID, notification(txt)) }
    }

    private fun checkDone() {
        if (jobs.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else updateNotif()
    }

    override fun onDestroy() {
        jobs.values.forEach { it.cancel() }
        super.onDestroy()
    }
}

// ============================================================ תזמון הקלטות ותזכורות
data class Sched(
    val id: String, val type: String, val title: String, val url: String,
    val start: Long, val end: Long, val epgId: String? = null, val channel: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("type", type).put("title", title).put("url", url)
        .put("start", start).put("end", end).put("epgId", epgId ?: "").put("channel", channel)

    companion object {
        fun fromJson(o: JSONObject) = Sched(
            o.optString("id"), o.optString("type"), o.optString("title"), o.optString("url"),
            o.optLong("start"), o.optLong("end"), o.optString("epgId").ifBlank { null }, o.optString("channel"),
        )
    }
}

object Scheduler {
    fun list(): List<Sched> = runCatching {
        val a = JSONArray(Prefs.str("schedules", "[]"))
        (0 until a.length()).map { Sched.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList()).sortedBy { it.start }

    private fun save(l: List<Sched>) = Prefs.setStr("schedules", JSONArray().apply { l.forEach { put(it.toJson()) } }.toString())

    fun exists(type: String, url: String, start: Long) = list().any { it.type == type && it.url == url && it.start == start }

    fun add(ctx: Context, s: Sched) {
        save(list().filter { it.id != s.id } + s)
        setAlarm(ctx, s)
    }

    fun cancel(ctx: Context, id: String) {
        save(list().filter { it.id != id })
        alarmManager(ctx).cancel(pending(ctx, id))
    }

    fun take(id: String): Sched? {
        val l = list(); val s = l.firstOrNull { it.id == id }
        save(l.filter { it.id != id })
        return s
    }

    private fun alarmManager(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pending(ctx: Context, id: String) = PendingIntent.getBroadcast(
        ctx, id.hashCode(), Intent(ctx, AlarmReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun setAlarm(ctx: Context, s: Sched) {
        val am = alarmManager(ctx)
        val at = (if (s.type == "rec") s.start - 30_000 else s.start - 60_000).coerceAtLeast(System.currentTimeMillis() + 1000)
        val pi = pending(ctx, s.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        val l = list().filter { it.end > now }
        save(l)
        l.forEach { setAlarm(ctx, it) }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val s = Scheduler.take(id) ?: return
        val now = System.currentTimeMillis()
        if (s.type == "rec") {
            if (s.end > now) RecordingService.start(ctx, s.title, s.url, s.end - now + 120_000)
        } else {
            Notif.init(ctx)
            val open = Intent(ctx, MainActivity::class.java)
                .putExtra("play_url", s.url).putExtra("play_title", s.channel)
                .putExtra("play_epg", s.epgId ?: "")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(ctx, id.hashCode(), open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, Notif.CH_REMIND)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(L("מתחיל עכשיו: {0}", s.title))
                .setContentText(L("בערוץ {0} – לחץ לצפייה", s.channel))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            runCatching { NotificationManagerCompat.from(ctx).notify(id.hashCode(), n) }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Scheduler.rescheduleAll(ctx)
    }
}
