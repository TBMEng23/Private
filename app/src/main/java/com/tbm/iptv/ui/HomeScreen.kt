package com.tbm.iptv.ui

import com.tbm.iptv.L

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.tbm.iptv.data.Catalog
import com.tbm.iptv.data.Catchup
import com.tbm.iptv.data.Channel
import com.tbm.iptv.data.Content
import com.tbm.iptv.data.Entry
import com.tbm.iptv.data.Epg
import com.tbm.iptv.data.EpgEntry
import com.tbm.iptv.data.Kind
import com.tbm.iptv.data.LoadState
import com.tbm.iptv.data.Parental
import com.tbm.iptv.data.PlayItem
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.Repo
import com.tbm.iptv.data.SeriesItem
import com.tbm.iptv.data.Trending
import com.tbm.iptv.data.Xtream
import com.tbm.iptv.data.toPlay
import com.tbm.iptv.player.PlayerFactory
import com.tbm.iptv.rec.RecordingService
import com.tbm.iptv.rec.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class Section(private val he: String, val icon: ImageVector) {
    HOME("בית", Icons.Filled.Home),
    LIVE("טלוויזיה", Icons.Filled.LiveTv),
    GUIDE("לוח שידורים", Icons.Filled.DateRange),
    MOVIES("סרטים", Icons.Filled.Movie),
    SERIES("סדרות", Icons.Filled.VideoLibrary),
    FAV("מועדפים", Icons.Filled.Star),
    RECORDINGS("הקלטות", Icons.Filled.FiberManualRecord),
    MULTI("מולטי-ויו", Icons.Filled.GridView),
    SEARCH("חיפוש", Icons.Filled.Search);

    val label: String get() = L(he)
}

private const val FAV_GROUP = "\u0000fav"
private const val NEW_GROUP = "\u0000new"
private const val RECENT_GROUP = "\u0000recent"

@Composable
fun HomeScreen(nav: (Screen) -> Unit) {
    val state by Repo.state.collectAsState()
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    val ver = Prefs.version.intValue
    val kids = remember(ver) { Prefs.isKids }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 600.dp
        val body: @Composable (Modifier) -> Unit = { m ->
            Box(m) {
                when (val s = state) {
                    is LoadState.Ready -> SectionContent(section, s.content, ver, wide, nav) { section = it }
                    is LoadState.Loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text(s.msg)
                    }
                    is LoadState.Error -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(L("שגיאה בטעינה"), style = MaterialTheme.typography.titleLarge)
                        Text(s.msg, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Button(onClick = { Prefs.activePlaylist()?.let { Repo.loadAsync(it, true) } }) { Text(L("נסה שוב")) }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { nav(Screen.Playlists) }) { Text(L("רשימות")) }
                        }
                    }
                    LoadState.Idle -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(L("לא נבחרה רשימת ערוצים"))
                        Button(onClick = { nav(Screen.Playlists) }) { Text(L("הוסף רשימה")) }
                    }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                SideRail(section, kids, { section = it }, nav)
                body(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                TopBarPhone(kids, { section = Section.SEARCH }, nav)
                body(Modifier.weight(1f).fillMaxWidth())
                BottomNav(section) { section = it }
            }
        }
    }
}

@Composable
private fun TopBarPhone(kids: Boolean, onSearch: () -> Unit, nav: (Screen) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("TBM", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text(" Player", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, L("חיפוש")) }
        IconButton(onClick = { nav(Screen.Profiles) }) { Icon(Icons.Filled.Person, L("פרופיל")) }
        if (!kids) IconButton(onClick = { nav(Screen.Settings()) }) { Icon(Icons.Filled.Settings, L("הגדרות")) }
    }
}

