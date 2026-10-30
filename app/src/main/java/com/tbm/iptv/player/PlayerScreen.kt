package com.tbm.iptv.player

import com.tbm.iptv.L

import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.mediarouter.app.MediaRouteButton
import coil.compose.AsyncImage
import com.google.android.gms.cast.framework.CastButtonFactory
import com.tbm.iptv.data.Epg
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.Repo
import com.tbm.iptv.subs.langLabel
import com.tbm.iptv.ui.FocusItem
import com.tbm.iptv.ui.fmtTime

@Composable
fun PlayerScreen(a: PlayerActivity) {
    val ui = a.ui
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = a.player
                    keepScreenOn = true
                    setShowSubtitleButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    controllerShowTimeoutMs = Prefs.osdTimeout * 1000
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                        ui.controlsVisible = v == View.VISIBLE
                    })
                    subtitleView?.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * Prefs.subSize)
                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null,
                        )
                    )
                    a.playerView = this
                }
            },
            update = { it.resizeMode = ui.resize },
            modifier = Modifier.fillMaxSize(),
        )

        if (ui.controlsVisible && !ui.pip) TopBar(a)

        if (ui.casting) Text(
            L("משדר לטלוויזיה (Chromecast)"), color = Color.White,
            modifier = Modifier.align(Alignment.Center).background(Color(0xAA000000), RoundedCornerShape(10.dp)).padding(16.dp),
        )

        ui.error?.let {
            Text(
                it, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xAA000000), RoundedCornerShape(10.dp)).padding(16.dp),
            )
        }

        if (ui.zapDigits.isNotEmpty()) Text(
            ui.zapDigits, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)
                .background(Color(0xAA000000), RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 6.dp),
        )

        ui.message?.let {
            Text(
                it, color = Color.White, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)
                    .background(Color(0xCC1C263C), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (ui.showList && !ui.pip) ChannelOverlay(a, Modifier.align(Alignment.CenterStart))
        if (ui.showSubs) SubtitleDialog(a)
        if (ui.showRecord) RecordDialog(a)
        ui.mtOffer?.let { offer ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { ui.mtOffer = null },
                title = { Text(L("לא נמצאה כתובית בעברית")) },
                text = { Text(L("לתרגם אוטומטית את הכתובית האנגלית לעברית? (תרגום מכונה, מוצג מימין לשמאל)")) },
                confirmButton = { TextButton(onClick = { ui.mtOffer = null; a.applySubtitle(offer) }) { Text(L("תרגם לעברית")) } },
                dismissButton = {
                    Row {
                        TextButton(onClick = { a.applyEnglishInstead() }) { Text(L("אנגלית")) }
                        TextButton(onClick = { ui.mtOffer = null }) { Text(L("בלי כתוביות")) }
                    }
                },
            )
        }
    }
}

@Composable
private fun TopBar(a: PlayerActivity) {
    val ui = a.ui
    val item = ui.item
    Epg.version.intValue
    val nowNext = if (ui.isLive && item != null) Epg.nowNext(item.epgId, item.channelName) else null to null
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { a.exit() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, L("חזרה"), tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(ui.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            nowNext.first?.let {
                Text(L("עכשיו: {0} (עד {1})", it.title, fmtTime(it.end)), color = Color(0xFFB0C4DE), fontSize = 12.sp, maxLines = 1)
            }
            nowNext.second?.let {
                Text(L("הבא: {0} {1}", fmtTime(it.start), it.title), color = Color(0xFF8899AA), fontSize = 11.sp, maxLines = 1)
            }
            ui.subLabel?.let { Text(L("כתובית: {0}", it), color = Color(0xFFB0C4DE), fontSize = 12.sp, maxLines = 1) }
        }
        if (ui.subLabel != null) {
            TextButton(onClick = { a.shiftSubtitle(-500) }) { Text("−0.5", color = Color.White) }
            Text("%+.1f".format(ui.subOffset / 1000.0), color = Color.White, fontSize = 12.sp)
            TextButton(onClick = { a.shiftSubtitle(500) }) { Text("+0.5", color = Color.White) }
        }
        if (ui.castAvailable) {
            AndroidView(
                factory = { ctx ->
                    MediaRouteButton(ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat)).also {
                        runCatching { CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, it) }
                    }
                },
                modifier = Modifier.size(48.dp),
            )
        }
        if (ui.isLive) {
            IconButton(onClick = { ui.showList = true }) { Icon(Icons.AutoMirrored.Filled.List, L("רשימת ערוצים"), tint = Color.White) }
            if (!Prefs.isKids) IconButton(onClick = { ui.showRecord = true }) {
                Icon(Icons.Filled.FiberManualRecord, L("הקלטה"), tint = Color(0xFFFF5252))
            }
        } else {
            IconButton(onClick = {
                ui.showSubs = true
                if (ui.subResults.isEmpty() && !ui.subSearching) a.searchSubtitles(auto = false)
            }) { Icon(Icons.Filled.ClosedCaption, L("חיפוש כתוביות"), tint = Color.White) }
        }
        IconButton(onClick = { a.cycleSleep() }) {
            Icon(Icons.Filled.Bedtime, L("טיימר שינה"), tint = if (ui.sleepMin > 0) Color(0xFFFFC94A) else Color.White)
        }
        IconButton(onClick = { a.cycleResize() }) { Icon(Icons.Filled.AspectRatio, L("יחס תצוגה"), tint = Color.White) }
        if (ui.hasPrev) IconButton(onClick = { a.prev() }) { Icon(Icons.Filled.SkipPrevious, L("הקודם"), tint = Color.White) }
        if (ui.hasNext) IconButton(onClick = { a.next() }) { Icon(Icons.Filled.SkipNext, L("הבא"), tint = Color.White) }
    }
}

