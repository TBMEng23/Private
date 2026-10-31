package com.tbm.iptv.ui

import com.tbm.iptv.L

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
    primary = Color(0xFF3A9BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF12335A),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = Color(0xFF22C997),
    background = Color(0xFF050608),
    onBackground = Color(0xFFF1F3F7),
    surface = Color(0xFF17181C),
    onSurface = Color(0xFFF1F3F7),
    surfaceVariant = Color(0xFF24262C),
    onSurfaceVariant = Color(0xFF9EA4B0),
    surfaceContainer = Color(0xFF1D1F24),
    surfaceContainerHigh = Color(0xFF26282E),
    surfaceContainerHighest = Color(0xFF2E3037),
    outline = Color(0xFF3A3D45),
    outlineVariant = Color(0xFF2A2C32),
    error = Color(0xFFFF6B6B),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        val dir = if (com.tbm.iptv.I18n.isEn) LayoutDirection.Ltr else LayoutDirection.Rtl
        CompositionLocalProvider(LocalLayoutDirection provides dir) {
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
        focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "focusScale")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
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
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L("חזרה")) }
        Text(
            title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
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
        if (com.tbm.iptv.player.ExternalPlayer.useFor(item.kind)) {
            val app = ctx.applicationContext
            // סרט/סדרה בלי כתובית שמורה – מחפש כתובית עברית לפני הפתיחה בנגן החיצוני
            if (item.kind != com.tbm.iptv.data.Kind.LIVE && !item.isCatchup && Prefs.autoSubs &&
                com.tbm.iptv.subs.SubtitleManager.loadCache(item.key) == null) {
                Toast.makeText(ctx, L("מחפש כתוביות לנגן החיצוני…"), Toast.LENGTH_SHORT).show()
                Repo.scope.launch {
                    val sf = kotlinx.coroutines.withTimeoutOrNull(15_000) {
                        runCatching {
                            val sm = com.tbm.iptv.subs.SubtitleManager
                            val q = sm.buildQuery(item)
                            val best = sm.searchAll(q).first.firstOrNull { !it.provider.startsWith(sm.MT_NAME) }
                            best?.let { r -> sm.download(r, q).also { sm.saveCache(item.key, it, r.provider) } }
                        }.getOrNull()
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (!com.tbm.iptv.player.ExternalPlayer.open(app, item, sf))
                            app.startActivity(Intent(app, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                return
            }
            if (com.tbm.iptv.player.ExternalPlayer.open(ctx, item)) return
        }
        ctx.startActivity(Intent(ctx, PlayerActivity::class.java))
    }

    /** חזרה מהחלון הצף למסך מלא – בלי לטעון מחדש */
    fun resumeMini(ctx: Context) {
        ctx.startActivity(Intent(ctx, PlayerActivity::class.java).putExtra("resume", true))
    }
}

fun fmtTime(ms: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}
