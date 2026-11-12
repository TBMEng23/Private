package com.tbm.iptv.ui

import com.tbm.iptv.L

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ViewAgenda
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
import com.tbm.iptv.ui.TvButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.tbm.iptv.ui.TvFilterChip as FilterChip
import androidx.compose.material3.Icon
import com.tbm.iptv.ui.TvIconButton as IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.tbm.iptv.ui.TvOutlinedButton as OutlinedButton
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
import com.tbm.iptv.data.Countries
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
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
    LOCAL("קבצים במכשיר", Icons.Filled.Folder),
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
    val sections = Section.entries.filter { !kids || (it != Section.RECORDINGS && it != Section.MULTI && it != Section.LOCAL) }
    val extra = if (kids) 1 else 3
    BoxWithConstraints(Modifier.width(84.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
        // כל הפריטים נכנסים במסך בלי גלילה (בסטרימר הגלילה "הקפיצה" את החלק העליון החוצה)
        val itemH = ((maxHeight - 34.dp) / (sections.size + extra)).coerceIn(26.dp, 58.dp)
        Column(
            Modifier.fillMaxSize().padding(vertical = 2.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("TBM", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 15.sp,
                modifier = Modifier.height(24.dp))
            sections.forEach { s -> RailItem(s.label, s.icon, s == section, itemH) { onSelect(s) } }
            androidx.compose.material3.HorizontalDivider()
            RailItem(Prefs.profile().name, Icons.Filled.Person, false, itemH) { nav(Screen.Profiles) }
            if (!kids) {
                RailItem(L("רשימות"), Icons.AutoMirrored.Filled.PlaylistPlay, false, itemH) { nav(Screen.Playlists) }
                RailItem(L("הגדרות"), Icons.Filled.Settings, false, itemH) { nav(Screen.Settings()) }
            }
        }
    }
}

@Composable
private fun RailItem(label: String, icon: ImageVector, selected: Boolean, h: androidx.compose.ui.unit.Dp = 52.dp, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth().height(h).padding(vertical = 1.dp), selected = selected, onClick = onClick) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (h < 34.dp) 18.dp else if (h < 46.dp) 20.dp else 24.dp))
            // צפוף מאוד – רק אייקון (התווית מופיעה כשהפריט מסומן)
            if (h >= 34.dp || LocalItemFocused.current)
                Text(label, fontSize = if (h < 46.dp) 9.sp else 10.sp, maxLines = 1, textAlign = TextAlign.Center, lineHeight = 10.sp)
        }
    }
}

@Composable
private fun SectionContent(section: Section, content: Content, ver: Int, wide: Boolean, nav: (Screen) -> Unit, go: (Section) -> Unit) {
    when (section) {
        Section.HOME -> if (Prefs.isKids) KidsHome(content, ver, nav) else HomeRows(content, ver, wide, nav, go)
        Section.LIVE -> BrowseSection(Kind.LIVE, content.live, ver, nav)
        Section.GUIDE -> GuideSection(content)
        Section.MOVIES -> BrowseSection(Kind.MOVIE, content.movies, ver, nav)
        Section.SERIES -> BrowseSection(Kind.SERIES, content.series, ver, nav)
        Section.FAV -> FavoritesSection(content, ver, nav)
        Section.RECORDINGS -> RecordingsSection(ver)
        Section.MULTI -> MultiViewSection(content)
        Section.LOCAL -> LocalFilesSection()
        Section.SEARCH -> SearchSection(content, ver, nav)
    }
}

// ============================================================ דף הבית – שורות
@Composable
private fun HomeRows(content: Content, ver: Int, wide: Boolean, nav: (Screen) -> Unit, go: (Section) -> Unit) {
    val ctx = LocalContext.current
    val live = remember(content, ver, Catalog.ready.intValue) { Catalog.get(Kind.LIVE, content.live).items.filterIsInstance<Channel>() }
    val movies = remember(content, ver, Catalog.ready.intValue) { Catalog.get(Kind.MOVIE, content.movies).items }
    val series = remember(content, ver, Catalog.ready.intValue) { Catalog.get(Kind.SERIES, content.series).items }
    val recentCh = remember(live, ver) {
        val byKey = live.associateBy { it.key }
        Prefs.recentChannels().mapNotNull { byKey[it] }
    }
    val recent = remember(ver) { Prefs.recent() }
    val allowedKeys = remember(movies, series) { (movies.map { it.key } + series.map { it.key }).toHashSet() }
    val recentMovies = remember(recent, allowedKeys) { recent.filter { it.item.kind == Kind.MOVIE && it.item.key in allowedKeys } }
    val recentEps = remember(recent) { recent.filter { it.item.kind == Kind.SERIES }.distinctBy { it.item.seriesName } }
    val newMovies = remember(movies) { movies.filterIsInstance<Channel>().filter { it.added > 0 && isPreferred(it) }.sortedByDescending { it.added }.take(30) }
    val newSeries = remember(series) { series.filterIsInstance<SeriesItem>().filter { it.added > 0 && isPreferred(it) }.sortedByDescending { it.added }.take(30) }
    // המדף: סדרות חדשות ועונות/פרקים חדשים (מה שהשתנה אצל הספק מאז הטעינה הקודמת)
    val shelfVer = com.tbm.iptv.data.Shelf.ready.intValue
    val seriesByKey = remember(series) { series.associateBy { it.key } }
    val shelfNew = remember(seriesByKey, shelfVer) { com.tbm.iptv.data.Shelf.newSeries.mapNotNull { seriesByKey[it] } }
    val shelfUpd = remember(seriesByKey, shelfVer) { com.tbm.iptv.data.Shelf.updated.mapNotNull { seriesByKey[it.first] } }
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
                    item { QuickTile(L("קבצים במכשיר"), Icons.Filled.Folder) { go(Section.LOCAL) } }
                    item { QuickTile(L("מולטי-ויו"), Icons.Filled.GridView) { go(Section.MULTI) } }
                }
                item { QuickTile(L("חיפוש"), Icons.Filled.Search) { go(Section.SEARCH) } }
            }
        }
        if (shelfUpd.isNotEmpty()) item {
            EntryRowStrip(L("🔄 עונות ופרקים חדשים"), shelfUpd, Kind.SERIES, ctx, nav, { com.tbm.iptv.data.Shelf.tag(it.key) }) { menu = MenuTarget(Kind.SERIES, it, series) }
        }
        if (shelfNew.isNotEmpty()) item {
            EntryRowStrip(L("🆕 סדרות חדשות על המדף"), shelfNew, Kind.SERIES, ctx, nav, { "חדש" }) { menu = MenuTarget(Kind.SERIES, it, series) }
        }
        if (newMovies.isNotEmpty()) item {
            EntryRowStrip(L("🆕 סרטים חדשים על המדף"), newMovies, Kind.MOVIE, ctx, nav) { menu = MenuTarget(Kind.MOVIE, it, movies) }
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
            RankedStrip(L("סרטים פופולריים היום"), trendMovies, nav) { menu = MenuTarget(Kind.MOVIE, it, movies) }
        }
        if (trendSeries.isNotEmpty()) item {
            RankedStrip(L("סדרות פופולריות היום"), trendSeries, nav) { menu = MenuTarget(Kind.SERIES, it, series) }
        }
        if (newSeries.isNotEmpty()) item {
            EntryRowStrip(L("🆕 סדרות שעודכנו לאחרונה"), newSeries, Kind.SERIES, ctx, nav) { menu = MenuTarget(Kind.SERIES, it, series) }
        }
        if (tmdb.isBlank() && !Prefs.isKids) item {
            FocusItem(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), onClick = { nav(Screen.Settings("content")) }) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(L("🔥 סרטים וסדרות פופולריים היום"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TmdbBadge()
                    }
                    Text(L("כדי לראות את הטרנדים של היום מתוך התוכן שלך, הוסף מפתח TMDB חינמי. לחץ כאן להגדרה."),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
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
    title: String, list0: List<Entry>, kind: Kind, ctx: android.content.Context, nav: (Screen) -> Unit,
    tag: ((Entry) -> String?)? = null, onLong: (Entry) -> Unit,
) {
    val list = remember(list0) { dedupeTitles(list0) }
    RowTitle(title)
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list) { e ->
            val r = Prefs.resume(e.key)
            Poster(e.name, e.logo, Prefs.isFav(e.key), r?.let { it.first.toFloat() / it.second.coerceAtLeast(1) },
                rating = e.rating(), badge = tag?.invoke(e), focusKey = e.key, onLong = { onLong(e) }) {
                if (kind == Kind.SERIES && e is SeriesItem) nav(Screen.Series(e))
                else if (e is Channel) nav(Screen.Movie(e))
            }
        }
    }
}

