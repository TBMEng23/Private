package com.tbm.iptv.ui

import com.tbm.iptv.L

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.PlayerView
import com.tbm.iptv.data.Catalog
import com.tbm.iptv.data.Channel
import com.tbm.iptv.data.Content
import com.tbm.iptv.data.Kind
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.toPlay
import com.tbm.iptv.player.PlayerFactory

/** Multi-view – עד 4 ערוצים במקביל. הקול מגיע מהחלון הנבחר */
@Composable
fun MultiViewSection(content: Content) {
    val ctx = LocalContext.current
    val ver = Prefs.version.intValue
    val chans = remember(content, ver) { Catalog.build(Kind.LIVE, content.live).items.filterIsInstance<Channel>() }
    val slots = remember { mutableStateListOf<Channel?>(null, null, null, null) }
    var active by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<Int?>(null) }
    val players = remember { List(4) { PlayerFactory.create(ctx) } }
    val owner = LocalLifecycleOwner.current

    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) players.forEach { it.pause() }
            if (e == Lifecycle.Event.ON_RESUME) players.forEachIndexed { i, p -> if (slots[i] != null) p.play() }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); players.forEach { it.release() } }
    }
    LaunchedEffect(active) { players.forEachIndexed { i, p -> p.volume = if (i == active) 1f else 0f } }

    fun setSlot(i: Int, c: Channel?) {
        slots[i] = c
        val p = players[i]
        if (c == null) { p.stop(); return }
        p.setMediaItem(PlayerFactory.mediaItem(c.url, c.name))
        p.volume = if (i == active) 1f else 0f
        p.prepare(); p.play()
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Text(L("Multi-view · לחיצה = בחירת קול · לחיצה ארוכה = החלפת ערוץ"), fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        for (r in 0..1) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (cIdx in 0..1) {
                    val i = r * 2 + cIdx
                    val ch = slots[i]
                    FocusItem(
                        Modifier.weight(1f).fillMaxHeight().padding(2.dp)
                            .border(2.dp, if (i == active && ch != null) MaterialTheme.colorScheme.primary else Color(0xFF222A3A)),
                        onLongClick = { picking = i },
                        onClick = { if (ch == null) picking = i else active = i },
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            if (ch != null) {
                                AndroidView(
                                    factory = { PlayerView(it).apply { useController = false; player = players[i] } },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Row(
                                    Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0x99000000)).padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (i == active) Icon(Icons.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Text(ch.name, color = Color.White, fontSize = 12.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
                                    IconButton(onClick = {
                                        Launcher.play(ctx, chans.map { it.toPlay() }, chans.indexOfFirst { it.key == ch.key }.coerceAtLeast(0))
                                    }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Filled.Fullscreen, L("מסך מלא"), tint = Color.White)
                                    }
                                }
                            } else {
                                Text(L("+ הוסף ערוץ"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.Center))
                            }
                        }
                    }
                }
            }
        }
    }

    picking?.let { idx ->
        ChannelPicker(chans, onPick = { setSlot(idx, it); active = idx; picking = null }, onDismiss = { picking = null },
            onClear = if (slots[idx] != null) ({ setSlot(idx, null); picking = null }) else null)
    }
}

@Composable
fun ChannelPicker(chans: List<Channel>, onPick: (Channel) -> Unit, onDismiss: () -> Unit, onClear: (() -> Unit)? = null) {
    var q by remember { mutableStateOf("") }
    val list = remember(q, chans) { if (q.isBlank()) chans.take(400) else chans.filter { it.name.contains(q, true) }.take(400) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("בחר ערוץ")) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(L("חיפוש…")) }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(list) { c ->
                        FocusItem(Modifier.fillMaxWidth(), onClick = { onPick(c) }) {
                            Text(c.name, Modifier.padding(10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("סגור")) } },
        dismissButton = { if (onClear != null) TextButton(onClick = onClear) { Text(L("נקה חלון")) } },
    )
}
