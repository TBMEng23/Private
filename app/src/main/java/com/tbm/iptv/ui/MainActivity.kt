package com.tbm.iptv.ui

import com.tbm.iptv.L

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.media3.ui.PlayerView
import com.tbm.iptv.data.Kind
import com.tbm.iptv.data.LoadState
import com.tbm.iptv.data.Parental
import com.tbm.iptv.data.PlayItem
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.Repo
import com.tbm.iptv.data.SeriesItem
import com.tbm.iptv.player.PlayerHolder
import com.tbm.iptv.rec.Notif
import com.tbm.iptv.rec.Scheduler

sealed interface Screen {
    data object Home : Screen
    data object Playlists : Screen
    data object Profiles : Screen
    data class Settings(val page: String = "main") : Screen
    data class Groups(val kind: Kind) : Screen
    data class Series(val item: SeriesItem) : Screen
    data class Movie(val item: com.tbm.iptv.data.Channel) : Screen
    data object Account : Screen
    data class KidsContent(val profileId: String) : Screen
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Notif.init(this)
        Scheduler.rescheduleAll(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        handleIntent(intent)
        setContent { AppTheme { AppRoot() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** פתיחה מהתראת תזכורת – מנגן את הערוץ */
    private fun handleIntent(i: Intent?) {
        val url = i?.getStringExtra("play_url") ?: return
        val title = i.getStringExtra("play_title") ?: ""
        i.removeExtra("play_url")
        Launcher.play(this, listOf(PlayItem(url, title, url, Kind.LIVE,
            epgId = i.getStringExtra("play_epg")?.ifBlank { null }, channelName = title)), 0)
    }

    override fun onStop() {
        super.onStop()
        if (PlayerHolder.miniActive) PlayerHolder.current()?.pause()
    }

    override fun onDestroy() {
        if (isFinishing) {
            if (Prefs.clearHistoryOnExit) Prefs.clearHistory()
            if (PlayerHolder.miniActive) PlayerHolder.release()
        }
        super.onDestroy()
    }
}

@Composable
fun AppRoot() {
    val stack = remember {
        mutableStateListOf<Screen>(
            when {
                Prefs.activePlaylist() == null -> Screen.Playlists
                Prefs.profiles().size > 1 && Prefs.bool("profile_picker", true) -> Screen.Profiles
                else -> Screen.Home
            }
        )
    }
    LaunchedEffect(Unit) {
        if (Repo.state.value is LoadState.Idle) Prefs.activePlaylist()?.let { Repo.loadAsync(it) }
    }
    val nav: (Screen) -> Unit = { s ->
        val needsPin = (s is Screen.Settings || s is Screen.Playlists || s is Screen.Groups) &&
            (Prefs.settingsLock || Prefs.isKids)
        if (needsPin) Parental.requireAlways(L("כניסה להגדרות")) { stack.add(s) } else stack.add(s)
    }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    val goHome: () -> Unit = { stack.clear(); stack.add(Screen.Home) }

    BackHandler(enabled = stack.size > 1) { back() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        when (val top = stack.last()) {
            Screen.Home -> HomeScreen(nav)
            Screen.Playlists -> PlaylistsScreen(onDone = goHome, onBack = if (stack.size > 1) back else null)
            Screen.Profiles -> ProfilesScreen(onDone = goHome, onManage = { stack.add(Screen.Settings("parental")) })
            is Screen.Settings -> SettingsScreen(top.page, nav, back)
            is Screen.Groups -> ManageGroupsScreen(top.kind, back) { pid -> stack.add(Screen.KidsContent(pid)) }
            is Screen.Series -> SeriesScreen(top.item, back)
            is Screen.Movie -> MovieScreen(top.item, back)
            Screen.Account -> AccountScreen(back)
            is Screen.KidsContent -> KidsContentScreen(top.profileId, back)
        }
        if (PlayerHolder.miniActive) DraggableMini()
        PinDialog()
        CrashReport()
    }
}

/** In-App PiP – חלון צף שממשיך לנגן בזמן דפדוף */
@Composable
private fun DraggableMini() {
    Ltr {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            var big by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Prefs.str("mini_big") == "1") }
            val w = if (big) 340.dp else 260.dp
            val h = w * 9f / 16f
            val maxX = with(density) { (maxWidth - w - 24.dp).toPx() }.coerceAtLeast(0f)
            val maxY = with(density) { (maxHeight - h - 24.dp).toPx() }.coerceAtLeast(0f)
            val saved = Prefs.str("mini_pos", "1,0.8").split(",").mapNotNull { it.toFloatOrNull() }
            var x by androidx.compose.runtime.remember(maxX) { androidx.compose.runtime.mutableFloatStateOf((saved.getOrNull(0) ?: 1f) * maxX) }
            var y by androidx.compose.runtime.remember(maxY) { androidx.compose.runtime.mutableFloatStateOf((saved.getOrNull(1) ?: 0.8f) * maxY) }
            MiniPlayer(
                Modifier.offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()) }
                    .pointerInput(maxX, maxY) {
                        detectDragGestures(onDragEnd = {
                            // נצמד לצד הקרוב (כמו ביוטיוב)
                            x = if (x < maxX / 2) 0f else maxX
                            Prefs.setStrQuiet("mini_pos", "${if (maxX > 0) x / maxX else 1f},${if (maxY > 0) y / maxY else 0.8f}")
                        }) { ch, d ->
                            ch.consume()
                            x = (x + d.x).coerceIn(0f, maxX)
                            y = (y + d.y).coerceIn(0f, maxY)
                        }
                    },
                w, h,
            ) { big = !big; Prefs.setStrQuiet("mini_big", if (big) "1" else "0") }
        }
    }
}

