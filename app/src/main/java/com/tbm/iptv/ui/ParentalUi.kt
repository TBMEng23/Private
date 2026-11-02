package com.tbm.iptv.ui

import com.tbm.iptv.L

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
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
            list.forEach { p -> ProfileTile(p) { choose(p, onDone) } }
            if (list.size < 6) FocusItem(onClick = { Parental.requireAlways(L("הוספת פרופיל")) { adding = true } }) {
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

fun choose(p: Profile, onDone: () -> Unit) {
    // פרופיל הורה תמיד דורש קוד (אם הוגדר קוד)
    if (!p.kids && Parental.hasPin() && !(Parental.unlocked && Prefs.profileId == p.id)) {
        Parental.requireAlways(L("כניסה לפרופיל {0}", p.name)) { Parental.switchProfile(p); Parental.unlocked = true; onDone() }
    } else {
        Parental.switchProfile(p); onDone()
    }
}

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
    var needPin by remember { mutableStateOf(false) }
    fun save() {
        val p = Profile(edit?.id ?: java.util.UUID.randomUUID().toString().take(6), name.trim(), kids, age, auto, color)
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
                Icon(if (p.kids) Icons.Filled.ChildCare else Icons.Filled.Person, null, tint = Color.White,
                    modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(p.name, fontWeight = FontWeight.Bold)
            Text(if (p.kids) L("ילדים · עד גיל {0}", p.age) else L("הורה · 🔒"), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
