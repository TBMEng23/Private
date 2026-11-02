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
import com.tbm.iptv.data.Epg
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

/** רצועת כתוביות מובנת בקובץ */
data class EmbeddedSub(val groupIndex: Int, val name: String, val selected: Boolean, val lang: String? = null)

/** סדר שפות: עברית, אנגלית, ואז כל השאר */
fun langRank(code: String?): Int = when (code?.lowercase()?.take(3)?.trimEnd('-', '_')) {
    "he", "iw", "heb" -> 0
    "en", "eng" -> 1
    else -> 2
}
fun isHebrewTrack(e: EmbeddedSub) = langRank(e.lang) == 0 || Regex("(?i)hebrew|עברית").containsMatchIn(e.name)

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
    /** להציג כפתורי פעולה (נסה שוב / נגן חיצוני / הבא) מתחת לשגיאה */
    var errorActions by mutableStateOf(false)
    /** חלון "אפשרויות" (⚙) – null סגור, אחרת דף: main/audio/speed/aspect/sleep */
    var options by mutableStateOf<String?>(null)
    var showRecord by mutableStateOf(false)
    var zapDigits by mutableStateOf("")
    var sleepMin by mutableIntStateOf(0)
    var castAvailable by mutableStateOf(false)
    /** אין עברית – הצעה לתרגם אוטומטית מאנגלית */
    var mtOffer by mutableStateOf<SubResult?>(null)
    /** קריינות AI פעילה */
    var voiceOn by mutableStateOf(false)
    /** כתוביות מובנות בקובץ (לא כולל החיצונית שלנו) */
    var embedded by mutableStateOf<List<EmbeddedSub>>(emptyList())
    /** רצועות שמע (שפות) */
    var audio by mutableStateOf<List<EmbeddedSub>>(emptyList())
    var speed by mutableStateOf(1f)
    /** מהירות זמנית בלחיצה ארוכה */
    var boosting by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var landscape by mutableStateOf(true)
    var stillWatching by mutableStateOf(false)
    var introSkipped by mutableStateOf(false)
    /** חיווי ג'סטות: "⏩ 10" / "🔆 60%" / "🔊 40%" */
    var gesture by mutableStateOf<String?>(null)
    /** זמן הנגיעה האחרונה – להסתרת הפקדים */
    var touchedAt by mutableStateOf(System.currentTimeMillis())
    /** לוח שידורים של הערוץ בתוך הנגן */
    var showGuide by mutableStateOf(false)
    /** צופה כרגע בתוכנית מהעבר (catch-up) של הערוץ הנוכחי */
    var inCatchup by mutableStateOf(false)
    /** צופה מתוך קובץ הקלטה שנכתב כרגע */
    var fromRecording by mutableStateOf(false)
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
            if (state == Player.STATE_READY) { retry = 0; ui.error = null; ui.errorActions = false }
            if (state == Player.STATE_ENDED) onEnded()
        }

        override fun onPlayerError(error: PlaybackException) = handleError(error)

        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
            if (!ui.voiceOn) return
            val t = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString(" ")
            if (t.isNotBlank()) voice?.speak(t)
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            val ours = ui.subLabel
            val list = ArrayList<EmbeddedSub>()
            var ourGroup: androidx.media3.common.Tracks.Group? = null
            tracks.groups.forEachIndexed { gi, g ->
                if (g.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
                val f = g.getTrackFormat(0)
                if (ours != null && f.label == ours) { ourGroup = g; return@forEachIndexed }
                val name = listOfNotNull(f.label, f.language?.let { langLabel(it.take(2)) }).joinToString(" · ").ifBlank { L("רצועה {0}", gi + 1) }
                list += EmbeddedSub(gi, name, g.isSelected, f.language)
            }
            // עברית ראשונה, אחריה אנגלית, ואז השאר
            ui.embedded = list.sortedWith(compareBy({ if (isHebrewTrack(it)) 0 else langRank(it.lang) }, { it.name }))
            val al = ArrayList<EmbeddedSub>()
            tracks.groups.forEachIndexed { gi, g ->
                if (g.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
                val f = g.getTrackFormat(0)
                val ch = if (f.channelCount > 2) " ${f.channelCount}ch" else ""
                val name = (listOfNotNull(f.label, f.language?.let { langLabel(it.take(2)) }).joinToString(" · ")
                    .ifBlank { L("שמע {0}", al.size + 1) }) + ch
                al += EmbeddedSub(gi, name, g.isSelected, f.language)
            }
            ui.audio = al
            // כתובית חיצונית נטענה – לבחור אותה ולכבות את המובנות
            val og = ourGroup
            if (og != null && subFile != null && Prefs.replaceEmbedded && !og.isSelected) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(androidx.media3.common.TrackSelectionOverride(og.mediaTrackGroup, 0))
                    .build()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) voice?.stop()
            updatePip()
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
        ui.landscape = Prefs.bool("player_landscape", true)
        applyOrientation()
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
            // הפקדים שלנו בלבד – אף פעם לא הפקדים המובנים של ExoPlayer (אחרת יש שני נגנים זה על זה)
            playerView?.useController = false
            playerView?.hideController()
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
        androidx.core.content.ContextCompat.registerReceiver(this, pipReceiver, android.content.IntentFilter(pipAction),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
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
        ui.introSkipped = false
        ui.stillWatching = false
        ui.inCatchup = false
        ui.fromRecording = false
        saveProgress()
        if (i != Repo.queueIndex) lastIndex = Repo.queueIndex
        attach(i)
        val item = q[i]
        subFile = null; subQuery = null; subJob?.cancel()
        ui.subLabel = null; ui.subOffset = 0; ui.subResults = emptyList(); ui.subErrors = emptyList()
        ui.error = null; retry = 0; lastGoodPos = 0L; ui.errorActions = false

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
            autoCount++
            if (autoCount >= 3) { ui.stillWatching = true; return }
            next()
        } else if (item.kind != Kind.LIVE || item.isCatchup) {
            finish()
        }
    }

    /** מיקום אחרון תקין – לחזרה אחרי ניתוק */
    private var lastGoodPos = 0L

    private fun httpCode(e: Throwable?): Int? {
        var c: Throwable? = e
        while (c != null) {
            if (c is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) return c.responseCode
            c = c.cause
        }
        return null
    }

    private fun handleError(e: PlaybackException) {
        val item = current ?: return
        val live = item.kind == Kind.LIVE && !item.isCatchup
        if (!live && player.currentPosition > 0) lastGoodPos = player.currentPosition
        val max = if (live) 5 else 3
        if (retry < max) {
            retry++
            ui.errorActions = false
            ui.error = L("מתחבר מחדש… ({0}/{1})", retry, max)
            lifecycleScope.launch {
                delay(2000L * retry)
                reload()
            }
            return
        }
        val code = httpCode(e)
        val why = when (code) {
            401, 403 -> L("הספק דחה את הבקשה (מנוי, הרשאה או יותר מדי חיבורים)")
            404 -> L("הקובץ לא נמצא אצל הספק")
            429, 458, 509 -> L("יותר מדי חיבורים פעילים לחשבון – סגור צפייה במכשיר אחר")
            in 500..599 -> L("שגיאת שרת אצל הספק – נסה שוב בעוד רגע")
            else -> e.errorCodeName
        }
        ui.error = L("שגיאת ניגון: {0}", why + (code?.let { " (HTTP $it)" } ?: ""))
        ui.errorActions = true
    }

    /** טוען מחדש את הזרם מהמקום שבו נעצר (VOD) או מהשידור החי */
    private fun reload() {
        val item = current ?: return
        val live = item.kind == Kind.LIVE && !item.isCatchup
        if (live) player.seekToDefaultPosition() else if (lastGoodPos > 0) player.seekTo(lastGoodPos)
        player.prepare(); player.play()
    }

    /** כפתור "נסה שוב" / "רענן" */
    fun retryNow() {
        if (player.currentPosition > 0 && !(ui.isLive)) lastGoodPos = player.currentPosition
        retry = 0; ui.error = null; ui.errorActions = false
        reload()
        toast(L("טוען מחדש…"))
    }

    private fun saveProgress() {
        val item = current ?: return
        if (item.kind == Kind.LIVE || item.isCatchup || item.isLocal) return
        val dur = player.duration
        val pos = player.currentPosition
        if (dur > 0 && pos > 5_000) Prefs.saveResume(item, pos, dur)
    }

    private var autoCount = 0

    /** הערוץ (Channel) של הפריט הנוכחי – ל-EPG ול-catch-up */
    fun currentChannel(): com.tbm.iptv.data.Channel? {
        val q = Repo.queue.getOrNull(Repo.queueIndex) ?: return null
        return Repo.content?.live?.firstOrNull { it.key == q.key }
    }

    /** צפייה בתוכנית שכבר שודרה, בלי לעזוב את הערוץ */
    fun playCatchup(c: com.tbm.iptv.data.Channel, e: com.tbm.iptv.data.EpgEntry) {
        val url = com.tbm.iptv.data.Catchup.url(Prefs.activePlaylist(), c, e.start, e.end)
        if (url == null) { toast(L("הערוץ לא תומך בצפייה לאחור")); return }
        val it = PlayItem("cu:${c.key}:${e.start}", "${c.name} · ${e.title}", url, Kind.LIVE,
            c.logo, epgId = c.epgId, channelName = c.name, isCatchup = true)
        current = it
        ui.item = it
        ui.title = it.title
        ui.isLive = false
        ui.inCatchup = true
        ui.showGuide = false
        ui.error = null
        player.setMediaItem(PlayerFactory.mediaItem(url, it.title))
        player.prepare(); player.play()
        toast(L("צפייה לאחור: {0}", e.title))
    }

    /** חזרה לשידור חי מצפייה לאחור */
    fun backToLive() { ui.inCatchup = false; playIndex(Repo.queueIndex) }

    /** המשך אחרי "עדיין צופה?" */
    fun continueWatching() { ui.stillWatching = false; autoCount = 0; next() }

    /** פעולת משתמש (לא אוטומטית) – מאפס מונה "עדיין צופה?" */
    fun touched() { ui.touchedAt = System.currentTimeMillis(); autoCount = 0 }

    fun applyOrientation() {
        requestedOrientation = if (ui.landscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    fun toggleRotate() {
        ui.landscape = !ui.landscape
        Prefs.setBool("player_landscape", ui.landscape)
        applyOrientation()
    }

    fun seekBy(ms: Long) {
        if (ui.isLive) return
        val d = player.duration
        var p = player.currentPosition + ms
        if (d > 0) p = p.coerceAtMost(d - 1000)
        player.seekTo(p.coerceAtLeast(0))
    }

    fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }

    fun setSpeed(s: Float) {
        ui.speed = s
        player.setPlaybackSpeed(s)
    }

    fun cycleSpeed() {
        val steps = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        setSpeed(steps[(steps.indexOf(ui.speed).coerceAtLeast(0) + 1) % steps.size])
        toast(L("מהירות: {0}×", ui.speed))
    }

    /** לחיצה ארוכה = 2× זמני, כמו ביוטיוב */
    fun boost(on: Boolean) {
        if (ui.isLive) return
        ui.boosting = on
        player.setPlaybackSpeed(if (on) 2f else ui.speed)
    }

    fun selectAudio(e: EmbeddedSub) {
        val g = player.currentTracks.groups.getOrNull(e.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, 0))
            .build()
        toast(L("שמע: {0}", e.name))
    }

    /** בהירות: שינוי יחסי (-1..1) */
    fun adjustBrightness(delta: Float) {
        val lp = window.attributes
        val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
        lp.screenBrightness = (cur + delta).coerceIn(0.02f, 1f)
        window.attributes = lp
        ui.gesture = "🔆 ${(lp.screenBrightness * 100).toInt()}%"
    }

    private var volAcc = 0f
    fun adjustVolume(delta: Float) {
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        volAcc += delta * max
        val steps = volAcc.toInt()
        if (steps != 0) {
            volAcc -= steps
            val v = (am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) + steps).coerceIn(0, max)
            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, v, 0)
        }
        val v = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        ui.gesture = "🔊 ${v * 100 / max.coerceAtLeast(1)}%"
    }

    fun setResize(m: Int) { ui.resize = m }

    /** מעבר לנגן חיצוני (VLC/MX) מהמיקום הנוכחי, עם הכתובית שנטענה */
    fun openExternal() {
        val item = current ?: return
        val pos = if (ui.isLive) 0L else player.currentPosition
        if (ExternalPlayer.open(this, item, subFile, pos)) { player.pause(); exit() }
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
        setSleep(steps[(steps.indexOf(ui.sleepMin) + 1) % steps.size])
    }

    fun setSleep(min: Int) {
        ui.sleepMin = min
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
    /** כתובת השידור החי של הערוץ הנוכחי (גם כשצופים מתוך הקלטה) */
    fun liveUrl(): String? = Repo.queue.getOrNull(Repo.queueIndex)?.url

    /** לחיצה על ⏺: מתחיל להקליט מיד (עד סוף התוכנית / ברירת מחדל), ואז פותח חלון אפשרויות */
    fun startRecording() {
        val item = Repo.queue.getOrNull(Repo.queueIndex) ?: return
        if (RecordingService.activeFor(item.url) != null) { ui.showRecord = true; return }
        val now = System.currentTimeMillis()
        val prog = Epg.nowNext(item.epgId, item.channelName).first
        val end = if (prog != null && prog.end > now + 60_000) prog.end + 5 * 60_000L else now + Prefs.recDefaultMin * 60_000L
        val title = item.channelName ?: item.title
        val path = RecordingService.start(this, (prog?.let { "$title - ${it.title}" } ?: title), item.url, end - now)
        toast(L("⏺ ההקלטה התחילה – עד {0}", com.tbm.iptv.ui.fmtTime(end)))
        ui.showRecord = true
        if (Prefs.recWatchFromFile && !ui.casting) lifecycleScope.launch {
            // ממתינים שיצטברו נתונים ואז עוברים לצפייה מתוך ההקלטה – חיבור אחד בלבד לספק
            val f = java.io.File(path)
            var waited = 0
            while (f.length() < 400_000 && waited < 15_000 && RecordingService.isRecordingFile(path)) {
                delay(300); waited += 300
            }
            if (f.length() > 50_000) playFromRecording(path)
        }
    }

    private fun playFromRecording(path: String) {
        val f = java.io.File(path)
        val src = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
            GrowingFileDataSource.Factory(f) { RecordingService.isRecordingFile(path) }
        ).createMediaSource(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(f)))
        player.setMediaSource(src)
        player.prepare(); player.play()
        ui.fromRecording = true
        ui.error = null
        toast(L("צופה מתוך ההקלטה – אפשר להשהות"))
    }

    fun stopRecording() {
        val url = liveUrl() ?: return
        val r = RecordingService.activeFor(url) ?: return
        RecordingService.stop(this, r.id)
        toast(L("ההקלטה נעצרה ונשמרה"))
        ui.showRecord = false
        if (ui.fromRecording) { ui.fromRecording = false; playIndex(Repo.queueIndex) }
    }

    fun setRecordingEnd(endAt: Long) {
        val url = liveUrl() ?: return
        val r = RecordingService.activeFor(url) ?: return
        RecordingService.setEnd(r.id, endAt)
        toast(L("ההקלטה תסתיים ב-{0}", com.tbm.iptv.ui.fmtTime(endAt)))
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
                    // יש כתובית עברית מובנית בקובץ – עדיפה על הורדה
                    ui.embedded.firstOrNull { isHebrewTrack(it) }?.let { he ->
                        if (subFile == null) { if (!he.selected) selectEmbedded(he); return@launch }
                    }
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

    /** בחירת כתובית מובנת מהקובץ */
    fun selectEmbedded(e: EmbeddedSub) {
        val g = player.currentTracks.groups.getOrNull(e.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, 0))
            .build()
        toast(L("נבחרה כתובית מובנת: {0}", e.name))
    }

    fun disableSubtitles() {
        subFile = null; ui.subLabel = null
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
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
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
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
        if (event.action == KeyEvent.ACTION_DOWN && !ui.showSubs && !ui.showList && !ui.showRecord && ui.options == null) {
            val live = ui.isLive
            val code = event.keyCode
            touched()
            when (code) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { togglePlay(); ui.controlsVisible = true; return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); ui.controlsVisible = true; return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(30_000); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-10_000); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                    if (!ui.controlsVisible) { ui.controlsVisible = true; return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> if (!live && !ui.controlsVisible) { seekBy(-10_000); ui.gesture = "⏪ 10"; return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (!live && !ui.controlsVisible) { seekBy(10_000); ui.gesture = "⏩ 10"; return true }
            }
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

    // ------------------------------------------------------------------ PiP עם כפתורים (כמו יוטיוב)
    private val pipAction = "com.tbm.iptv.PIP"
    private val pipReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) {
            when (i.getIntExtra("cmd", 0)) {
                1 -> prev()
                2 -> togglePlay()
                3 -> next()
                4 -> seekBy(-10_000)
                5 -> seekBy(10_000)
            }
            updatePip()
        }
    }

    private fun pipSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    @android.annotation.TargetApi(26)
    private fun action(cmd: Int, icon: Int, title: String): android.app.RemoteAction {
        val pi = android.app.PendingIntent.getBroadcast(
            this, cmd, Intent(pipAction).setPackage(packageName).putExtra("cmd", cmd),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return android.app.RemoteAction(android.graphics.drawable.Icon.createWithResource(this, icon), title, title, pi)
    }

    @android.annotation.TargetApi(26)
    private fun pipParams(): PictureInPictureParams {
        val playing = player.isPlaying
        val live = ui.isLive || ui.inCatchup
        val actions = listOf(
            if (live) action(1, android.R.drawable.ic_media_previous, L("ערוץ קודם")) else action(4, android.R.drawable.ic_media_rew, L("10 שניות אחורה")),
            action(2, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, L("ניגון/השהיה")),
            if (live) action(3, android.R.drawable.ic_media_next, L("ערוץ הבא")) else action(5, android.R.drawable.ic_media_ff, L("10 שניות קדימה")),
        )
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).setActions(actions)
        if (Build.VERSION.SDK_INT >= 31) {
            b.setAutoEnterEnabled(Prefs.systemPip && playing)
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    fun updatePip() {
        if (!pipSupported()) return
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    fun enterPip() {
        if (!pipSupported()) return
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    /** האם המשתמש התיר "תמונה בתוך תמונה" לאפליקציה (בשיאומי/HyperOS לעיתים כבוי כברירת מחדל) */
    fun pipAllowed(): Boolean = runCatching {
        val ops = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_PICTURE_IN_PICTURE, applicationInfo.uid, packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_PICTURE_IN_PICTURE, applicationInfo.uid, packageName)
        mode == android.app.AppOpsManager.MODE_ALLOWED
    }.getOrDefault(true)

    /** פותח את מסך ההרשאה של תמונה בתוך תמונה לאפליקציה */
    fun openPipSettings() {
        val i = Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", android.net.Uri.parse("package:$packageName"))
        runCatching { startActivity(i) }.onFailure {
            runCatching { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // אנדרואיד 12+ נכנס אוטומטית (setAutoEnterEnabled); אם זה לא קרה (יצרנים מסוימים) – נכנסים ידנית
        if (!Prefs.systemPip || !player.isPlaying || !pipSupported()) return
        if (Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode) return
        if (!pipAllowed()) {
            if (!Prefs.bool("pip_warned", false)) {
                Prefs.setBool("pip_warned", true)
                android.widget.Toast.makeText(this, L("החלון הקטן חסום – אפשר אותו בהגדרות: אפליקציות ← TBM Player ← תמונה בתוך תמונה"), android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        enterPip()
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        if (!toMini && !ui.casting) player.pause()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
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