@Composable
private fun MiniPlayer(modifier: Modifier, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, onToggleSize: () -> Unit) {
    val ctx = LocalContext.current
    val p = PlayerHolder.current() ?: return
    var playing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(p.isPlaying) }
    var showCtl by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { playing = p.isPlaying; kotlinx.coroutines.delay(400) } }
    LaunchedEffect(showCtl) { if (showCtl) { kotlinx.coroutines.delay(4000); showCtl = false } }
    val item = com.tbm.iptv.data.Repo.queue.getOrNull(com.tbm.iptv.data.Repo.queueIndex)
    val live = item?.kind == com.tbm.iptv.data.Kind.LIVE
    fun go(d: Int) {
        val q = com.tbm.iptv.data.Repo.queue
        val i = com.tbm.iptv.data.Repo.queueIndex + d
        if (i !in q.indices) return
        com.tbm.iptv.data.Repo.queueIndex = i
        PlayerHolder.miniTitle = q[i].title
        p.setMediaItem(com.tbm.iptv.player.PlayerFactory.mediaItem(q[i].url, q[i].title)); p.prepare(); p.play()
        if (q[i].kind == com.tbm.iptv.data.Kind.LIVE) Prefs.addRecentChannel(q[i].key)
    }
    Box(
        modifier.padding(12.dp).size(w, h).clip(RoundedCornerShape(12.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable { showCtl = !showCtl },
    ) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; player = p } },
            update = { it.player = p },
            modifier = Modifier.fillMaxSize(),
        )
        if (showCtl) {
            Box(Modifier.fillMaxSize().background(Color(0x77000000))) {
                IconButton(onClick = { Launcher.resumeMini(ctx) }, modifier = Modifier.align(Alignment.TopStart).size(36.dp)) {
                    Icon(Icons.Filled.OpenInFull, L("הגדל"), tint = Color.White)
                }
                IconButton(onClick = { PlayerHolder.release() }, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
                    Icon(Icons.Filled.Close, L("סגור"), tint = Color.White)
                }
                Ltr { androidx.compose.foundation.layout.Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showCtl = true; if (live) go(-1) else p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0)) }) {
                        Icon(if (live) Icons.Filled.SkipPrevious else Icons.Filled.Replay10, null, tint = Color.White)
                    }
                    IconButton(onClick = { showCtl = true; if (p.isPlaying) p.pause() else p.play() }, modifier = Modifier.size(52.dp)) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { showCtl = true; if (live) go(1) else p.seekTo(p.currentPosition + 10_000) }) {
                        Icon(if (live) Icons.Filled.SkipNext else Icons.Filled.Forward10, null, tint = Color.White)
                    }
                } }
                IconButton(onClick = { showCtl = true; onToggleSize() }, modifier = Modifier.align(Alignment.BottomStart).size(34.dp)) {
                    Icon(Icons.Filled.AspectRatio, L("שנה גודל"), tint = Color.White, modifier = Modifier.size(18.dp))
                }
                androidx.compose.material3.Text(PlayerHolder.miniTitle, color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
                    maxLines = 1, modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp))
            }
        }
    }
}

/** דוח קריסה מהפעם הקודמת – אפשר להעתיק ולשלוח */
@Composable
private fun CrashReport() {
    val ctx = LocalContext.current
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.tbm.iptv.CrashLog.read()) }
    val t = text ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { com.tbm.iptv.CrashLog.clear(); text = null },
        title = { androidx.compose.material3.Text(L("האפליקציה נסגרה בפעם הקודמת")) },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(L("לחץ \"העתק\" ושלח את הדוח כדי שאפשר יהיה לתקן."),
                    fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp))
                androidx.compose.material3.Text(
                    t.take(1500),
                    fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                    modifier = Modifier.padding(top = 8.dp).androidx_verticalScroll(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("TBM crash", t))
                android.widget.Toast.makeText(ctx, L("הועתק"), android.widget.Toast.LENGTH_SHORT).show()
            }) { androidx.compose.material3.Text(L("העתק")) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { com.tbm.iptv.CrashLog.clear(); text = null }) {
                androidx.compose.material3.Text(L("סגור"))
            }
        },
    )
}

@Composable
private fun Modifier.androidx_verticalScroll(): Modifier =
    this.heightIn(max = 260.dp).verticalScroll(rememberScrollState())