@Composable
private fun Poster(
    name: String, logo: String?, fav: Boolean, progress: Float?, sub: String? = null,
    width: androidx.compose.ui.unit.Dp? = 118.dp, rating: Float = 0f, platform: com.tbm.iptv.data.Platforms.P? = null,
    badge: String? = null, focusKey: String? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit,
) {
    // בשלט: אחרי חזרה מדף הסרט/הסדרה – הסימון חוזר לפוסטר שנלחץ
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    if (focusKey != null && focusKey == FocusMemory.last) LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120); runCatching { fr.requestFocus() }; FocusMemory.last = null
    }
    FocusItem(Modifier.then(if (focusKey != null) Modifier.focusRequester(fr) else Modifier), onLongClick = onLong,
        onClick = { if (focusKey != null) FocusMemory.last = focusKey; onClick() }) {
        Column((if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).padding(4.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (logo == null) Text(name, Modifier.align(Alignment.Center).padding(6.dp), textAlign = TextAlign.Center, fontSize = 13.sp)
                AsyncImage(logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (fav) Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp))
                if (rating > 0f) Text("%.1f".format(rating), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp))
                if (platform != null) PlatformBadge(platform, Modifier.align(Alignment.BottomStart).padding(4.dp))
                // "עונה 3 חדשה" / "פרקים חדשים" / "חדש"
                if (badge != null) Text(L(badge), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color(0xFFE50914), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp))
                if (progress != null && progress > 0f) LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    color = Color(0xFFE53935),
                )
            }
            // בפרופיל ילדים: שם בעברית (מ-TMDB) גדול, והשם המקורי קטן מתחתיו
            val heName = if (Prefs.isKids) androidx.compose.runtime.produceState<String?>(null, name) {
                value = withContext(Dispatchers.IO) { com.tbm.iptv.data.Localize.hebTitle(name) }
            }.value else null
            if (heName != null) {
                Text(heName, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp))
                Text(name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Text(name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            sub?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ============================================================ דפדוף: קטגוריות + תוכן
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseSection(kind: Kind, all: List<Entry>, ver: Int, nav: (Screen) -> Unit) {
    val unlocked = Parental.unlocked
    val view = remember(all, ver, unlocked, Catalog.ready.intValue) { Catalog.get(kind, all) }
    val filtered = view.items
    val counts = remember(filtered) { filtered.groupingBy { it.group }.eachCount() }
    val favs = remember(ver) { Prefs.favs() }
    val favCount = remember(filtered, favs) { filtered.count { it.key in favs } }
    val hasNew = remember(filtered) { filtered.any { ((it as? Channel)?.added ?: (it as? SeriesItem)?.added ?: 0L) > 0L } }
    val recentKeys = remember(ver, filtered) {
        when (kind) {
            Kind.LIVE -> Prefs.recentChannels()
            Kind.MOVIE -> Prefs.recent().filter { it.item.kind == Kind.MOVIE }.map { it.item.key }
            Kind.SERIES -> {
                val byName = filtered.associateBy { it.name }
                Prefs.recent().filter { it.item.kind == Kind.SERIES }.mapNotNull { r -> r.item.seriesName?.let { byName[it]?.key } }.distinct()
            }
        }
    }
    var mode by remember(kind, ver) { mutableStateOf(Prefs.str("view_${kind.name}", if (kind == Kind.LIVE) "grid" else "rows")) }
    var drill by rememberSaveable(kind) { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var group by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(kind) { mutableStateOf("") }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var pickGroup by remember { mutableStateOf(false) }

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
        if (g != null && g in view.lockedGroups && !Parental.unlocked) Parental.require(L("קבוצה נעולה")) { group = g; drill = true }
        else { group = g; drill = true }
    }
    androidx.activity.compose.BackHandler(enabled = mode != "grid" && drill) { drill = false; group = null }
    val modeButton: @Composable () -> Unit = {
        Box {
            IconButton(onClick = { modeMenu = true }) {
                Icon(when (mode) { "rows" -> Icons.Filled.ViewAgenda; "cats" -> Icons.AutoMirrored.Filled.ViewList; else -> Icons.Filled.GridView }, L("תצוגה"))
            }
            androidx.compose.material3.DropdownMenu(modeMenu, { modeMenu = false }) {
                listOf("grid" to L("רשת + בחירת קטגוריה"), "rows" to L("שורות לפי קטגוריה"), "cats" to L("רשימת קטגוריות")).forEach { (m, t) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text((if (m == mode) "✓ " else "") + t) },
                        onClick = { modeMenu = false; mode = m; drill = false; group = null; Prefs.setStr("view_${kind.name}", m) })
                }
            }
        }
    }

    val specials = buildList {
        add(Triple<String?, String, Int>(null, L("הכל"), filtered.size))
        add(Triple<String?, String, Int>(FAV_GROUP, L("★ מועדפים"), favCount))
        if (kind == Kind.LIVE && recentKeys.isNotEmpty()) add(Triple<String?, String, Int>(RECENT_GROUP, L("🕘 אחרונים"), recentKeys.size))
        if (kind != Kind.LIVE && hasNew) add(Triple<String?, String, Int>(NEW_GROUP, L("🆕 נוספו לאחרונה"), 200))
        if (kind != Kind.LIVE && recentKeys.isNotEmpty()) add(Triple<String?, String, Int>(RECENT_GROUP, L("🕘 נצפו לאחרונה"), recentKeys.size))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val catW = maxWidth * 0.26f
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(catW).fillMaxHeight().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(kind.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (!Prefs.isKids) {
                            IconButton(onClick = { Prefs.activePlaylist()?.let { Repo.loadAsync(it, true) } }) { Icon(Icons.Filled.Refresh, L("רענון")) }
                            IconButton(onClick = { nav(Screen.Groups(kind)) }) { Icon(Icons.Filled.FilterList, L("ניהול קבוצות")) }
                        }
                        modeButton()
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
                if (mode == "rows" && group == null && query.isBlank())
                    RowsView(kind, filtered, view.groups, if (Parental.unlocked) emptySet() else view.lockedGroups, favs, nav,
                        Modifier.weight(1f), selectGroup) { menu = MenuTarget(kind, it, all) }
                else ContentArea(kind, shownItems, favs, all, true, nav, Modifier.weight(1f)) { menu = MenuTarget(kind, it, all) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text(L("חיפוש ב{0}", kind.label)) })
                    if (!Prefs.isKids) IconButton(onClick = { nav(Screen.Groups(kind)) }) { Icon(Icons.Filled.FilterList, L("ניהול קבוצות")) }
                    modeButton()
                }
                val curLabel0 = specials.firstOrNull { it.first == group }?.second ?: group ?: L("הכל")
                if (mode != "grid" && drill) {
                    // בתוך קטגוריה (מתצוגת שורות/קטגוריות) – כותרת עם חזרה
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { drill = false; group = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L("חזרה")) }
                        Text(curLabel0, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${shownItems.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                    }
                    ContentArea(kind, shownItems, favs, all, false, nav, Modifier.weight(1f)) { menu = MenuTarget(kind, it, all) }
                } else if (mode == "cats" && query.isBlank()) {
                    CatsList(specials, view.groups, counts, if (Parental.unlocked) emptySet() else view.lockedGroups, Modifier.weight(1f), selectGroup)
                } else if (mode == "rows" && query.isBlank()) {
                    RowsView(kind, filtered, view.groups, if (Parental.unlocked) emptySet() else view.lockedGroups, favs, nav,
                        Modifier.weight(1f), selectGroup) { menu = MenuTarget(kind, it, all) }
                } else if (mode != "grid") {
                    ContentArea(kind, shownItems, favs, all, false, nav, Modifier.weight(1f)) { menu = MenuTarget(kind, it, all) }
                } else {
                // כפתור קטגוריה אחד – פותח רשימה אנכית עם חיפוש (במקום גלילה לצדדים)
                val curLabel = specials.firstOrNull { it.first == group }?.second ?: group ?: L("הכל")
                val curCount = if (group != null && specials.none { it.first == group }) counts[group] ?: 0 else shownItems.size
                FocusItem(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), onClick = { pickGroup = true }) {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.FilterList, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(L("קטגוריה"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(curLabel, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("$curCount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("  ▾", fontSize = 18.sp)
                    }
                }
                ContentArea(kind, shownItems, favs, all, false, nav, Modifier.weight(1f)) { menu = MenuTarget(kind, it, all) }
                }
            }
        }
    }
    menu?.let { ItemMenu(it) { menu = null } }
    if (pickGroup) CategoryPicker(
        title = kind.label, specials = specials, groups = view.groups, counts = counts, current = group,
        locked = if (Parental.unlocked) emptySet() else view.lockedGroups,
        onPick = { g -> pickGroup = false; selectGroup(g) }, onDismiss = { pickGroup = false },
    )
}

