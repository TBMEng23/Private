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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
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
    var viewPick by remember { mutableStateOf<Kind?>(null) }
    val modeName: (Kind) -> String = { k ->
        when (Prefs.str("view_${k.name}", "grid")) { "rows" -> L("שורות לפי קטגוריה"); "cats" -> L("רשימת קטגוריות"); else -> L("רשת + בחירת קטגוריה") }
    }
    val p = Prefs.activePlaylist()
    GroupTitle(L("כללי"))
    Card {
        NavRow(L("שפת אפליקציה"), Icons.Filled.Translate, sub = if (com.tbm.iptv.I18n.isEn) "English" else "עברית") {
            com.tbm.iptv.I18n.set(if (com.tbm.iptv.I18n.isEn) "he" else "en")
        }
        NavRow(L("מקורות תוכן"), Icons.Filled.Sensors, sub = p?.name) { nav(Screen.Playlists) }
        NavRow(L("נתוני EPG"), Icons.Filled.DateRange, sub = L("לוח שידורים וצפייה לאחור")) { nav(Screen.Settings("epg")) }
    }
    GroupTitle(L("תצוגה"))
    Card {
        NavRow(L("תצוגת ערוצים"), Icons.Filled.LiveTv, sub = modeName(Kind.LIVE)) { viewPick = Kind.LIVE }
        NavRow(L("תצוגת סרטים"), Icons.Filled.Movie, sub = modeName(Kind.MOVIE)) { viewPick = Kind.MOVIE }
        NavRow(L("תצוגת סדרות"), Icons.Filled.VideoLibrary, sub = modeName(Kind.SERIES)) { viewPick = Kind.SERIES }
        NavRow(L("סידור והסתרת קטגוריות"), Icons.Filled.Folder, sub = L("סדר, הסתרה, הצמדה, סינון ארצות")) { nav(Screen.Groups(Kind.LIVE)) }
        NavRow(L("תוכן ופופולרי היום"), Icons.Filled.Tune, sub = L("מפתח TMDB")) { nav(Screen.Settings("content")) }
        val trOn = Prefs.bool("translate_meta", true)
        NavRow(L("תרגום תקצירים, ז'אנרים ושמות"), Icons.Filled.Translate, arrow = false,
            sub = if (trOn) L("פעיל – לשפת הממשק (TMDB / Google)") else L("כבוי")) { Prefs.setBool("translate_meta", !trOn) }
    }
    GroupTitle(L("נגן"))
    Card {
        NavRow(L("אפשרויות נגן"), Icons.Filled.SettingsSuggest, sub = L("PiP, פרק הבא אוטומטי, נגן חיצוני, קריינות")) { nav(Screen.Settings("player")) }
        NavRow(L("שמע וכתוביות"), Icons.Filled.ClosedCaption, sub = L("חיפוש אוטומטי, מקורות, תרגום מכונה")) { nav(Screen.Settings("subs")) }
    }
    GroupTitle(L("בקרת הורים"))
    Card {
        NavRow(L("בקרת הורים ופרופילים"), Icons.Filled.Shield, sub = L("קוד, נעילות, פרופיל ילדים")) {
            Parental.requireAlways(L("בקרת הורים")) { nav(Screen.Settings("parental")) }
        }
    }
    GroupTitle(L("נתונים"))
    Card {
        NavRow(L("גיבוי ושחזור"), Icons.Filled.Backup) { nav(Screen.Settings("backup")) }
        NavRow(L("היסטוריית צפייה"), Icons.Filled.Delete, arrow = false, sub = L("{0} פריטים – לחץ לניקוי", Prefs.recent().size)) { confirm = "history" }
        NavRow(L("איפוס העדפות משתמש"), Icons.Filled.RestartAlt, arrow = false) { confirm = "reset" }
    }
    GroupTitle(L("אודות"))
    Card { NavRow(L("אודות"), Icons.Filled.Info, sub = "TBM Player " + com.tbm.iptv.appVersion(ctx)) { nav(Screen.Settings("about")) } }
    viewPick?.let { k ->
        AlertDialog(
            onDismissRequest = { viewPick = null },
            title = { Text(L("תצוגת {0}", k.label)) },
            text = {
                Column {
                    listOf("grid" to L("רשת + בחירת קטגוריה"), "rows" to L("שורות לפי קטגוריה"), "cats" to L("רשימת קטגוריות")).forEach { (m, t) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = Prefs.str("view_${k.name}", "grid") == m, onClick = { Prefs.setStr("view_${k.name}", m); viewPick = null })
                            Text(t)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewPick = null }) { Text(L("סגור")) } },
        )
    }
    val vctx = LocalContext.current
    Text("TBM Player " + remember { com.tbm.iptv.appVersion(vctx) }, fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)

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
            SwitchRow(L("כתובית חיצונית מחליפה כתוביות מובנות בקובץ"), Prefs.replaceEmbedded) { Prefs.replaceEmbedded = it }
            SwitchRow(L("רקע שחור (מכסה כתוביות צרובות בתמונה)"), Prefs.subBox) { Prefs.subBox = it }
            Text(L("גובה הכתוביות"), fontSize = 13.sp)
            Slider(value = Prefs.subPos, onValueChange = { Prefs.subPos = it }, valueRange = 0f..0.5f)
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
            SubTestPanel()
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
            VoiceSettings()
            Text(L("מהירות דיבור: {0}×", "%.1f".format(Prefs.voiceRate)), fontSize = 13.sp)
            Slider(value = Prefs.voiceRate, onValueChange = { Prefs.voiceRate = it }, valueRange = 0.6f..1.8f)
            Text(L("עוצמת הקול המקורי בזמן הקריינות: {0}%", (Prefs.voiceDuck * 100).toInt()), fontSize = 13.sp)
            Slider(value = Prefs.voiceDuck, onValueChange = { Prefs.voiceDuck = it }, valueRange = 0f..1f)
            Hint(L("הכפתור 🗣 בנגן מפעיל ומכבה. דורש קול עברי מותקן: הגדרות אנדרואיד ← שפה וקלט ← המרת טקסט לדיבור ← Google ← עברית."))
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ExternalPlayerSettings()
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
            val ctx = LocalContext.current
            val ver = remember { com.tbm.iptv.appVersion(ctx) }
            Text("TBM Player", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(L("גרסה {0}", ver), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
private fun GroupTitle(t: String) {
    Text(t, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun NavRow(label: String, icon: ImageVector, arrow: Boolean = true, sub: String? = null, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = if (sub != null) 11.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(label, fontSize = 16.sp)
                sub?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
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
    var editing by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    var menuFor by remember { mutableStateOf<Playlist?>(null) }
    var infoFor by remember { mutableStateOf<Playlist?>(null) }
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
                    val disabled = Prefs.bool("disabled_${p.id}", false)
                    FocusItem(Modifier.fillMaxWidth(), onLongClick = { menuFor = p }, onClick = {
                        if (disabled) menuFor = p else activate(p)
                    }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (p.id == active) "✓" else "", Modifier.width(22.dp), color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(if (p.isXtream) "XCAPI" else if (p.url.startsWith("file://")) L("M3U · קובץ מקומי") else "M3U",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                upd?.let { Text(L("עדכון אחרון: {0}", it), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                exp?.let { Text(L("תוקף: {0}", it), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                if (p.isXtream && p.user.isNotBlank()) Text(L("משתמש: {0}", p.user), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (disabled) L("מושבת") else if (p.id == active) L("● פעיל כעת") else L("לחץ כדי לעבור למקור הזה"), fontSize = 12.sp,
                                    color = if (p.id == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (p.id == active) FontWeight.Bold else FontWeight.Normal)
                            }
                            IconButton(onClick = { menuFor = p }) { Icon(Icons.Filled.MoreVert, L("אפשרויות")) }
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
    editing?.let { old ->
        val save: (Playlist) -> Unit = { np ->
            val changed = np.url != old.url || np.user != old.user || np.pass != old.pass || np.type != old.type
            Prefs.savePlaylists(lists.map { if (it.id == old.id) np else it })
            editing = null
            if (changed) {
                Repo.deletePlaylistCache(old)
                Prefs.setStrQuiet("tz_${old.id}", ""); Prefs.setStrQuiet("exp_${old.id}", "")
                if (old.id == active) activate(np, true)
            }
            Toast.makeText(ctx, L("נשמר"), Toast.LENGTH_SHORT).show()
        }
        SourceEditor(old, onSave = save, onDismiss = { editing = null })
    }
    menuFor?.let { m ->
        val disabled = Prefs.bool("disabled_${m.id}", false)
        SourceMenu(m.name, disabled, onDismiss = { menuFor = null }) { action ->
            menuFor = null
            when (action) {
                "refresh" -> activate(m, true)
                "epg" -> {
                    val c = Repo.content
                    if (m.id == active && c != null) {
                        Repo.scope.launch { com.tbm.iptv.data.Epg.load(m, c, true) }
                        Toast.makeText(ctx, L("מעדכן מדריך שידורים…"), Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(ctx, L("עבור למקור הזה כדי לעדכן את המדריך"), Toast.LENGTH_SHORT).show()
                }
                "clear" -> { com.tbm.iptv.data.Epg.clear(m); Toast.makeText(ctx, L("המדריך נמחק"), Toast.LENGTH_SHORT).show() }
                "disable" -> Prefs.setBool("disabled_${m.id}", !disabled)
                "edit" -> editing = m
                "delete" -> deleting = m
                "info" -> infoFor = m
            }
        }
    }
    infoFor?.let { SourceInfo(it) { infoFor = null } }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(L("מחיקת מקור")) },
            text = { Text(L("למחוק את \"{0}\"?", d.name)) },
            confirmButton = {
                TextButton(onClick = {
                    Repo.deletePlaylistCache(d)
                    val rest = lists.filter { it.id != d.id }
                    Prefs.savePlaylists(rest)
                    deleting = null
                    if (d.id == active) rest.firstOrNull()?.let { activate(it) }
                }) { Text(L("מחק")) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(L("ביטול")) } },
        )
    }
}

@Composable
private fun M3uDialog(onAdd: (Playlist) -> Unit, onDismiss: () -> Unit, edit: Playlist? = null) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(edit?.name ?: "") }
    var url by remember { mutableStateOf(edit?.url ?: "") }
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
        title = { Text(if (edit != null) L("עריכת רשימת M3U") else L("הוספת רשימת M3U")) },
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
                val id = edit?.id ?: UUID.randomUUID().toString().take(8)
                val p = if (u.contains("get.php") && !user.isNullOrBlank() && !pass.isNullOrBlank())
                    Playlist(id, name.ifBlank { parsed?.host ?: "Xtream" }, "xtream", u, user, pass)
                else Playlist(id, name.ifBlank { L("רשימה") }, "m3u", u)
                onAdd(p)
            }) { Text(if (edit != null) L("שמור") else L("הוסף")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("ביטול")) } },
    )
}

@Composable
private fun XtreamDialog(onAdd: (Playlist) -> Unit, onDismiss: () -> Unit, edit: Playlist? = null) {
    var name by remember { mutableStateOf(edit?.name ?: "") }
    var server by remember { mutableStateOf(edit?.url ?: "") }
    var user by remember { mutableStateOf(edit?.user ?: "") }
    var pass by remember { mutableStateOf(edit?.pass ?: "") }
    var showPass by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (edit != null) L("עריכת חשבון Xtream") else L("חשבון Xtream Codes")) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(L("שם")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(server, { server = it }, label = { Text(L("כתובת שרת (http://host:port)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text(L("שם משתמש")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text(L("סיסמה")) }, singleLine = true,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, L("הצג סיסמה"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = server.isNotBlank() && user.isNotBlank() && pass.isNotBlank(), onClick = {
                onAdd(Playlist(edit?.id ?: UUID.randomUUID().toString().take(8), name.ifBlank { "Xtream" }, "xtream",
                    server.trim(), user.trim(), pass.trim()))
            }) { Text(if (edit != null) L("שמור") else L("התחבר")) }
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

/** בדיקת חיבור לכל ספק כתוביות */
@Composable
private fun SubTestPanel() {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val results = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<String, Pair<Boolean, String>?>() }
    val running = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf<String>() }
    fun run(p: com.tbm.iptv.subs.SubProvider) {
        if (p.name in running) return
        running += p.name; results[p.name] = null
        scope.launch {
            results[p.name] = com.tbm.iptv.subs.SubtitleManager.test(p)
            running -= p.name
        }
    }
    Column(Modifier.padding(vertical = 6.dp)) {
        OutlinedButton(onClick = { com.tbm.iptv.subs.SubtitleManager.providers.forEach { run(it) } }, modifier = Modifier.fillMaxWidth()) {
            Text(L("🔌 בדוק חיבור לכל המקורות"))
        }
        com.tbm.iptv.subs.SubtitleManager.providers.forEach { p ->
            val r = results[p.name]
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    when {
                        p.name in running -> Text(L("בודק…"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        r != null -> Text((if (r.first) "" else "✗ ") + r.second, fontSize = 12.sp,
                            color = if (r.first) Color(0xFF22C997) else MaterialTheme.colorScheme.error)
                        !p.enabled() -> Text(L("לא פעיל"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (p.name in running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else TextButton(onClick = { run(p) }) { Text(L("בדוק")) }
            }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
}


/** בחירת נגן: מובנה/חיצוני לכל סוג תוכן + נגן חיצוני מועדף */
@Composable
private fun ExternalPlayerSettings() {
    val ctx = LocalContext.current
    Prefs.version.intValue
    var pick by remember { mutableStateOf(false) }
    Text(L("בחירת נגן"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    listOf(Kind.LIVE to L("ערוצים"), Kind.MOVIE to L("סרטים"), Kind.SERIES to L("סדרות")).forEach { (k, label) ->
        val ext = com.tbm.iptv.player.ExternalPlayer.useFor(k)
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            FilterChipSimple(L("מובנה"), !ext) { com.tbm.iptv.player.ExternalPlayer.setFor(k, false) }
            Spacer(Modifier.width(6.dp))
            FilterChipSimple(L("חיצוני"), ext) { com.tbm.iptv.player.ExternalPlayer.setFor(k, true) }
        }
    }
    val p = com.tbm.iptv.player.ExternalPlayer.pkg
    FocusItem(Modifier.fillMaxWidth(), onClick = { pick = true }) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp)) {
            Text(L("נגן חיצוני מועדף"))
            Text(if (p.isBlank()) L("לשאול בכל פעם") else com.tbm.iptv.player.ExternalPlayer.appName(ctx, p),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Hint(L("בנגן החיצוני מצורפת כתובית עברית (מהמטמון או מחיפוש אוטומטי) והצפייה ממשיכה מהמקום שעצרת. בנגן המובנה: ⚙ ← \"פתח בנגן חיצוני\"."))
    if (pick) {
        val apps = remember { com.tbm.iptv.player.ExternalPlayer.installed(ctx) }
        AlertDialog(
            onDismissRequest = { pick = false },
            title = { Text(L("נגן חיצוני מועדף")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    (listOf(com.tbm.iptv.player.ExternalPlayer.App("", L("לשאול בכל פעם"))) + apps).forEach { a ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = p == a.pkg, onClick = { com.tbm.iptv.player.ExternalPlayer.pkg = a.pkg; pick = false })
                            Text(a.name)
                        }
                    }
                    if (apps.isEmpty()) Hint(L("לא נמצאו נגנים מותקנים – התקן VLC או MX Player מחנות Play"))
                }
            },
            confirmButton = { TextButton(onClick = { pick = false }) { Text(L("סגור")) } },
        )
    }
}


// ============================================================ מקור תוכן: תפריט, מידע, עריכה (כמו MyTVOnline)
@Composable
private fun SourceMenu(title: String, disabled: Boolean, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickableNoRipple(onDismiss), contentAlignment = Alignment.BottomCenter) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).clickableNoRipple {}) {
                Column(Modifier.padding(bottom = 12.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(width = 40.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(20.dp))
                    HorizontalDivider()
                    listOf(
                        Triple("refresh", Icons.Filled.Refresh, L("רענון")),
                        Triple("epg", Icons.Filled.DateRange, L("עדכון מדריך שידורים")),
                        Triple("clear", Icons.Filled.Delete, L("מחיקת מדריך שידורים")),
                        Triple("disable", if (disabled) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, if (disabled) L("הפעלה") else L("השבתה")),
                        Triple("edit", Icons.Filled.Edit, L("עריכה")),
                        Triple("delete", Icons.Filled.Delete, L("מחיקה")),
                        Triple("info", Icons.Filled.Info, L("מידע")),
                    ).forEach { (k, icon, label) ->
                        FocusItem(Modifier.fillMaxWidth(), onClick = { onAction(k) }) {
                            Row(Modifier.padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null)
                                Spacer(Modifier.width(20.dp))
                                Text(label, fontSize = 17.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null, onClick = onClick))

@Composable
private fun SourceInfo(p: Playlist, onDismiss: () -> Unit) {
    var rows by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(p.id) {
        if (!p.isXtream) {
            val c = Repo.content
            rows = listOfNotNull(L("סוג") to "M3U", c?.let { L("ערוצים") to "${it.live.size}" },
                c?.let { L("סרטים") to "${it.movies.size}" }, c?.let { L("סדרות") to "${it.series.size}" })
            return@LaunchedEffect
        }
        try {
            rows = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.tbm.iptv.data.Xtream(p).accountDetails() }
        } catch (e: Exception) { err = e.message ?: L("שגיאה") }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    err != null -> Text(L("שגיאה: {0}", err))
                    rows == null -> CircularProgressIndicator()
                    else -> rows!!.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                            Text(v, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("סגור")) } },
    )
}

/** עריכת מקור – מסך מלא: שם, כתובת (מוסתרת), התחברות, היסט EPG, אפשרויות מתקדמות */
@Composable
private fun SourceEditor(p: Playlist, onSave: (Playlist) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var url by remember { mutableStateOf(p.url) }
    var login by remember { mutableStateOf(p.isXtream) }
    var user by remember { mutableStateOf(p.user) }
    var pass by remember { mutableStateOf(p.pass) }
    var showUrl by remember { mutableStateOf(false) }
    var showPass by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(Prefs.str("epg_off_${p.id}", "0")) }
    var offMenu by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var epgUrl by remember { mutableStateOf(Prefs.epgUrl(p)) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars)
                .verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text(L("עריכת מקור"), fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                OutlinedTextField(name, { name = it }, label = { Text(L("שם המקור")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(url, { url = it }, label = { Text(L("כתובת השרת / הרשימה")) }, singleLine = true,
                    visualTransformation = if (showUrl) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailingIcon = { IconButton(onClick = { showUrl = !showUrl }) { Icon(if (showUrl) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } },
                    modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(L("נדרשת התחברות? (Xtream)"), fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Switch(checked = login, onCheckedChange = { login = it })
                }
                if (login) {
                    OutlinedTextField(user, { user = it }, label = { Text(L("שם משתמש")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(pass, { pass = it }, label = { Text(L("סיסמה")) }, singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { showPass = !showPass }) { Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } },
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                }
                Box {
                    OutlinedTextField(
                        value = if (offset == "0") L("ללא") else (if (offset.startsWith("-")) "" else "+") + offset + L(" שעות"),
                        onValueChange = {}, readOnly = true, label = { Text(L("היסט זמן למדריך (EPG)")) },
                        trailingIcon = { Icon(Icons.Filled.ExpandMore, null) }, modifier = Modifier.fillMaxWidth())
                    Box(Modifier.matchParentSize().clickableNoRipple { offMenu = true })
                    androidx.compose.material3.DropdownMenu(offMenu, { offMenu = false }) {
                        listOf("-3", "-2", "-1.5", "-1", "-0.5", "0", "0.5", "1", "1.5", "2", "3").forEach { o ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (o == "0") L("ללא") else (if (o.startsWith("-")) "" else "+") + o + L(" שעות")) },
                                onClick = { offset = o; offMenu = false })
                        }
                    }
                }
                Hint(L("אם לוח השידורים מראה תוכניות בשעה לא נכונה (למשל שעה מוקדם), הזז כאן."))
                FocusItem(Modifier.fillMaxWidth(), onClick = { advanced = !advanced }) {
                    Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(L("אפשרויות מתקדמות"), fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Icon(if (advanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                    }
                }
                if (advanced) {
                    OutlinedTextField(epgUrl, { epgUrl = it }, label = { Text(L("כתובת EPG חלופית (XMLTV)")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                    Hint(L("ריק = המדריך של הספק."))
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = {
                    Prefs.setStr("epg_off_${p.id}", offset)
                    Prefs.setEpgUrl(p, epgUrl.trim())
                    onSave(p.copy(name = name.ifBlank { p.name }, url = url.trim(), type = if (login) "xtream" else "m3u",
                        user = if (login) user.trim() else "", pass = if (login) pass.trim() else ""))
                }, enabled = url.isNotBlank() && (!login || (user.isNotBlank() && pass.isNotBlank())),
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp)) { Text(L("עדכן"), fontSize = 17.sp) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp)) {
                    Text(L("ביטול"), fontSize = 17.sp)
                }
            }
        }
    }
}


/** קריינות: סגנון קול, דוברים שונים, בחירת קול לגבר/לאישה מתוך הקולות המותקנים + ניסיון */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceSettings() {
    val ctx = LocalContext.current
    Prefs.version.intValue
    var voices by remember { mutableStateOf<List<android.speech.tts.Voice>>(emptyList()) }
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        var t: android.speech.tts.TextToSpeech? = null
        t = android.speech.tts.TextToSpeech(ctx.applicationContext) { st ->
            if (st == android.speech.tts.TextToSpeech.SUCCESS) {
                t?.language = com.tbm.iptv.player.VoiceOver.localeOf("he")
                voices = t?.let { com.tbm.iptv.player.VoiceOver.voicesFor(it, "he") } ?: emptyList()
            }
        }
        tts = t
        onDispose { runCatching { t?.stop(); t?.shutdown() } }
    }
    fun test(sp: com.tbm.iptv.player.Speaker, voiceName: String, text: String) {
        val t = tts ?: return
        voices.firstOrNull { it.name == voiceName }?.let { runCatching { t.voice = it } }
        t.setPitch(sp.pitch); t.setSpeechRate(Prefs.voiceRate * sp.rate)
        t.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "test")
    }
    val cfg = com.tbm.iptv.player.VoiceCfg
    Text(L("סגנון קול"), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("auto" to L("לפי הדובר"), "narrator" to L("מספר"), "man" to L("גבר"), "woman" to L("אישה"), "child" to L("ילד")).forEach { (k, t) ->
            FilterChipSimple(t, cfg.style == k) {
                cfg.style = k
                val sp = when (k) { "man" -> com.tbm.iptv.player.Speaker.MAN; "woman" -> com.tbm.iptv.player.Speaker.WOMAN
                    "child" -> com.tbm.iptv.player.Speaker.CHILD; else -> com.tbm.iptv.player.Speaker.NARRATOR }
                test(sp, if (k == "man") cfg.maleVoice else cfg.femaleVoice, L("שלום, ככה אני נשמע."))
            }
        }
    }
    Hint(L("\"לפי הדובר\": מזהה מהדקדוק אם מדבר גבר או אישה (\"אני הולכת\" / \"אני הולך\") ומחליף קול בהתאם."))
    SwitchRow(L("קול שונה לכל צד בדיאלוג (שורות עם מקף)"), cfg.multi) { cfg.multi = it }
    if (voices.isEmpty()) Hint(L("לא נמצאו קולות עבריים מותקנים – התקן: הגדרות אנדרואיד ← המרת טקסט לדיבור ← Google ← עברית"))
    else listOf(
        Triple(L("קול לגבר"), cfg.maleVoice, com.tbm.iptv.player.Speaker.MAN),
        Triple(L("קול לאישה / ילד"), cfg.femaleVoice, com.tbm.iptv.player.Speaker.WOMAN),
    ).forEach { (label, cur, sp) ->
        var open by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label)
                val idx = voices.indexOfFirst { it.name == cur }
                Text(if (idx < 0) L("ברירת מחדל") else com.tbm.iptv.player.VoiceOver.label(voices[idx], idx), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { test(sp, cur, if (sp == com.tbm.iptv.player.Speaker.MAN) L("אני הולך הביתה, נתראה מחר.") else L("אני הולכת הביתה, נתראה מחר.")) }) { Text(L("▶ נסה")) }
            Box {
                TextButton(onClick = { open = true }) { Text(L("בחר")) }
                androidx.compose.material3.DropdownMenu(open, { open = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text(L("ברירת מחדל")) }, onClick = {
                        if (sp == com.tbm.iptv.player.Speaker.MAN) cfg.maleVoice = "" else cfg.femaleVoice = ""; open = false })
                    voices.forEachIndexed { i, v ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(com.tbm.iptv.player.VoiceOver.label(v, i)) }, onClick = {
                            if (sp == com.tbm.iptv.player.Speaker.MAN) cfg.maleVoice = v.name else cfg.femaleVoice = v.name
                            open = false
                            test(sp, v.name, L("שלום, ככה אני נשמע."))
                        })
                    }
                }
            }
        }
    }
    Hint(L("קולות ☁ (רשת) נשמעים טבעיים יותר אבל צריכים אינטרנט. כשיש רק קול אחד, ההבדל בין גבר/אישה/ילד נעשה בגובה הקול."))
}
