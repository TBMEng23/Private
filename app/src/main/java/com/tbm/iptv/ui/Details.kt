package com.tbm.iptv.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tbm.iptv.L
import com.tbm.iptv.data.Channel
import com.tbm.iptv.data.Episode
import com.tbm.iptv.data.Localize
import com.tbm.iptv.data.MediaInfo
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.Repo
import com.tbm.iptv.data.SeriesItem
import com.tbm.iptv.data.toPlay

private val DetailBg = Color(0xFF0E0F13)
private val NetRed = Color(0xFFE50914)
/** כיוון טקסט לפי התוכן: אנגלית משמאל לימין, עברית מימין לשמאל */
private val ContentDir = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content)

/** "45m" / "1h 33m" */
fun fmtMin(sec: Int): String {
    if (sec <= 0) return ""
    val m = (sec + 30) / 60
    return if (m >= 60) L("{0}ש׳ {1}ד׳", m / 60, m % 60) else L("{0}ד׳", m)
}

/** מנקה כותרת פרק: "MobLand - S02E02 - Song 2" → "Song 2" */
fun cleanEpTitle(t: String, series: String): String {
    var r = t.replace(Regex("(?i)^\\s*" + Regex.escape(series) + "\\s*[-–:|]?\\s*"), "")
    r = r.replace(Regex("(?i)^\\s*S\\d{1,2}\\s*E\\d{1,3}\\s*[-–:|]?\\s*"), "")
    return r.trim()
}

