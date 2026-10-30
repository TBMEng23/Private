package com.tbm.iptv.ui

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.tbm.iptv.data.Channel
import com.tbm.iptv.data.Content
import com.tbm.iptv.data.Countries
import com.tbm.iptv.data.EpgEntry
import com.tbm.iptv.data.Entry
import com.tbm.iptv.data.Kind
import com.tbm.iptv.data.LoadState
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.Repo
import com.tbm.iptv.data.SeriesItem
import com.tbm.iptv.data.Xtream
import com.tbm.iptv.data.toPlay
import com.tbm.iptv.player.PlayerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Section(val label: String, val icon: ImageVector) {
    LIVE("טלוויזיה", Icons.Filled.LiveTv),
    MOVIES("סרטים", Icons.Filled.Movie),
    SERIES("סדרות", Icons.Filled.VideoLibrary),
    FAV("מועדפים", Icons.Filled.Favorite),
    RECENT("המשך צפייה", Icons.Filled.History),
    SEARCH("חיפוש", Icons.Filled.Search),
}

private val adultRx = Regex("(?i)(adult|xxx|18\\+|\\+18|porn|for adults|למבוגרים)")

fun <T : Entry> filterEntries(list: List<T>, kind: Kind): List<T> {
    val hidden = Prefs.hidden()
    val allowed = Prefs.allowedCountries(kind)
    val hideAdult = Prefs.hideAdult
    val ok = HashMap<String, Boolean>()
    return list.filter { e ->
        ok.getOrPut(e.group) {
            "${kind.name}|${e.group}" !in hidden &&
                (allowed.isEmpty() || Countries.of(e.group) in allowed) &&
                !(hideAdult && adultRx.containsMatchIn(e.group))
        }
    }
}

@Composable
fun HomeScreen(nav: (Screen) -> Unit) {
    val state by Repo.state.collectAsState()
    var section by rememberSaveable { mutableStateOf(Section.LIVE) }
    val ver = Prefs.version.intValue

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SideRail(section, { section = it }, nav)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (val s = state) {
                is LoadState.Ready -> SectionContent(section, s.content, ver, nav)
                is LoadState.Loading -> Column(
                    Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(s.msg)
                }
                is LoadState.Error -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("שגיאה בטעינה", style = MaterialTheme.typography.titleLarge)
                    Text(s.msg, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = { Prefs.activePlaylist()?.let { Repo.loadAsync(it, true) } }) { Text("נסה שוב") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { nav(Screen.Playlists) }) { Text("רשימות") }
                    }
                }
                LoadState.Idle -> Column(
                    Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("לא נבחרה רשימת ערוצים")
                    Button(onClick = { nav(Screen.Playlists) }) { Text("הוסף רשימה") }
                }
            }
        }
    }
}

@Composable
private fun SideRail(section: Section, onSelect: (Section) -> Unit, nav: (Screen) -> Unit) {
    Column(
        Modifier.width(88.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TBM", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Section.entries.forEach { s ->
            RailItem(s.label, s.icon, s == section) { onSelect(s) }
        }
        Spacer(Modifier.weight(1f))
        RailItem("רשימות", Icons.AutoMirrored.Filled.PlaylistPlay, false) { nav(Screen.Playlists) }
        RailItem("הגדרות", Icons.Filled.Settings, false) { nav(Screen.Settings) }
    }
}

