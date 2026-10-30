package com.tbm.iptv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.tbm.iptv.data.Kind
import com.tbm.iptv.data.LoadState
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.Repo
import com.tbm.iptv.data.SeriesItem

sealed interface Screen {
    data object Home : Screen
    data object Playlists : Screen
    data object Settings : Screen
    data class Filter(val kind: Kind) : Screen
    data class Series(val item: SeriesItem) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot() {
    val stack = remember {
        mutableStateListOf<Screen>(if (Prefs.activePlaylist() == null) Screen.Playlists else Screen.Home)
    }
    LaunchedEffect(Unit) {
        if (Repo.state.value is LoadState.Idle) Prefs.activePlaylist()?.let { Repo.loadAsync(it) }
    }
    val nav: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    val goHome: () -> Unit = { stack.clear(); stack.add(Screen.Home) }

    BackHandler(enabled = stack.size > 1) { back() }

    when (val top = stack.last()) {
        Screen.Home -> HomeScreen(nav)
        Screen.Playlists -> PlaylistsScreen(onDone = goHome, onBack = if (stack.size > 1) back else null)
        Screen.Settings -> SettingsScreen(nav, back)
        is Screen.Filter -> FilterScreen(top.kind, back)
        is Screen.Series -> SeriesScreen(top.item, back)
    }
}