/** בחירת קטגוריה – רשימה אנכית במסך מלא עם חיפוש */
@Composable
private fun CategoryPicker(
    title: String, specials: List<Triple<String?, String, Int>>, groups: List<String>, counts: Map<String, Int>,
    current: String?, locked: Set<String>, onPick: (String?) -> Unit, onDismiss: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, groups) { if (q.isBlank()) groups else groups.filter { it.contains(q, true) } }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPaddingCompat()) {
                ScreenHeader(L("קטגוריות – {0}", title), onDismiss)
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(L("חיפוש קטגוריה…")) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                    if (q.isBlank()) items(specials) { (g, label, n) ->
                        CategoryRow(label, n, current == g, false) { onPick(g) }
                    }
                    items(shown) { g ->
                        CategoryRow(g, counts[g] ?: 0, current == g, g in locked) { onPick(g) }
                    }
                    if (shown.isEmpty()) item {
                        Text(L("לא נמצאה קטגוריה"), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.systemBarsPaddingCompat(): Modifier = this.then(Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars))

@Composable
private fun CategoryRow(name: String, count: Int, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth().padding(vertical = 1.dp), selected = selected, onClick = onClick) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
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
    // ילדים: בדיקת סיווג גיל ברקע לסרטים/סדרות שמוצגים – מה שמעל הגיל נעלם מהרשימה
    if (Prefs.isKids && kind != Kind.LIVE) LaunchedEffect(items) { com.tbm.iptv.data.KidsFilter.scanAges(items.take(150), Prefs.profile().age) }
    when (kind) {
        Kind.LIVE -> {
            val chans = items.filterIsInstance<Channel>()
            var preview by remember { mutableStateOf<Channel?>(null) }
            val playAt: (Int) -> Unit = { i -> Launcher.play(ctx, chans.map { it.toPlay() }, i) }
            // חזרה מהנגן: הרשימה קופצת לערוץ האחרון שצפית בו (גם אם החלפת ערוצים במסך מלא)
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            var focusKey by remember { mutableStateOf<String?>(null) }
            var focusTick by remember { mutableStateOf(0) }
            val owner = LocalLifecycleOwner.current
            DisposableEffect(owner, chans) {
                val obs = LifecycleEventObserver { _, ev ->
                    if (ev == Lifecycle.Event.ON_RESUME) {
                        val k = Repo.queue.getOrNull(Repo.queueIndex)?.key
                        val i = chans.indexOfFirst { it.key == k }
                        if (i >= 0) {
                            if (wide) preview = chans[i]
                            focusKey = k; focusTick++
                        }
                    }
                }
                owner.lifecycle.addObserver(obs)
                onDispose { owner.lifecycle.removeObserver(obs) }
            }
            LaunchedEffect(focusTick) {
                val i = chans.indexOfFirst { it.key == focusKey }
                if (i >= 0) runCatching { listState.scrollToItem((i - 3).coerceAtLeast(0)) }
            }
            if (wide) {
                Row(modifier) {
                    ChannelList(chans, favs, preview?.key ?: focusKey, Modifier.weight(1f), onLong, listState, focusKey, focusTick) { c, i ->
                        if (preview?.key == c.key) playAt(i) else preview = c
                    }
                    PreviewPane(preview, chans, Modifier.weight(1f)) {
                        val i = chans.indexOfFirst { it.key == preview?.key }
                        if (i >= 0) playAt(i)
                    }
                }
            } else {
                ChannelList(chans, favs, focusKey, modifier, onLong, listState, focusKey, focusTick) { _, i -> playAt(i) }
            }
        }
        Kind.MOVIE -> PosterGrid(items, favs, modifier, onLong) { e -> nav(Screen.Movie(e as Channel)) }
        Kind.SERIES -> PosterGrid(items, favs, modifier, onLong) { e -> nav(Screen.Series(e as SeriesItem)) }
    }
}

@Composable
fun ChannelList(
    items: List<Channel>, favs: Set<String>, selectedKey: String?, modifier: Modifier,
    onLong: (Entry) -> Unit,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    focusKey: String? = null, focusTick: Int = 0,
    onClick: (Channel, Int) -> Unit,
) {
    if (items.isEmpty()) {
        Box(modifier.fillMaxSize()) { Text(L("אין ערוצים"), Modifier.align(Alignment.Center)) }
        return
    }
    val epgVer = Epg.version.intValue
    LazyColumn(modifier, state = state, contentPadding = PaddingValues(6.dp)) {
        itemsIndexed(items) { i, c ->
            val now = remember(c.key, epgVer) { Epg.nowNext(c.epgId, c.name).first }
            // בשלט: הפוקוס חוזר לערוץ האחרון שצפית בו
            val fr = remember { androidx.compose.ui.focus.FocusRequester() }
            if (c.key == focusKey) LaunchedEffect(focusTick) {
                kotlinx.coroutines.delay(150); runCatching { fr.requestFocus() }
            }
            FocusItem(
                Modifier.fillMaxWidth().padding(vertical = 1.dp).focusRequester(fr),
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
                width = null, rating = e.rating(), platform = com.tbm.iptv.data.Platforms.detect(e), focusKey = e.key, onLong = { onLong(e) }) { onClick(e) }
        }
    }
}

// ============================================================ מועדפים
@Composable
private fun FavoritesSection(content: Content, ver: Int, nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val favs = remember(ver) { Prefs.favs() }
    val live = remember(content, ver, Catalog.ready.intValue) { Catalog.get(Kind.LIVE, content.live).items.filterIsInstance<Channel>().filter { it.key in favs } }
    val vod = remember(content, ver, Catalog.ready.intValue) { Catalog.get(Kind.MOVIE, content.movies).items.filterIsInstance<Channel>().filter { it.key in favs } }
    val ser = remember(content, ver, Catalog.ready.intValue) { Catalog.get(Kind.SERIES, content.series).items.filterIsInstance<SeriesItem>().filter { it.key in favs } }
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
private fun LocalFilesSection() {
    val ctx = LocalContext.current
    val ver = Prefs.version.intValue
    val folders = remember(ver) { com.tbm.iptv.data.LocalMedia.folders() }
    var videos by remember { mutableStateOf<List<com.tbm.iptv.data.LocalVideo>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    LaunchedEffect(folders) {
        scanning = true
        videos = withContext(Dispatchers.IO) { folders.flatMap { runCatching { com.tbm.iptv.data.LocalMedia.scan(ctx, it) }.getOrDefault(emptyList()) } }
            .sortedWith(compareBy({ it.show ?: "" }, { it.season ?: 0 }, { it.episode ?: 0 }, { it.name.lowercase() }))
        scanning = false
    }
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Launcher.play(ctx, listOf(com.tbm.iptv.data.LocalMedia.fromPicked(ctx, uri)), 0)
    }
    val pickDir = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) com.tbm.iptv.data.LocalMedia.addFolder(ctx, uri)
    }
    fun play(list: List<com.tbm.iptv.data.LocalVideo>, i: Int) =
        Launcher.play(ctx, list.map { com.tbm.iptv.data.LocalMedia.toPlay(it) }, i)
    val movies = videos.filter { it.show == null }
    val shows = videos.filter { it.show != null }.groupBy { it.show!! }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { runCatching { pickFile.launch(arrayOf("video/*")) } }) { Text(L("🎬 בחר קובץ")) }
                OutlinedButton(onClick = { runCatching { pickDir.launch(null) } }) { Text(L("📁 הוסף תיקייה")) }
            }
            Text(L("סרטים וסדרות מהמכשיר, מכרטיס זיכרון או מ-USB. כתוביות בעברית נמצאות ומתורגמות אוטומטית, וקובץ כתוביות באותו שם ליד הסרט נטען לבד."),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (folders.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(folders) { f ->
                    FilterChip(selected = true, onClick = {}, label = { Text("📁 " + com.tbm.iptv.data.LocalMedia.folderName(f), maxLines = 1) },
                        trailingIcon = { IconButton(onClick = { com.tbm.iptv.data.LocalMedia.removeFolder(ctx, f) }, Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, L("הסר"), Modifier.size(16.dp)) } })
                }
            }
        }
        if (scanning) item { Text(L("סורק…"), modifier = Modifier.padding(8.dp)) }
        if (!scanning && folders.isNotEmpty() && videos.isEmpty()) item { Text(L("לא נמצאו קבצי וידאו בתיקיות"), modifier = Modifier.padding(8.dp)) }
        if (movies.isNotEmpty()) {
            item { SectionTitle(L("🎬 סרטים ({0})", movies.size)) }
            items(movies, key = { it.key }) { v -> LocalRow(v, Prefs.resume(v.key)) { play(movies, movies.indexOf(v)) } }
        }
        shows.forEach { (show, eps) ->
            item(key = "sh:$show") { SectionTitle("📺 $show (${eps.size})") }
            items(eps, key = { it.key }) { v -> LocalRow(v, Prefs.resume(v.key)) { play(eps, eps.indexOf(v)) } }
        }
    }
}

