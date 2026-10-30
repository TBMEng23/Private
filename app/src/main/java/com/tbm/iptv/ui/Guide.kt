package com.tbm.iptv.ui

import com.tbm.iptv.L

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tbm.iptv.data.Catalog
import com.tbm.iptv.data.Catchup
import com.tbm.iptv.data.Channel
import com.tbm.iptv.data.Content
import com.tbm.iptv.data.Epg
import com.tbm.iptv.data.EpgEntry
import com.tbm.iptv.data.Kind
import com.tbm.iptv.data.PlayItem
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.toPlay
import com.tbm.iptv.rec.Sched
import com.tbm.iptv.rec.Scheduler
import java.util.Calendar

private const val HOUR = 3_600_000L

/** לוח שידורים מלא – ערוצים לאורך, זמן לרוחב. תוכנית שעברה = Catch-up, עתידית = תזכורת/הקלטה */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideSection(content: Content) {
    val ver = Prefs.version.intValue
    val epgVer = Epg.version.intValue
    val view = remember(content, ver) { Catalog.build(Kind.LIVE, content.live) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    val chans = remember(view, group) {
        view.items.filterIsInstance<Channel>().filter { group == null || it.group == group }
    }
    val now0 = remember { System.currentTimeMillis() }
    var winStart by rememberSaveable {
        mutableLongStateOf(Calendar.getInstance().apply {
            timeInMillis = now0
            set(Calendar.MINUTE, if (get(Calendar.MINUTE) >= 30) 30 else 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis)
    }
    var dialog by remember { mutableStateOf<Pair<Channel, EpgEntry>?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(L("לוח שידורים"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { winStart -= 2 * HOUR }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, L("מוקדם יותר")) }
            TextButton(onClick = {
                winStart = System.currentTimeMillis() / (30 * 60_000L) * (30 * 60_000L)
            }) { Text(L("עכשיו")) }
            IconButton(onClick = { winStart += 2 * HOUR }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, L("מאוחר יותר")) }
        }
        if (Epg.status.isNotBlank()) Text(Epg.status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FilterChip(group == null, { group = null }, { Text(L("הכל")) }) }
            items(view.groups.filter { it !in view.lockedGroups || com.tbm.iptv.data.Parental.unlocked }) { g ->
                FilterChip(group == g, { group = g }, { Text(g, maxLines = 1) })
            }
        }
        // ציר הזמן תמיד משמאל לימין
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val chW = 120.dp
                val span = if (maxWidth < 600.dp) 2 * HOUR else 3 * HOUR
                val timelineW = maxWidth - chW
                Column {
                    // שורת שעות
                    Row(Modifier.fillMaxWidth().height(26.dp)) {
                        Spacer(Modifier.width(chW))
                        Box(Modifier.width(timelineW).fillMaxHeight()) {
                            var t = winStart
                            while (t < winStart + span) {
                                val x = timelineW * ((t - winStart).toFloat() / span)
                                Text(fmtTime(t), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.offset(x = x + 4.dp, y = 4.dp))
                                t += 30 * 60_000L
                            }
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(chans) { _, c ->
                            GuideRow(c, winStart, span, chW, timelineW, epgVer) { e -> dialog = c to e }
                        }
                    }
                }
            }
        }
    }
    dialog?.let { (c, e) -> ProgramDialog(c, e, chans) { dialog = null } }
}

