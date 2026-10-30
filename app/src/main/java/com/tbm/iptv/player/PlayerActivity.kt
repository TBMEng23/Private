package com.tbm.iptv.player

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tbm.iptv.data.Kind
import com.tbm.iptv.data.PlayItem
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.Repo
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
}

class PlayerActivity : ComponentActivity() {
    lateinit var player: ExoPlayer
    val ui = PlayerUi()
    var playerView: PlayerView? = null

    private var current: PlayItem? = null
    private var subQuery: SubQuery? = null
    private var subFile: SubtitleFile? = null
    private var subJob: Job? = null
    private var retry = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        player = PlayerFactory.create(this)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) { retry = 0; ui.error = null }
                if (state == Player.STATE_ENDED) onEnded()
            }

            override fun onPlayerError(error: PlaybackException) = handleError(error)
        })

        addOnPictureInPictureModeChangedListener { info ->
            ui.pip = info.isInPictureInPictureMode
            playerView?.useController = !info.isInPictureInPictureMode
        }

        setContent { AppTheme { PlayerScreen(this) } }
        playIndex(Repo.queueIndex)
    }

    // ------------------------------------------------------------------ ניגון
    fun playIndex(i: Int) {
        val q = Repo.queue
        if (i !in q.indices) return
        saveProgress()
        Repo.queueIndex = i
        val item = q[i]
        current = item
        subFile = null; subQuery = null; subJob?.cancel()
        ui.subLabel = null; ui.subOffset = 0; ui.subResults = emptyList(); ui.subErrors = emptyList()
        ui.error = null; retry = 0
        ui.title = item.title
        ui.isLive = item.kind == Kind.LIVE
        ui.hasNext = item.kind != Kind.MOVIE && i < q.size - 1
        ui.hasPrev = item.kind != Kind.MOVIE && i > 0

        var start = 0L
        if (item.kind != Kind.LIVE) {
            Prefs.resume(item.key)?.let { (pos, dur) ->
                if (pos > 10_000 && (dur <= 0 || pos < dur * 0.95)) {
                    start = pos; toast("ממשיך מ-${fmt(pos)}")
                }
            }
        }
        player.setMediaItem(PlayerFactory.mediaItem(item.url, item.title), start)
        player.prepare()
        player.play()

        if (item.kind != Kind.LIVE && Prefs.autoSubs) searchSubtitles(auto = true)
    }

    fun next() = playIndex(Repo.queueIndex + 1)
    fun prev() = playIndex(Repo.queueIndex - 1)

    private fun onEnded() {
        val item = current ?: return
        if (item.kind != Kind.LIVE && player.duration > 0) Prefs.saveResume(item, player.duration, player.duration)
        if (item.kind == Kind.SERIES && Prefs.autoNext && ui.hasNext) {
            toast("עובר לפרק הבא…"); next()
        } else if (item.kind == Kind.MOVIE || item.kind == Kind.SERIES) {
            finish()
        }
    }

    private fun handleError(e: PlaybackException) {
        val item = current ?: return
        if (item.kind == Kind.LIVE && retry < 5) {
            retry++
            ui.error = "מתחבר מחדש… ($retry/5)"
            lifecycleScope.launch {
                delay(2000L * retry)
                player.seekToDefaultPosition()
                player.prepare(); player.play()
            }
        } else {
            ui.error = "שגיאת ניגון: ${e.errorCodeName}\nנסה נגן חיצוני (הגדרות) או ערוץ אחר"
        }
    }

    private fun saveProgress() {
        val item = current ?: return
        if (item.kind == Kind.LIVE) return
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
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "תצוגה: זום (מילוי עם חיתוך)"
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "תצוגה: מתיחה למסך מלא"
                else -> "תצוגה: מקורית"
            }
        )
    }

    // ------------------------------------------------------------------ כתוביות
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
                    ui.subErrors = listOf("אין אתר כתוביות פעיל – הוסף מפתחות בהגדרות")
                    return@launch
                }
                val (results, errors) = SubtitleManager.searchAll(q)
                ui.subResults = results
                ui.subErrors = errors
                if (auto) {
                    val best = SubtitleManager.pickBest(results)
                    if (best != null) applySubtitle(best) else toast("לא נמצאו כתוביות אוטומטית")
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException)
                    ui.subErrors = listOf(e.message ?: e.javaClass.simpleName)
            } finally {
                ui.subSearching = false
            }
        }
    }

    fun applySubtitle(r: SubResult) {
        val item = current ?: return
        lifecycleScope.launch {
            toast("מוריד כתובית מ-${r.provider}…")
            try {
                val q = subQuery ?: SubtitleManager.buildQuery(item).also { subQuery = it }
                subFile = SubtitleManager.download(r, q)
                ui.subOffset = 0
                ui.subLabel = "${r.provider} · ${langLabel(r.lang)}"
                reloadWithSubtitle()
                toast("נטענה כתובית: ${ui.subLabel}")
            } catch (e: Exception) {
                toast("הורדת הכתובית נכשלה: ${e.message ?: ""}")
            }
        }
    }

    fun shiftSubtitle(deltaMs: Long) {
        if (subFile == null) { toast("לא נטענה כתובית"); return }
        ui.subOffset += deltaMs
        reloadWithSubtitle()
        toast("סנכרון כתוביות: %+.1f שניות".format(ui.subOffset / 1000.0))
    }

    fun disableSubtitles() {
        subFile = null; ui.subLabel = null
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        toast("הכתוביות כובו")
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !ui.showSubs) {
            val live = ui.isLive
            when (event.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> { next(); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { prev(); return true }
                KeyEvent.KEYCODE_DPAD_UP -> if (live && !ui.controlsVisible) { next(); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> if (live && !ui.controlsVisible) { prev(); return true }
                KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_MENU -> {
                    ui.showSubs = true
                    if (ui.subResults.isEmpty() && !ui.subSearching) searchSubtitles(auto = false)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        playIndex(Repo.queueIndex)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player.isPlaying &&
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
        player.pause()
    }

    override fun onDestroy() {
        saveProgress()
        player.release()
        super.onDestroy()
    }
}