/** רשימת ערוצים בתוך הנגן (כמו TiviMate) */
@Composable
private fun ChannelOverlay(a: PlayerActivity, modifier: Modifier) {
    val ui = a.ui
    val q = Repo.queue
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (Repo.queueIndex - 3).coerceAtLeast(0))
    Epg.version.intValue
    Surface(
        color = Color(0xEE0B1220),
        modifier = modifier.fillMaxHeight().widthIn(max = 420.dp).fillMaxWidth(0.6f),
    ) {
        LazyColumn(state = state, modifier = Modifier.padding(6.dp)) {
            itemsIndexed(q) { i, it ->
                val now = Epg.nowNext(it.epgId, it.channelName).first
                FocusItem(
                    Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    selected = i == Repo.queueIndex,
                    onClick = { ui.showList = false; a.playIndex(i) },
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", Modifier.width(36.dp), color = Color(0xFF8899AA), fontSize = 12.sp)
                        AsyncImage(it.logo, null, Modifier.size(40.dp, 28.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            now?.let { e -> Text(e.title, color = Color(0xFF8899AA), maxLines = 1, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordDialog(a: PlayerActivity) {
    val ui = a.ui
    val item = ui.item
    val now = if (item != null) Epg.nowNext(item.epgId, item.channelName).first else null
    Dialog(onDismissRequest = { ui.showRecord = false }) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(L("הקלטת {0}", ui.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (now != null) {
                    val left = ((now.end - System.currentTimeMillis()) / 60_000L).toInt() + 2
                    OutlinedButton(onClick = { a.recordNow(left.coerceAtLeast(5)); ui.showRecord = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(L("עד סוף \"{0}\" ({1} דק׳)", now.title, left))
                    }
                }
                listOf(30, 60, 120, 180).forEach { m ->
                    OutlinedButton(onClick = { a.recordNow(m); ui.showRecord = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(L("{0} דקות", m))
                    }
                }
                TextButton(onClick = { ui.showRecord = false }) { Text(L("ביטול")) }
            }
        }
    }
}

@Composable
private fun SubtitleDialog(a: PlayerActivity) {
    val ui = a.ui
    Dialog(onDismissRequest = { ui.showSubs = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(0.92f).heightIn(max = 560.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L("כתוביות"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { a.searchSubtitles(auto = false) }) { Text(L("חפש שוב")) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { a.disableSubtitles(); ui.showSubs = false }) { Text(L("כבה")) }
                }
                Spacer(Modifier.size(8.dp))
                if (ui.subSearching) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(L("מחפש ב-Wizdom, כתובית, OpenSubtitles, SubDL…"))
                    }
                }
                if (!ui.subSearching && ui.subResults.isEmpty()) {
                    Text(L("לא נמצאו כתוביות"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ui.subErrors.forEach {
                    Text("⚠ $it", color = Color(0xFFFFB74D), fontSize = 12.sp)
                }
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(ui.subResults) { r ->
                        FocusItem(Modifier.fillMaxWidth(), onClick = { a.applySubtitle(r); ui.showSubs = false }) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    "${langLabel(r.lang)} · ${r.provider}",
                                    color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(r.release.ifBlank { "—" }, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}
