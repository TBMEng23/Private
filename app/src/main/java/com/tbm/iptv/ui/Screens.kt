package com.tbm.iptv.ui

import com.tbm.iptv.L

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tbm.iptv.data.Catalog
import com.tbm.iptv.data.Countries
import com.tbm.iptv.data.Entry
import com.tbm.iptv.data.Epg
import com.tbm.iptv.data.Episode
import com.tbm.iptv.data.Kind
import com.tbm.iptv.data.Parental
import com.tbm.iptv.data.Playlist
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.Profile
import com.tbm.iptv.data.Repo
import com.tbm.iptv.data.SeriesItem
import com.tbm.iptv.data.toPlay
import com.tbm.iptv.subs.SubtitleManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ============================================================ מסך סדרה
@Composable
fun SeriesScreen(s: SeriesItem, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ver = Prefs.version.intValue
    var eps by remember { mutableStateOf<List<Episode>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s.key) {
        val p = Prefs.activePlaylist()
        if (p == null) { err = L("אין רשימה פעילה"); return@LaunchedEffect }
        try { eps = Repo.episodes(p, s) } catch (e: Exception) { err = e.message ?: L("שגיאה") }
    }
    val all = eps ?: emptyList()
    val seasons = remember(all) { all.map { it.season }.distinct().sorted() }
    var season by remember(seasons) { mutableStateOf(seasons.firstOrNull() ?: 1) }
    val fav = remember(ver) { Prefs.isFav(s.key) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(s.name, onBack) {
            IconButton(onClick = { Prefs.toggleFav(s.key) }) {
                Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, L("מועדף"), tint = if (fav) Gold else MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            AsyncImage(
                s.logo, null,
                Modifier.size(width = 110.dp, height = 165.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(s.group, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                if (all.isNotEmpty()) Text(L("{0} עונות · {1} פרקים", seasons.size, all.size), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                s.plot?.let { Text(it, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                val next = remember(all, ver) {
                    all.firstOrNull { e -> Prefs.resume(e.key).let { it == null || it.first < it.second * 0.95 } }
                }
                if (next != null) Button(onClick = {
                    Launcher.play(ctx, all.map { it.toPlay(s) }, all.indexOf(next))
                }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("▶ " + (if (Prefs.resume(next.key) != null) L("המשך") else L("נגן")) + " S%02dE%02d".format(next.season, next.episode))
                }
            }
        }
        when {
            err != null -> Text(L("שגיאה: {0}", err), Modifier.padding(16.dp))
            eps == null -> Box(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            all.isEmpty() -> Text(L("לא נמצאו פרקים"), Modifier.padding(16.dp))
            else -> {
                if (seasons.size > 1) {
                    ScrollableTabRow(selectedTabIndex = seasons.indexOf(season).coerceAtLeast(0), edgePadding = 8.dp) {
                        seasons.forEach { n -> Tab(selected = n == season, onClick = { season = n }, text = { Text(L("עונה {0}", n)) }) }
                    }
                }
                val list = all.filter { it.season == season }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                    items(list) { e ->
                        val r = remember(ver, e.key) { Prefs.resume(e.key) }
                        FocusItem(Modifier.fillMaxWidth().padding(vertical = 2.dp), onClick = {
                            Launcher.play(ctx, all.map { it.toPlay(s) }, all.indexOf(e))
                        }) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(e.image ?: s.logo, null,
                                    Modifier.size(width = 104.dp, height = 58.dp).clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(L("פרק {0}", e.episode), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (e.title.isNotBlank()) Text(e.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (r != null && r.second > 0) {
                                        if (r.first >= r.second * 0.95) Text(L("✓ נצפה"), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                        else LinearProgressIndicator(
                                            progress = { (r.first.toFloat() / r.second).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = Color(0xFFE53935),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ ניהול קבוצות
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ManageGroupsScreen(startKind: Kind, onBack: () -> Unit) {
    var kind by remember { mutableStateOf(startKind) }
    val content = Repo.content
    val all: List<Entry> = when (kind) {
        Kind.LIVE -> content?.live
        Kind.MOVIE -> content?.movies
        Kind.SERIES -> content?.series
    } ?: emptyList()
    val ver = Prefs.version.intValue
    val groupsRaw = remember(all) { all.map { it.group }.distinct() }
    val groups = remember(groupsRaw, ver, kind) { Catalog.orderGroups(kind, groupsRaw) }
    val counts = remember(all) { all.groupingBy { it.group }.eachCount() }
    val countries = remember(groupsRaw) { groupsRaw.map { Countries.of(it) }.distinct().sortedBy { Countries.label(it) } }
    val hidden = remember(ver) { Prefs.hidden() }
    val allowed = remember(ver, kind) { Prefs.allowedCountries(kind) }
    val pinned = remember(ver, kind) { Prefs.pinnedGroups(kind) }
    val locked = remember(ver) { Prefs.lockedGroups() }
    val kidsOk = remember(ver) { Prefs.kidsGroups() }
    val hiddenItems = remember(ver, all) { val h = Prefs.hiddenItems(); all.filter { it.key in h } }
    var q by remember { mutableStateOf("") }
    var groupMenu by remember { mutableStateOf<String?>(null) }
    var countriesOpen by remember { mutableStateOf(false) }
    var cq by remember { mutableStateOf("") }
    val shown = remember(groups, q, allowed) {
        groups.filter { (q.isBlank() || it.contains(q, true)) && (allowed.isEmpty() || Countries.of(it) in allowed) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(L("ניהול קבוצות"), onBack)
        TabRow(selectedTabIndex = kind.ordinal) {
            Kind.entries.forEach { k -> Tab(selected = k == kind, onClick = { kind = k; q = "" }, text = { Text(k.label) }) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
            item {
                val toggleC: (String) -> Unit = { c ->
                    val n = allowed.toMutableSet(); if (!n.add(c)) n.remove(c); Prefs.setAllowedCountries(kind, n)
                }
                FocusItem(Modifier.fillMaxWidth(), onClick = { countriesOpen = !countriesOpen }) {
                    Row(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(L("סינון לפי ארצות"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (allowed.isEmpty()) L("מוצגות כל הארצות ({0})", countries.size)
                                else L("נבחרו: {0}", allowed.joinToString(", ") { Countries.label(it) }),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(if (countriesOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                    }
                }
                if (!countriesOpen && allowed.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        allowed.sortedBy { Countries.label(it) }.forEach { c ->
                            FilterChip(selected = true, onClick = { toggleC(c) }, label = { Text(Countries.label(c) + "  ✕") })
                        }
                    }
                }
                if (countriesOpen) {
                    OutlinedTextField(cq, { cq = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(L("הקלד שם מדינה (ישראל, UK, France…)")) })
                    Spacer(Modifier.height(6.dp))
                    val shownC = countries.filter { c ->
                        cq.isBlank() || Countries.label(c).contains(cq, true) || c.contains(cq, true) ||
                            java.util.Locale("", c).getDisplayCountry(java.util.Locale.ENGLISH).contains(cq, true)
                    }.sortedByDescending { it in allowed }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        shownC.forEach { c ->
                            FilterChip(selected = c in allowed, onClick = { toggleC(c) }, label = { Text(Countries.label(c)) })
                        }
                    }
                    if (shownC.isEmpty()) Text(L("לא נמצאה מדינה"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(L("ללא סימון = כל הארצות מוצגות."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (allowed.isNotEmpty()) TextButton(onClick = { Prefs.setAllowedCountries(kind, emptySet()) }) { Text(L("נקה בחירת ארצות")) }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text(L("חיפוש קבוצה…")) })
                Row {
                    TextButton(onClick = { Prefs.setHidden(kind, shown, false) }) { Text(L("הצג הכל")) }
                    TextButton(onClick = { Prefs.setHidden(kind, shown, true) }) { Text(L("הסתר הכל")) }
                }
                Text(L("לחיצה על קבוצה: נעילה, אישור לילדים, שינוי סדר"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val pinnedShown = shown.filter { it in pinned }
            if (pinnedShown.isNotEmpty()) {
                item { GroupHeader(L("📌 קבוצות נעוצות")) }
                items(pinnedShown) { g -> GroupRow(kind, g, counts[g] ?: 0, true, "${kind.name}|$g" !in hidden,
                    "${kind.name}|$g" in locked, "${kind.name}|$g" in kidsOk) { groupMenu = g } }
            }
            item { GroupHeader(L("כל הקבוצות")) }
            items(shown.filter { it !in pinned }) { g -> GroupRow(kind, g, counts[g] ?: 0, false, "${kind.name}|$g" !in hidden,
                "${kind.name}|$g" in locked, "${kind.name}|$g" in kidsOk) { groupMenu = g } }
            if (hiddenItems.isNotEmpty()) {
                item { GroupHeader(L("פריטים מוסתרים")) }
                items(hiddenItems) { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.name, Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = { Prefs.setItemHidden(e.key, false) }) { Text(L("החזר")) }
                    }
                }
            }
        }
    }

    groupMenu?.let { g ->
        val k = "${kind.name}|$g"
        AlertDialog(
            onDismissRequest = { groupMenu = null },
            title = { Text(g, maxLines = 2) },
            text = {
                Column {
                    SettingRow(L("⬆ הזז למעלה")) { Catalog.moveGroup(kind, groupsRaw, g, -1) }
                    SettingRow(L("⬇ הזז למטה")) { Catalog.moveGroup(kind, groupsRaw, g, 1) }
                    SettingRow(if (k in locked) L("🔓 בטל נעילה") else L("🔒 נעל בקוד")) {
                        Parental.require(L("נעילת קבוצה")) { Prefs.setGroupLocked(kind, g, k !in locked) }
                    }
                    SettingRow(if (k in kidsOk) L("👶 בטל אישור לילדים") else L("👶 אשר את כל הקבוצה לילדים")) {
                        Parental.require(L("אישור לילדים")) { Prefs.setKidsGroup(kind, g, k !in kidsOk) }
                    }
                    if (!Parental.hasPin()) Text(L("כדי שנעילה תעבוד יש להגדיר קוד הורים"), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = { groupMenu = null }) { Text(L("סגור")) } },
        )
    }
}

@Composable
private fun GroupHeader(t: String) {
    Text(t, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(10.dp))
}

@Composable
private fun GroupRow(kind: Kind, g: String, count: Int, pinned: Boolean, visible: Boolean, locked: Boolean, kids: Boolean, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(g, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (visible) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$count · ${Countries.label(Countries.of(g))}" + (if (locked) " · 🔒" else "") + (if (kids) " · 👶" else ""),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { Prefs.togglePinned(kind, g) }) {
                Icon(if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, L("נעיצה"))
            }
            IconButton(onClick = { Prefs.setHidden(kind, listOf(g), visible) }) {
                Icon(if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, L("הצג/הסתר"),
                    tint = if (visible) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ============================================================ הגדרות
@Composable
fun SettingsScreen(page: String, nav: (Screen) -> Unit, onBack: () -> Unit) {
    Prefs.version.intValue
    val title = when (page) {
        "epg" -> L("נתוני EPG"); "subs" -> L("שמע וכתוביות"); "player" -> L("אפשרויות נגן"); "parental" -> L("בקרת הורים ופרופילים")
        "content" -> L("תוכן"); "backup" -> L("גיבוי ושחזור"); "about" -> L("אודות"); else -> L("הגדרות")
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
            when (page) {
                "epg" -> EpgPage()
                "subs" -> SubsPage()
                "player" -> PlayerPage()
                "parental" -> ParentalPage()
                "content" -> ContentPage()
                "backup" -> BackupPage()
                "about" -> AboutPage()
                else -> MainSettings(nav)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun MainSettings(nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf<String?>(null) }
    Card {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(L("שפת אפליקציה"), fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !com.tbm.iptv.I18n.isEn, onClick = { com.tbm.iptv.I18n.set("he") })
                Text("עברית", Modifier.padding(end = 16.dp))
                RadioButton(selected = com.tbm.iptv.I18n.isEn, onClick = { com.tbm.iptv.I18n.set("en") })
                Text("English")
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Card {
        NavRow(L("מקורות תוכן"), Icons.Filled.Sensors) { nav(Screen.Playlists) }
        NavRow(L("ניהול קבוצות"), Icons.Filled.Folder) { nav(Screen.Groups(Kind.LIVE)) }
        NavRow(L("נתוני EPG"), Icons.Filled.DateRange) { nav(Screen.Settings("epg")) }
        NavRow(L("תוכן ופופולרי היום"), Icons.Filled.Tune) { nav(Screen.Settings("content")) }
        NavRow(L("נקה היסטוריה"), Icons.Filled.Delete, arrow = false) { confirm = "history" }
    }
    Spacer(Modifier.height(10.dp))
    Card {
        NavRow(L("שמע וכתוביות"), Icons.Filled.ClosedCaption) { nav(Screen.Settings("subs")) }
        NavRow(L("אפשרויות נגן"), Icons.Filled.SettingsSuggest) { nav(Screen.Settings("player")) }
        NavRow(L("בקרת הורים ופרופילים"), Icons.Filled.Shield) {
            Parental.requireAlways(L("בקרת הורים")) { nav(Screen.Settings("parental")) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Card {
        NavRow(L("גיבוי ושחזור"), Icons.Filled.Backup) { nav(Screen.Settings("backup")) }
        NavRow(L("איפוס העדפות משתמש"), Icons.Filled.RestartAlt, arrow = false) { confirm = "reset" }
    }
    Spacer(Modifier.height(10.dp))
    Card { NavRow(L("אודות"), Icons.Filled.Info) { nav(Screen.Settings("about")) } }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (c == "history") L("ניקוי היסטוריה") else L("איפוס העדפות")) },
            text = {
                Text(if (c == "history") L("למחוק את היסטוריית הצפייה והערוצים האחרונים?")
                else L("לאפס את כל ההעדפות (מועדפים, סינונים, פרופילים, קוד)? רשימות הערוצים והמפתחות יישמרו."))
            },
            confirmButton = {
                TextButton(onClick = {
                    if (c == "history") Prefs.clearHistory() else Prefs.resetPreferences()
                    Toast.makeText(ctx, L("בוצע"), Toast.LENGTH_SHORT).show(); confirm = null
                }) { Text(L("אישור")) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(L("ביטול")) } },
        )
    }
}

@Composable
private fun EpgPage() {
    val p = Prefs.activePlaylist()
    val content = Repo.content
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(L("מצב: ") + Epg.status.ifBlank { if (Epg.isEmpty) L("לא נטען") else L("נטען ✓") }, fontSize = 13.sp)
            if (p != null) {
                Field(L("כתובת XMLTV ידנית (לא חובה)"), Prefs.epgUrl(p), KeyboardType.Uri) { Prefs.setEpgUrl(p, it) }
                Hint(L("ב-Xtream המדריך נטען אוטומטית. ב-M3U נטען מ-url-tvg אם קיים. אפשר להדביק כאן כל קישור XMLTV (גם ‎.gz)."))
                Button(onClick = { if (content != null) Repo.scope.launch { Epg.load(p, content, true) } }) { Text(L("טען מדריך עכשיו")) }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    RefreshChoice()
}

@Composable
private fun RefreshChoice() {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(L("רענון אוטומטי של רשימה ומדריך"), fontWeight = FontWeight.Bold)
            listOf(6 to L("כל 6 שעות"), 12 to L("כל 12 שעות"), 24 to L("פעם ביום"), 72 to L("כל 3 ימים")).forEach { (h, l) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = Prefs.refreshHours == h, onClick = { Prefs.refreshHours = h })
                    Text(l)
                }
            }
        }
    }
}

@Composable
private fun ContentPage() {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(L("פופולרי היום (TMDB)"), fontWeight = FontWeight.Bold)
            Field(L("מפתח TMDB API"), Prefs.tmdbKey, secret = true) { Prefs.tmdbKey = it }
            Hint(L("מפתח חינמי: themoviedb.org ← הרשמה ← Settings ← API ← Request an API key. מתאים לך את הסרטים והסדרות הפופולריים היום מתוך מה שיש אצל הספק."))
        }
    }
    Spacer(Modifier.height(10.dp))
    RefreshChoice()
}

@Composable
private fun SubsPage() {
    val ctx = LocalContext.current
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(L("שמע"), fontWeight = FontWeight.Bold)
            Field(L("שפת שמע מועדפת (he / en / ריק = ברירת מחדל)"), Prefs.audioLang) { Prefs.audioLang = it.lowercase() }
        }
    }
    Spacer(Modifier.height(10.dp))
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(L("כתוביות אונליין"), fontWeight = FontWeight.Bold)
            SwitchRow(L("חיפוש וטעינה אוטומטיים בסרטים וסדרות"), Prefs.autoSubs) { Prefs.autoSubs = it }
            Field(L("שפות מועדפות לפי סדר (he,en)"), Prefs.str("sub_langs", "he,en")) { Prefs.setStr("sub_langs", it) }
            Text(L("גודל כתוביות: {0}×", "%.1f".format(Prefs.subSize)), fontSize = 13.sp)
            Slider(value = Prefs.subSize, onValueChange = { Prefs.subSize = it }, valueRange = 0.6f..2.0f)
            OutlinedButton(onClick = {
                val n = SubtitleManager.clearCache()
                Toast.makeText(ctx, L("נמחקו {0} כתוביות שמורות", n), Toast.LENGTH_SHORT).show()
            }) { Text(L("נקה מטמון כתוביות")) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(L("התחברות לאתרי כתוביות"), fontWeight = FontWeight.Bold)
            Provider(L("Wizdom – עברית, ללא הרשמה"), "p_wizdom")

            Provider(L("כתובית (Ktuvit) – עברית"), "p_ktuvit")
            Field(L("אימייל בכתובית"), Prefs.str("ktuvit_email"), KeyboardType.Email) { Prefs.setStr("ktuvit_email", it) }
            Field(L("סיסמה מוצפנת (hashed)"), Prefs.str("ktuvit_hash"), secret = true) { Prefs.setStr("ktuvit_hash", it) }
            Hint(L("כתובית לא מקבלת סיסמה רגילה. במחשב: התחבר ל-ktuvit.me ← F12 ← Network ← בקשת Login ← Payload – העתק את ערך Password."))

            Provider(L("OpenSubtitles – עברית ואנגלית"), "p_os")
            Field(L("מפתח API"), Prefs.str("os_key"), secret = true) { Prefs.setStr("os_key", it) }
            Field(L("שם משתמש (לא חובה – מגדיל מכסה)"), Prefs.str("os_user")) { Prefs.setStr("os_user", it) }
            Field(L("סיסמה"), Prefs.str("os_pass"), secret = true) { Prefs.setStr("os_pass", it) }
            Hint(L("מפתח חינמי: opensubtitles.com ← Profile ← API consumers ← New consumer."))

            Provider(L("SubDL – עברית ואנגלית"), "p_subdl")
            Field(L("מפתח API"), Prefs.str("subdl_key"), secret = true) { Prefs.setStr("subdl_key", it) }
            Hint(L("מפתח חינמי: subdl.com ← Account ← API Key."))

            Provider(L("SubSource – עברית ואנגלית"), "p_subsource")
            Field(L("מפתח API"), Prefs.str("subsource_key"), secret = true) { Prefs.setStr("subsource_key", it) }
            Hint(L("מפתח חינמי: subsource.net ← חשבון ← לשונית API."))

            Provider(L("OpenSubtitles v3 – ללא מפתח"), "p_os3")
            Provider(L("OpenSubtitles.org – ללא מפתח"), "p_osorg")
            Provider(L("Podnapisi – ללא הרשמה"), "p_podnapisi", false)
            Provider(L("YIFY – סרטים, ללא הרשמה"), "p_yify", false)
        }
    }
    Spacer(Modifier.height(10.dp))
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(L("כשאין כתובית בעברית"), fontWeight = FontWeight.Bold)
            SwitchRow(L("תרגום אוטומטי של כתובית אנגלית לעברית"), Prefs.bool("machine_tr", true)) { Prefs.setBool("machine_tr", it) }
            listOf("ask" to L("לשאול אותי בכל פעם"), "auto" to L("לתרגם אוטומטית בלי לשאול")).forEach { (v, l) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = Prefs.str("mt_mode", "ask") == v, onClick = { Prefs.setStr("mt_mode", v) })
                    Text(l)
                }
            }
            Hint(L("התרגום מבוצע ב-Google Translate ומוצג מימין לשמאל. איכותו נמוכה מתרגום אנושי."))
        }
    }
}

@Composable
private fun PlayerPage() {
    Card {
        Column(Modifier.padding(12.dp)) {
            SwitchRow(L("In-App PiP – חלון צף בזמן דפדוף"), Prefs.inAppPip) { Prefs.inAppPip = it }
            SwitchRow(L("System PiP – תמונה בתוך תמונה ביציאה מהאפליקציה"), Prefs.systemPip) { Prefs.systemPip = it }
            SwitchRow(L("ניגון אוטומטי של הפרק הבא"), Prefs.autoNext) { Prefs.autoNext = it }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(L("קריינות AI מהכתוביות"), fontWeight = FontWeight.Bold)
            SwitchRow(L("להפעיל אוטומטית בסרטים וסדרות"), Prefs.voiceDefault) { Prefs.voiceDefault = it }
            Text(L("מהירות דיבור: {0}×", "%.1f".format(Prefs.voiceRate)), fontSize = 13.sp)
            Slider(value = Prefs.voiceRate, onValueChange = { Prefs.voiceRate = it }, valueRange = 0.6f..1.8f)
            Text(L("עוצמת הקול המקורי בזמן הקריינות: {0}%", (Prefs.voiceDuck * 100).toInt()), fontSize = 13.sp)
            Slider(value = Prefs.voiceDuck, onValueChange = { Prefs.voiceDuck = it }, valueRange = 0f..1f)
            Hint(L("הכפתור 🗣 בנגן מפעיל ומכבה. דורש קול עברי מותקן: הגדרות אנדרואיד ← שפה וקלט ← המרת טקסט לדיבור ← Google ← עברית."))
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SwitchRow(L("נגן חיצוני (VLC / MX)"), Prefs.externalPlayer) { Prefs.externalPlayer = it }
            Text(L("זמן הצגת פקדים: {0} שניות", Prefs.osdTimeout), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 5, 8, 12).forEach { s ->
                    FilterChipSimple("$s", Prefs.osdTimeout == s) { Prefs.osdTimeout = s }
                }
            }
            Field(L("User-Agent (אם הספק חוסם)"), Prefs.userAgent) { Prefs.userAgent = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipSimple(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ParentalPage() {
    val ctx = LocalContext.current
    var pinDialog by remember { mutableStateOf(false) }
    var newProfile by remember { mutableStateOf<Boolean?>(null) }
    val profiles = Prefs.profiles()

    Card {
        Column(Modifier.padding(12.dp)) {
            Text(L("קוד הורים"), fontWeight = FontWeight.Bold)
            Text(if (Parental.hasPin()) L("קוד מוגדר ✓") else L("לא הוגדר קוד – נעילות לא פעילות"), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pinDialog = true }) { Text(if (Parental.hasPin()) L("שנה קוד") else L("הגדר קוד")) }
                if (Parental.hasPin()) OutlinedButton(onClick = { Prefs.pin = ""; Prefs.settingsLock = false }) { Text(L("הסר קוד")) }
            }
            SwitchRow(L("כניסה בטביעת אצבע / פנים"), Prefs.biometric) { Prefs.biometric = it }
            SwitchRow(L("נעילת הגדרות בקוד"), Prefs.settingsLock) { Prefs.settingsLock = it }
            SwitchRow(L("ניקוי היסטוריית צפייה ביציאה"), Prefs.clearHistoryOnExit) { Prefs.clearHistoryOnExit = it }
        }
    }
    Spacer(Modifier.height(10.dp))
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(L("קטגוריות למבוגרים (בפרופיל מבוגר)"), fontWeight = FontWeight.Bold)
            listOf("lock" to L("נעול בקוד"), "hide" to L("מוסתר לגמרי"), "show" to L("מוצג")).forEach { (v, l) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = Prefs.adultMode == v, onClick = { Prefs.adultMode = v })
                    Text(l)
                }
            }
            Hint(L("בפרופיל ילדים תוכן למבוגרים לעולם לא מוצג."))
        }
    }
    Spacer(Modifier.height(10.dp))
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(L("פרופילים"), fontWeight = FontWeight.Bold)
            profiles.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Icon(if (p.kids) Icons.Filled.ChildCare else Icons.Filled.Lock, null,
                        tint = if (p.kids) Color(0xFF00A86B) else MaterialTheme.colorScheme.primary)
                    Text(p.name + if (p.kids) L(" (ילדים)") else L(" (מבוגר)"), Modifier.weight(1f).padding(horizontal = 8.dp))
                    if (p.id != "main") IconButton(onClick = { Prefs.saveProfiles(profiles.filter { it.id != p.id }) }) {
                        Icon(Icons.Filled.Delete, L("מחק"))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { newProfile = true }) { Text(L("+ פרופיל ילדים")) }
                OutlinedButton(onClick = { newProfile = false }) { Text(L("+ פרופיל מבוגר")) }
            }
            Hint(L("פרופיל ילדים רואה רק ערוצים, סרטים וסדרות שאישרת: לחיצה ארוכה על פריט ← \"אשר לפרופיל ילדים\", או ניהול קבוצות ← לחיצה על קבוצה ← \"אשר לילדים\". מעבר מפרופיל ילדים למבוגר דורש קוד."))
            if (!Parental.hasPin() && profiles.any { it.kids }) Text(L("⚠ הגדר קוד הורים – אחרת הילדים יוכלו לעבור לפרופיל מבוגר"),
                color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }

    if (pinDialog) PinSetDialog { pinDialog = false }
    newProfile?.let { kids ->
        var name by remember { mutableStateOf(if (kids) L("ילדים") else "") }
        AlertDialog(
            onDismissRequest = { newProfile = null },
            title = { Text(if (kids) L("פרופיל ילדים חדש") else L("פרופיל מבוגר חדש")) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(L("שם")) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    Prefs.saveProfiles(profiles + Profile(UUID.randomUUID().toString().take(6), name.trim(), kids))
                    Toast.makeText(ctx, L("הפרופיל נוסף"), Toast.LENGTH_SHORT).show()
                    newProfile = null
                }) { Text(L("הוסף")) }
            },
            dismissButton = { TextButton(onClick = { newProfile = null }) { Text(L("ביטול")) } },
        )
    }
}

@Composable
private fun PinSetDialog(onDone: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val ok = a.length in 4..8 && a == b
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(L("קוד הורים חדש")) },
        text = {
            Column {
                OutlinedTextField(a, { v -> a = v.filter { it.isDigit() }.take(8) }, label = { Text(L("קוד (4–8 ספרות)")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                OutlinedTextField(b, { v -> b = v.filter { it.isDigit() }.take(8) }, label = { Text(L("אימות קוד")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (b.isNotEmpty() && a != b) Text(L("הקודים לא תואמים"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { Prefs.pin = a; Parental.unlocked = true; onDone() }) { Text(L("שמור")) } },
        dismissButton = { TextButton(onClick = onDone) { Text(L("ביטול")) } },
    )
}

@Composable
private fun BackupPage() {
    val ctx = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(Prefs.exportJson().toByteArray()) }
            Toast.makeText(ctx, L("הגיבוי נשמר"), Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(ctx, L("שמירה נכשלה"), Toast.LENGTH_SHORT).show() }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val txt = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            Prefs.importJson(txt)
            Prefs.activePlaylist()?.let { Repo.loadAsync(it, true) }
            Toast.makeText(ctx, L("השחזור הושלם"), Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(ctx, it.message ?: L("שחזור נכשל"), Toast.LENGTH_LONG).show() }
    }
    Card {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Backup, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(L("גיבוי ושחזור"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(L("שומר רשימות, מועדפים, סינונים, פרופילים, הגדרות ומפתחות לקובץ אחד. שימושי למעבר בין טלפון לטלוויזיה."),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
            Button(onClick = {
                save.launch("TBMPlayer-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json")
            }, modifier = Modifier.fillMaxWidth()) { Text(L("גיבוי")) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { load.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(L("שחזור")) }
        }
    }
}

@Composable
private fun AboutPage() {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("TBM Player 1.0", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(L("נגן IPTV ל-M3U ו-Xtream: לוח שידורים, צפייה לאחור, הקלטות, Multi-view, Chromecast, בקרת הורים ופרופילים, וכתוביות אוטומטיות מ-Wizdom, כתובית, OpenSubtitles ו-SubDL."),
                fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Text(L("האפליקציה היא נגן בלבד ואינה מספקת תוכן."), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ---------- רכיבי הגדרות ----------
@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun NavRow(label: String, icon: ImageVector, arrow: Boolean = true, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, Modifier.weight(1f).padding(horizontal = 14.dp), fontSize = 16.sp)
            if (arrow) Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingRow(label: String, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth(), onClick = onClick) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
    }
}

@Composable private fun Hint(t: String) {
    Text(t, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable private fun Provider(label: String, key: String, def: Boolean = true) {
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    SwitchRow(label, Prefs.bool(key, def)) { Prefs.setBool(key, it) }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    FocusItem(Modifier.fillMaxWidth(), onClick = { onChange(!value) }) {
        Row(Modifier.padding(vertical = 4.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Switch(checked = value, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, type: KeyboardType = KeyboardType.Text, secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it.trim()) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else type),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    )
}

// ============================================================ מקורות תוכן
@Composable
fun PlaylistsScreen(onDone: () -> Unit, onBack: (() -> Unit)?) {
    val ver = Prefs.version.intValue
    val lists = remember(ver) { Prefs.playlists() }
    val active = remember(ver) { Prefs.activePlaylist()?.id }
    var dialog by remember { mutableStateOf<String?>(null) }
    val df = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }

    fun activate(p: Playlist, force: Boolean = false) {
        Prefs.activeId = p.id
        Repo.loadAsync(p, force)
        onDone()
    }

    val ctx = LocalContext.current
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val txt = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            Prefs.importJson(txt)
            com.tbm.iptv.I18n.init()
            val p = Prefs.activePlaylist() ?: throw IllegalStateException(L("בגיבוי אין רשימת ערוצים"))
            Toast.makeText(ctx, L("השחזור הושלם"), Toast.LENGTH_SHORT).show()
            activate(p)
        }.onFailure { Toast.makeText(ctx, it.message ?: L("שחזור נכשל"), Toast.LENGTH_LONG).show() }
    }

    if (lists.isEmpty() && onBack == null) {
        WelcomeScreen(
            onXtream = { dialog = "xtream" },
            onM3u = { dialog = "m3u" },
            onRestore = { restore.launch(arrayOf("application/json", "*/*")) },
        )
        val addFirst: (Playlist) -> Unit = { p -> Prefs.savePlaylists(listOf(p)); dialog = null; activate(p) }
        when (dialog) {
            "m3u" -> M3uDialog(onAdd = addFirst, onDismiss = { dialog = null })
            "xtream" -> XtreamDialog(onAdd = addFirst, onDismiss = { dialog = null })
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (onBack != null) ScreenHeader(L("מקורות תוכן"), onBack)
        else Column(Modifier.padding(20.dp)) {
            Text("TBM Player", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 28.sp)
            Text(L("הוסף רשימת M3U או חשבון Xtream Codes כדי להתחיל"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            lists.forEach { p ->
                val exp = Prefs.str("exp_${p.id}").toLongOrNull()?.takeIf { it > 0 }?.let { df.format(Date(it * 1000)) }
                val upd = Prefs.str("updated_${p.id}").toLongOrNull()?.let { df.format(Date(it)) }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (p.id == active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                ) {
                    FocusItem(Modifier.fillMaxWidth(), onClick = { activate(p) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (p.id == active) "✓" else "", Modifier.width(22.dp), color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(if (p.isXtream) "XCAPI" else if (p.url.startsWith("file://")) L("M3U · קובץ מקומי") else "M3U",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                upd?.let { Text(L("עדכון אחרון: {0}", it), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                exp?.let { Text(L("תוקף: {0}", it), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            IconButton(onClick = { activate(p, true) }) { Icon(Icons.Filled.Refresh, L("רענון")) }
                            IconButton(onClick = {
                                Repo.deletePlaylistCache(p)
                                Prefs.savePlaylists(lists.filter { it.id != p.id })
                            }) { Icon(Icons.Filled.Delete, L("מחיקה")) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { dialog = "xtream" }) { Text("+ Xtream Codes") }
                OutlinedButton(onClick = { dialog = "m3u" }) { Text(L("+ רשימת M3U")) }
            }
            TextButton(onClick = { restore.launch(arrayOf("application/json", "*/*")) }) { Text(L("שחזור מגיבוי…")) }
        }
        if (lists.isNotEmpty()) Button(
            onClick = { Prefs.activePlaylist()?.let { activate(it, true) } },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text(L("רענן הכל")) }
    }

    val add: (Playlist) -> Unit = { p ->
        Prefs.savePlaylists(lists + p)
        dialog = null
        activate(p)
    }
    when (dialog) {
        "m3u" -> M3uDialog(onAdd = add, onDismiss = { dialog = null })
        "xtream" -> XtreamDialog(onAdd = add, onDismiss = { dialog = null })
    }
}

@Composable
private fun M3uDialog(onAdd: (Playlist) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val f = File(ctx.filesDir, "local_${System.currentTimeMillis()}.m3u")
                ctx.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                url = "file://${f.absolutePath}"
                if (name.isBlank()) name = L("קובץ מקומי")
            } catch (e: Exception) {
                Toast.makeText(ctx, L("לא ניתן לקרוא את הקובץ"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("הוספת רשימת M3U")) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(L("שם")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text(L("כתובת M3U (http…)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { pick.launch(arrayOf("*/*")) }) { Text(L("או בחר קובץ מהמכשיר…")) }
                Text(L("קישור get.php עם שם משתמש וסיסמה יומר אוטומטית ל-Xtream (סדרות, EPG וצפייה לאחור)."),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = url.isNotBlank(), onClick = {
                val u = url.trim()
                val parsed = runCatching { Uri.parse(u) }.getOrNull()
                val user = parsed?.getQueryParameter("username")
                val pass = parsed?.getQueryParameter("password")
                val id = UUID.randomUUID().toString().take(8)
                val p = if (u.contains("get.php") && !user.isNullOrBlank() && !pass.isNullOrBlank())
                    Playlist(id, name.ifBlank { parsed?.host ?: "Xtream" }, "xtream", u, user, pass)
                else Playlist(id, name.ifBlank { L("רשימה") }, "m3u", u)
                onAdd(p)
            }) { Text(L("הוסף")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("ביטול")) } },
    )
}

@Composable
private fun XtreamDialog(onAdd: (Playlist) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("חשבון Xtream Codes")) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(L("שם")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(server, { server = it }, label = { Text(L("כתובת שרת (http://host:port)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text(L("שם משתמש")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text(L("סיסמה")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = server.isNotBlank() && user.isNotBlank() && pass.isNotBlank(), onClick = {
                onAdd(Playlist(UUID.randomUUID().toString().take(8), name.ifBlank { "Xtream" }, "xtream",
                    server.trim(), user.trim(), pass.trim()))
            }) { Text(L("התחבר")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("ביטול")) } },
    )
}

// ============================================================ מסך פתיחה
@Composable
private fun WelcomeScreen(onXtream: () -> Unit, onM3u: () -> Unit, onRestore: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color(0xFF0E2A4A), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)
            )
        )
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("TBM", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 52.sp)
                Text(" Player", fontWeight = FontWeight.Bold, fontSize = 34.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(L("טלוויזיה, סרטים וסדרות – עם כתוביות בעברית"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 36.dp))
            WelcomeButton(L("כניסה עם Xtream Codes"), L("שרת, שם משתמש וסיסמה"), primary = true, onClick = onXtream)
            Spacer(Modifier.height(12.dp))
            WelcomeButton(L("רשימת M3U"), L("קישור או קובץ מהמכשיר"), primary = false, onClick = onM3u)
            Spacer(Modifier.height(12.dp))
            WelcomeButton(L("שחזור מגיבוי"), L("מחזיר חשבון, מועדפים, הגדרות ומפתחות מקובץ גיבוי"), primary = false, onClick = onRestore)
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { com.tbm.iptv.I18n.set("he") }) {
                    Text("עברית", fontWeight = if (!com.tbm.iptv.I18n.isEn) FontWeight.Bold else FontWeight.Normal)
                }
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { com.tbm.iptv.I18n.set("en") }) {
                    Text("English", fontWeight = if (com.tbm.iptv.I18n.isEn) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun WelcomeButton(title: String, sub: String, primary: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
    ) {
        FocusItem(Modifier.fillMaxWidth(), onClick = onClick) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    color = if (primary) Color.White else MaterialTheme.colorScheme.onSurface)
                Text(sub, fontSize = 12.sp,
                    color = if (primary) Color(0xDDFFFFFF) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