@Composable
private fun LocalRow(v: com.tbm.iptv.data.LocalVideo, r: Pair<Long, Long>?, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth().padding(vertical = 2.dp), onClick = onClick) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                val t = if (v.season != null) "S%02dE%02d · ".format(v.season, v.episode ?: 0) + v.title else v.title
                Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(listOfNotNull("%.1f GB".format(v.size / 1e9).takeIf { v.size > 0 }, v.folder,
                    if (v.subtitle != null) L("כתוביות מקומיות ✓") else null).joinToString(" · "),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (r != null && r.second > 0) LinearProgressIndicator(progress = { (r.first.toFloat() / r.second).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp))
            }
        }
    }
}

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
    // בחירה מרובה ומחיקה עם אישור
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var confirm by remember { mutableStateOf<List<File>?>(null) }
    LaunchedEffect(files) { selected.retainAll(files.map { it.absolutePath }.toSet()); if (files.isEmpty()) selecting = false }
    confirm?.let { list ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (list.size == 1) L("למחוק את ההקלטה?") else L("למחוק {0} הקלטות?", list.size)) },
            text = { Text(if (list.size == 1) list[0].nameWithoutExtension.replace("__", " · ") else
                L("{0} MB יפונו מהמכשיר. לא ניתן לשחזר.", list.sumOf { it.length() } / 1_000_000)) },
            confirmButton = { com.tbm.iptv.ui.TvTextButton(onClick = {
                list.forEach { it.delete() }
                selected.clear(); selecting = false; confirm = null; tick++
            }) { Text(L("מחק"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { com.tbm.iptv.ui.TvTextButton(onClick = { confirm = null }) { Text(L("ביטול")) } },
        )
    }
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
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle(L("הקלטות שמורות")) }
                if (files.isNotEmpty()) {
                    if (selecting) {
                        com.tbm.iptv.ui.TvTextButton(onClick = {
                            if (selected.size == files.size) selected.clear()
                            else { selected.clear(); selected.addAll(files.map { it.absolutePath }) }
                        }) { Text(if (selected.size == files.size) L("נקה בחירה") else L("בחר הכל")) }
                        com.tbm.iptv.ui.TvTextButton(enabled = selected.isNotEmpty(), onClick = {
                            confirm = files.filter { it.absolutePath in selected }
                        }) { Text(L("מחק ({0})", selected.size), color = if (selected.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray) }
                        com.tbm.iptv.ui.TvTextButton(onClick = { selecting = false; selected.clear() }) { Text(L("ביטול")) }
                    } else com.tbm.iptv.ui.TvTextButton(onClick = { selecting = true }) { Text(L("☑ בחירה מרובה")) }
                }
            }
        }
        if (files.isEmpty()) item {
            Text(L("אין הקלטות. לחץ על ⏺ בנגן או על תוכנית בלוח השידורים כדי להקליט."), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(files) { f: File ->
            val path = f.absolutePath
            val isSel = path in selected
            FocusItem(Modifier.fillMaxWidth(), selected = isSel, onLongClick = {
                selecting = true; if (!isSel) selected.add(path)
            }, onClick = {
                if (selecting) { if (isSel) selected.remove(path) else selected.add(path) }
                else Launcher.play(ctx, listOf(PlayItem("rec:${f.name}", f.nameWithoutExtension.replace("__", " · "),
                    android.net.Uri.fromFile(f).toString(), Kind.MOVIE, isLocal = true)), 0)
            }) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selecting) androidx.compose.material3.Checkbox(checked = isSel, onCheckedChange = { c ->
                        if (c) selected.add(path) else selected.remove(path)
                    })
                    Column(Modifier.weight(1f)) {
                        Text(f.nameWithoutExtension.replace("__", " · "), maxLines = 2)
                        Text("${f.length() / 1_000_000} MB · ${fmtDay(f.lastModified())} ${fmtTime(f.lastModified())}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!selecting) IconButton(onClick = { confirm = listOf(f) }) { Icon(Icons.Filled.Delete, L("מחק")) }
                }
            }
        }
    }
}