private fun openTrailer(ctx: android.content.Context, t: String) {
    val url = if (t.startsWith("http")) t else "https://www.youtube.com/watch?v=$t"
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ============================================================ חלקים משותפים
@Composable
private fun Hero(image: String?, title: String, onBack: () -> Unit, original: String? = null) {
    Box(Modifier.fillMaxWidth().height(320.dp)) {
        AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color(0x88000000), 0.3f to Color.Transparent, 0.65f to Color(0x66000000), 1f to DetailBg)))
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, L("חזרה"), tint = Color.White)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            original?.let { Text(it, color = Color(0xFFCFD0D6), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun MetaLine(info: MediaInfo?, rating: Float, extra: String?) {
    val parts = listOfNotNull(info?.year, info?.genre?.replace(",", " /")?.takeIf { it.isNotBlank() }, extra?.takeIf { it.isNotBlank() })
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        val r = info?.rating?.takeIf { it > 0 }?.toFloat() ?: rating
        if (r > 0f) {
            Row(Modifier.background(Color(0xFF2A2B33), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(14.dp))
                Text(" TMDb %.1f".format(r), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(parts.joinToString(" • ") { "\u2068$it\u2069" }, color = Color(0xFFB8B9C0), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlayRow(label: String, fav: Boolean, onPlay: () -> Unit, onFav: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onPlay, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        ) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(48.dp).border(1.dp, Color(0xFF55565E), CircleShape).clip(CircleShape).clickable { onFav() },
            contentAlignment = Alignment.Center) {
            Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, L("מועדף"), tint = if (fav) Gold else Color.White)
        }
    }
}

@Composable
private fun ResumeBar(pos: Long, dur: Long) {
    if (dur <= 0 || pos <= 0) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(progress = { (pos.toFloat() / dur).coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(3.dp), color = NetRed, trackColor = Color(0xFF3A3B42))
        Spacer(Modifier.width(10.dp))
        Text(L("נותרו {0}", fmtMin(((dur - pos) / 1000).toInt()).ifBlank { L("0ד׳") }), color = Color(0xFFB8B9C0), fontSize = 12.sp)
    }
}

@Composable
private fun Plot(text: String?) {
    if (text.isNullOrBlank()) return
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).clickable { open = !open }) {
        Text(text, color = Color(0xFFE4E4E8), fontSize = 14.sp, lineHeight = 20.sp, style = ContentDir,
            maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
        if (text.length > 140) Text(if (open) L("פחות") else L("עוד"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun CastList(info: MediaInfo?) {
    val cast = info?.cast?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
    Column(Modifier.padding(16.dp)) {
        info?.director?.let {
            Text(L("במאי"), color = Color(0xFF9A9BA3), fontSize = 12.sp)
            Text(it, color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
        }
        if (cast.isEmpty() && info?.director == null) Text(L("אין מידע על השחקנים"), color = Color(0xFF9A9BA3))
        cast.forEach { n ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(Color(0xFF2A2B33), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, null, tint = Color(0xFF9A9BA3))
                }
                Spacer(Modifier.width(12.dp))
                Text(n, color = Color.White)
            }
        }
    }
}

/** מזהה YouTube מתוך מזהה/קישור */
private fun ytId(t: String): String? {
    if (!t.contains("/") && !t.contains("=")) return t.trim().ifBlank { null }
    Regex("(?:v=|youtu\\.be/|embed/|shorts/)([A-Za-z0-9_-]{6,})").find(t)?.let { return it.groupValues[1] }
    return null
}

@Composable
private fun TrailerTab(info: MediaInfo?) {
    val ctx = LocalContext.current
    val t = info?.trailer
    var playing by remember(t) { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        val id = t?.let { ytId(it) }
        if (t == null) Text(L("לא נמצא טריילר"), color = Color(0xFF9A9BA3))
        else if (playing && id != null) TrailerPlayer(id)
        else {
            val img = info.backdrops.firstOrNull() ?: id?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color.Black)
                .clickable { if (id != null) playing = true else openTrailer(ctx, t) }, contentAlignment = Alignment.Center) {
                AsyncImage(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.size(64.dp).background(Color(0xCC000000), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, L("טריילר"), tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
        }
        if (t != null) Text(L("פתח ב-YouTube"), color = Color(0xFF9A9BA3), fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp).clickable { openTrailer(ctx, t) })
    }
}

/** נגן טריילר בתוך האפליקציה (YouTube מוטמע) – כפתור מסך מלא מגדיל ומסובב לרוחב */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TrailerPlayer(id: String) {
    val ctx = LocalContext.current
    var full by remember { mutableStateOf<android.view.View?>(null) }
    var cb by remember { mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { c ->
            android.webkit.WebView(c).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) { full = view; cb = callback }
                    override fun onHideCustomView() { full = null; cb = null }
                }
                webViewClient = android.webkit.WebViewClient()
                val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}iframe{border:0;width:100%;height:100%}</style></head>
<body><iframe src="https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0&modestbranding=1&fs=1"
allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen
referrerpolicy="strict-origin-when-cross-origin"></iframe></body></html>"""
                loadDataWithBaseURL("https://tbmplayer.app/", html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
    )
    full?.let { v ->
        val act = ctx.findActivity()
        androidx.compose.runtime.DisposableEffect(v) {
            val old = act?.requestedOrientation
            act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose { act?.requestedOrientation = old ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { cb?.onCustomViewHidden(); full = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { c ->
                    android.widget.FrameLayout(c).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        (v.parent as? android.view.ViewGroup)?.removeView(v)
                        addView(v, android.widget.FrameLayout.LayoutParams(-1, -1))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) { if (c is android.app.Activity) return c; c = c.baseContext }
    return null
}

@Composable
private fun MediaTab(images: List<String>) {
    if (images.isEmpty()) { Text(L("אין תמונות"), color = Color(0xFF9A9BA3), modifier = Modifier.padding(16.dp)); return }
    LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(images) { u ->
            AsyncImage(u, null, Modifier.width(280.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1F25)), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun DetailTabs(tabs: List<String>, sel: Int, onSel: (Int) -> Unit) {
    TabRow(selectedTabIndex = sel, containerColor = DetailBg, contentColor = Color.White, modifier = Modifier.padding(top = 8.dp)) {
        tabs.forEachIndexed { i, t ->
            Tab(selected = i == sel, onClick = { onSel(i) },
                text = { Text(t, fontWeight = if (i == sel) FontWeight.Bold else FontWeight.Normal, maxLines = 1) },
                selectedContentColor = Color.White, unselectedContentColor = Color(0xFF9A9BA3))
        }
    }
}

// ============================================================ דף סדרה
@Composable
fun SeriesScreen(s: SeriesItem, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ver = Prefs.version.intValue
    var info by remember { mutableStateOf<MediaInfo?>(null) }
    var eps by remember { mutableStateOf<List<Episode>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s.key) {
        val p = Prefs.activePlaylist()
        if (p == null) { err = L("אין רשימה פעילה"); return@LaunchedEffect }
        try { val (i, e) = Repo.details(p, s); info = i; eps = e } catch (e: Exception) { err = e.message ?: L("שגיאה") }
    }
    if (AgeGate(info, true, s.name, onBack)) return
    val all = eps ?: emptyList()
    val seasons = remember(all) { all.map { it.season }.distinct().sorted() }
    // הפרק להמשך: האחרון שנצפה – אם לא הסתיים אותו, אחרת הבא אחריו
    val next = remember(all, ver) {
        val last = all.indexOfLast { Prefs.resume(it.key) != null }
        when {
            last < 0 -> all.firstOrNull()
            Prefs.resume(all[last].key)!!.let { it.first < it.second * 0.95 } -> all[last]
            else -> all.getOrNull(last + 1) ?: all[last]
        }
    }
    val nextResume = remember(next, ver) { next?.let { Prefs.resume(it.key) } }
    var season by remember(seasons, next) { mutableIntStateOf(next?.season ?: seasons.firstOrNull() ?: 1) }
    var tab by remember { mutableIntStateOf(0) }
    var seasonMenu by remember { mutableStateOf(false) }
    val fav = remember(ver) { Prefs.isFav(s.key) }
    val play: (Episode) -> Unit = { e -> Launcher.play(ctx, all.map { it.toPlay(s) }, all.indexOf(e)) }
    // תרגום לשפת הממשק
    val lang = com.tbm.iptv.I18n.lang
    var loc by remember { mutableStateOf<Pair<String?, MediaInfo>?>(null) }
    LaunchedEffect(info, lang) { info?.let { loc = localizeInfo(s.name, it, s.plot, true) } }
    val shown = loc?.second ?: info
    var epLoc by remember { mutableStateOf<Map<String, Pair<String?, String?>>>(emptyMap()) }
    LaunchedEffect(season, all, lang) {
        val list = all.filter { it.season == season && it.key !in epLoc }
        if (list.isEmpty() || !Localize.enabled) return@LaunchedEffect
        val tr = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Localize.texts(list.map { it.plot })
        }
        epLoc = epLoc + list.mapIndexed { i, e -> e.key to (null to tr[i]) }
    }
    LaunchedEffect(lang) { epLoc = emptyMap() }

    LazyColumn(Modifier.fillMaxSize().background(DetailBg)) {
        item { Hero(info?.backdrops?.firstOrNull() ?: s.logo, loc?.first ?: s.name, onBack, if (loc?.first != null) s.name else null) }
        item {
            MetaLine(shown, s.rating, if (seasons.isNotEmpty()) L("{0} עונות", seasons.size) else null)
            if (next != null) {
                val started = nextResume != null && nextResume.first < nextResume.second * 0.95
                PlayRow((if (started) L("המשך") else L("נגן")) + " S${next.season} E${next.episode}", fav, { play(next) }) { Prefs.toggleFav(s.key) }
                if (started) ResumeBar(nextResume!!.first, nextResume.second)
            } else {
                PlayRow(L("נגן"), fav, {}) { Prefs.toggleFav(s.key) }
            }
            Plot(shown?.plot ?: s.plot)
            DetailTabs(listOf(L("פרקים"), L("טריילר"), L("שחקנים"), L("תמונות")), tab) { tab = it }
        }
        when (tab) {
            0 -> when {
                err != null -> item { Text(L("שגיאה: {0}", err), Modifier.padding(16.dp), color = Color.White) }
                eps == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) } }
                all.isEmpty() -> item { Text(L("לא נמצאו פרקים"), Modifier.padding(16.dp), color = Color.White) }
                else -> {
                    val list = all.filter { it.season == season }
                    item {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                OutlinedButton(onClick = { if (seasons.size > 1) seasonMenu = true }, shape = RoundedCornerShape(6.dp)) {
                                    Text(L("עונה {0}", season), color = Color.White, fontWeight = FontWeight.Bold)
                                    if (seasons.size > 1) Icon(Icons.Filled.ArrowDropDown, null, tint = Color.White)
                                }
                                DropdownMenu(seasonMenu, { seasonMenu = false }) {
                                    seasons.forEach { n ->
                                        DropdownMenuItem(text = { Text(L("עונה {0}", n) + "  (" + all.count { it.season == n } + ")") },
                                            onClick = { season = n; seasonMenu = false })
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(L("{0} פרקים", list.size), color = Color(0xFF9A9BA3), fontSize = 13.sp)
                        }
                    }
                    items(list) { e ->
                        val l = epLoc[e.key]
                        EpisodeRow(if (l == null) e else e.copy(title = l.first ?: e.title, plot = l.second ?: e.plot), s, info, ver, e.key == next?.key) { play(e) }
                    }
                }
            }
            1 -> item { TrailerTab(info) }
            2 -> item { CastList(shown) }
            else -> item { MediaTab((info?.backdrops.orEmpty() + all.mapNotNull { it.image }).distinct().take(40)) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EpisodeRow(e: Episode, s: SeriesItem, info: MediaInfo?, ver: Int, current: Boolean, onClick: () -> Unit) {
    val r = remember(ver, e.key) { Prefs.resume(e.key) }
    var open by remember { mutableStateOf(false) }
    val title = cleanEpTitle(e.title, s.name).ifBlank { L("פרק {0}", e.episode) }
    val dur = if (e.durationSec > 0) e.durationSec else (r?.second?.div(1000)?.toInt() ?: info?.durationSec ?: 0)
    FocusItem(Modifier.fillMaxWidth(), selected = current, onClick = onClick) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E1F25))) {
                    AsyncImage(e.image ?: info?.backdrops?.firstOrNull() ?: s.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.align(Alignment.Center).size(32.dp).background(Color(0x99000000), CircleShape).border(1.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    if (r != null && r.second > 0) LinearProgressIndicator(
                        progress = { (r.first.toFloat() / r.second).coerceIn(0f, 1f) },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                        color = NetRed, trackColor = Color(0x55FFFFFF),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("S${e.season}E${e.episode}  $title", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = buildList {
                        fmtMin(dur).takeIf { it.isNotBlank() }?.let { add(it) }
                        if (r != null && r.second > 0 && r.first >= r.second * 0.95) add(L("✓ נצפה"))
                    }.joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, color = Color(0xFF9A9BA3), fontSize = 12.sp)
                }
            }
            e.plot?.takeIf { it.isNotBlank() }?.let { p ->
                Text(p, color = Color(0xFFB8B9C0), fontSize = 13.sp, lineHeight = 18.sp, style = ContentDir,
                    maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp).clickable { open = !open })
                if (!open && p.length > 150) Text(L("עוד"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.clickable { open = true })
            }
        }
    }
}

// ============================================================ דף סרט
@Composable
fun MovieScreen(c: Channel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ver = Prefs.version.intValue
    var info by remember { mutableStateOf<MediaInfo?>(null) }
    LaunchedEffect(c.key) {
        val p = Prefs.activePlaylist() ?: return@LaunchedEffect
        info = runCatching { Repo.vodInfo(p, c) }.getOrNull()
    }
    if (AgeGate(info, false, c.name, onBack)) return
    val r = remember(ver) { Prefs.resume(c.key) }
    val started = r != null && r.first < r.second * 0.95
    val fav = remember(ver) { Prefs.isFav(c.key) }
    var tab by remember { mutableIntStateOf(0) }
    val lang = com.tbm.iptv.I18n.lang
    var loc by remember { mutableStateOf<Pair<String?, MediaInfo>?>(null) }
    LaunchedEffect(info, lang) { loc = localizeInfo(c.name, info ?: MediaInfo(), null, false) }
    val shown = loc?.second ?: info
    LazyColumn(Modifier.fillMaxSize().background(DetailBg)) {
        item { Hero(info?.backdrops?.firstOrNull() ?: c.logo, loc?.first ?: c.name, onBack, if (loc?.first != null) c.name else null) }
        item {
            MetaLine(shown, c.rating, fmtMin(info?.durationSec ?: 0))
            PlayRow(if (started) L("המשך") else L("נגן"), fav, { Launcher.play(ctx, listOf(c.toPlay()), 0) }) { Prefs.toggleFav(c.key) }
            if (started) ResumeBar(r!!.first, r.second)
            Plot(shown?.plot)
            DetailTabs(listOf(L("שחקנים"), L("טריילר"), L("תמונות")), tab) { tab = it }
        }
        when (tab) {
            0 -> item { CastList(shown) }
            1 -> item { TrailerTab(info) }
            else -> item { MediaTab(info?.backdrops.orEmpty()) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}


/** מתרגם שם/תקציר/ז'אנר/שחקנים לשפת הממשק. מחזיר (שם מתורגם או null, מידע מתורגם) */
private suspend fun localizeInfo(name: String, i: MediaInfo, fallbackPlot: String?, tv: Boolean): Pair<String?, MediaInfo> =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!Localize.enabled) return@withContext null to i
        runCatching {
            val tm = Localize.tmdb(i.tmdbId, tv)
            val plot0 = i.plot ?: fallbackPlot
            // שם הסרט/הסדרה נשאר מקורי – מתרגמים רק תקציר, ז'אנר ושמות
            val tr = Localize.texts(listOf(if (tm?.second == null) plot0 else null, i.cast, i.director))
            null to i.copy(plot = tm?.second ?: tr[0] ?: plot0, genre = Localize.genre(i.genre), cast = tr[1], director = tr[2])
        }.getOrDefault(null to i)
    }


/** בפרופיל ילדים: בודק סיווג גיל ב-TMDB. מחזיר true (ומציג מסך חסימה) אם התוכן מעל גיל הפרופיל */
@Composable
private fun AgeGate(info: MediaInfo?, tv: Boolean, title: String, onBack: () -> Unit): Boolean {
    if (!Prefs.isKids) return false
    val prof = Prefs.profile()
    var rated by remember(info) { mutableStateOf<Int?>(null) }
    LaunchedEffect(info) {
        val id = info?.tmdbId ?: return@LaunchedEffect
        rated = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.tbm.iptv.data.KidsFilter.tmdbAge(id, tv) }
    }
    val r = rated ?: return false
    if (r <= prof.age) return false
    Column(Modifier.fillMaxSize().background(DetailBg).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("🔒", fontSize = 56.sp)
        Text(L("לא מתאים לגיל"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(title, color = Color(0xFFB8B9C0), modifier = Modifier.padding(top = 4.dp))
        Text(L("התוכן הזה מסווג לגיל {0}+ ולכן לא זמין בפרופיל {1}.", r, prof.name), color = Color(0xFFB8B9C0),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        Button(onClick = onBack, modifier = Modifier.padding(top = 20.dp)) { Text(L("חזרה")) }
    }
    return true
}
