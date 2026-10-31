package com.tbm.iptv.player

import com.tbm.iptv.L

import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.tbm.iptv.rec.RecordingService
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

/** סגנון כתוביות: גודל, מיקום, ורקע שחור לכיסוי כתוביות צרובות */
fun applySubStyle(pv: PlayerView) {
    val sv = pv.subtitleView ?: return
    sv.setApplyEmbeddedStyles(false)
    sv.setApplyEmbeddedFontSizes(false)
    sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * Prefs.subSize)
    sv.setBottomPaddingFraction(Prefs.subPos)
    val box = Prefs.subBox
    sv.setStyle(
        CaptionStyleCompat(
            android.graphics.Color.WHITE,
            if (box) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT,
            if (box) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT,
            if (box) CaptionStyleCompat.EDGE_TYPE_NONE else CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            android.graphics.Color.BLACK,
            null,
        )
    )
}

@Composable
fun PlayerScreen(a: PlayerActivity) {
    val ui = a.ui
    val styleVer = Prefs.version.intValue
    // הסתרה אוטומטית של הפקדים (כמו נטפליקס)
    LaunchedEffect(ui.controlsVisible, ui.touchedAt, ui.showGuide, ui.showList) {
        if (ui.controlsVisible && !ui.showGuide && !ui.showList) {
            kotlinx.coroutines.delay(Prefs.osdTimeout * 1000L)
            if (a.player.isPlaying) ui.controlsVisible = false
        }
    }
    LaunchedEffect(ui.gesture) { if (ui.gesture != null) { kotlinx.coroutines.delay(900); ui.gesture = null } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = a.player
                    keepScreenOn = true
                    useController = false
                    a.playerView = this
                }
            },
            update = {
                it.resizeMode = ui.resize
                @Suppress("UNUSED_VARIABLE") val v = styleVer
                applySubStyle(it)
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (!ui.pip) GestureLayer(a)

        if (ui.controlsVisible && !ui.pip) {
            if (ui.locked) LockedOverlay(a) else Controls(a)
        }

        if (!ui.controlsVisible && !ui.pip) {
            val recs by RecordingService.active.collectAsState()
            val url = a.liveUrl()
            if (recs.values.any { it.url == url }) Text("● REC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    .background(Color(0xCCE50914), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        if (ui.boosting) Text("2× ⏩", color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
                .background(Color(0x99000000), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 6.dp))

        ui.gesture?.let {
            Text(it, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).background(Color(0x99000000), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp))
        }

        if (!ui.pip) NetflixExtras(a, Modifier.align(Alignment.BottomEnd))

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
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp)
                    .background(Color(0xCC1C263C), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (ui.showList && !ui.pip) ChannelOverlay(a, Modifier.align(Alignment.CenterStart))
        if (ui.showGuide && !ui.pip) ChannelGuidePanel(a, Modifier.align(Alignment.CenterEnd))
        if (ui.showSubs) SubtitleDialog(a)
        if (ui.options != null) OptionsSheet(a)
        if (ui.showRecord) RecordDialog(a)
        if (ui.stillWatching) androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            title = { Text(L("עדיין צופה?")) },
            text = { Text(L("ניגנו 3 פרקים ברצף. להמשיך לפרק הבא?")) },
            confirmButton = { TextButton(onClick = { a.continueWatching() }) { Text(L("המשך לצפות")) } },
            dismissButton = { TextButton(onClick = { ui.stillWatching = false; a.exit() }) { Text(L("יציאה")) } },
        )
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