@Composable
private fun GuideRow(
    c: Channel, winStart: Long, span: Long, chW: androidx.compose.ui.unit.Dp, timelineW: androidx.compose.ui.unit.Dp,
    epgVer: Int, onClick: (EpgEntry) -> Unit,
) {
    val ctx = LocalContext.current
    val progs = remember(c.key, epgVer, winStart) {
        Epg.forChannel(c).filter { it.end > winStart && it.start < winStart + span }
    }
    val now = System.currentTimeMillis()
    Row(Modifier.fillMaxWidth().height(56.dp).padding(vertical = 1.dp)) {
        FocusItem(Modifier.width(chW).fillMaxHeight(), onClick = {
            Launcher.play(ctx, listOf(c.toPlay()), 0)
        }) {
            Row(Modifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(c.logo, null, Modifier.size(36.dp, 28.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(4.dp))
                Text(c.name, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.width(timelineW).fillMaxHeight()) {
            if (progs.isEmpty()) {
                Text(L("אין מידע"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
            }
            progs.forEach { e ->
                val s = maxOf(e.start, winStart); val en = minOf(e.end, winStart + span)
                val x = timelineW * ((s - winStart).toFloat() / span)
                val w = timelineW * ((en - s).toFloat() / span)
                val live = now in e.start until e.end
                val past = e.end <= now
                val bg = when {
                    live -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    past && Catchup.available(c, e, now) -> Color(0x3300C2A8)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                FocusItem(
                    Modifier.offset(x = x).width(w).fillMaxHeight().padding(1.dp),
                    onClick = { onClick(e) },
                ) {
                    Box(Modifier.fillMaxSize().background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                        Column {
                            Text(e.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (live) FontWeight.Bold else FontWeight.Normal)
                            Text("${fmtTime(e.start)}–${fmtTime(e.end)}", fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
            if (now in winStart until winStart + span) {
                val x = timelineW * ((now - winStart).toFloat() / span)
                Box(Modifier.offset(x = x).width(2.dp).fillMaxHeight().background(Color(0xFFFF5252)))
            }
        }
    }
}

/** פרטי תוכנית + פעולות: צפייה, צפייה מההתחלה (Catch-up), תזכורת, הקלטה מתוזמנת */
@Composable
fun ProgramDialog(c: Channel, e: EpgEntry, queue: List<Channel>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val now = System.currentTimeMillis()
    val live = now in e.start until e.end
    val past = e.end <= now
    val future = e.start > now
    val cu = Catchup.available(c, e, now)
    val p = Prefs.activePlaylist()

    fun playCatchup() {
        val url = Catchup.url(p, c, e.start, e.end)
        if (url == null) { Toast.makeText(ctx, L("הערוץ לא תומך בצפייה לאחור"), Toast.LENGTH_SHORT).show(); return }
        Launcher.play(ctx, listOf(PlayItem(
            key = "cu:${c.key}:${e.start}", title = "${c.name} · ${e.title}", url = url, kind = Kind.LIVE,
            logo = c.logo, epgId = c.epgId, channelName = c.name, isCatchup = true,
        )), 0)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("${c.name} · ${fmtDay(e.start)} ${fmtTime(e.start)}–${fmtTime(e.end)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                if (e.desc.isNotBlank()) Text(e.desc, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp), maxLines = 8)
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (live) Button(onClick = {
                        val l = queue.map { it.toPlay() }
                        Launcher.play(ctx, l, queue.indexOfFirst { it.key == c.key }.coerceAtLeast(0)); onDismiss()
                    }, modifier = Modifier.fillMaxWidth()) { Text(L("▶ צפה בשידור חי")) }
                    if ((live || past) && cu) OutlinedButton(onClick = { playCatchup() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (live) L("⏮ צפה מההתחלה") else L("▶ צפה (Catch-up)"))
                    }
                    if (past && !cu) Text(L("התוכנית שודרה ואין לה ארכיון"), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (future) {
                        OutlinedButton(onClick = {
                            Scheduler.add(ctx, Sched("r${e.start}${c.key.hashCode()}", "remind", e.title, c.url,
                                e.start, e.end, c.epgId, c.name))
                            Toast.makeText(ctx, L("תזכורת נקבעה"), Toast.LENGTH_SHORT).show(); onDismiss()
                        }, modifier = Modifier.fillMaxWidth()) { Text(L("🔔 תזכורת")) }
                    }
                    if ((future || live) && !Prefs.isKids) {
                        OutlinedButton(onClick = {
                            Scheduler.add(ctx, Sched("rec${e.start}${c.key.hashCode()}", "rec", "${c.name} - ${e.title}", c.url,
                                maxOf(e.start, now), e.end + 2 * 60_000L, c.epgId, c.name))
                            Toast.makeText(ctx, L("ההקלטה תוזמנה"), Toast.LENGTH_SHORT).show(); onDismiss()
                        }, modifier = Modifier.fillMaxWidth()) { Text(L("⏺ הקלט את התוכנית")) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("סגור")) } },
    )
}

fun fmtDay(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val today = Calendar.getInstance()
    val diff = ((ms / 86_400_000L) - (today.timeInMillis / 86_400_000L)).toInt()
    return when (diff) {
        0 -> L("היום"); -1 -> L("אתמול"); 1 -> L("מחר")
        else -> "%02d/%02d".format(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1)
    }
}