// ============================================================ שדה חיפוש: מקלדת עברית בטלוויזיה + חיפוש קולי
@Composable
fun SearchInput(q: String, placeholder: String = L("חפש ערוץ, סרט או סדרה…"), onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val tv = com.tbm.iptv.data.Device.isTv
    // בטלוויזיה – מקלדת פנימית (עברית/אנגלית) במקום מקלדת המערכת שאין בה עברית
    var useOsk by rememberSaveable { mutableStateOf(tv) }
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val txt = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!txt.isNullOrBlank()) onChange(txt)
    }
    fun startVoice() {
        val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, if (com.tbm.iptv.I18n.isEn) "en-US" else "he-IL")
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, if (com.tbm.iptv.I18n.isEn) "en-US" else "he-IL")
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, L("מה לחפש?"))
        runCatching { voice.launch(i) }.onFailure {
            android.widget.Toast.makeText(ctx, L("חיפוש קולי לא זמין במכשיר הזה"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(q, onChange, singleLine = true, readOnly = useOsk, modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder) },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, L("נקה")) } })
            IconButton(onClick = { startVoice() }) { Icon(Icons.Filled.Mic, L("חיפוש קולי"), tint = MaterialTheme.colorScheme.primary) }
            if (tv) IconButton(onClick = { useOsk = !useOsk }) { Icon(Icons.Filled.Keyboard, L("מקלדת")) }
        }
        if (useOsk) HebKeyboard(q, onChange)
    }
}