/** ג'סטות: הקשה = פקדים, הקשה כפולה בצדדים = ±10 שנ', לחיצה ארוכה = 2×, גרירה אנכית = בהירות/עוצמה */
@Composable
private fun GestureLayer(a: PlayerActivity) {
    val ui = a.ui
    var width by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(1) }
    var height by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(1) }
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { width = it.width; height = it.height }
            .pointerInput(ui.isLive, ui.locked) {
                detectTapGestures(
                    onTap = { a.touched(); ui.controlsVisible = !ui.controlsVisible },
                    onDoubleTap = { o ->
                        if (!ui.locked) {
                            a.touched()
                            val right = o.x > width / 2f
                            // כמו יוטיוב: צד ימין = קדימה
                            if (ui.isLive) { if (right) a.next() else a.prev() }
                            else { a.seekBy(if (right) 10_000 else -10_000); ui.gesture = if (right) "⏩ 10" else "⏪ 10" }
                        }
                    },
                    onLongPress = { if (!ui.locked) a.boost(true) },
                    onPress = {
                        tryAwaitRelease()
                        if (ui.boosting) a.boost(false)
                    },
                )
            }
            .pointerInput(ui.locked) {
                var side = 0
                detectVerticalDragGestures(
                    onDragStart = { o -> side = if (o.x < width / 2f) -1 else 1 },
                    onVerticalDrag = { change, dy ->
                        if (!ui.locked) {
                            change.consume()
                            val d = -dy / height * 1.6f
                            if (side < 0) a.adjustBrightness(d) else a.adjustVolume(d)
                        }
                    },
                )
            }
    )
}

@Composable
private fun LockedOverlay(a: PlayerActivity) {
    Box(Modifier.fillMaxSize()) {
        IconButton(
            onClick = { a.ui.locked = false; a.touched() },
            modifier = Modifier.align(Alignment.CenterStart).padding(24.dp).size(56.dp)
                .background(Color(0x99000000), androidx.compose.foundation.shape.CircleShape),
        ) { Icon(Icons.Filled.Lock, L("ביטול נעילה"), tint = Color.White) }
    }
}