@Composable
private fun RailItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth(), selected = selected, onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon, label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(label, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SectionContent(section: Section, content: Content, ver: Int, nav: (Screen) -> Unit) {
    when (section) {
        Section.LIVE -> BrowseSection(Kind.LIVE, content.live, ver, nav)
        Section.MOVIES -> BrowseSection(Kind.MOVIE, content.movies, ver, nav)
        Section.SERIES -> BrowseSection(Kind.SERIES, content.series, ver, nav)
        Section.FAV -> FavoritesSection(content, ver, nav)
        Section.RECENT -> RecentSection(ver)
        Section.SEARCH -> SearchSection(content, nav)
    }
}

// ============================================================ דפדוף: קטגוריות + תוכן
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseSection(kind: Kind, all: List<Entry>, ver: Int, nav: (Screen) -> Unit) {
    val filtered = remember(all, ver) { filterEntries(all, kind) }
    val counts = remember(filtered) { filtered.groupingBy { it.group }.eachCount() }
    val groups = remember(filtered) { filtered.map { it.group }.distinct() }
    val favs = remember(ver) { Prefs.favs() }
    val favCount = remember(filtered, favs) { filtered.count { it.key in favs } }
    var group by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(kind) { mutableStateOf("") }
    val shownItems = remember(filtered, group, query, favs) {
        filtered.filter { e ->
            when (group) {
                null -> true
                FAV_GROUP -> e.key in favs
                else -> e.group == group
            } && (query.isBlank() || e.name.contains(query, true))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                CategoryColumn(
                    kind, groups, counts, filtered.size, favCount,
                    group, { group = it }, query, { query = it }, nav,
                    Modifier.width(maxWidth * 0.26f).fillMaxHeight(),
                )
                ContentArea(kind, shownItems, favs, wide = true, nav = nav, modifier = Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        query, { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text("חיפוש ב${kind.label}") },
                    )
                    IconButton(onClick = { nav(Screen.Filter(kind)) }) { Icon(Icons.Filled.FilterList, "סינון") }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { FilterChip(group == null, { group = null }, { Text("הכל (${filtered.size})") }) }
                    item { FilterChip(group == FAV_GROUP, { group = FAV_GROUP }, { Text("★ מועדפים") }) }
                    items(groups) { g ->
                        FilterChip(group == g, { group = g }, { Text("$g (${counts[g] ?: 0})", maxLines = 1) })
                    }
                }
                ContentArea(kind, shownItems, favs, wide = false, nav = nav, modifier = Modifier.weight(1f))
            }
        }
    }
}

private const val FAV_GROUP = "\u0000fav"

@Composable
private fun CategoryColumn(
    kind: Kind, groups: List<String>, counts: Map<String, Int>, total: Int, favCount: Int,
    selected: String?, onSelect: (String?) -> Unit,
    query: String, onQuery: (String) -> Unit,
    nav: (Screen) -> Unit, modifier: Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(kind.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { Prefs.activePlaylist()?.let { Repo.loadAsync(it, true) } }) {
                Icon(Icons.Filled.Refresh, "רענון")
            }
            IconButton(onClick = { nav(Screen.Filter(kind)) }) { Icon(Icons.Filled.FilterList, "סינון ארצות וקבוצות") }
        }
        OutlinedTextField(
            query, onQuery, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            placeholder = { Text("חיפוש…") },
        )
        LazyColumn(contentPadding = PaddingValues(6.dp)) {
            item { CategoryRow("הכל", total, selected == null) { onSelect(null) } }
            item { CategoryRow("★ מועדפים", favCount, selected == FAV_GROUP) { onSelect(FAV_GROUP) } }
            items(groups) { g -> CategoryRow(g, counts[g] ?: 0, selected == g) { onSelect(g) } }
        }
    }
}

@Composable
private fun CategoryRow(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth().padding(vertical = 1.dp), selected = selected, onClick = onClick) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ContentArea(kind: Kind, items: List<Entry>, favs: Set<String>, wide: Boolean, nav: (Screen) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    when (kind) {
        Kind.LIVE -> {
            @Suppress("UNCHECKED_CAST")
            val chans = items as List<Channel>
            var preview by remember { mutableStateOf<Channel?>(null) }
            val playAt: (Int) -> Unit = { i -> Launcher.play(ctx, chans.map { it.toPlay() }, i) }
            if (wide) {
                Row(modifier) {
                    ChannelList(chans, favs, preview?.key, Modifier.weight(1f)) { c, i ->
                        if (preview?.key == c.key) playAt(i) else preview = c
                    }
                    PreviewPane(preview, Modifier.weight(1f)) {
                        val i = chans.indexOfFirst { it.key == preview?.key }
                        if (i >= 0) playAt(i)
                    }
                }
            } else {
                ChannelList(chans, favs, null, modifier) { _, i -> playAt(i) }
            }
        }
        Kind.MOVIE -> PosterGrid(items, favs, modifier) { e ->
            Launcher.play(ctx, listOf((e as Channel).toPlay()), 0)
        }
        Kind.SERIES -> PosterGrid(items, favs, modifier) { e -> nav(Screen.Series(e as SeriesItem)) }
    }
}

