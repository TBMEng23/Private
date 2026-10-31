package com.tbm.iptv.player

import com.tbm.iptv.L

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.gms.cast.framework.CastContext
import com.tbm.iptv.data.Kind
import com.tbm.iptv.data.PlayItem
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.Repo
import com.tbm.iptv.rec.RecordingService
import com.tbm.iptv.subs.SubQuery
import com.tbm.iptv.subs.SubResult
import com.tbm.iptv.subs.SubtitleFile
import com.tbm.iptv.subs.SubtitleManager
import com.tbm.iptv.subs.langLabel
import com.tbm.iptv.ui.AppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerUi {
    var title by mutableStateOf("")
    var item by mutableStateOf<PlayItem?>(null)
    var isLive by mutableStateOf(false)
    var hasNext by mutableStateOf(false)
    var hasPrev by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var controlsVisible by mutableStateOf(true)
    var pip by mutableStateOf(false)
    var resize by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)

    var showSubs by mutableStateOf(false)
    var subSearching by mutableStateOf(false)
    var subResults by mutableStateOf<List<SubResult>>(emptyList())
    var subErrors by mutableStateOf<List<String>>(emptyList())
    var subLabel by mutableStateOf<String?>(null)
    var subOffset by mutableLongStateOf(0L)

    var showList by mutableStateOf(false)
    var showRecord by mutableStateOf(false)
    var zapDigits by mutableStateOf("")
    var sleepMin by mutableIntStateOf(0)
    var castAvailable by mutableStateOf(false)
    /** אין עברית – הצעה לתרגם אוטומטית מאנגלית */
    var mtOffer by mutableStateOf<SubResult?>(null)
    /** קריינות AI פעילה */
    var voiceOn by mutableStateOf(false)
    var casting by mutableStateOf(false)
}

class PlayerActivity : FragmentActivity() {
    lateinit var player: ExoPlayer
    val ui = PlayerUi()
    var playerView: PlayerView? = null

