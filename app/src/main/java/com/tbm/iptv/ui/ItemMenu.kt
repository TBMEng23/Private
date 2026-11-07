package com.tbm.iptv.ui

import com.tbm.iptv.L

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbm.iptv.data.Catalog
import com.tbm.iptv.data.Channel
import com.tbm.iptv.data.Entry
import com.tbm.iptv.data.Kind
import com.tbm.iptv.data.Parental
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.rec.RecordingService

class MenuTarget(val kind: Kind, val entry: Entry, val all: List<Entry>)

/** תפריט לחיצה ארוכה: מועדפים, שינוי שם, סדר, הסתרה, נעילה, אישור לילדים, הקלטה */
@Composable
fun ItemMenu(t: MenuTarget, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val e = t.entry
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(e.name) }
    val ver = Prefs.version.intValue
    val fav = remember(ver) { Prefs.isFav(e.key) }

    if (renaming) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(L("שינוי שם")) },
            text = { OutlinedTextField(newName, { newName = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { Prefs.setRename(e.key, newName.trim()); onDismiss() }) { Text(L("שמור")) } },
            dismissButton = { TextButton(onClick = { Prefs.setRename(e.key, null); onDismiss() }) { Text(L("שם מקורי")) } },
        )
        return
    }

    fun guarded(title: String, action: () -> Unit) = Parental.require(title) { action(); onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.name, fontWeight = FontWeight.Bold, maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MenuRow(if (fav) L("★ הסר ממועדפים") else L("☆ הוסף למועדפים")) { Prefs.toggleFav(e.key); onDismiss() }
                if (Prefs.isKids) {
                    // בתוך פרופיל ילדים: ההורה חוסם פריט / קטגוריה שעברו סינון – רק עם קוד הורים
                    val pid = Prefs.profileId
                    MenuRow(L("⛔ חסום בפרופיל הזה")) {
                        Parental.requireAlways(L("חסימה לילדים")) {
                            Prefs.setKidsItemBlocked(pid, e.key, true)
                            Toast.makeText(ctx, L("נחסם: {0}", e.name), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                    MenuRow(L("⛔ חסום את כל הקטגוריה \"{0}\"", e.group)) {
                        Parental.requireAlways(L("חסימה לילדים")) {
                            Prefs.setKidsGroupState(pid, "${t.kind.name}|${e.group}", -1)
                            Toast.makeText(ctx, L("הקטגוריה נחסמה: {0}", e.group), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                }
                if (!Prefs.isKids) {
                    MenuRow(L("✏ שינוי שם")) { Parental.require(L("שינוי שם")) { renaming = true } }
                    MenuRow(L("⬆ הזז למעלה")) { Catalog.move(t.kind, t.all, e, -1); onDismiss() }
                    MenuRow(L("⬇ הזז למטה")) { Catalog.move(t.kind, t.all, e, 1); onDismiss() }
                    MenuRow(L("🚫 הסתר")) { guarded(L("הסתרה")) { Prefs.setItemHidden(e.key, true) } }
                    val locked = e.key in Prefs.lockedItems()
                    MenuRow(if (locked) L("🔓 בטל נעילה") else L("🔒 נעל בקוד")) {
                        if (!Parental.hasPin()) {
                            Toast.makeText(ctx, L("קודם הגדר קוד הורים בהגדרות"), Toast.LENGTH_SHORT).show()
                        } else guarded(L("נעילה")) { Prefs.setItemLocked(e.key, !locked) }
                    }
                    // חסימה לפרופיל ילדים מסוים
                    Prefs.profiles().filter { it.kids }.forEach { kp ->
                        val blocked = e.key in Prefs.kidsItemExc(kp.id)
                        MenuRow(if (blocked) L("✓ החזר לפרופיל {0}", kp.name) else L("⛔ חסום לפרופיל {0}", kp.name)) {
                            guarded(L("חסימה לילדים")) { Prefs.setKidsItemBlocked(kp.id, e.key, !blocked) }
                        }
                    }
                    val kidsOk = e.key in Prefs.kidsItems()
                    MenuRow(if (kidsOk) L("👶 בטל אישור לילדים") else L("👶 אשר לפרופיל ילדים")) {
                        guarded(L("אישור לילדים")) { Prefs.setKidsItem(e.key, !kidsOk) }
                    }
                    if (e is Channel && t.kind == Kind.LIVE) {
                        MenuRow(L("⏺ הקלט שעה מעכשיו")) {
                            RecordingService.start(ctx, e.name, e.url, 60 * 60_000L)
                            Toast.makeText(ctx, L("ההקלטה התחילה"), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("סגור")) } },
    )
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth(), onClick = onClick) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