/** פקדים בסגנון נטפליקס */
@Composable
private fun Controls(a: PlayerActivity) {
    val ui = a.ui
    var pos by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var dur by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var playing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    var dragging by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            pos = a.player.currentPosition; dur = a.player.duration.coerceAtLeast(0); playing = a.player.isPlaying
            kotlinx.coroutines.delay(400)
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0x55000000))) {
        TopBar(a, Modifier.align(Alignment.TopCenter))

        // מרכז: ‹ערוץ  ⏪10  ▶  ⏩10  ערוץ›
        Box(Modifier.align(Alignment.Center)) { com.tbm.iptv.ui.Ltr { Row(verticalAlignment = Alignment.CenterVertically) {
            if (ui.isLive || ui.inCatchup) {
                if (ui.hasPrev) BigBtn(Icons.AutoMirrored.Filled.KeyboardArrowLeft, L("ערוץ קודם"), 56) { a.touched(); a.prev() }
                Spacer(Modifier.width(40.dp))
            } else {
                BigBtn(Icons.Filled.Replay10, L("10 שניות אחורה"), 48) { a.touched(); a.seekBy(-10_000) }
                Spacer(Modifier.width(36.dp))
            }
            BigBtn(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, L("ניגון/השהיה"), 72, filled = true) {
                a.touched(); a.togglePlay()
            }
            if (ui.isLive || ui.inCatchup) {
                Spacer(Modifier.width(40.dp))
                if (ui.hasNext) BigBtn(Icons.AutoMirrored.Filled.KeyboardArrowRight, L("ערוץ הבא"), 56) { a.touched(); a.next() }
            } else {
                Spacer(Modifier.width(36.dp))
                BigBtn(Icons.Filled.Forward10, L("10 שניות קדימה"), 48) { a.touched(); a.seekBy(10_000) }
            }
        } } }

        // תחתית: פס זמן + כפתורים
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (!ui.isLive && dur > 0) com.tbm.iptv.ui.Ltr {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtDur(dragging?.let { (it * dur).toLong() } ?: pos), color = Color.White, fontSize = 12.sp)
                    androidx.compose.material3.Slider(
                        value = dragging ?: (pos.toFloat() / dur).coerceIn(0f, 1f),
                        onValueChange = { dragging = it; a.touched() },
                        onValueChangeFinished = { dragging?.let { a.player.seekTo((it * dur).toLong()) }; dragging = null },
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = Color(0xFFE50914), activeTrackColor = Color(0xFFE50914),
                            inactiveTrackColor = Color(0x66FFFFFF)),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    Text("-" + fmtDur((dur - pos).coerceAtLeast(0)), color = Color.White, fontSize = 12.sp)
                }
            } else if (ui.isLive) com.tbm.iptv.ui.Ltr {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Text("● LIVE", color = Color(0xFFE50914), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    val item = ui.item
                    val now = if (item != null) Epg.nowNext(item.epgId, item.channelName).first else null
                    if (now != null && now.end > now.start) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { ((System.currentTimeMillis() - now.start).toFloat() / (now.end - now.start)).coerceIn(0f, 1f) },
                            color = Color(0xFFE50914), trackColor = Color(0x44FFFFFF),
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Text(fmtTime(now.end), color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ui.inCatchup) BarBtn(Icons.Filled.LiveTv, L("חזרה לשידור חי")) { a.backToLive() }
                if (ui.isLive || ui.inCatchup) {
                    BarBtn(Icons.Filled.DateRange, L("לוח שידורים")) { ui.showGuide = true }
                    BarBtn(Icons.AutoMirrored.Filled.List, L("ערוצים")) { ui.showList = true }
                }
                if (!ui.isLive) BarBtn(Icons.Filled.Speed, if (ui.speed == 1f) L("מהירות") else "${ui.speed}×") { a.touched(); a.cycleSpeed() }
                BarBtn(Icons.Filled.Subtitles, L("שמע וכתוביות")) {
                    ui.showSubs = true
                    if (!ui.isLive && ui.subResults.isEmpty() && !ui.subSearching) a.searchSubtitles(auto = false)
                }
                BarBtn(Icons.Filled.AspectRatio, L("יחס תצוגה")) { a.touched(); a.cycleResize() }
                BarBtn(Icons.Filled.ScreenRotation, if (ui.landscape) L("לאורך") else L("לרוחב")) { a.touched(); a.toggleRotate() }
                BarBtn(Icons.Filled.Lock, L("נעילה")) { ui.locked = true }
                if (!ui.isLive && ui.hasNext) BarBtn(Icons.Filled.SkipNext, L("הפרק הבא")) { a.next() }
            }
        }
    }
}

@Composable
private fun BigBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, size: Int, filled: Boolean = false, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size.dp).then(
            if (filled) Modifier.background(Color(0x33FFFFFF), androidx.compose.foundation.shape.CircleShape) else Modifier
        ),
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size((size * 0.7f).dp)) }
}

@Composable
private fun BarBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    FocusItem(onClick = onClick) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1)
        }
    }
}

private fun fmtDur(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** "דלג על פתיח" ו"הפרק הבא בעוד…" – כמו נטפליקס */
@Composable
private fun NetflixExtras(a: PlayerActivity, modifier: Modifier) {
    val ui = a.ui
    val item = ui.item ?: return
    if (item.kind != com.tbm.iptv.data.Kind.SERIES || item.isLocal) return
    var pos by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var dur by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    LaunchedEffect(item.key) {
        while (true) { pos = a.player.currentPosition; dur = a.player.duration; kotlinx.coroutines.delay(500) }
    }
    Column(modifier.padding(end = 24.dp, bottom = 120.dp), horizontalAlignment = Alignment.End) {
        if (!ui.introSkipped && pos in 15_000..240_000) {
            androidx.compose.material3.OutlinedButton(
                onClick = { a.seekBy(85_000); ui.introSkipped = true },
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = Color(0xCC000000)),
            ) { Text(L("דלג על הפתיח"), color = Color.White) }
        }
        val left = dur - pos
        if (ui.hasNext && dur > 120_000 && left in 1..30_000) {
            androidx.compose.material3.Button(
                onClick = { a.next() },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White),
            ) { Text(L("הפרק הבא בעוד {0}", left / 1000), color = Color.Black, fontWeight = FontWeight.Bold) }
        }
    }
}

