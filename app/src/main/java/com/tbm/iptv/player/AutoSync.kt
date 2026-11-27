package com.tbm.iptv.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10

/**
 * סנכרון כתוביות לפי הקול: "מקשיבים" לפס הקול (זיהוי דיבור לפי אנרגיה בתחום הדיבור),
 * ומחפשים את ההזזה (וגם תיקון קצב 23.976/24/25) שבה זמני הכתוביות הכי חופפים לרגעי הדיבור.
 */
@UnstableApi
object AudioTap : TeeAudioProcessor.AudioBufferSink {
    const val BIN_MS = 100
    @Volatile private var armedBase = -1L      // מיקום המדיה בתחילת הזרם הבא (אחרי seek/טעינה)
    @Volatile var collecting = false; private set
    @Volatile var aborted = false; private set
    @Volatile var noPcm = false; private set
    private var baseMs = 0L
    private var sr = 0; private var ch = 0; private var enc = C.ENCODING_INVALID
    private var frames = 0L
    private var binAcc = 0.0; private var binCount = 0
    private val bins = ArrayList<Float>()      // אנרגיה (dB) לכל 100ms
    // מסננים פשוטים: high-pass ~250Hz, low-pass ~3.5kHz – תחום הדיבור
    private var hpPrevIn = 0.0; private var hpPrevOut = 0.0; private var lp = 0.0
    private var hpA = 0.0; private var lpA = 0.0

    /** מתחילים לאסוף מהזרם הבא שמתחיל במיקום [posMs] */
    fun arm(posMs: Long) = synchronized(this) {
        armedBase = posMs; collecting = false; aborted = false; noPcm = false; bins.clear()
    }
    fun stop() = synchronized(this) { collecting = false; armedBase = -1L }
    val collectedMs: Long get() = synchronized(this) { bins.size.toLong() * BIN_MS }
    fun snapshot(): Pair<Long, FloatArray> = synchronized(this) { baseMs to bins.toFloatArray() }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) = synchronized(this) {
        sr = sampleRateHz; ch = channelCount; enc = encoding
        pitchN = 0; decim = 0; decimAcc = 0.0
        if (armedBase >= 0) {
            baseMs = armedBase; armedBase = -1L
            frames = 0; binAcc = 0.0; binCount = 0; bins.clear()
            hpPrevIn = 0.0; hpPrevOut = 0.0; lp = 0.0
            val dt = 1.0 / maxOf(sr, 1)
            val rcH = 1.0 / (2 * Math.PI * 250); hpA = rcH / (rcH + dt)
            val rcL = 1.0 / (2 * Math.PI * 3500); lpA = dt / (rcL + dt)
            noPcm = encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT
            collecting = !noPcm
        } else if (collecting) {
            // קפיצה באמצע האיסוף (המשתמש הזיז) – לא יודעים איפה אנחנו; עוצרים
            collecting = false; aborted = true
        }
    }

    // ---------- זיהוי גובה קול (לקריינות לפי הדובר): ~8kHz, חלונות של 40ms ----------
    @Volatile var pitchOn = false
    private val pitchBuf = FloatArray(320); private var pitchN = 0; private var decim = 0; private var decimAcc = 0.0
    private val pitchLog = ArrayDeque<Pair<Long, Float>>()   // (זמן שעון, הרץ) – רק חלונות עם דיבור
    /** חציון גובה הקול בחלון הזמן (שעון), או null אם אין מספיק דיבור */
    fun pitchSince(fromMs: Long, minFrames: Int = 4): Float? = synchronized(pitchLog) {
        val l = pitchLog.filter { it.first >= fromMs }.map { it.second }.sorted()
        if (l.size < minFrames) null else l[l.size / 2]
    }
    private fun pitchFrame(rateHz: Int) {
        // אוטוקורלציה מנורמלת, 60–420 הרץ
        val n = pitchN; var e = 0.0
        for (i in 0 until n) e += pitchBuf[i] * pitchBuf[i]
        if (e / n < 2e-5) return
        val minLag = rateHz / 420; val maxLag = minOf(n - 1, rateHz / 60)
        var best = 0.0; var bestLag = 0
        for (lag in minLag..maxLag) {
            var c = 0.0; var e1 = 0.0; var e2 = 0.0
            var i = 0
            while (i + lag < n) { val a = pitchBuf[i]; val b = pitchBuf[i + lag]; c += a * b; e1 += a * a; e2 += b * b; i++ }
            val r = c / Math.sqrt(e1 * e2 + 1e-12)
            if (r > best) { best = r; bestLag = lag }
        }
        if (best < 0.55 || bestLag == 0) return
        val hz = rateHz.toFloat() / bestLag
        synchronized(pitchLog) {
            pitchLog.addLast(System.currentTimeMillis() to hz)
            while (pitchLog.size > 400) pitchLog.removeFirst()
        }
    }
    private fun feedPitch(buffer: ByteBuffer) {
        if (ch <= 0 || sr <= 0) return
        val b = buffer.duplicate().order(ByteOrder.nativeOrder())
        val float = enc == C.ENCODING_PCM_FLOAT
        if (!float && enc != C.ENCODING_PCM_16BIT) return
        val frameBytes = ch * (if (float) 4 else 2)
        val factor = maxOf(1, sr / 8000); val rate = sr / factor
        while (b.remaining() >= frameBytes) {
            var m = 0.0
            for (c in 0 until ch) m += if (float) b.float.toDouble() else b.short / 32768.0
            decimAcc += m / ch; decim++
            if (decim >= factor) {
                pitchBuf[pitchN++] = (decimAcc / decim).toFloat(); decim = 0; decimAcc = 0.0
                if (pitchN >= pitchBuf.size) { pitchFrame(rate); pitchN = 0 }
            }
        }
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (pitchOn) runCatching { feedPitch(buffer) }
        if (!collecting) return
        synchronized(this) {
            if (!collecting || ch <= 0 || sr <= 0) return
            val b = buffer.duplicate().order(ByteOrder.nativeOrder())
            val samplesPerBin = sr * BIN_MS / 1000
            val float = enc == C.ENCODING_PCM_FLOAT
            val frameBytes = ch * (if (float) 4 else 2)
            while (b.remaining() >= frameBytes) {
                var m = 0.0
                for (c in 0 until ch) m += if (float) b.float.toDouble() else b.short / 32768.0
                m /= ch
                // high-pass ואז low-pass
                val hp = hpA * (hpPrevOut + m - hpPrevIn); hpPrevIn = m; hpPrevOut = hp
                lp += lpA * (hp - lp)
                binAcc += lp * lp; binCount++; frames++
                if (binCount >= samplesPerBin) {
                    val rms = binAcc / binCount
                    bins += (10 * log10(rms + 1e-10)).toFloat()
                    binAcc = 0.0; binCount = 0
                }
            }
        }
    }
}