@Composable
fun ChannelList(
    items: List<Channel>, favs: Set<String>, selectedKey: String?, modifier: Modifier,
    onClick: (Channel, Int) -> Unit,
) {
    if (items.isEmpty()) {
        Box(modifier.fillMaxSize()) { Text("אין ערוצים", Modifier.align(Alignment.Center)) }
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(6.dp)) {
        itemsIndexed(items) { i, c ->
            FocusItem(
                Modifier.fillMaxWidth().padding(vertical = 1.dp),
                selected = c.key == selectedKey,
                onLongClick = { Prefs.toggleFav(c.key) },
                onClick = { onClick(c, i) },
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.width(40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Box(
                        Modifier.size(width = 52.dp, height = 34.dp).clip(RoundedCornerShape(4.dp))
                            .background(Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(c.logo, null, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(c.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (c.key in favs) Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PreviewPane(ch: Channel?, modifier: Modifier, onFull: () -> Unit) {
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
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    LaunchedEffect(ch?.key) {
        epg = emptyList()
        val p = Prefs.activePlaylist()
        val sid = ch?.streamId
        if (sid != null && p != null && p.isXtream) {
            epg = withContext(Dispatchers.IO) { runCatching { Xtream(p).shortEpg(sid) }.getOrDefault(emptyList()) }
        }
    }

    Column(modifier.fillMaxHeight().padding(8.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                .background(Color.Black).clickable { onFull() },
        ) {
            AndroidView(
                factory = { PlayerView(it).apply { useController = false; this.player = player } },
                modifier = Modifier.fillMaxSize(),
            )
            if (ch == null) Text(
                "בחר ערוץ לתצוגה מקדימה\nלחיצה נוספת – מסך מלא",
                Modifier.align(Alignment.Center), textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (ch != null) {
            Text(ch.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(ch.group, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            val now = System.currentTimeMillis()
            LazyColumn(Modifier.weight(1f)) {
                items(epg) { e ->
                    val live = now in e.start until e.end
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "${fmtTime(e.start)}–${fmtTime(e.end)}  ${e.title}",
                            color = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            fontWeight = if (live) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp, maxLines = 2,
                        )
                        if (live && e.end > e.start) LinearProgressIndicator(
                            progress = { ((now - e.start).toFloat() / (e.end - e.start)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                        )
                    }
                }
            }
            Button(onClick = onFull, modifier = Modifier.fillMaxWidth()) { Text("מסך מלא") }
        }
    }
}

@Composable
fun PosterGrid(items: List<Entry>, favs: Set<String>, modifier: Modifier, onClick: (Entry) -> Unit) {
    if (items.isEmpty()) {
        Box(modifier.fillMaxSize()) { Text("אין פריטים", Modifier.align(Alignment.Center)) }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(116.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItemsIndexed(items) { _, e ->
            FocusItem(onLongClick = { Prefs.toggleFav(e.key) }, onClick = { onClick(e) }) {
                Column(Modifier.padding(4.dp)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (e.logo == null) Text(
                            e.name, Modifier.align(Alignment.Center).padding(6.dp),
                            textAlign = TextAlign.Center, fontSize = 13.sp,
                        )
                        AsyncImage(e.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (e.key in favs) Icon(
                            Icons.Filled.Star, null, tint = Gold,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
                        )
                        val r = Prefs.resume(e.key)
                        if (r != null && r.second > 0) LinearProgressIndicator(
                            progress = { (r.first.toFloat() / r.second).coerceIn(0f, 1f) },
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        )
                    }
                    Text(e.name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

// ============================================================ מועדפים
@Composable
private fun FavoritesSection(content: Content, ver: Int, nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val favs = remember(ver) { Prefs.favs() }
    val live = remember(content, favs) { content.live.filter { it.key in favs } }
    val vod = remember(content, favs) { content.movies.filter { it.key in favs } }
    val ser = remember(content, favs) { content.series.filter { it.key in favs } }
    if (live.isEmpty() && vod.isEmpty() && ser.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text("אין מועדפים עדיין.\nלחיצה ארוכה על ערוץ, סרט או סדרה מוסיפה למועדפים.",
                Modifier.align(Alignment.Center).padding(24.dp), textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp)) {
        if (live.isNotEmpty()) {
            item { SectionTitle("ערוצים") }
            itemsIndexed(live) { i, c ->
                EntryRow(c, true, onLong = { Prefs.toggleFav(c.key) }) {
                    Launcher.play(ctx, live.map { it.toPlay() }, i)
                }
            }
        }
        if (vod.isNotEmpty()) {
            item { SectionTitle("סרטים") }
            items(vod) { m -> EntryRow(m, true, onLong = { Prefs.toggleFav(m.key) }) { Launcher.play(ctx, listOf(m.toPlay()), 0) } }
        }
        if (ser.isNotEmpty()) {
            item { SectionTitle("סדרות") }
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

// ============================================================ המשך צפייה
@Composable
private fun RecentSection(ver: Int) {
    val ctx = LocalContext.current
    val list = remember(ver) { Prefs.recent() }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize()) { Text("עוד לא צפית בכלום", Modifier.align(Alignment.Center)) }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp)) {
        item { SectionTitle("המשך צפייה (לחיצה ארוכה = הסרה)") }
        items(list) { r ->
            FocusItem(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                onLongClick = { Prefs.removeResume(r.item.key) },
                onClick = { Launcher.play(ctx, listOf(r.item), 0) },
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(r.item.logo, null, Modifier.size(width = 54.dp, height = 76.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (r.dur > 0) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (r.pos.toFloat() / r.dur).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("נותרו ${(r.dur - r.pos) / 60000} דק׳", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ חיפוש כללי
@Composable
private fun SearchSection(content: Content, nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    val live = remember(q, content) { if (q.length < 2) emptyList() else content.live.filter { it.name.contains(q, true) }.take(60) }
    val vod = remember(q, content) { if (q.length < 2) emptyList() else content.movies.filter { it.name.contains(q, true) }.take(60) }
    val ser = remember(q, content) { if (q.length < 2) emptyList() else content.series.filter { it.name.contains(q, true) }.take(60) }
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("חפש ערוץ, סרט או סדרה…") })
        LazyColumn(Modifier.fillMaxSize()) {
            if (live.isNotEmpty()) {
                item { SectionTitle("ערוצים (${live.size})") }
                itemsIndexed(live) { i, c -> EntryRow(c, false) { Launcher.play(ctx, live.map { it.toPlay() }, i) } }
            }
            if (vod.isNotEmpty()) {
                item { SectionTitle("סרטים (${vod.size})") }
                items(vod) { m -> EntryRow(m, false) { Launcher.play(ctx, listOf(m.toPlay()), 0) } }
            }
            if (ser.isNotEmpty()) {
                item { SectionTitle("סדרות (${ser.size})") }
                items(ser) { s -> EntryRow(s, false) { nav(Screen.Series(s)) } }
            }
            if (q.length >= 2 && live.isEmpty() && vod.isEmpty() && ser.isEmpty()) {
                item { Text("לא נמצאו תוצאות", Modifier.padding(16.dp)) }
            }
        }
    }
}
