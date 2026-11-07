package com.tbm.iptv.ui

import com.tbm.iptv.L

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.tbm.iptv.data.Parental
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.Profile

/** חלון הזנת קוד הורים – מוצג מעל כל המסכים כש-Parental.request פעיל */
@Composable
fun PinDialog() {
    val req = Parental.request ?: return
    val ctx = LocalContext.current
    var pin by remember(req) { mutableStateOf("") }
    var err by remember(req) { mutableStateOf(false) }

    fun ok() { Parental.unlocked = true; Parental.request = null; req.onOk() }

    LaunchedEffect(req) {
        val act = ctx as? FragmentActivity
        if (Prefs.biometric && act != null &&
            BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(ctx),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (Parental.request === req) ok()
                    }
                })
            runCatching {
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(req.title)
                        .setSubtitle(L("TBM Player – בקרת הורים"))
                        .setNegativeButtonText(L("הזן קוד"))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        .build()
                )
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = { Parental.request = null }) {
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface) {
            PinPad(req.title, L("הזן את קוד ההורים (4 ספרות)"), err, onCancel = { Parental.request = null }) { code ->
                if (Parental.check(code)) { ok(); true } else { err = true; false }
            }
        }
    }
}

/**
 * מקלדת מספרים לקוד 4 ספרות (כמו בנטפליקס). onComplete מחזיר true אם הקוד התקבל, אחרת הנקודות מתאפסות.
 */