@Composable
private fun HebKeyboard(q: String, onChange: (String) -> Unit) {
    var heb by rememberSaveable { mutableStateOf(!com.tbm.iptv.I18n.isEn) }
    val rows = if (heb) listOf("1234567890", "קראטוןםפ", "שדגכעיחלךף", "זסבהנמצתץ")
        else listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")
    @Composable
    fun Key(label: String, w: Float = 1f, onClick: () -> Unit) {
        Box(Modifier.padding(2.dp).height(40.dp).width((38 * w).dp)) {
            FocusItem(Modifier.fillMaxSize(), onClick = onClick) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center) { Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { r -> Row { r.forEach { ch -> Key(ch.toString()) { onChange(q + ch) } } } }
        Row {
            Key(if (heb) "EN" else "עב", 1.4f) { heb = !heb }
            Key(L("רווח"), 4f) { onChange("$q ") }
            Key("⌫", 1.4f) { if (q.isNotEmpty()) onChange(q.dropLast(1)) }
            Key(L("נקה"), 1.6f) { onChange("") }
        }
    }
}

// ============================================================ חיפוש כללי
@Composable
private fun SearchSection(content: Content, ver: Int, nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    val liveAll = remember(content, ver, Catalog.ready.intValue) { Catalog.get(Kind.LIVE, content.live).items.filterIsInstance<Channel>() }
    val vodAll = remember(content, ver, Catalog.ready.intValue) { Catalog.get(Kind.MOVIE, content.movies).items.filterIsInstance<Channel>() }
    val serAll = remember(content, ver, Catalog.ready.intValue) { Catalog.get(Kind.SERIES, content.series).items.filterIsInstance<SeriesItem>() }
    val live = remember(q, liveAll) { if (q.length < 2) emptyList() else liveAll.filter { it.name.contains(q, true) }.take(60) }
    val vod = remember(q, vodAll) { if (q.length < 2) emptyList() else vodAll.filter { it.name.contains(q, true) }.take(60) }
    val ser = remember(q, serAll) { if (q.length < 2) emptyList() else serAll.filter { it.name.contains(q, true) }.take(60) }
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        SearchInput(q) { q = it }
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


fun Entry.rating(): Float = (this as? Channel)?.rating ?: (this as? SeriesItem)?.rating ?: 0f
private fun Entry.addedAt(): Long = (this as? Channel)?.added ?: (this as? SeriesItem)?.added ?: 0L

// ============================================================ תצוגת קטגוריות (כמו Televizo)
@Composable
private fun CatsList(
    specials: List<Triple<String?, String, Int>>, groups: List<String>, counts: Map<String, Int>, locked: Set<String>,
    modifier: Modifier, onPick: (String?) -> Unit,
) {
    LazyColumn(modifier.fillMaxSize()) {
        items(specials) { (g, label, n) -> CatLine(label, if (g == NEW_GROUP) null else n, false, g == FAV_GROUP) { onPick(g) } }
        items(groups) { g -> CatLine(g, counts[g] ?: 0, g in locked, false) { onPick(g) } }
    }
}

@Composable
private fun CatLine(name: String, count: Int?, locked: Boolean, accent: Boolean, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                if (locked) Icon(Icons.Filled.Lock, null, Modifier.size(16.dp).padding(end = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(name, Modifier.weight(1f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (accent) Gold else MaterialTheme.colorScheme.onBackground)
                if (count != null && !locked) Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.HorizontalDivider(color = Color(0x22FFFFFF))
        }
    }
}

// ============================================================ תצוגת שורות (כמו MyTVOnline)
@Composable
private fun RowsView(
    kind: Kind, items: List<Entry>, groups: List<String>, locked: Set<String>, favs: Set<String>, nav: (Screen) -> Unit,
    modifier: Modifier, onSeeAll: (String?) -> Unit, onLong: (Entry) -> Unit,
) {
    val byGroup = remember(items) { items.groupBy { it.group } }
    val shownGroups = remember(groups, byGroup, locked) { groups.filter { it !in locked && byGroup[it] != null } }
    val favItems = remember(items, favs) { items.filter { it.key in favs } }
    // תוכן "מומלץ": רק בשפות שהמשתמש מבין (בלי ערבית וכו')
    val pref = remember(items) { items.filter { isPreferred(it) } }
    var curated by remember(kind) { mutableStateOf(kind != Kind.LIVE && Prefs.str("rows_src_${kind.name}", "curated") == "curated") }
    var top by remember(items) { mutableStateOf<List<Entry>>(emptyList()) }
    var genreRows by remember(items) { mutableStateOf<List<Pair<String, List<Entry>>>>(emptyList()) }
    LaunchedEffect(items, kind) {
        if (kind == Kind.LIVE) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            top = runCatching { if (kind == Kind.MOVIE) Trending.movies(pref) else Trending.series(pref) }.getOrDefault(emptyList())
            val rows = ArrayList<Pair<String, List<Entry>>>()
            for (g in Trending.genres) {
                val l = runCatching { Trending.byGenre(g, kind == Kind.SERIES, pref) }.getOrDefault(emptyList())
                if (l.size >= 4) { rows += L(g.he) to l; genreRows = rows.toList() }
            }
        }
    }
    val newest = remember(pref) { pref.filter { it.addedAt() > 0 }.sortedByDescending { it.addedAt() }.take(40) }
    // שורה לכל פלטפורמה (Netflix, Prime, Disney+…) לפי מה שהספק מסמן
    val platformRows = remember(pref) {
        if (kind == Kind.LIVE) emptyList() else {
            val g = pref.groupBy { com.tbm.iptv.data.Platforms.detect(it) }
            com.tbm.iptv.data.Platforms.all.mapNotNull { p ->
                g[p]?.takeIf { it.size >= 6 }?.let { l -> L("ב-{0}", p.name) to l.sortedByDescending { it.addedAt() }.take(40) }
            }
        }
    }
    val topRated = remember(pref) { pref.filter { it.rating() >= 7.5f }.sortedByDescending { it.rating() }.take(40) }
    val resume = remember(items, Prefs.version.intValue) {
        val rm = Prefs.resumeMap()
        if (rm.isEmpty()) emptyList()
        else items.mapNotNull { e -> rm[e.key]?.let { r -> if (r.first < r.second * 0.95) e else null } }.take(30)
    }
    // המדף (סדרות): עונות/פרקים חדשים וסדרות חדשות
    val ctx2 = LocalContext.current
    val shelfV = com.tbm.iptv.data.Shelf.ready.intValue
    val itemsByKey = remember(items) { if (kind == Kind.SERIES) items.associateBy { it.key } else emptyMap() }
    val shelfUpd2 = remember(itemsByKey, shelfV) { com.tbm.iptv.data.Shelf.updated.mapNotNull { itemsByKey[it.first] } }
    val shelfNew2 = remember(itemsByKey, shelfV) { com.tbm.iptv.data.Shelf.newSeries.mapNotNull { itemsByKey[it] } }
    val heroes = remember(pref, top) {
        if (kind == Kind.LIVE) emptyList()
        else (top.ifEmpty { newest.ifEmpty { topRated } }).filter { it.logo != null }.take(8)
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (kind != Kind.LIVE) item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.tbm.iptv.ui.TvFilterChip(selected = curated, onClick = { curated = true; Prefs.setStrQuiet("rows_src_${kind.name}", "curated") },
                    label = { Text(L("✨ מומלץ")) })
                com.tbm.iptv.ui.TvFilterChip(selected = !curated, onClick = { curated = false; Prefs.setStrQuiet("rows_src_${kind.name}", "provider") },
                    label = { Text(L("📂 קטגוריות הספק")) })
            }
        }
        if (heroes.isNotEmpty()) item { FeaturedHero(heroes, kind, favs, nav) }
        if (curated) {
            if (top.isNotEmpty()) item {
                RankedStrip(if (kind == Kind.MOVIE) L("Top 10 סרטים היום") else L("Top 10 סדרות היום"), top.take(10), nav, onLong)
            }
            if (resume.isNotEmpty()) item { CategoryStrip(L("המשך צפייה"), resume, kind, favs, nav, null, onLong) }
            if (shelfUpd2.isNotEmpty()) item { EntryRowStrip(L("🔄 עונות ופרקים חדשים"), shelfUpd2, kind, ctx2, nav, { com.tbm.iptv.data.Shelf.tag(it.key) }, onLong) }
            if (shelfNew2.isNotEmpty()) item { EntryRowStrip(L("🆕 סדרות חדשות על המדף"), shelfNew2, kind, ctx2, nav, { "חדש" }, onLong) }
            if (newest.isNotEmpty()) item { CategoryStrip(if (kind == Kind.MOVIE) L("🆕 סרטים חדשים") else L("🆕 סדרות חדשות"), newest, kind, favs, nav, { onSeeAll(NEW_GROUP) }, onLong) }
            if (favItems.isNotEmpty()) item { CategoryStrip(L("★ הרשימה שלי"), favItems, kind, favs, nav, { onSeeAll(FAV_GROUP) }, onLong) }
            if (topRated.size >= 4) item { CategoryStrip(L("⭐ הכי מדורגים"), topRated, kind, favs, nav, null, onLong) }
            items(platformRows, key = { "p:" + it.first }) { (name, list) -> CategoryStrip(name, list, kind, favs, nav, null, onLong) }
            items(genreRows, key = { "g:" + it.first }) { (name, list) -> CategoryStrip(name, list, kind, favs, nav, null, onLong) }
            if (Prefs.tmdbKey.isBlank()) item {
                Text(L("💡 עם מפתח TMDB חינמי (הגדרות ← תצוגה) יופיעו כאן גם Top 10 של היום ושורות ז'אנר מדויקות יותר."),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            item { Text(L("קטגוריות הספק"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 22.dp)) }
        } else if (favItems.isNotEmpty()) item { CategoryStrip(L("★ מועדפים"), favItems, kind, favs, nav, { onSeeAll(FAV_GROUP) }, onLong) }
        items(shownGroups, key = { it }) { g ->
            CategoryStrip(g, byGroup[g].orEmpty(), kind, favs, nav, { onSeeAll(g) }, onLong)
        }
        if (shownGroups.isEmpty()) item { Text(L("אין פריטים"), Modifier.padding(24.dp)) }
    }
}

private val arabicRx = Regex("[\\u0600-\\u06FF]")
private val okCountries = setOf("IL", "EN", "US", "GB", "CA", "AU", "IE", "NZ", Countries.OTHER)

/** תוכן בשפה שהמשתמש קורא (עברית/אנגלית או מדינות שבחר בסינון) – לבאנר ולשורות "מומלץ" */
private fun isPreferred(e: Entry): Boolean {
    if (arabicRx.containsMatchIn(e.name) || arabicRx.containsMatchIn(e.group)) return false
    val c = Countries.of(e.group)
    val chosen = Prefs.str("pref_countries").split(",").filter { it.isNotBlank() }.toSet()
    return c in (if (chosen.isEmpty()) okCountries else chosen + Countries.OTHER)
}

@Composable
private fun FeaturedHero(list0: List<Entry>, kind: Kind, favs: Set<String>, nav: (Screen) -> Unit) {
    val list = remember(list0) { dedupeTitles(list0) }
    // אפשר לגלול הצידה בעצמך; כשלא נוגעים – מתחלף לבד כל 8 שניות
    val pager = androidx.compose.foundation.pager.rememberPagerState { list.size }
    LaunchedEffect(list, pager.currentPage, pager.isScrollInProgress) {
        if (!pager.isScrollInProgress && list.size > 1) {
            kotlinx.coroutines.delay(8000)
            runCatching { pager.animateScrollToPage((pager.currentPage + 1) % list.size) }
        }
    }
    Box(Modifier.fillMaxWidth().height(440.dp).padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)) {
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize(), key = { list[it].key }) { page ->
            val e = list[page.coerceIn(0, list.size - 1)]
            val open = { if (e is SeriesItem) nav(Screen.Series(e)) else if (e is Channel) nav(Screen.Movie(e)) }
            Box(Modifier.fillMaxSize().clickable { open() }) {
                AsyncImage(e.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                    0.55f to Color.Transparent, 1f to Color(0xEE0B0C10))))
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(e.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (e.rating() > 0f) Text("★ %.1f".format(e.rating()), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(e.group, color = Color(0xFFCFD0D6), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = open, shape = RoundedCornerShape(6.dp)) {
                            Icon(Icons.Filled.Info, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(L("פרטים"))
                        }
                        val fav = e.key in favs
                        OutlinedButton(onClick = { Prefs.toggleFav(e.key) }, shape = RoundedCornerShape(6.dp)) {
                            Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, Modifier.size(18.dp), tint = if (fav) Color(0xFFE53935) else Color.White)
                            Spacer(Modifier.width(6.dp)); Text(L("מועדף"), color = Color.White)
                        }
                    }
                }
            }
        }
        // נקודות – לחיצה על נקודה קופצת אליה
        val scope = rememberCoroutineScope()
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            list.indices.forEach { i ->
                val cur = i == pager.currentPage
                Box(Modifier.size(if (cur) 9.dp else 7.dp).clip(RoundedCornerShape(50))
                    .background(if (cur) Color.White else Color(0x66FFFFFF))
                    .clickable { scope.launch { pager.animateScrollToPage(i) } })
            }
        }
    }
}

