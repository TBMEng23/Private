package com.tbm.iptv.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tbm.iptv.data.Prefs
import com.tbm.iptv.data.PlayItem
import com.tbm.iptv.data.Repo
import com.tbm.iptv.player.PlayerActivity

val Gold = Color(0xFFFFC94A)

private val Scheme = darkColorScheme(
    primary = Color(0xFF2EA8FF),
    onPrimary = Color.White,
    secondary = Color(0xFF00C2A8),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF141C2E),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1E2840),
    onSurfaceVariant = Color(0xFFA9B3C7),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(color = Scheme.background, contentColor = Scheme.onBackground) { content() }
        }
    }
}

/** פריט שניתן לבחור גם במגע וגם בשלט (מודגש בפוקוס) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusItem(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        else -> Color.Transparent
    }
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(if (focused) 2.dp else 0.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onLongClick = onLongClick,
                onClick = onClick,
            )
    ) { content() }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
        Text(
            title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        actions()
    }
}

object Launcher {
    fun play(ctx: Context, queue: List<PlayItem>, index: Int) {
        if (index !in queue.indices) return
        Repo.queue = queue
        Repo.queueIndex = index
        val item = queue[index]
        if (Prefs.externalPlayer) {
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(Uri.parse(item.url), "video/*")
                        .putExtra("title", item.title)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(ctx, "לא נמצא נגן חיצוני – משתמש בנגן המובנה", Toast.LENGTH_SHORT).show()
            }
        }
        ctx.startActivity(Intent(ctx, PlayerActivity::class.java))
    }
}

fun fmtTime(ms: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}