@Composable
fun PinPad(title: String, subtitle: String, error: Boolean, onCancel: () -> Unit, onComplete: (String) -> Boolean) {
    var code by remember { mutableStateOf("") }
    var shake by remember { mutableStateOf(false) }
    LaunchedEffect(shake) { if (shake) { kotlinx.coroutines.delay(500); shake = false; code = "" } }
    fun press(d: String) {
        if (code.length >= 4 || shake) return
        code += d
        if (code.length == 4 && !onComplete(code)) shake = true
    }
    Ltr {
        Column(Modifier.padding(22.dp).width(290.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(if (error || shake) L("קוד שגוי – נסה שוב") else subtitle, fontSize = 13.sp,
                color = if (error || shake) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { i ->
                    Box(Modifier.size(18.dp).clip(CircleShape).background(
                        when {
                            shake -> MaterialTheme.colorScheme.error
                            i < code.length -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }))
                }
            }
            Spacer(Modifier.height(20.dp))
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("✕", "0", "⌫")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    row.forEach { k ->
                        FocusItem(onClick = {
                            when (k) { "⌫" -> code = code.dropLast(1); "✕" -> onCancel(); else -> press(k) }
                        }) {
                            Box(Modifier.size(68.dp).clip(CircleShape).background(
                                if (k == "✕" || k == "⌫") Color.Transparent else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center) {
                                Text(k, fontSize = if (k.length == 1 && k[0].isDigit()) 26.sp else 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** הגדרת / שינוי קוד הורים: הזנה + אימות */
@Composable
fun PinSetFlow(onDone: (Boolean) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = { onDone(false) }) {
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface) {
            val f = first
            if (f == null) {
                PinPad(L("קוד הורים חדש"), if (mismatch) L("הקודים לא תאמו – התחל שוב") else L("בחר קוד בן 4 ספרות"),
                    false, onCancel = { onDone(false) }) { c -> first = c; mismatch = false; true }
            } else {
                androidx.compose.runtime.key("confirm") {
                    PinPad(L("אימות קוד"), L("הזן שוב את אותו קוד"), false, onCancel = { onDone(false) }) { c ->
                        if (c == f) { Prefs.pin = c; Parental.unlocked = true; onDone(true); true }
                        else { first = null; mismatch = true; true }
                    }
                }
            }
        }
    }
}

/** "מי צופה?" – בחירת פרופיל כמו בנטפליקס. מעבר לפרופיל הורה דורש קוד */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesScreen(onDone: () -> Unit, onManage: () -> Unit = {}) {
    val ver = Prefs.version.intValue
    val list = remember(ver) { Prefs.profiles() }
    var adding by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    if (switching) androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator()
            Text(L("טוען את הפרופיל…"), color = Color.White, modifier = Modifier.padding(top = 10.dp))
        }
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("TBM Player", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 26.sp)
        Spacer(Modifier.height(8.dp))
        Text(L("מי צופה?"), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            list.forEach { p -> ProfileTile(p) { choose(p) { switching = true; warmThen(onDone) { switching = false } } } }
            if (list.size < 8) FocusItem(onClick = { Parental.requireAlways(L("הוספת פרופיל")) { adding = true } }) {
                Column(Modifier.padding(10.dp).width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text("+", fontSize = 40.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(L("הוסף פרופיל"), fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.OutlinedButton(onClick = { Parental.requireAlways(L("ניהול פרופילים")) { onManage() } }) {
            Text(L("✏ ניהול פרופילים"))
        }
    }
    if (adding) ProfileEditDialog(null) { adding = false }
}

/** מחשב את התוכן של הפרופיל החדש ברקע ורק אז עובר למסך הבית – בלי לתקוע את המסך */
fun warmThen(onDone: () -> Unit, after: () -> Unit) {
    val c = com.tbm.iptv.data.Repo.content
    if (c == null) { onDone(); after(); return }
    com.tbm.iptv.data.Repo.scope.launch(kotlinx.coroutines.Dispatchers.Default) {
        runCatching { com.tbm.iptv.data.Catalog.warm(c) }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { after(); onDone() }
    }
}

fun choose(p: Profile, onDone: () -> Unit) {
    // פרופיל הורה תמיד דורש קוד (אם הוגדר קוד)
    if (!p.kids && Parental.hasPin() && !(Parental.unlocked && Prefs.profileId == p.id)) {
        Parental.requireAlways(L("כניסה לפרופיל {0}", p.name)) { Parental.switchProfile(p); Parental.unlocked = true; onDone() }
    } else {
        Parental.switchProfile(p); onDone()
    }
}

/** דמויות לבחירה לפרופיל */
val profileAvatars = listOf("🦁", "🐼", "🦄", "🐶", "🐱", "🦊", "🐸", "🐵", "🦖", "🐬", "🦋", "🚀", "⚽", "🎨", "👑", "🦸", "🧚", "🤖", "🐻", "🐰")

/** צבעי אווטאר */
val profileColors = listOf(Color(0xFF3A9BFF), Color(0xFF00A86B), Color(0xFFE50914), Color(0xFFFFB300), Color(0xFF8E44AD), Color(0xFFFF6F91))

/** יצירה / עריכה של פרופיל (שם, הורה/ילדים, גיל, סינון אוטומטי, צבע) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditDialog(edit: Profile?, onDone: () -> Unit) {
    val all = Prefs.profiles()
    var name by remember { mutableStateOf(edit?.name ?: "") }
    var kids by remember { mutableStateOf(edit?.kids ?: true) }
    var age by remember { mutableStateOf(edit?.age ?: 7) }
    var auto by remember { mutableStateOf(edit?.auto ?: true) }
    var color by remember { mutableStateOf(edit?.color ?: (all.size % profileColors.size)) }
    var avatar by remember { mutableStateOf(edit?.avatar ?: "") }
    var needPin by remember { mutableStateOf(false) }
    fun save() {
        // עריכה שומרת את כל הגדרות התוכן הקיימות (מדינות, נושאים…), ופרופיל ילד חדש מקבל אותן מפרופיל הילדים הראשון
        val base = edit ?: (if (kids) all.firstOrNull { it.kids } else null)?.copy(id = "", name = "")
        val p = (base ?: Profile("", "", kids)).copy(id = edit?.id ?: java.util.UUID.randomUUID().toString().take(6),
            name = name.trim(), kids = kids, age = age, auto = auto, color = color, avatar = avatar)
        Prefs.saveProfiles(if (edit == null) all + p else all.map { if (it.id == p.id) p else it })
        onDone()
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (edit == null) L("פרופיל חדש") else L("עריכת פרופיל")) },
        text = {
            Column(Modifier.verticalScrollCompat()) {
                OutlinedTextField(name, { name = it }, label = { Text(L("שם")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    profileColors.forEachIndexed { i, c ->
                        Box(Modifier.size(32.dp).clip(CircleShape).background(c)
                            .then(if (i == color) Modifier.background(Color.White.copy(alpha = 0.35f)) else Modifier)
                            .clickableCompat { color = i }, contentAlignment = Alignment.Center) {
                            if (i == color) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                // דמות לפרופיל
                Text(L("דמות"), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (listOf("") + profileAvatars).forEach { a ->
                        Box(Modifier.size(40.dp).clip(CircleShape)
                            .background(if (a == avatar) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickableCompat { avatar = a }, contentAlignment = Alignment.Center) {
                            Text(if (a.isEmpty()) "∅" else a, fontSize = 22.sp)
                        }
                    }
                }
                if (edit?.id != "main") {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = !kids, onClick = { kids = false }); Text(L("הורה / מבוגר"))
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.material3.RadioButton(selected = kids, onClick = { kids = true }); Text(L("ילדים"))
                    }
                }
                if (kids) {
                    Text(L("גיל הילד – יוצג רק תוכן שמתאים עד הגיל הזה:"), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3, 5, 7, 10, 13, 16).forEach { a ->
                            androidx.compose.material3.FilterChip(selected = age == a, onClick = { age = a }, label = { Text(L("עד {0}", a)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text(L("סינון אוטומטי (ערוצים וקטגוריות ילדים)"), modifier = Modifier.weight(1f), fontSize = 14.sp)
                        androidx.compose.material3.Switch(checked = auto, onCheckedChange = { auto = it })
                    }
                    Text(L("בנוסף אפשר לאשר ידנית כל ערוץ, סרט או קבוצה (לחיצה ארוכה ← \"אשר לפרופיל ילדים\"). תוכן אימה, פשע ולמבוגרים נחסם לפי הגיל, ועם מפתח TMDB נבדק גם סיווג הגיל הרשמי של כל סרט וסדרה."),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                // פרופיל ילדים בלי קוד הורים – הילדים יוכלו לעבור להורה. מבקשים להגדיר קוד קודם
                if (kids && !Parental.hasPin()) needPin = true else save()
            }) { Text(L("שמור")) }
        },
        dismissButton = {
            Row {
                if (edit != null && edit.id != "main") TextButton(onClick = {
                    Prefs.saveProfiles(all.filter { it.id != edit.id })
                    if (Prefs.profileId == edit.id) Prefs.profileId = "main"
                    onDone()
                }) { Text(L("מחק"), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDone) { Text(L("ביטול")) }
            }
        },
    )
    if (needPin) PinSetFlow { ok -> needPin = false; if (ok) save() }
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier =
    this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

@Composable
private fun Modifier.clickableCompat(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))

@Composable
private fun ProfileTile(p: Profile, onClick: () -> Unit) {
    FocusItem(onClick = onClick) {
        Column(Modifier.padding(10.dp).width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(84.dp).clip(CircleShape)
                    .background(profileColors[p.color.coerceIn(0, profileColors.size - 1)]),
                contentAlignment = Alignment.Center,
            ) {
                if (p.avatar.isNotBlank()) Text(p.avatar, fontSize = 46.sp)
                else Icon(if (p.kids) Icons.Filled.ChildCare else Icons.Filled.Person, null, tint = Color.White,
                    modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(p.name, fontWeight = FontWeight.Bold)
            Text(if (p.kids) L("ילדים · עד גיל {0}", p.age) else L("הורה · 🔒"), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * ניהול התוכן של פרופיל ילדים: גיל, סינון אוטומטי, ולכל קבוצה (ערוצים/סרטים/סדרות) – אוטומטי / מאושר / חסום.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KidsContentScreen(profileId: String, onBack: () -> Unit) {
    val ver = Prefs.version.intValue
    val prof = remember(ver) { Prefs.profiles().firstOrNull { it.id == profileId } } ?: return
    val content = com.tbm.iptv.data.Repo.content
    var kind by remember { mutableStateOf(com.tbm.iptv.data.Kind.LIVE) }
    var q by remember { mutableStateOf("") }
    var onlyAllowed by remember { mutableStateOf(false) }
    fun saveProf(p: Profile) = Prefs.saveProfiles(Prefs.profiles().map { if (it.id == p.id) p else it })
    val all: List<com.tbm.iptv.data.Entry> = when (kind) {
        com.tbm.iptv.data.Kind.LIVE -> content?.live.orEmpty()
        com.tbm.iptv.data.Kind.MOVIE -> content?.movies.orEmpty()
        com.tbm.iptv.data.Kind.SERIES -> content?.series.orEmpty()
    }
    val groups = remember(all) { all.groupingBy { it.group }.eachCount().toList() }
    val inc = remember(ver) { Prefs.kidsInc(profileId) }
    val exc = remember(ver) { Prefs.kidsExc(profileId) }
    val legacy = remember(ver) { Prefs.kidsGroups() }
    // מצב כל קבוצה: 1 מאושר ידנית, -1 חסום, 2 אוטומטי (מוצג), 0 לא מוצג
    val pick = kind.name in prof.pickOnly
    val cset = prof.countriesFor(kind.name)
    fun state(g: String): Int {
        val k = "${kind.name}|$g"
        return when {
            k in exc || com.tbm.iptv.data.Catalog.isAdult(g) -> -1
            k in inc || (!pick && k in legacy) -> 1
            pick -> 0
            com.tbm.iptv.data.KidsFilter.blocked(g, prof.age) -> 0
            cset.isNotEmpty() && com.tbm.iptv.data.Countries.of(g) !in cset -> 0
            prof.auto && (com.tbm.iptv.data.KidsFilter.matches(g, prof.age) || com.tbm.iptv.data.KidsFilter.topicMatch(prof.topics, g)) -> 2
            else -> 0
        }
    }
    val byGroup = remember(all) { all.groupBy { it.group } }
    val itemExc = remember(ver) { Prefs.kidsItemExc(profileId) }
    var expanded by remember(kind) { mutableStateOf<String?>(null) }
    // חיפוש גם בשמות הערוצים/הסרטים – הקטגוריה תיפתח אוטומטית
    val itemHit: (String) -> Boolean = { g -> q.isNotBlank() && byGroup[g].orEmpty().any { it.name.contains(q, true) } }
    val shown = groups.filter { (g, _) -> (q.isBlank() || g.contains(q, true) || itemHit(g)) && (!onlyAllowed || state(g) > 0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(L("תוכן לפרופיל {0}", prof.name), onBack)
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
        item {
        Column(Modifier.padding(horizontal = 14.dp)) {
            Text(L("גיל מקסימלי:"), fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 5, 7, 10, 13, 16).forEach { a ->
                    androidx.compose.material3.FilterChip(selected = prof.age == a, onClick = { saveProf(prof.copy(age = a)) }, label = { Text(L("עד {0}", a)) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("סינון אוטומטי לפי שמות קטגוריות וערוצים"), modifier = Modifier.weight(1f), fontSize = 14.sp)
                androidx.compose.material3.Switch(checked = prof.auto, onCheckedChange = { saveProf(prof.copy(auto = it)) })
            }
            // נושאים נוספים
            Text(L("נושאים נוספים שמותרים:"), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("nature" to L("🌿 טבע ובעלי חיים"), "sport" to L("⚽ ספורט"), "docu" to L("🔬 מדע ודוקו"),
                    "music" to L("🎵 מוזיקה"), "edu" to L("📚 חינוכי")).forEach { (t, label) ->
                    androidx.compose.material3.FilterChip(selected = t in prof.topics, label = { Text(label) }, onClick = {
                        saveProf(prof.copy(topics = if (t in prof.topics) prof.topics - t else prof.topics + t))
                    })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(com.tbm.iptv.data.Kind.LIVE, com.tbm.iptv.data.Kind.MOVIE, com.tbm.iptv.data.Kind.SERIES).forEach { k ->
                    androidx.compose.material3.FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) })
                }
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.FilterChip(selected = onlyAllowed, onClick = { onlyAllowed = !onlyAllowed }, label = { Text(L("רק מה שמוצג")) })
            }
            // ---- הגדרות לסוג התוכן הנבחר (ערוצים / סרטים / סדרות)
            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    val k = kind.name
                    Text(L("הגדרות ל{0}", kind.label), fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = !pick, onClick = { saveProf(prof.copy(pickOnly = prof.pickOnly - k)) })
                        Text(L("אוטומטי – תוכן ילדים לפי גיל ונושאים"), fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = pick, onClick = { saveProf(prof.copy(pickOnly = prof.pickOnly + k)) })
                        Text(L("רק קטגוריות שאבחר מהשרת (ברשימה למטה)"), fontSize = 14.sp)
                    }
                    if (pick) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (kind == com.tbm.iptv.data.Kind.LIVE) L("בתוך הקטגוריות שבחרתי – רק ערוצים שמתאימים לגיל (ילדים + הנושאים)")
                            else L("בתוך הקטגוריות שבחרתי – לחסום תוכן שלא מתאים לגיל"), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(checked = k in prof.ageInside, onCheckedChange = {
                            saveProf(prof.copy(ageInside = if (it) prof.ageInside + k else prof.ageInside - k))
                        })
                    }
                    if (kind != com.tbm.iptv.data.Kind.LIVE) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(L("רק עברית ואנגלית"), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(checked = k in prof.langOnly, onCheckedChange = {
                            saveProf(prof.copy(langOnly = if (it) prof.langOnly + k else prof.langOnly - k))
                        })
                    }
                    var showC by remember(kind) { mutableStateOf(false) }
                    val countryCounts = remember(all) {
                        all.groupingBy { com.tbm.iptv.data.Countries.of(it.group) }.eachCount()
                            .filterKeys { it != com.tbm.iptv.data.Countries.OTHER }.toList().sortedByDescending { it.second }
                    }
                    FocusItem(Modifier.fillMaxWidth(), onClick = { showC = !showC }) {
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(L("מדינות:"), fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(if (cset.isEmpty()) L("כולן") else cset.joinToString(", ") { com.tbm.iptv.data.Countries.label(it) },
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(if (showC) "▲" else "▼")
                        }
                    }
                    if (showC) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val setC: (Set<String>) -> Unit = { n -> saveProf(prof.copy(kindCountries = prof.kindCountries + (k to n))) }
                        androidx.compose.material3.FilterChip(selected = cset.isEmpty(), label = { Text(L("כולן")) }, onClick = { setC(emptySet()) })
                        countryCounts.take(30).forEach { (c, _) ->
                            androidx.compose.material3.FilterChip(selected = c in cset, label = { Text(com.tbm.iptv.data.Countries.label(c)) },
                                onClick = { setC(if (c in cset) cset - c else cset + c) })
                        }
                    }
                }
            }
            OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(L("חיפוש קטגוריה…")) }, modifier = Modifier.fillMaxWidth())
            val allowedCount = groups.filter { state(it.first) > 0 }.sumOf { it.second }
            Text(if (pick) L("סמן ✅ את הקטגוריות מהשרת שהילד יראה ({0} פריטים נבחרו מתוך {1}).", allowedCount, all.size)
                else L("יוצגו לילד: {0} פריטים מתוך {1}. לחיצה על קטגוריה מחליפה: מוצג ⇄ חסום.", allowedCount, all.size), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
        }
        }
        if (content == null) item { Text(L("התוכן עדיין נטען…"), Modifier.padding(16.dp)) }
            items(shown.size) { i ->
                val (g, n) = shown[i]
                val st = state(g)
                val key = "${kind.name}|$g"
                FocusItem(Modifier.fillMaxWidth(), onClick = {
                    if (com.tbm.iptv.data.Catalog.isAdult(g)) return@FocusItem
                    Prefs.setKidsGroupState(profileId, key, if (pick) (if (st > 0) 0 else 1) else if (st > 0) -1 else 1)
                    if (st > 0 && key in legacy) Prefs.setKidsGroup(kind, g, false)
                }) {
                    Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(when (st) { 1 -> "✅"; 2 -> "🟢"; -1 -> "⛔"; else -> "⚪" }, fontSize = 18.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g, maxLines = 1)
                            val blockedHere = byGroup[g].orEmpty().count { it.key in itemExc }
                            Text(when (st) { 1 -> if (pick) L("נבחר") else L("מאושר ידנית"); 2 -> L("מוצג אוטומטית (מתאים לגיל)"); -1 -> L("חסום")
                                else -> if (pick) L("לא נבחר – לחץ לבחירה") else L("לא מוצג") } + " · $n" +
                                (if (blockedHere > 0) " · " + L("{0} חסומים", blockedHere) else ""), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // פתיחת הקטגוריה – חסימה של ערוץ/סרט בודד
                        androidx.compose.material3.TextButton(onClick = { expanded = if (expanded == g) null else g }) {
                            Text(if (expanded == g || itemHit(g)) L("סגור ▲") else L("פריטים ▼"), fontSize = 12.sp)
                        }
                    }
                }
                if (expanded == g || itemHit(g)) Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                    byGroup[g].orEmpty().filter { q.isBlank() || !itemHit(g) || it.name.contains(q, true) }.take(400).forEach { e ->
                        val blocked = e.key in itemExc
                        FocusItem(Modifier.fillMaxWidth(), onClick = { Prefs.setKidsItemBlocked(profileId, e.key, !blocked) }) {
                            Row(Modifier.padding(start = 44.dp, end = 16.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (blocked) "⛔" else "✓", fontSize = 15.sp, color = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(e.name, maxLines = 1, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text(if (blocked) L("חסום") else L("מותר"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