@Composable
private fun CategoryStrip(
    title: String, list0: List<Entry>, kind: Kind, favs: Set<String>, nav: (Screen) -> Unit, onSeeAll: (() -> Unit)?, onLong: (Entry) -> Unit,
) {
    val ctx = LocalContext.current
    val list = remember(list0) { dedupeTitles(list0) }
    val shown = remember(list) { list.take(30) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$title (${list.size})", fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (onSeeAll != null) com.tbm.iptv.ui.TvTextButton(onClick = onSeeAll) { Text(L("הכל ›"), fontWeight = FontWeight.Bold) }
        else Spacer(Modifier.height(44.dp))
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (kind == Kind.LIVE) {
            val chans = shown.filterIsInstance<Channel>()
            itemsIndexed(chans) { i, c -> ChannelCard(c, c.key in favs, { onLong(c) }) {
                val all = list.filterIsInstance<Channel>()
                Launcher.play(ctx, all.map { it.toPlay() }, i)
            } }
        } else items(shown) { e ->
            val r = remember(e.key) { Prefs.resume(e.key) }
            Poster(e.name, e.logo, e.key in favs, r?.let { it.first.toFloat() / it.second.coerceAtLeast(1) },
                width = 124.dp, rating = e.rating(), platform = com.tbm.iptv.data.Platforms.detect(e), focusKey = e.key, onLong = { onLong(e) }) {
                if (e is SeriesItem) nav(Screen.Series(e)) else if (e is Channel) nav(Screen.Movie(e))
            }
        }
    }
}

@Composable
private fun ChannelCard(c: Channel, fav: Boolean, onLong: () -> Unit, onClick: () -> Unit) {
    val epgVer = Epg.version.intValue
    val now = remember(c.key, epgVer) { Epg.nowNext(c.epgId, c.name).first }
    FocusItem(onLongClick = onLong, onClick = onClick) {
        Column(Modifier.width(170.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp)) {
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                AsyncImage(c.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                if (fav) Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.align(Alignment.TopEnd).size(14.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(c.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now?.title ?: L("אין מידע שידור"), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val t = System.currentTimeMillis()
            LinearProgressIndicator(
                progress = { if (now != null && now.end > now.start) ((t - now.start).toFloat() / (now.end - now.start)).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
            )
        }
    }
}


@Composable
private fun TmdbBadge() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("TMDB", color = Color(0xFF90CEA1), fontWeight = FontWeight.Black, fontSize = 15.sp)
        Spacer(Modifier.width(5.dp))
        Box(Modifier.size(width = 34.dp, height = 12.dp).background(
            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF90CEA1), Color(0xFF01B4E4))), RoundedCornerShape(6.dp)))
    }
}

/** שורת טרנדים עם מספרי דירוג גדולים (כמו MyTVOnline / נטפליקס) */
@Composable
private fun RankedStrip(title: String, list0: List<Entry>, nav: (Screen) -> Unit, onLong: (Entry) -> Unit) {
    val list = remember(list0) { dedupeTitles(list0) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TmdbBadge()
    }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(list.take(20)) { i, e ->
                FocusItem(onLongClick = { onLong(e) }, onClick = {
                    if (e is SeriesItem) nav(Screen.Series(e)) else if (e is Channel) nav(Screen.Movie(e))
                }) {
                    Column(Modifier.width(168.dp).padding(4.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text("${i + 1}", fontSize = 64.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                style = androidx.compose.ui.text.TextStyle(color = Color.White, drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)),
                                modifier = Modifier.width(if (i >= 9) 62.dp else 40.dp))
                            Box(Modifier.weight(1f).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                if (e.logo == null) Text(e.name, Modifier.align(Alignment.Center).padding(6.dp), textAlign = TextAlign.Center, fontSize = 12.sp)
                                AsyncImage(e.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                com.tbm.iptv.data.Platforms.detect(e)?.let { PlatformBadge(it, Modifier.align(Alignment.BottomStart).padding(4.dp)) }
                                if (e.rating() > 0f) Text("%.1f".format(e.rating()), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        Text(com.tbm.iptv.data.Localize.cleanTitle(e.name), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 40.dp, top = 6.dp))
                        Text(e.group, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 40.dp))
                    }
                }
            }
        }
    }
}