@Composable
private fun BottomNav(section: Section, onSelect: (Section) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 4.dp)) {
        listOf(Section.HOME, Section.LIVE, Section.MOVIES, Section.SERIES, Section.FAV).forEach { s ->
            FocusItem(Modifier.weight(1f), selected = s == section, onClick = { onSelect(s) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(s.icon, s.label, tint = if (s == section) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s.label, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SideRail(section: Section, kids: Boolean, onSelect: (Section) -> Unit, nav: (Screen) -> Unit) {
    Column(
        Modifier.width(92.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TBM", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Section.entries.filter { !kids || (it != Section.RECORDINGS && it != Section.MULTI) }.forEach { s ->
            RailItem(s.label, s.icon, s == section) { onSelect(s) }
        }
        Spacer(Modifier.weight(1f))
        RailItem(Prefs.profile().name, Icons.Filled.Person, false) { nav(Screen.Profiles) }
        if (!kids) {
            RailItem(L("רשימות"), Icons.AutoMirrored.Filled.PlaylistPlay, false) { nav(Screen.Playlists) }
            RailItem(L("הגדרות"), Icons.Filled.Settings, false) { nav(Screen.Settings()) }
        }
    }
}

@Composable
private fun RailItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth(), selected = selected, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SectionContent(section: Section, content: Content, ver: Int, wide: Boolean, nav: (Screen) -> Unit, go: (Section) -> Unit) {
    when (section) {
        Section.HOME -> HomeRows(content, ver, wide, nav, go)
        Section.LIVE -> BrowseSection(Kind.LIVE, content.live, ver, nav)
        Section.GUIDE -> GuideSection(content)
        Section.MOVIES -> BrowseSection(Kind.MOVIE, content.movies, ver, nav)
        Section.SERIES -> BrowseSection(Kind.SERIES, content.series, ver, nav)
        Section.FAV -> FavoritesSection(content, ver, nav)
        Section.RECORDINGS -> RecordingsSection(ver)
        Section.MULTI -> MultiViewSection(content)
        Section.SEARCH -> SearchSection(content, ver, nav)
    }
}

// ============================================================ דף הבית – שורות
@Composable
private fun HomeRows(content: Content, ver: Int, wide: Boolean, nav: (Screen) -> Unit, go: (Section) -> Unit) {
    val ctx = LocalContext.current
    val live = remember(content, ver) { Catalog.build(Kind.LIVE, content.live).items.filterIsInstance<Channel>() }
    val movies = remember(content, ver) { Catalog.build(Kind.MOVIE, content.movies).items }
    val series = remember(content, ver) { Catalog.build(Kind.SERIES, content.series).items }
    val recentCh = remember(live, ver) {
        val byKey = live.associateBy { it.key }
        Prefs.recentChannels().mapNotNull { byKey[it] }
    }
    val recent = remember(ver) { Prefs.recent() }
    val allowedKeys = remember(movies, series) { (movies.map { it.key } + series.map { it.key }).toHashSet() }
    val recentMovies = remember(recent, allowedKeys) { recent.filter { it.item.kind == Kind.MOVIE && it.item.key in allowedKeys } }
    val recentEps = remember(recent) { recent.filter { it.item.kind == Kind.SERIES }.distinctBy { it.item.seriesName } }
    val newMovies = remember(movies) { movies.filterIsInstance<Channel>().filter { it.added > 0 }.sortedByDescending { it.added }.take(30) }
    val newSeries = remember(series) { series.filterIsInstance<SeriesItem>().filter { it.added > 0 }.sortedByDescending { it.added }.take(30) }
    var trendMovies by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var trendSeries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    val tmdb = Prefs.tmdbKey
    LaunchedEffect(movies, series, tmdb) {
        if (tmdb.isNotBlank()) withContext(Dispatchers.IO) {
            trendMovies = runCatching { Trending.movies(movies) }.getOrDefault(emptyList())
            trendSeries = runCatching { Trending.series(series) }.getOrDefault(emptyList())
        }
    }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        if (!wide) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { QuickTile(L("לוח שידורים"), Icons.Filled.DateRange) { go(Section.GUIDE) } }
                if (!Prefs.isKids) {
                    item { QuickTile(L("הקלטות"), Icons.Filled.FiberManualRecord) { go(Section.RECORDINGS) } }
                    item { QuickTile(L("מולטי-ויו"), Icons.Filled.GridView) { go(Section.MULTI) } }
                }
                item { QuickTile(L("חיפוש"), Icons.Filled.Search) { go(Section.SEARCH) } }
            }
        }
        if (recentCh.isNotEmpty()) item {
            RowTitle(L("ערוצים אחרונים"))
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(recentCh) { i, c ->
                    FocusItem(onClick = { Launcher.play(ctx, recentCh.map { it.toPlay() }, i) }) {
                        Column(Modifier.width(130.dp).padding(4.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                AsyncImage(c.logo, null, Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit)
                            }
                            Text(c.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (recentMovies.isNotEmpty()) item {
            RowTitle(L("המשך צפייה – סרטים"))
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recentMovies) { r ->
                    Poster(r.item.title, r.item.logo, false, r.pos.toFloat() / r.dur.coerceAtLeast(1),
                        onLong = { Prefs.removeResume(r.item.key) }) { Launcher.play(ctx, listOf(r.item), 0) }
                }
            }
        }
        if (recentEps.isNotEmpty()) item {
            RowTitle(L("המשך צפייה – סדרות"))
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recentEps) { r ->
                    val s = series.firstOrNull { it.name == r.item.seriesName } as? SeriesItem
                    Poster(r.item.seriesName ?: r.item.title, s?.logo ?: r.item.logo, false, r.pos.toFloat() / r.dur.coerceAtLeast(1),
                        sub = "S%02dE%02d".format(r.item.season ?: 0, r.item.episode ?: 0),
                        onLong = { Prefs.removeResume(r.item.key) }) {
                        if (s != null) nav(Screen.Series(s)) else Launcher.play(ctx, listOf(r.item), 0)
                    }
                }
            }
        }
        if (trendMovies.isNotEmpty()) item {
            EntryRowStrip(L("🔥 פופולרי היום – סרטים"), trendMovies, Kind.MOVIE, ctx, nav) { menu = MenuTarget(Kind.MOVIE, it, movies) }
        }
        if (trendSeries.isNotEmpty()) item {
            EntryRowStrip(L("🔥 פופולרי היום – סדרות"), trendSeries, Kind.SERIES, ctx, nav) { menu = MenuTarget(Kind.SERIES, it, series) }
        }
        if (newMovies.isNotEmpty()) item {
            EntryRowStrip(L("🆕 סרטים שנוספו לאחרונה"), newMovies, Kind.MOVIE, ctx, nav) { menu = MenuTarget(Kind.MOVIE, it, movies) }
        }
        if (newSeries.isNotEmpty()) item {
            EntryRowStrip(L("🆕 סדרות שעודכנו לאחרונה"), newSeries, Kind.SERIES, ctx, nav) { menu = MenuTarget(Kind.SERIES, it, series) }
        }
        if (tmdb.isBlank() && !Prefs.isKids) item {
            Text(L("טיפ: הוסף מפתח TMDB בהגדרות ← תוכן כדי לראות מה פופולרי היום"),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        if (recentCh.isEmpty() && recent.isEmpty() && newMovies.isEmpty() && trendMovies.isEmpty()) item {
            Text(L("ברוך הבא! בחר ערוץ, סרט או סדרה כדי להתחיל."), modifier = Modifier.padding(24.dp))
        }
    }
    menu?.let { ItemMenu(it) { menu = null } }
}

@Composable
private fun QuickTile(label: String, icon: ImageVector, onClick: () -> Unit) {
    FocusItem(onClick = onClick) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RowTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun EntryRowStrip(
    title: String, list: List<Entry>, kind: Kind, ctx: android.content.Context, nav: (Screen) -> Unit, onLong: (Entry) -> Unit,
) {
    RowTitle(title)
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list) { e ->
            val r = Prefs.resume(e.key)
            Poster(e.name, e.logo, Prefs.isFav(e.key), r?.let { it.first.toFloat() / it.second.coerceAtLeast(1) },
                onLong = { onLong(e) }) {
                if (kind == Kind.SERIES && e is SeriesItem) nav(Screen.Series(e))
                else if (e is Channel) Launcher.play(ctx, listOf(e.toPlay()), 0)
            }
        }
    }
}

@Composable
private fun Poster(
    name: String, logo: String?, fav: Boolean, progress: Float?, sub: String? = null,
    width: androidx.compose.ui.unit.Dp? = 118.dp, onLong: (() -> Unit)? = null, onClick: () -> Unit,
) {
    FocusItem(onLongClick = onLong, onClick = onClick) {
        Column((if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).padding(4.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (logo == null) Text(name, Modifier.align(Alignment.Center).padding(6.dp), textAlign = TextAlign.Center, fontSize = 13.sp)
                AsyncImage(logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (fav) Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp))
                if (progress != null && progress > 0f) LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    color = Color(0xFFE53935),
                )
            }
            Text(name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            sub?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ============================================================ דפדוף: קטגוריות + תוכן
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseSection(kind: Kind, all: List<Entry>, ver: Int, nav: (Screen) -> Unit) {
    val unlocked = Parental.unlocked
    val view = remember(all, ver, unlocked) { Catalog.build(kind, all) }
    val filtered = view.items
    val counts = remember(filtered) { filtered.groupingBy { it.group }.eachCount() }
    val favs = remember(ver) { Prefs.favs() }
    val favCount = remember(filtered, favs) { filtered.count { it.key in favs } }
    val hasNew = remember(filtered) { filtered.any { ((it as? Channel)?.added ?: (it as? SeriesItem)?.added ?: 0L) > 0L } }
    val recentKeys = remember(ver) { Prefs.recentChannels() }
    var group by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(kind) { mutableStateOf("") }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }

    val shownItems = remember(filtered, group, query, favs, recentKeys) {
        val base = when (group) {
            null -> filtered
            FAV_GROUP -> filtered.filter { it.key in favs }
            RECENT_GROUP -> { val m = filtered.associateBy { it.key }; recentKeys.mapNotNull { m[it] } }
            NEW_GROUP -> filtered.sortedByDescending { (it as? Channel)?.added ?: (it as? SeriesItem)?.added ?: 0L }.take(200)
            else -> filtered.filter { it.group == group }
        }
        if (query.isBlank()) base else base.filter { it.name.contains(query, true) }
    }

    val selectGroup: (String?) -> Unit = { g ->
        if (g != null && g in view.lockedGroups && !Parental.unlocked) Parental.require(L("קבוצה נעולה")) { group = g }
        else group = g
    }

    val specials = buildList {
        add(Triple<String?, String, Int>(null, L("הכל"), filtered.size))
        add(Triple<String?, String, Int>(FAV_GROUP, L("★ מועדפים"), favCount))
        if (kind == Kind.LIVE && recentKeys.isNotEmpty()) add(Triple<String?, String, Int>(RECENT_GROUP, L("🕘 אחרונים"), recentKeys.size))
        if (kind != Kind.LIVE && hasNew) add(Triple<String?, String, Int>(NEW_GROUP, L("🆕 נוספו לאחרונה"), 200))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(maxWidth * 0.26f).fillMaxHeight().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(kind.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (!Prefs.isKids) {
                            IconButton(onClick = { Prefs.activePlaylist()?.let { Repo.loadAsync(it, true) } }) { Icon(Icons.Filled.Refresh, L("רענון")) }
                            IconButton(onClick = { nav(Screen.Groups(kind)) }) { Icon(Icons.Filled.FilterList, L("ניהול קבוצות")) }
                        }
                    }
                    OutlinedTextField(query, { query = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), placeholder = { Text(L("חיפוש…")) })
                    LazyColumn(contentPadding = PaddingValues(6.dp)) {
                        items(specials) { (g, label, n) -> CategoryRow(label, n, group == g, false) { selectGroup(g) } }
                        items(view.groups) { g ->
                            CategoryRow(g, counts[g] ?: 0, group == g, g in view.lockedGroups && !Parental.unlocked) { selectGroup(g) }
                        }
                    }
                }
                ContentArea(kind, shownItems, favs, all, true, nav, Modifier.weight(1f)) { menu = MenuTarget(kind, it, all) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text(L("חיפוש ב{0}", kind.label)) })
                    if (!Prefs.isKids) IconButton(onClick = { nav(Screen.Groups(kind)) }) { Icon(Icons.Filled.FilterList, L("ניהול קבוצות")) }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(specials) { (g, label, _) -> FilterChip(group == g, { selectGroup(g) }, { Text(label) }) }
                    items(view.groups) { g ->
                        val lock = g in view.lockedGroups && !Parental.unlocked
                        FilterChip(group == g, { selectGroup(g) }, { Text((if (lock) "🔒 " else "") + "$g (${counts[g] ?: 0})", maxLines = 1) })
                    }
                }
                ContentArea(kind, shownItems, favs, all, false, nav, Modifier.weight(1f)) { menu = MenuTarget(kind, it, all) }
            }
        }
    }
    menu?.let { ItemMenu(it) { menu = null } }
}