/** לוח שידורים של הערוץ הנוכחי בתוך הנגן – כולל צפייה לאחור */
@Composable
private fun ChannelGuidePanel(a: PlayerActivity, modifier: Modifier) {
    val ui = a.ui
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val c = androidx.compose.runtime.remember(Repo.queueIndex) { a.currentChannel() }
    Epg.version.intValue
    val progs = androidx.compose.runtime.remember(c?.key, Epg.version.intValue) { if (c != null) Epg.forChannel(c) else emptyList() }
    val now = System.currentTimeMillis()
    val curIdx = progs.indexOfFirst { now in it.start until it.end }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (curIdx - 2).coerceAtLeast(0))
    Surface(color = Color(0xF00B1220), modifier = modifier.fillMaxHeight().widthIn(max = 460.dp).fillMaxWidth(0.7f)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c?.name ?: "", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(if (c?.hasCatchup == true) L("⏪ צפייה לאחור זמינה – {0} ימים", (c?.archiveDays ?: 1).coerceAtLeast(1)) else L("אין ארכיון לערוץ הזה"),
                        color = Color(0xFF8899AA), fontSize = 12.sp)
                }
                IconButton(onClick = { ui.showGuide = false }) { Icon(Icons.Filled.Close, L("סגור"), tint = Color.White) }
            }
            if (progs.isEmpty()) Text(L("אין מידע לוח שידורים לערוץ הזה"), color = Color(0xFF8899AA), modifier = Modifier.padding(16.dp))
            LazyColumn(state = state) {
                itemsIndexed(progs) { i, e ->
                    val day = com.tbm.iptv.ui.fmtDay(e.start)
                    val newDay = i == 0 || com.tbm.iptv.ui.fmtDay(progs[i - 1].start) != day
                    val live = now in e.start until e.end
                    val past = e.end <= now
                    val cu = c != null && com.tbm.iptv.data.Catchup.available(c, e, now)
                    Column {
                        if (newDay) {
                            Text(day, color = Color(0xFF3A9BFF), fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                        }
                        FocusItem(Modifier.fillMaxWidth(), selected = live, onClick = {
                            val ch = c
                            if (ch != null) {
                                if ((past || live) && cu) a.playCatchup(ch, e)
                                else if (live) { ui.showGuide = false; if (ui.inCatchup) a.backToLive() }
                                else if (!past) {
                                    com.tbm.iptv.rec.Scheduler.add(ctx, com.tbm.iptv.rec.Sched("r${e.start}${ch.key.hashCode()}", "remind",
                                        e.title, ch.url, e.start, e.end, ch.epgId, ch.name))
                                    a.toast(L("תזכורת נקבעה: {0}", e.title))
                                }
                            }
                        }) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(fmtTime(e.start), color = Color(0xFF8899AA), fontSize = 12.sp, modifier = Modifier.width(48.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, color = if (past && !cu) Color(0xFF8899AA) else Color.White, fontSize = 14.sp,
                                        fontWeight = if (live) FontWeight.Bold else FontWeight.Normal, maxLines = 2)
                                    if (live && e.desc.isNotBlank()) Text(e.desc, color = Color(0xFF8899AA), fontSize = 11.sp, maxLines = 2)
                                }
                                Text(
                                    when { live && cu -> "⏮"; live -> "●"; past && cu -> "▶"; !past -> "🔔"; else -> "" },
                                    color = if (live) Color(0xFFE50914) else Color.White, fontSize = 16.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(a: PlayerActivity, modifier: Modifier) {
    val ui = a.ui
    val item = ui.item
    Epg.version.intValue
    val nowNext = if (ui.isLive && item != null) Epg.nowNext(item.epgId, item.channelName) else null to null
    Row(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xDD000000), Color.Transparent)))
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
        if (ui.castAvailable) {
            AndroidView(
                factory = { ctx ->
                    MediaRouteButton(ContextThemeWrapper(ctx, com.tbm.iptv.R.style.AppTheme_Player)).also {
                        runCatching { CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, it) }
                    }
                },
                modifier = Modifier.size(48.dp),
            )
        }
        if ((ui.isLive || ui.fromRecording) && !Prefs.isKids) {
            val recs by RecordingService.active.collectAsState()
            val url = a.liveUrl()
            val rec = recs.values.firstOrNull { it.url == url }
            if (rec != null) {
                FocusItem(onClick = { ui.showRecord = true }) {
                    Text("● REC  ⏹", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.background(Color(0xFFE50914), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
                }
            } else IconButton(onClick = { a.startRecording() }) {
                Icon(Icons.Filled.FiberManualRecord, L("הקלטה"), tint = Color(0xFFFF5252))
            }
        }
        if (!ui.isLive) IconButton(onClick = { a.toggleVoice() }) {
            Icon(Icons.Filled.RecordVoiceOver, L("קריינות AI"), tint = if (ui.voiceOn) Color(0xFFFFC94A) else Color.White)
        }
        IconButton(onClick = { a.touched(); ui.showSubs = true }) {
            Icon(Icons.Filled.Subtitles, L("שמע וכתוביות"), tint = Color.White)
        }
        IconButton(onClick = { a.touched(); ui.options = "main" }) {
            Icon(Icons.Filled.Settings, L("אפשרויות"), tint = Color.White)
        }
    }
}

private fun resizeName(m: Int) = when (m) {
    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> L("זום (מילוי עם חיתוך)")
    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> L("מתיחה למסך מלא")
    else -> L("מקורי")
}

/** חלון אפשרויות בסגנון MyTVOnline: שמע, כתוביות, מהירות, יחס תצוגה, טיימר שינה */
@Composable
private fun OptionsSheet(a: PlayerActivity) {
    val ui = a.ui
    val page = ui.options ?: return
    val close = { ui.options = null }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { close() } }, contentAlignment = Alignment.BottomCenter) {
            Surface(
                color = Color(0xFF26272C), shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(max = 520.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(width = 40.dp, height = 4.dp)
                        .background(Color(0xFF6A6B72), RoundedCornerShape(2.dp)))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (page != "main") IconButton(onClick = { ui.options = "main" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, L("חזרה"), tint = Color.White)
                        } else Spacer(Modifier.width(12.dp))
                        Text(when (page) {
                            "audio" -> L("רצועת שמע"); "speed" -> L("מהירות"); "aspect" -> L("יחס תצוגה"); "sleep" -> L("טיימר שינה")
                            else -> L("אפשרויות")
                        }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = Color(0x22FFFFFF))
                    LazyColumn {
                        when (page) {
                            "main" -> {
                                val aud = ui.audio.firstOrNull { it.selected }?.name ?: ui.audio.firstOrNull()?.name ?: L("ברירת מחדל")
                                item { OptRow(Icons.Filled.MusicNote, L("רצועת שמע"), aud) { ui.options = "audio" } }
                                val sub = ui.subLabel ?: ui.embedded.firstOrNull { it.selected }?.name ?: L("כבוי")
                                item { OptRow(Icons.Filled.Subtitles, L("כתוביות"), sub) { ui.options = null; ui.showSubs = true } }
                                if (!ui.isLive) item { OptRow(Icons.Filled.Speed, L("מהירות"), if (ui.speed == 1f) L("רגילה") else "${ui.speed}×") { ui.options = "speed" } }
                                item { OptRow(Icons.Filled.AspectRatio, L("יחס תצוגה"), resizeName(ui.resize)) { ui.options = "aspect" } }
                                item { OptRow(Icons.Filled.Bedtime, L("טיימר שינה"), if (ui.sleepMin > 0) L("{0} דק׳", ui.sleepMin) else L("כבוי")) { ui.options = "sleep" } }
                                if (!ui.isLive) item { OptRow(Icons.Filled.RecordVoiceOver, L("קריינות AI"), if (ui.voiceOn) L("פעילה") else L("כבויה")) { a.toggleVoice() } }
                                item { OptRow(Icons.Filled.ScreenRotation, L("סובב מסך"), if (ui.landscape) L("לרוחב") else L("לאורך")) { a.toggleRotate() } }
                                item {
                                    val ctx = androidx.compose.ui.platform.LocalContext.current
                                    val p = ExternalPlayer.pkg
                                    OptRow(Icons.AutoMirrored.Filled.OpenInNew, L("פתח בנגן חיצוני"),
                                        if (p.isBlank()) L("VLC / MX – בחירה") else ExternalPlayer.appName(ctx, p)) { ui.options = null; a.openExternal() }
                                }
                            }
                            "audio" -> {
                                if (ui.audio.isEmpty()) item { Text(L("אין רצועות שמע נוספות"), color = Color(0xFFAAAAAA), modifier = Modifier.padding(20.dp)) }
                                items(ui.audio) { t -> OptChoice(t.name, t.selected) { a.selectAudio(t); close() } }
                            }
                            "speed" -> items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) { s ->
                                OptChoice(if (s == 1f) L("רגילה") else "$s×", ui.speed == s) { a.setSpeed(s); close() }
                            }
                            "aspect" -> items(listOf(
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL,
                            )) { m -> OptChoice(resizeName(m), ui.resize == m) { a.setResize(m); close() } }
                            "sleep" -> items(listOf(0, 15, 30, 45, 60, 90, 120)) { m ->
                                OptChoice(if (m == 0) L("כבוי") else L("{0} דק׳", m), ui.sleepMin == m) { a.setSleep(m); close() }
                            }
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun OptRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 17.sp)
                Text(value, color = Color(0xFFA9AAB0), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun OptChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth(), selected = selected, onClick = onClick) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.Check, null, tint = Color(0xFF3A9BFF))
        }
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

/** חלון הקלטה: ההקלטה כבר רצה – כאן משנים זמן סיום או עוצרים */
@Composable
private fun RecordDialog(a: PlayerActivity) {
    val ui = a.ui
    val all by RecordingService.active.collectAsState()
    val url = a.liveUrl()
    val rec = all.values.firstOrNull { it.url == url }
    var custom by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val item = ui.item
    val prog = if (item != null) Epg.nowNext(item.epgId, item.channelName).first else null
    Dialog(onDismissRequest = { ui.showRecord = false }) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rec == null) {
                    Text(L("אין הקלטה פעילה לערוץ הזה"), style = MaterialTheme.typography.titleMedium)
                    androidx.compose.material3.Button(onClick = { ui.showRecord = false; a.startRecording() }, modifier = Modifier.fillMaxWidth()) {
                        Text(L("⏺ התחל להקליט"))
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("● REC", color = Color(0xFFE50914), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(rec.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(L("מסתיים ב-{0} · {1} MB", fmtTime(rec.endsAt), rec.bytes / 1_000_000),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    if (ui.fromRecording) Text(L("צופה מתוך ההקלטה (חיבור אחד לספק) – אפשר להשהות"), fontSize = 12.sp,
                        color = Color(0xFF22C997))
                    androidx.compose.material3.Button(
                        onClick = { a.stopRecording() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(L("⏹ עצור הקלטה ושמור"), color = Color.White) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { a.setRecordingEnd(rec.endsAt + 30 * 60_000L) }, modifier = Modifier.weight(1f)) { Text(L("+30 דק׳")) }
                        OutlinedButton(onClick = { a.setRecordingEnd(rec.endsAt + 60 * 60_000L) }, modifier = Modifier.weight(1f)) { Text(L("+שעה")) }
                    }
                    if (prog != null) OutlinedButton(onClick = { a.setRecordingEnd(prog.end + 5 * 60_000L) }, modifier = Modifier.fillMaxWidth()) {
                        Text(L("עד סוף \"{0}\" ({1})", prog.title, fmtTime(prog.end)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = { a.setRecordingEnd(System.currentTimeMillis() + 12 * 3_600_000L) }, modifier = Modifier.fillMaxWidth()) {
                        Text(L("ללא הגבלה – עד שאעצור"))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.OutlinedTextField(
                            custom, { v -> custom = v.filter { it.isDigit() }.take(4) }, singleLine = true,
                            label = { Text(L("עוד X דקות מעכשיו")) }, modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        )
                        Spacer(Modifier.width(6.dp))
                        TextButton(enabled = custom.isNotBlank(), onClick = {
                            a.setRecordingEnd(System.currentTimeMillis() + (custom.toLongOrNull() ?: 0L) * 60_000L); custom = ""
                        }) { Text(L("קבע")) }
                    }
                }
                TextButton(onClick = { ui.showRecord = false }, modifier = Modifier.align(Alignment.End)) { Text(L("סגור")) }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
                // שמע (שפות) – כמו נטפליקס: שמע וכתוביות באותו חלון
                if (ui.audio.size > 1) {
                    Text(L("שמע:"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ui.audio) { e ->
                            androidx.compose.material3.FilterChip(selected = e.selected, onClick = { a.selectAudio(e) },
                                label = { Text(e.name, fontSize = 12.sp) })
                        }
                    }
                }
                if (ui.subLabel != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L("סנכרון כתובית:"), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { a.shiftSubtitle(-500) }) { Text("−0.5") }
                    Text("%+.1f".format(ui.subOffset / 1000.0), fontSize = 12.sp)
                    TextButton(onClick = { a.shiftSubtitle(500) }) { Text("+0.5") }
                }
                if (ui.isLive) {
                    Text(L("חיפוש כתוביות זמין לסרטים וסדרות"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                var manual by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(a.currentQueryTitle()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(
                        manual, { manual = it }, singleLine = true, modifier = Modifier.weight(1f),
                        label = { Text(L("שם לחיפוש (אפשר שם מקורי או tt1234567)")) },
                    )
                    Spacer(Modifier.width(6.dp))
                    androidx.compose.material3.Button(onClick = { a.searchManual(manual) }) { Text(L("חפש")) }
                }
                val active = com.tbm.iptv.subs.SubtitleManager.providers.filter { it.enabled() }.map { it.name }
                val off = com.tbm.iptv.subs.SubtitleManager.providers.filter { !it.enabled() }.map { it.name }
                Text(L("מקורות פעילים: {0}", active.joinToString(", ")), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                if (off.isNotEmpty()) Text(L("לא פעילים (חסר מפתח בהגדרות ← שמע וכתוביות): {0}", off.joinToString(", ")),
                    fontSize = 11.sp, color = Color(0xFFFFB74D))
                Spacer(Modifier.size(6.dp))
                if (ui.subSearching) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(L("מחפש כתוביות…"))
                    }
                }
                if (!ui.subSearching && ui.subResults.isEmpty()) {
                    Text(L("לא נמצאו כתוביות"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ui.subErrors.forEach {
                    Text("⚠ $it", color = Color(0xFFFFB74D), fontSize = 12.sp)
                }
                // כתוביות מובנות בקובץ + כיסוי כתוביות צרובות
                if (ui.embedded.isNotEmpty()) {
                    Text(L("כתוביות מובנות בקובץ:"), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ui.embedded) { e ->
                            androidx.compose.material3.FilterChip(selected = e.selected, onClick = { a.selectEmbedded(e); ui.showSubs = false },
                                label = { Text(e.name, fontSize = 12.sp) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    androidx.compose.material3.Switch(checked = Prefs.subBox, onCheckedChange = { Prefs.subBox = it })
                    Text(L("רקע שחור (מכסה כתוביות צרובות בתמונה)"), fontSize = 12.sp, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                    TextButton(onClick = { Prefs.subPos = Prefs.subPos + 0.03f }) { Text("▲") }
                    TextButton(onClick = { Prefs.subPos = Prefs.subPos - 0.03f }) { Text("▼") }
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