/** תווית פלטפורמה על פוסטר (טקסט בצבעי המותג, בלי לוגו) */
@Composable
fun PlatformBadge(p: com.tbm.iptv.data.Platforms.P, modifier: Modifier = Modifier) {
    Text(p.short, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1,
        modifier = modifier.background(Color(p.color), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
}


// ============================================================ מסך בית לילדים
/** מסך פתיחה צבעוני לפרופיל ילדים: מה חדש, להמשיך לצפות, ערוצים, סרטים, סדרות ונושאים – רק מתוך התוכן המותר */
@Composable
private fun KidsHome(content: Content, ver: Int, nav: (Screen) -> Unit) {
    val prof = remember(ver) { Prefs.profile() }
    val live = remember(content, ver, Catalog.ready.intValue) { Catalog.get(Kind.LIVE, content.live).items.filterIsInstance<Channel>() }
    // דיבוב עברי קודם
    val movies = remember(content, ver, Catalog.ready.intValue) { Catalog.get(Kind.MOVIE, content.movies).items.sortedByDescending { com.tbm.iptv.data.Sources.hebDub(it) } }
    val series = remember(content, ver, Catalog.ready.intValue) { Catalog.get(Kind.SERIES, content.series).items.sortedByDescending { com.tbm.iptv.data.Sources.hebDub(it) } }
    val favs = remember(ver) { Prefs.favs() }
    val kids = remember(ver) { Prefs.kidsNames() }
    val child = remember(ver) { Prefs.kidChild.takeIf { it in kids } }
    val favItems = remember(favs, live, movies, series) { (live + movies + series).filter { it.key in favs } }
    LaunchedEffect(movies, series) { com.tbm.iptv.data.KidsFilter.scanAges(movies.take(80) + series.take(80), prof.age) }
    // שורות לפי דמויות ונושאים
    val themeRows = remember(movies, series) {
        com.tbm.iptv.data.KidsThemes.all.mapNotNull { (title, rx) ->
            val l = (movies + series).filter { e -> rx.containsMatchIn(e.name) || ((e as? SeriesItem)?.plot?.let { rx.containsMatchIn(it) } == true) }
                .take(30)
            if (l.size >= 3) L(title) to l else null
        }
    }
    val fresh = remember(movies, series) { (movies + series).filter { it.addedAt() > 0 }.sortedByDescending { it.addedAt() }.take(30) }
    val resume = remember(movies, ver) {
        val recent = Prefs.recent()
        val keys = recent.map { it.item.key }.toSet()
        movies.filter { it.key in keys } + series.filter { s -> recent.any { it.item.seriesName == s.name } }
    }
    val topicRows = remember(live, movies, series) {
        prof.topics.mapNotNull { t ->
            val rx = com.tbm.iptv.data.KidsFilter.topicRx[t] ?: return@mapNotNull null
            val l = live.filter { rx.containsMatchIn(it.group) || rx.containsMatchIn(it.name) }.take(30)
            val title = when (t) { "nature" -> L("🌿 טבע ובעלי חיים"); "sport" -> L("⚽ ספורט"); "docu" -> L("🔬 מדע ודוקו")
                "music" -> L("🎵 מוזיקה"); else -> L("📚 חינוכי") }
            if (l.isEmpty()) null else title to l
        }
    }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    val colors = listOf(Color(0xFFFF6F91), Color(0xFFFFB300), Color(0xFF00C2A8), Color(0xFF3A9BFF), Color(0xFF8E44AD))
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Box(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(22.dp))
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(colors[prof.color % colors.size], colors[(prof.color + 2) % colors.size])))
                .padding(20.dp)) {
                Column {
                    // הברכה לפי שמות הילדים שההורה הגדיר (עד 5)
                    val who = when {
                        kids.size > 1 -> kids.dropLast(1).joinToString(", ") + " ו" + kids.last()
                        kids.size == 1 -> kids[0]
                        else -> prof.name
                    }
                    Text(L("שלום {0}! 👋", who) + (if (prof.avatar.isNotBlank()) " " + prof.avatar else ""), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    val many = kids.size > 1 || prof.name.trim() == "ילדים"
                    Text(if (many) L("מה בא לכם לראות היום?") else L("מה בא לך לראות היום?"), color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp)
                }
            }
        }
        if (live.isEmpty() && movies.isEmpty() && series.isEmpty()) item {
            Text(L("עדיין אין תוכן מאושר לפרופיל הזה. בקש מאבא או אמא לבחור תוכן בהגדרות ← בקרת הורים ← תוכן."),
                modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center)
        }
        // מי צופה? – מועדפים נפרדים לכל ילד
        if (kids.size > 1) item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L("מי צופה?"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(kids) { n ->
                        com.tbm.iptv.ui.TvFilterChip(selected = n == child, onClick = { Prefs.kidChild = if (n == child) "" else n },
                            label = { Text(n, fontSize = 16.sp, fontWeight = FontWeight.Bold) })
                    }
                }
            }
        }
        if (favItems.isNotEmpty()) item {
            CategoryStrip(if (child != null) L("⭐ המועדפים של {0}", child) else L("⭐ המועדפים שלי"), favItems, Kind.MOVIE, favs, nav, null) { e ->
                menu = MenuTarget(if (e is SeriesItem) Kind.SERIES else if (e is Channel && e.kind == Kind.LIVE) Kind.LIVE else Kind.MOVIE, e, favItems)
            }
        } else if (child != null) item {
            Text(L("{0}, לחץ לחיצה ארוכה על סרט או ערוץ ובחר ☆ כדי להוסיף למועדפים שלך", child), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        if (resume.isNotEmpty()) item { CategoryStrip(L("▶ להמשיך לצפות"), resume, Kind.MOVIE, favs, nav, null) { menu = MenuTarget(Kind.MOVIE, it, movies) } }
        if (fresh.isNotEmpty()) item { CategoryStrip(L("🆕 חדש בשבילך"), fresh, Kind.MOVIE, favs, nav, null) { menu = MenuTarget(Kind.MOVIE, it, movies) } }
        if (live.isNotEmpty()) item { CategoryStrip(L("📺 הערוצים שלי"), live, Kind.LIVE, favs, nav, null) { menu = MenuTarget(Kind.LIVE, it, live) } }
        if (movies.isNotEmpty()) item { CategoryStrip(L("🎬 סרטים"), movies.sortedByDescending { it.rating() }, Kind.MOVIE, favs, nav, null) { menu = MenuTarget(Kind.MOVIE, it, movies) } }
        if (series.isNotEmpty()) item { CategoryStrip(L("🍿 סדרות"), series.sortedByDescending { it.rating() }, Kind.SERIES, favs, nav, null) { menu = MenuTarget(Kind.SERIES, it, series) } }
        items(themeRows, key = { "th:" + it.first }) { (title, l) ->
            CategoryStrip(title, l, Kind.MOVIE, favs, nav, null) { e -> menu = MenuTarget(if (e is SeriesItem) Kind.SERIES else Kind.MOVIE, e, l) }
        }
        items(topicRows, key = { "t:" + it.first }) { (title, l) -> CategoryStrip(title, l, Kind.LIVE, favs, nav, null) { menu = MenuTarget(Kind.LIVE, it, live) } }
    }
    menu?.let { ItemMenu(it) { menu = null } }
}


/** הפוסטר האחרון שנלחץ – כדי להחזיר אליו את הסימון בשלט אחרי חזרה */
object FocusMemory { @Volatile var last: String? = null }


/**
 * אותו סרט/סדרה מכמה קטגוריות של הספק (למשל "AMZ - X" ו-"4K-AMZ - X") מוצג בשורות פעם אחת;
 * בדף הפרטים כל הגרסאות מופיעות תחת "מקורות". עדיפות לגרסה שכבר התחלת לצפות בה, אחריה ל-HD (4K כבד לסטרימר).
 */
fun dedupeTitles(l: List<Entry>): List<Entry> {
    if (l.size < 2) return l
    val groups = LinkedHashMap<String, MutableList<Entry>>()
    for (e in l) {
        val k = com.tbm.iptv.data.Sources.norm(e.name).ifBlank { e.key } + "|" + (com.tbm.iptv.data.Sources.year(e.name) ?: "")
        groups.getOrPut(k) { ArrayList() } += e
    }
    if (groups.size == l.size) return l
    return groups.values.map { g ->
        if (g.size == 1) g[0]
        else g.firstOrNull { Prefs.resume(it.key) != null }
            ?: g.firstOrNull { com.tbm.iptv.data.Sources.quality(it) !in setOf("4K", "CAM") }
            ?: g[0]
    }
}