@Composable
private fun CategoryRow(name: String, count: Int, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth().padding(vertical = 1.dp), selected = selected, onClick = onClick) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (locked) Icon(Icons.Filled.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name, Modifier.weight(1f).padding(horizontal = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            if (!locked) Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ContentArea(
    kind: Kind, items: List<Entry>, favs: Set<String>, all: List<Entry>, wide: Boolean, nav: (Screen) -> Unit,
    modifier: Modifier, onLong: (Entry) -> Unit,
) {
    val ctx = LocalContext.current
    when (kind) {
        Kind.LIVE -> {
            val chans = items.filterIsInstance<Channel>()
            var preview by remember { mutableStateOf<Channel?>(null) }
            val playAt: (Int) -> Unit = { i -> Launcher.play(ctx, chans.map { it.toPlay() }, i) }
            if (wide) {
                Row(modifier) {
                    ChannelList(chans, favs, preview?.key, Modifier.weight(1f), onLong) { c, i ->
                        if (preview?.key == c.key) playAt(i) else preview = c
                    }
                    PreviewPane(preview, chans, Modifier.weight(1f)) {
                        val i = chans.indexOfFirst { it.key == preview?.key }
                        if (i >= 0) playAt(i)
                    }
                }
            } else {
                ChannelList(chans, favs, null, modifier, onLong) { _, i -> playAt(i) }
            }
        }
        Kind.MOVIE -> PosterGrid(items, favs, modifier, onLong) { e -> Launcher.play(ctx, listOf((e as Channel).toPlay()), 0) }
        Kind.SERIES -> PosterGrid(items, favs, modifier, onLong) { e -> nav(Screen.Series(e as SeriesItem)) }
    }
}

@Composable
fun ChannelList(
    items: List<Channel>, favs: Set<String>, selectedKey: String?, modifier: Modifier,
    onLong: (Entry) -> Unit, onClick: (Channel, Int) -> Unit,
) {
    if (items.isEmpty()) {
        Box(modifier.fillMaxSize()) { Text(L("אין ערוצים"), Modifier.align(Alignment.Center)) }
        return
    }
    val epgVer = Epg.version.intValue
    LazyColumn(modifier, contentPadding = PaddingValues(6.dp)) {
        itemsIndexed(items) { i, c ->
            val now = remember(c.key, epgVer) { Epg.nowNext(c.epgId, c.name).first }
            FocusItem(
                Modifier.fillMaxWidth().padding(vertical = 1.dp),
                selected = c.key == selectedKey,
                onLongClick = { onLong(c) },
                onClick = { onClick(c, i) },
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.width(40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Box(Modifier.size(width = 52.dp, height = 34.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center) {
                        AsyncImage(c.logo, null, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        now?.let { Text(it.title, fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (c.hasCatchup) Text("⏪", fontSize = 11.sp)
                    if (c.key in favs) Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PreviewPane(ch: Channel?, queue: List<Channel>, modifier: Modifier, onFull: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember { PlayerFactory.create(ctx) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(Unit) { onDispose { player.release() } }
    DisposableEffect(owner, ch?.key) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> if (ch != null) {
                    player.setMediaItem(PlayerFactory.mediaItem(ch.url, ch.name))
                    player.prepare(); player.play()
                }
                Lifecycle.Event.ON_PAUSE -> player.stop()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val epgVer = Epg.version.intValue
    var shortEpg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    LaunchedEffect(ch?.key, epgVer) {
        shortEpg = emptyList()
        val p = Prefs.activePlaylist()
        val sid = ch?.streamId
        if (ch != null && Epg.forChannel(ch).isEmpty() && sid != null && p != null && p.isXtream) {
            shortEpg = withContext(Dispatchers.IO) { runCatching { Xtream(p).shortEpg(sid) }.getOrDefault(emptyList()) }
        }
    }
    val epg = remember(ch?.key, epgVer, shortEpg) {
        val now = System.currentTimeMillis()
        val full = if (ch != null) Epg.forChannel(ch) else emptyList()
        if (full.isNotEmpty()) full.filter { it.end > now - 6 * 3_600_000L }.take(12) else shortEpg
    }
    var dialog by remember { mutableStateOf<EpgEntry?>(null) }

    Column(modifier.fillMaxHeight().padding(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color.Black).clickable { onFull() }) {
            AndroidView(factory = { PlayerView(it).apply { useController = false; this.player = player } }, modifier = Modifier.fillMaxSize())
            if (ch == null) Text(L("בחר ערוץ לתצוגה מקדימה\nלחיצה נוספת – מסך מלא"), Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        if (ch != null) {
            Text(ch.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(ch.group + if (ch.hasCatchup) L(" · ⏪ צפייה לאחור זמינה") else "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            val now = System.currentTimeMillis()
            LazyColumn(Modifier.weight(1f)) {
                items(epg) { e ->
                    val live = now in e.start until e.end
                    val cu = Catchup.available(ch, e, now)
                    FocusItem(Modifier.fillMaxWidth(), onClick = { dialog = e }) {
                        Column(Modifier.padding(vertical = 4.dp, horizontal = 4.dp)) {
                            Text("${fmtDay(e.start)} ${fmtTime(e.start)}  ${e.title}" + if (cu && !live) "  ⏪" else "",
                                color = if (live) MaterialTheme.colorScheme.primary else if (e.end <= now) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                                fontWeight = if (live) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp, maxLines = 2)
                            if (live && e.end > e.start) LinearProgressIndicator(
                                progress = { ((now - e.start).toFloat() / (e.end - e.start)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                            )
                        }
                    }
                }
            }
            Button(onClick = onFull, modifier = Modifier.fillMaxWidth()) { Text(L("מסך מלא")) }
        }
    }
    val d = dialog
    if (d != null && ch != null) ProgramDialog(ch, d, queue) { dialog = null }
}

@Composable
fun PosterGrid(items: List<Entry>, favs: Set<String>, modifier: Modifier, onLong: (Entry) -> Unit, onClick: (Entry) -> Unit) {
    if (items.isEmpty()) {
        Box(modifier.fillMaxSize()) { Text(L("אין פריטים"), Modifier.align(Alignment.Center)) }
        return
    }
    val ver = Prefs.version.intValue
    LazyVerticalGrid(
        columns = GridCells.Adaptive(116.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItemsIndexed(items) { _, e ->
            val r = remember(e.key, ver) { Prefs.resume(e.key) }
            Poster(e.name, e.logo, e.key in favs, r?.let { it.first.toFloat() / it.second.coerceAtLeast(1) },
                width = null, onLong = { onLong(e) }) { onClick(e) }
        }
    }
}

// ============================================================ מועדפים
@Composable
private fun FavoritesSection(content: Content, ver: Int, nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val favs = remember(ver) { Prefs.favs() }
    val live = remember(content, ver) { Catalog.build(Kind.LIVE, content.live).items.filterIsInstance<Channel>().filter { it.key in favs } }
    val vod = remember(content, ver) { Catalog.build(Kind.MOVIE, content.movies).items.filterIsInstance<Channel>().filter { it.key in favs } }
    val ser = remember(content, ver) { Catalog.build(Kind.SERIES, content.series).items.filterIsInstance<SeriesItem>().filter { it.key in favs } }
    if (live.isEmpty() && vod.isEmpty() && ser.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text(L("אין מועדפים עדיין.\nלחיצה ארוכה על ערוץ, סרט או סדרה ← הוסף למועדפים."),
                Modifier.align(Alignment.Center).padding(24.dp), textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp)) {
        if (live.isNotEmpty()) {
            item { SectionTitle(L("ערוצים")) }
            itemsIndexed(live) { i, c ->
                EntryRow(c, true, onLong = { Prefs.toggleFav(c.key) }) { Launcher.play(ctx, live.map { it.toPlay() }, i) }
            }
        }
        if (vod.isNotEmpty()) {
            item { SectionTitle(L("סרטים")) }
            items(vod) { m -> EntryRow(m, true, onLong = { Prefs.toggleFav(m.key) }) { Launcher.play(ctx, listOf(m.toPlay()), 0) } }
        }
        if (ser.isNotEmpty()) {
            item { SectionTitle(L("סדרות")) }
            items(ser) { s -> EntryRow(s, true, onLong = { Prefs.toggleFav(s.key) }) { nav(Screen.Series(s)) } }
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun EntryRow(e: Entry, fav: Boolean, sub: String? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth().padding(vertical = 1.dp), onLongClick = onLong, onClick = onClick) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(e.logo, null, Modifier.size(width = 48.dp, height = 36.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub ?: e.group, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (fav) Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(16.dp))
        }
    }
}

// ============================================================ הקלטות
@Composable
private fun RecordingsSection(ver: Int) {
    val ctx = LocalContext.current
    val active by RecordingService.active.collectAsState()
    var tick by remember { mutableStateOf(0) }
    val files = remember(ver, active.size, tick) {
        RecordingService.dir(ctx).listFiles()?.filter { f -> f.isFile && active.values.none { it.file == f.absolutePath } }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    val sched = remember(ver) { Scheduler.list() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        if (active.isNotEmpty()) {
            item { SectionTitle(L("מקליט עכשיו")) }
            items(active.values.toList()) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⏺", color = Color(0xFFFF5252))
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(r.title, maxLines = 1)
                        Text(L("{0} MB · עד {1}", r.bytes / 1_000_000, fmtTime(r.endsAt)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { RecordingService.stop(ctx, r.id) }) { Icon(Icons.Filled.Stop, L("עצור")) }
                }
            }
        }
        if (sched.isNotEmpty()) {
            item { SectionTitle(L("מתוזמן")) }
            items(sched) { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (s.type == "rec") "⏺" else "🔔")
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(s.title, maxLines = 1)
                        Text("${fmtDay(s.start)} ${fmtTime(s.start)}–${fmtTime(s.end)} · ${s.channel}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { Scheduler.cancel(ctx, s.id) }) { Icon(Icons.Filled.Delete, L("בטל")) }
                }
            }
        }
        item { SectionTitle(L("הקלטות שמורות")) }
        if (files.isEmpty()) item {
            Text(L("אין הקלטות. לחץ על ⏺ בנגן או על תוכנית בלוח השידורים כדי להקליט."), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(files) { f: File ->
            FocusItem(Modifier.fillMaxWidth(), onClick = {
                Launcher.play(ctx, listOf(PlayItem("rec:${f.name}", f.nameWithoutExtension.replace("__", " · "),
                    android.net.Uri.fromFile(f).toString(), Kind.MOVIE, isLocal = true)), 0)
            }) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.nameWithoutExtension.replace("__", " · "), maxLines = 2)
                        Text("${f.length() / 1_000_000} MB · ${fmtDay(f.lastModified())} ${fmtTime(f.lastModified())}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { f.delete(); tick++ }) { Icon(Icons.Filled.Delete, L("מחק")) }
                }
            }
        }
    }
}

// ============================================================ חיפוש כללי
@Composable
private fun SearchSection(content: Content, ver: Int, nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    val liveAll = remember(content, ver) { Catalog.build(Kind.LIVE, content.live).items.filterIsInstance<Channel>() }
    val vodAll = remember(content, ver) { Catalog.build(Kind.MOVIE, content.movies).items.filterIsInstance<Channel>() }
    val serAll = remember(content, ver) { Catalog.build(Kind.SERIES, content.series).items.filterIsInstance<SeriesItem>() }
    val live = remember(q, liveAll) { if (q.length < 2) emptyList() else liveAll.filter { it.name.contains(q, true) }.take(60) }
    val vod = remember(q, vodAll) { if (q.length < 2) emptyList() else vodAll.filter { it.name.contains(q, true) }.take(60) }
    val ser = remember(q, serAll) { if (q.length < 2) emptyList() else serAll.filter { it.name.contains(q, true) }.take(60) }
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(L("חפש ערוץ, סרט או סדרה…")) })
        LazyColumn(Modifier.fillMaxSize()) {
            if (live.isNotEmpty()) {
                item { SectionTitle(L("ערוצים ({0})", live.size)) }
                itemsIndexed(live) { i, c -> EntryRow(c, false) { Launcher.play(ctx, live.map { it.toPlay() }, i) } }
            }
            if (vod.isNotEmpty()) {
                item { SectionTitle(L("סרטים ({0})", vod.size)) }
                items(vod) { m -> EntryRow(m, false) { Launcher.play(ctx, listOf(m.toPlay()), 0) } }
            }
            if (ser.isNotEmpty()) {
                item { SectionTitle(L("סדרות ({0})", ser.size)) }
                items(ser) { s -> EntryRow(s, false) { nav(Screen.Series(s)) } }
            }
            if (q.length >= 2 && live.isEmpty() && vod.isEmpty() && ser.isEmpty()) {
                item { Text(L("לא נמצאו תוצאות"), Modifier.padding(16.dp)) }
            }
        }
    }
}