    private var current: PlayItem? = null
    private var subQuery: SubQuery? = null
    private var subFile: SubtitleFile? = null
    private var subJob: Job? = null
    private var sleepJob: Job? = null
    private var zapJob: Job? = null
    private var retry = 0
    private var lastIndex = -1
    private var toMini = false
    private var castPlayer: CastPlayer? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) { retry = 0; ui.error = null }
            if (state == Player.STATE_ENDED) onEnded()
        }

        override fun onPlayerError(error: PlaybackException) = handleError(error)

        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
            if (!ui.voiceOn) return
            val t = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString(" ")
            if (t.isNotBlank()) voice?.speak(t)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) voice?.stop()
        }
    }

    private var voice: VoiceOver? = null

    fun toggleVoice() {
        if (ui.voiceOn) {
            ui.voiceOn = false
            voice?.stop()
            toast(L("קריינות AI כבויה"))
            return
        }
        if (subFile == null && ui.subLabel == null) toast(L("קריינות תתחיל כשתיטען כתובית"))
        if (voice == null) voice = VoiceOver(this,
            onDuck = { d -> if (!ui.casting) player.volume = if (d) Prefs.voiceDuck else 1f },
            onMissingVoice = { toast(L("אין קול מותקן לשפה הזו – התקן קול עברי בהגדרות אנדרואיד ← המרת טקסט לדיבור (Google)")) })
        voice?.setLanguage(subFile?.lang ?: "he")
        ui.voiceOn = true
        toast(L("קריינות AI פעילה"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        player = PlayerHolder.get(this)
        player.addListener(listener)
        PlayerHolder.miniActive = false

        addOnPictureInPictureModeChangedListener { info ->
            ui.pip = info.isInPictureInPictureMode
            playerView?.useController = !info.isInPictureInPictureMode
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    ui.showList -> ui.showList = false
                    ui.showRecord -> ui.showRecord = false
                    else -> exit()
                }
            }
        })

        setupCast()
        setContent { AppTheme { PlayerScreen(this) } }

        if (intent.getBooleanExtra("resume", false) && Repo.queue.isNotEmpty()) attach(Repo.queueIndex)
        else playIndex(Repo.queueIndex)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!intent.getBooleanExtra("resume", false)) playIndex(Repo.queueIndex)
    }

    // ------------------------------------------------------------------ ניגון
    private fun attach(i: Int) {
        val q = Repo.queue
        if (i !in q.indices) return
        Repo.queueIndex = i
        val item = q[i]
        current = item
        ui.item = item
        ui.title = item.title
        ui.isLive = item.kind == Kind.LIVE && !item.isCatchup
        ui.hasNext = !item.isLocal && item.kind != Kind.MOVIE && !item.isCatchup && i < q.size - 1
        ui.hasPrev = !item.isLocal && item.kind != Kind.MOVIE && !item.isCatchup && i > 0
        PlayerHolder.miniTitle = item.title
    }

    fun playIndex(i: Int) {
        val q = Repo.queue
        if (i !in q.indices) return
        saveProgress()
        if (i != Repo.queueIndex) lastIndex = Repo.queueIndex
        attach(i)
        val item = q[i]
        subFile = null; subQuery = null; subJob?.cancel()
        ui.subLabel = null; ui.subOffset = 0; ui.subResults = emptyList(); ui.subErrors = emptyList()
        ui.error = null; retry = 0

        var start = 0L
        if (item.kind != Kind.LIVE && !item.isCatchup) {
            Prefs.resume(item.key)?.let { (pos, dur) ->
                if (pos > 10_000 && (dur <= 0 || pos < dur * 0.95)) {
                    start = pos; toast(L("ממשיך מ-{0}", fmt(pos)))
                }
            }
        }
        if (item.kind == Kind.LIVE && !item.isCatchup) Prefs.addRecentChannel(item.key)

        val cp = castPlayer
        if (ui.casting && cp != null) {
            cp.setMediaItem(PlayerFactory.castItem(item.url, item.title), start)
            cp.prepare(); cp.play()
            return
        }
        player.setMediaItem(PlayerFactory.mediaItem(item.url, item.title), start)
        player.prepare()
        player.play()

        voice?.stop()
        if (Prefs.voiceDefault && item.kind != Kind.LIVE && !ui.voiceOn) toggleVoice()
        if (item.kind != Kind.LIVE && !item.isCatchup && !item.isLocal) {
            val cached = SubtitleManager.loadCache(item.key)
            if (cached != null) {
                subFile = cached.first
                ui.subLabel = cached.second
                reloadWithSubtitle()
            } else if (Prefs.autoSubs) searchSubtitles(auto = true)
        }
    }

    fun next() = playIndex(Repo.queueIndex + 1)
    fun prev() = playIndex(Repo.queueIndex - 1)
    fun lastChannel() { if (lastIndex >= 0) playIndex(lastIndex) }

    private fun onEnded() {
        val item = current ?: return
        if (item.kind != Kind.LIVE && !item.isCatchup && player.duration > 0)
            Prefs.saveResume(item, player.duration, player.duration)
        if (item.kind == Kind.SERIES && Prefs.autoNext && ui.hasNext) {
            toast(L("עובר לפרק הבא…")); next()
        } else if (item.kind != Kind.LIVE || item.isCatchup) {
            finish()
        }
    }

    private fun handleError(e: PlaybackException) {
        val item = current ?: return
        if (item.kind == Kind.LIVE && !item.isCatchup && retry < 5) {
            retry++
            ui.error = L("מתחבר מחדש… ({0}/5)", retry)
            lifecycleScope.launch {
                delay(2000L * retry)
                player.seekToDefaultPosition()
                player.prepare(); player.play()
            }
        } else {
            ui.error = L("שגיאת ניגון: {0}\nנסה נגן חיצוני (הגדרות) או ערוץ אחר", e.errorCodeName)
        }
    }

    private fun saveProgress() {
        val item = current ?: return
        if (item.kind == Kind.LIVE || item.isCatchup || item.isLocal) return
        val dur = player.duration
        val pos = player.currentPosition
        if (dur > 0 && pos > 5_000) Prefs.saveResume(item, pos, dur)
    }

    fun cycleResize() {
        ui.resize = when (ui.resize) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        toast(
            when (ui.resize) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> L("תצוגה: זום (מילוי עם חיתוך)")
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> L("תצוגה: מתיחה למסך מלא")
                else -> L("תצוגה: מקורית")
            }
        )
    }

    // ------------------------------------------------------------------ טיימר שינה
    fun cycleSleep() {
        val steps = listOf(0, 15, 30, 60, 90, 120)
        ui.sleepMin = steps[(steps.indexOf(ui.sleepMin) + 1) % steps.size]
        sleepJob?.cancel()
        if (ui.sleepMin > 0) {
            sleepJob = lifecycleScope.launch {
                delay(ui.sleepMin * 60_000L)
                player.pause(); finish()
            }
            toast(L("טיימר שינה: {0} דקות", ui.sleepMin))
        } else toast(L("טיימר שינה כבוי"))
    }

    // ------------------------------------------------------------------ הקלטה
    fun recordNow(minutes: Int) {
        val item = current ?: return
        RecordingService.start(this, item.title, item.url, minutes * 60_000L)
        toast(L("ההקלטה התחילה ({0} דק׳)", minutes))
    }

    // ------------------------------------------------------------------ Chromecast
    private fun setupCast() {
        val cc = runCatching {
            @Suppress("DEPRECATION")
            CastContext.getSharedInstance(this)
        }.getOrNull() ?: return
        ui.castAvailable = true
        castPlayer = runCatching { CastPlayer(cc) }.getOrNull()?.apply {
            setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() = toCast()
                override fun onCastSessionUnavailable() = toLocal()
            })
        }
        if (castPlayer?.isCastSessionAvailable == true) ui.casting = true
    }

    private fun toCast() {
        val cp = castPlayer ?: return
        val item = current ?: return
        val pos = if (ui.isLive) 0L else player.currentPosition
        player.pause()
        cp.setMediaItem(PlayerFactory.castItem(item.url, item.title), pos)
        cp.prepare(); cp.play()
        playerView?.player = cp
        ui.casting = true
        toast(L("משדר לטלוויזיה…"))
    }

    private fun toLocal() {
        val cp = castPlayer
        val pos = cp?.currentPosition ?: 0L
        ui.casting = false
        playerView?.player = player
        if (!ui.isLive && pos > 0) player.seekTo(pos)
        player.play()
    }

    // ------------------------------------------------------------------ זפזופ במספרים
    fun zapDigit(d: Int) {
        if (ui.zapDigits.length >= 5) return
        ui.zapDigits += d.toString()
        zapJob?.cancel()
        zapJob = lifecycleScope.launch {
            delay(1500)
            val n = ui.zapDigits.toIntOrNull() ?: 0
            ui.zapDigits = ""
            if (n in 1..Repo.queue.size) playIndex(n - 1) else toast(L("אין ערוץ {0}", n))
        }
    }

    // ------------------------------------------------------------------ כתוביות
    /** חיפוש לפי שם שהמשתמש הקליד */
    fun searchManual(title: String) {
        val item = current ?: return
        if (title.isBlank()) return
        subJob?.cancel()
        lifecycleScope.launch {
            ui.subSearching = true
            subQuery = runCatching { SubtitleManager.buildQueryFor(title, item) }.getOrNull()
            searchSubtitles(auto = false)
        }
    }

    /** השם שנשלח לחיפוש – מוצג בתיבת החיפוש */
    fun currentQueryTitle(): String = subQuery?.title ?: com.tbm.iptv.data.TitleParser.parse(
        current?.seriesName ?: current?.title ?: "").title

    fun searchSubtitles(auto: Boolean) {
        val item = current ?: return
        subJob?.cancel()
        subJob = lifecycleScope.launch {
            ui.subSearching = true
            ui.subResults = emptyList()
            ui.subErrors = emptyList()
            try {
                val q = subQuery ?: SubtitleManager.buildQuery(item).also { subQuery = it }
                if (SubtitleManager.providers.none { it.enabled() }) {
                    ui.subErrors = listOf(L("אין אתר כתוביות פעיל – הוסף מפתחות בהגדרות"))
                    return@launch
                }
                val (results, errors) = SubtitleManager.searchAll(q)
                ui.subResults = results
                ui.subErrors = errors
                if (auto) {
                    val best = SubtitleManager.pickBest(results)
                    when {
                        best == null -> toast(L("לא נמצאו כתוביות אוטומטית"))
                        best.provider.startsWith(SubtitleManager.MT_NAME) && Prefs.str("mt_mode", "ask") == "ask" ->
                            ui.mtOffer = best
                        else -> applySubtitle(best)
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException)
                    ui.subErrors = listOf(e.message ?: e.javaClass.simpleName)
            } finally {
                ui.subSearching = false
            }
        }
    }

    fun applySubtitle(first: SubResult) {
        val item = current ?: return
        lifecycleScope.launch {
            // הכתובית שנבחרה, ואחריה עד 4 חלופות באותה שפה למקרה שההורדה נכשלת
            val candidates = listOf(first) + ui.subResults.filter { it !== first && it.lang == first.lang }.take(4)
            var lastErr: Exception? = null
            for (r in candidates) {
            toast(L("מוריד כתובית מ-{0}…", r.provider))
            try {
                val q = subQuery ?: SubtitleManager.buildQuery(item).also { subQuery = it }
                val sf = SubtitleManager.download(r, q)
                subFile = sf
                ui.subOffset = 0
                val label = "${r.provider} · ${langLabel(r.lang)}"
                ui.subLabel = label
                SubtitleManager.saveCache(item.key, sf, label)
                voice?.setLanguage(sf.lang)
                reloadWithSubtitle()
                toast(L("נטענה כתובית: {0}", label))
                return@launch
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastErr = e
            }
            }
            toast(L("הורדת הכתובית נכשלה: {0}", lastErr?.message ?: ""))
        }
    }

    /** טעינת כתובית אנגלית במקום תרגום */
    fun applyEnglishInstead() {
        ui.mtOffer = null
        ui.subResults.firstOrNull { it.lang == "en" }?.let { applySubtitle(it) } ?: toast(L("אין כתובית באנגלית"))
    }

    fun shiftSubtitle(deltaMs: Long) {
        if (subFile == null) { toast(L("לא נטענה כתובית")); return }
        ui.subOffset += deltaMs
        reloadWithSubtitle()
        toast(L("סנכרון כתוביות: %+.1f שניות").format(ui.subOffset / 1000.0))
    }

    fun disableSubtitles() {
        subFile = null; ui.subLabel = null
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        toast(L("הכתוביות כובו"))
    }

    private fun reloadWithSubtitle() {
        val item = current ?: return
        val sf = subFile ?: return
        val file = SubtitleManager.writeFile(sf, ui.subOffset, cacheDir)
        val pos = player.currentPosition
        val playing = player.playWhenReady
        player.setMediaItem(
            PlayerFactory.mediaItem(item.url, item.title, SubTrack(file, sf.mime, sf.lang, ui.subLabel ?: sf.lang)),
            pos,
        )
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(sf.lang)
            .build()
        player.prepare()
        player.playWhenReady = playing
    }

    // ------------------------------------------------------------------ עזר
    fun toast(msg: String) {
        ui.message = msg
        lifecycleScope.launch {
            delay(3000)
            if (ui.message == msg) ui.message = null
        }
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
    }

    /** יציאה מהנגן – אם In-App PiP פעיל, ממשיך בחלון צף בתוך האפליקציה */
    fun exit() {
        if (Prefs.inAppPip && player.isPlaying && !ui.casting) {
            toMini = true
            PlayerHolder.miniActive = true
        }
        finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !ui.showSubs && !ui.showList && !ui.showRecord) {
            val live = ui.isLive
            val code = event.keyCode
            if (live && code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
                zapDigit(code - KeyEvent.KEYCODE_0); return true
            }
            when (code) {
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> { next(); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { prev(); return true }
                KeyEvent.KEYCODE_LAST_CHANNEL -> { lastChannel(); return true }
                KeyEvent.KEYCODE_DPAD_UP -> if (live && !ui.controlsVisible) { next(); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> if (live && !ui.controlsVisible) { prev(); return true }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT ->
                    if (live && !ui.controlsVisible) { ui.showList = true; return true }
                KeyEvent.KEYCODE_GUIDE -> if (live) { ui.showList = true; return true }
                KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_MENU -> {
                    if (live) { ui.showList = true } else {
                        ui.showSubs = true
                        if (ui.subResults.isEmpty() && !ui.subSearching) searchSubtitles(auto = false)
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Prefs.systemPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player.isPlaying &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        if (!toMini && !ui.casting) player.pause()
    }

    override fun onDestroy() {
        saveProgress()
        player.removeListener(listener)
        playerView?.player = null
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        voice?.shutdown()
        player.volume = 1f
        if (!toMini) PlayerHolder.release()
        super.onDestroy()
    }
}