object AutoSync {
    data class Result(val offsetMs: Long, val scale: Double, val confidence: Double)

    private val tsRx = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})""")

    /** זמני הכתוביות (התחלה, סוף) במילישניות – SRT/VTT */
    fun cues(text: String): List<Pair<Long, Long>> = tsRx.findAll(text).map { m ->
        val g = m.groupValues.drop(1).map { it.toLong() }
        ((g[0] * 3600 + g[1] * 60 + g[2]) * 1000 + g[3]) to ((g[4] * 3600 + g[5] * 60 + g[6]) * 1000 + g[7])
    }.filter { it.second > it.first }.toList()

    /**
     * baseMs – זמן המדיה של הדגימה הראשונה; db – אנרגיה לכל 100ms; cues – זמני הכתוביות *כפי שהם עכשיו* (כולל הזזה קיימת).
     * מחזיר הזזה (ms) וקצב שיש להחיל על הזמנים הנוכחיים, או null אם אין מספיק ביטחון.
     */
    fun solve(baseMs: Long, db: FloatArray, cues: List<Pair<Long, Long>>): Result? {
        val n = db.size
        if (n < 300 || cues.size < 5) return null
        // זיהוי דיבור: מעל רצפת הרעש (אחוזון 20) ב-8dB, עם החלקה קלה
        val sorted = db.sorted()
        val floor = sorted[(n * 0.2).toInt()]
        val top = sorted[(n * 0.95).toInt()]
        if (top - floor < 10f) return null      // אין הבדל בין שקט לדיבור (מוזיקה רציפה וכו')
        val thr = floor + maxOf(8f, (top - floor) * 0.35f)
        val v = IntArray(n) { i ->
            var c = 0; for (j in maxOf(0, i - 1)..minOf(n - 1, i + 1)) if (db[j] > thr) c++
            if (c >= 2) 1 else -1
        }
        val pre = LongArray(n + 1); for (i in 0 until n) pre[i + 1] = pre[i] + v[i]
        val bin = AudioTap.BIN_MS
        val winStart = baseMs; val winEnd = baseMs + n.toLong() * bin
        fun score(scale: Double, off: Long): Long {
            var s = 0L
            for ((a, b) in cues) {
                val x = (a * scale).toLong() + off; val y = (b * scale).toLong() + off
                if (y <= winStart || x >= winEnd) continue
                val i0 = ((maxOf(x, winStart) - winStart) / bin).toInt().coerceIn(0, n)
                val i1 = ((minOf(y, winEnd) - winStart) / bin).toInt().coerceIn(0, n)
                if (i1 > i0) s += pre[i1] - pre[i0]
            }
            return s
        }
        val scales = doubleArrayOf(1.0, 25.0 / 23.976, 23.976 / 25.0, 24.0 / 23.976, 23.976 / 24.0, 25.0 / 24.0, 24.0 / 25.0)
        val mid = (winStart + winEnd) / 2
        var best: Result? = null; var bestScore = Long.MIN_VALUE
        val all = ArrayList<Long>()
        for (sc in scales) {
            // בקצב אחר – ההזזה הטבעית סביב אמצע החלון
            val center = (mid - mid * sc).toLong()
            // טווח: סביב "מיושר כאן" וגם סביב "מיושר מההתחלה" (הזזה 0)
            var off = minOf(center, 0L) - 30_000
            while (off <= maxOf(center, 0L) + 30_000) {
                val s = score(sc, off)
                if (sc == 1.0) all += s
                if (s > bestScore) { bestScore = s; best = Result(off, sc, 0.0) }
                off += bin
            }
        }
        val b = best ?: return null
        // ביטחון: עד כמה הטוב ביותר בולט מעל ההתפלגות (z-score), והעדפה לקצב רגיל אם קרוב
        val mean = all.average(); val sd = kotlin.math.sqrt(all.map { (it - mean) * (it - mean) }.average()).coerceAtLeast(1.0)
        val z = (bestScore - mean) / sd
        if (b.scale != 1.0) {
            val plain = (-30_000L..30_000L step bin.toLong()).maxOf { score(1.0, it) }
            if (bestScore - plain < maxOf(15L, (abs(bestScore) * 0.08).toLong())) {
                val o = (-30_000L..30_000L step bin.toLong()).maxBy { score(1.0, it) }
                return if (z >= 3.5) Result(o, 1.0, z) else null
            }
        }
        return if (z >= 3.5) b.copy(confidence = z) else null
    }
}
