package com.tbm.iptv.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tbm.iptv.data.Countries
import com.tbm.iptv.data.Entry
import com.tbm.iptv.data.Episode
import com.tbm.iptv.data.Kind
import com.tbm.iptv.data.Playlist
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.Repo
import com.tbm.iptv.data.SeriesItem
import com.tbm.iptv.data.toPlay
import java.io.File
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
        if (p == null) { err = "אין רשימה פעילה"; return@LaunchedEffect }
        try { eps = Repo.episodes(p, s) } catch (e: Exception) { err = e.message ?: "שגיאה" }
    }
    val all = eps ?: emptyList()
    val seasons = remember(all) { all.map { it.season }.distinct().sorted() }
    var season by remember(seasons) { mutableStateOf(seasons.firstOrNull() ?: 1) }
    val fav = remember(ver) { Prefs.isFav(s.key) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(s.name, onBack) {
            IconButton(onClick = { Prefs.toggleFav(s.key) }) {
                Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "מועדף", tint = if (fav) Gold else MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            AsyncImage(
                s.logo, null,
                Modifier.size(width = 100.dp, height = 150.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(s.group, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                s.plot?.let { Text(it, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis) }
            }
        }
        when {
            err != null -> Text("שגיאה: $err", Modifier.padding(16.dp))
            eps == null -> Box(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            all.isEmpty() -> Text("לא נמצאו פרקים", Modifier.padding(16.dp))
            else -> {
                if (seasons.size > 1) {
                    ScrollableTabRow(selectedTabIndex = seasons.indexOf(season).coerceAtLeast(0), edgePadding = 8.dp) {
                        seasons.forEach { n ->
                            Tab(selected = n == season, onClick = { season = n }, text = { Text("עונה $n") })
                        }
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
                                AsyncImage(
                                    e.image ?: s.logo, null,
                                    Modifier.size(width = 96.dp, height = 54.dp).clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("פרק ${e.episode}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (e.title.isNotBlank()) Text(e.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (r != null && r.second > 0) {
                                        val done = r.first >= r.second * 0.95
                                        if (done) Text("✓ נצפה", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                        else LinearProgressIndicator(
                                            progress = { (r.first.toFloat() / r.second).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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

// ============================================================ סינון ארצות וקבוצות
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(kind: Kind, onBack: () -> Unit) {
    val content = Repo.content
    val all: List<Entry> = when (kind) {
        Kind.LIVE -> content?.live
        Kind.MOVIE -> content?.movies
        Kind.SERIES -> content?.series
    } ?: emptyList()
    val ver = Prefs.version.intValue
    val groups = remember(all) { all.map { it.group }.distinct() }
    val countries = remember(groups) { groups.map { Countries.of(it) }.distinct().sortedBy { Countries.label(it) } }
    val hidden = remember(ver) { Prefs.hidden() }
    val allowed = remember(ver) { Prefs.allowedCountries(kind) }
    var q by remember { mutableStateOf("") }
    val shown = remember(groups, q, allowed) {
        groups.filter { (q.isBlank() || it.contains(q, true)) && (allowed.isEmpty() || Countries.of(it) in allowed) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("סינון – ${kind.label}", onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
            item {
                Text("ארצות", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("סמן את הארצות שברצונך לראות. ללא סימון = הכל מוצג.", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    countries.forEach { c ->
                        FilterChip(
                            selected = c in allowed,
                            onClick = {
                                val n = allowed.toMutableSet(); if (!n.add(c)) n.remove(c)
                                Prefs.setAllowedCountries(kind, n)
                            },
                            label = { Text(Countries.label(c)) },
                        )
                    }
                }
                if (allowed.isNotEmpty()) TextButton(onClick = { Prefs.setAllowedCountries(kind, emptySet()) }) { Text("נקה בחירת ארצות") }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text("קבוצות", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("חיפוש קבוצה…") })
                Row {
                    TextButton(onClick = { Prefs.setHidden(kind, shown, false) }) { Text("הצג את כולן") }
                    TextButton(onClick = { Prefs.setHidden(kind, shown, true) }) { Text("הסתר את כולן") }
                }
            }
            items(shown) { g ->
                val visible = "${kind.name}|$g" !in hidden
                FocusItem(Modifier.fillMaxWidth(), onClick = { Prefs.setHidden(kind, listOf(g), visible) }) {
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = visible, onCheckedChange = { Prefs.setHidden(kind, listOf(g), !it) })
                        Text(g, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Countries.label(Countries.of(g)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ============================================================ הגדרות
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(nav: (Screen) -> Unit, onBack: () -> Unit) {
    Prefs.version.intValue // מאזין לשינויים
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("הגדרות", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Group("תוכן")
            SwitchRow("הסתר תוכן למבוגרים", Prefs.hideAdult) { Prefs.hideAdult = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav(Screen.Filter(Kind.LIVE)) }) { Text("סינון ערוצים") }
                OutlinedButton(onClick = { nav(Screen.Filter(Kind.MOVIE)) }) { Text("סינון סרטים") }
                OutlinedButton(onClick = { nav(Screen.Filter(Kind.SERIES)) }) { Text("סינון סדרות") }
            }
            Button(onClick = { Prefs.activePlaylist()?.let { Repo.loadAsync(it, true) }; onBack() }, modifier = Modifier.padding(top = 8.dp)) {
                Text("רענן את הרשימה עכשיו")
            }

            Group("נגן")
            SwitchRow("מעבר אוטומטי לפרק הבא", Prefs.autoNext) { Prefs.autoNext = it }
            SwitchRow("השתמש בנגן חיצוני (VLC / MX)", Prefs.externalPlayer) { Prefs.externalPlayer = it }
            Field("User-Agent (אם הספק חוסם את הנגן)", Prefs.userAgent) { Prefs.userAgent = it }

            Group("כתוביות")
            SwitchRow("חיפוש וטעינת כתוביות אוטומטית בסרטים וסדרות", Prefs.autoSubs) { Prefs.autoSubs = it }
            Field("שפות לפי סדר עדיפות (he,en)", Prefs.str("sub_langs", "he,en")) { Prefs.setStr("sub_langs", it) }
            Text("גודל כתוביות: ${"%.1f".format(Prefs.subSize)}×", fontSize = 13.sp)
            Slider(value = Prefs.subSize, onValueChange = { Prefs.subSize = it }, valueRange = 0.6f..2.0f)

            Provider("Wizdom – עברית, ללא הרשמה", "p_wizdom")

            Provider("כתובית (Ktuvit) – עברית", "p_ktuvit")
            Field("אימייל בכתובית", Prefs.str("ktuvit_email"), KeyboardType.Email) { Prefs.setStr("ktuvit_email", it) }
            Field("סיסמה מוצפנת (hashed)", Prefs.str("ktuvit_hash"), secret = true) { Prefs.setStr("ktuvit_hash", it) }
            Hint("כתובית לא מקבלת סיסמה רגילה. במחשב: התחבר ל-ktuvit.me ← F12 ← לשונית Network ← בקשת Login ← Payload – העתק את ערך Password.")

            Provider("OpenSubtitles – עברית ואנגלית", "p_os")
            Field("מפתח API", Prefs.str("os_key"), secret = true) { Prefs.setStr("os_key", it) }
            Field("שם משתמש (לא חובה – מגדיל מכסת הורדות)", Prefs.str("os_user")) { Prefs.setStr("os_user", it) }
            Field("סיסמה", Prefs.str("os_pass"), secret = true) { Prefs.setStr("os_pass", it) }
            Hint("מפתח חינמי: opensubtitles.com ← הרשמה ← Profile ← API consumers ← New consumer.")

            Provider("SubDL – עברית ואנגלית", "p_subdl")
            Field("מפתח API", Prefs.str("subdl_key"), secret = true) { Prefs.setStr("subdl_key", it) }
            Hint("מפתח חינמי: subdl.com ← הרשמה ← Account ← API Key.")

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable private fun Group(t: String) {
    Text(t, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
}

@Composable private fun Hint(t: String) {
    Text(t, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable private fun Provider(label: String, key: String) {
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    SwitchRow(label, Prefs.bool(key, true)) { Prefs.setBool(key, it) }
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
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else type),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    )
}

// ============================================================ רשימות השמעה
@Composable
fun PlaylistsScreen(onDone: () -> Unit, onBack: (() -> Unit)?) {
    val ver = Prefs.version.intValue
    val lists = remember(ver) { Prefs.playlists() }
    val active = remember(ver) { Prefs.activePlaylist()?.id }
    var dialog by remember { mutableStateOf<String?>(null) }

    fun activate(p: Playlist) {
        Prefs.activeId = p.id
        Repo.loadAsync(p)
        onDone()
    }

    Column(Modifier.fillMaxSize()) {
        if (onBack != null) ScreenHeader("רשימות ערוצים", onBack)
        else Text("ברוך הבא ל-TBM Player", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (lists.isEmpty()) Text("הוסף רשימת M3U או חשבון Xtream Codes כדי להתחיל.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            lists.forEach { p ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (p.id == active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    FocusItem(Modifier.fillMaxWidth(), onClick = { activate(p) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text(
                                    (if (p.isXtream) "Xtream · " else "M3U · ") +
                                        if (p.url.startsWith("file://")) "קובץ מקומי" else p.url,
                                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { Prefs.activeId = p.id; Repo.loadAsync(p, true); onDone() }) {
                                Icon(Icons.Filled.Refresh, "רענון")
                            }
                            IconButton(onClick = {
                                Repo.deletePlaylistCache(p)
                                Prefs.savePlaylists(lists.filter { it.id != p.id })
                            }) { Icon(Icons.Filled.Delete, "מחיקה") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { dialog = "m3u" }) { Text("+ רשימת M3U") }
                Button(onClick = { dialog = "xtream" }) { Text("+ Xtream Codes") }
            }
        }
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
                if (name.isBlank()) name = "קובץ מקומי"
            } catch (e: Exception) {
                Toast.makeText(ctx, "לא ניתן לקרוא את הקובץ", Toast.LENGTH_SHORT).show()
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הוספת רשימת M3U") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("שם") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text("כתובת M3U (http…)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { pick.launch(arrayOf("*/*")) }) { Text("או בחר קובץ מהמכשיר…") }
                Text("קישור get.php עם שם משתמש וסיסמה יומר אוטומטית ל-Xtream (סדרות ו-EPG מלאים).",
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
                else Playlist(id, name.ifBlank { "רשימה" }, "m3u", u)
                onAdd(p)
            }) { Text("הוסף") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
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
        title = { Text("חשבון Xtream Codes") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("שם") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(server, { server = it }, label = { Text("כתובת שרת (http://host:port)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text("שם משתמש") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text("סיסמה") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = server.isNotBlank() && user.isNotBlank() && pass.isNotBlank(), onClick = {
                onAdd(Playlist(UUID.randomUUID().toString().take(8), name.ifBlank { "Xtream" }, "xtream",
                    server.trim(), user.trim(), pass.trim()))
            }) { Text("התחבר") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
