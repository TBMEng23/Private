package com.tbm.iptv.ui

import com.tbm.iptv.L

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
                !Prefs.isKids && Prefs.profiles().size > 1 -> Screen.Profiles
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

    Box(Modifier.fillMaxSize()) {
        when (val top = stack.last()) {
            Screen.Home -> HomeScreen(nav)
            Screen.Playlists -> PlaylistsScreen(onDone = goHome, onBack = if (stack.size > 1) back else null)
            Screen.Profiles -> ProfilesScreen(onDone = goHome)
            is Screen.Settings -> SettingsScreen(top.page, nav, back)
            is Screen.Groups -> ManageGroupsScreen(top.kind, back)
            is Screen.Series -> SeriesScreen(top.item, back)
        }
        if (PlayerHolder.miniActive) MiniPlayer(Modifier.align(Alignment.BottomEnd))
        PinDialog()
    }
}

/** In-App PiP – חלון צף שממשיך לנגן בזמן דפדוף */
@Composable
private fun MiniPlayer(modifier: Modifier) {
    val ctx = LocalContext.current
    val p = PlayerHolder.current() ?: return
    Box(
        modifier.padding(12.dp).size(220.dp, 124.dp).clip(RoundedCornerShape(10.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
            .background(Color.Black)
            .clickable { Launcher.resumeMini(ctx) },
    ) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; player = p } },
            update = { it.player = p },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(onClick = { PlayerHolder.release() }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
            Icon(Icons.Filled.Close, L("סגור"), tint = Color.White)
        }
    }
}
