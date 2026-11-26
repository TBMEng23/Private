package com.tbm.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbm.iptv.L
import com.tbm.iptv.data.Prefs

/** עיצוב כתוביות – כפתורים (נוח גם בשלט): גודל, רקע, גובה, צבע, הדגשה. משמש בהגדרות ובחלון הכתוביות בנגן */
@Composable
fun SubStyleControls(compact: Boolean) {
    Prefs.version.intValue   // רענון התצוגה אחרי שינוי
    val fs = if (compact) 12.sp else 13.sp
    @Composable
    fun Line(label: String, value: String, minus: String, plus: String, onMinus: () -> Unit, onPlus: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
            Text(label, fontSize = fs, modifier = Modifier.weight(1f))
            TvTextButton(onClick = onMinus) { Text(minus, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Text(value, fontSize = fs, fontWeight = FontWeight.Bold, modifier = Modifier.width(if (compact) 78.dp else 96.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TvTextButton(onClick = onPlus) { Text(plus, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
    val bgNames = listOf(L("שקוף"), L("חצי שקוף"), L("שחור"))
    val colorNames = listOf(L("לבן"), L("צהוב"), L("תכלת"))
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (!compact) Text(L("עיצוב כתוביות"), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Line(L("גודל"), "%.1f×".format(Prefs.subSize), "A−", "A+",
            { Prefs.subSize = (Prefs.subSize - 0.1f).coerceAtLeast(0.6f) }, { Prefs.subSize = (Prefs.subSize + 0.1f).coerceAtMost(2.5f) })
        Line(L("רקע"), bgNames[Prefs.subBg], "◀", "▶", { Prefs.subBg = (Prefs.subBg + 2) % 3 }, { Prefs.subBg = (Prefs.subBg + 1) % 3 })
        Line(L("גובה מהתחתית"), "${(Prefs.subPos * 100).toInt()}%", "▼", "▲",
            { Prefs.subPos = Prefs.subPos - 0.03f }, { Prefs.subPos = Prefs.subPos + 0.03f })
        Line(L("צבע"), colorNames[Prefs.subColor], "◀", "▶", { Prefs.subColor = Prefs.subColor - 1 }, { Prefs.subColor = Prefs.subColor + 1 })
        Line(L("הדגשה"), if (Prefs.subBold) L("מודגש") else L("רגיל"), "◀", "▶",
            { Prefs.subBold = !Prefs.subBold }, { Prefs.subBold = !Prefs.subBold })
    }
}
