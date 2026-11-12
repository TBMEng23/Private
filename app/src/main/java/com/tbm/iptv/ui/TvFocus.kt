package com.tbm.iptv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * הדגשת פוקוס בסגנון נטפליקס לכפתורים רגילים (בסטרימר עם שלט):
 * מסגרת לבנה עבה + הגדלה קלה – כדי שתמיד יהיה ברור על איזה כפתור אתה עומד.
 */
fun Modifier.tvFocus(shape: Shape = RoundedCornerShape(50)): Modifier = composed {
    var f by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (f) 1.08f else 1f, label = "tvFocus")
    this.onFocusChanged { f = it.hasFocus }
        .then(if (sc != 1f) Modifier.graphicsLayer { scaleX = sc; scaleY = sc } else Modifier)
        .border(if (f) 3.dp else 0.dp, if (f) Color.White else Color.Transparent, shape)
}

@Composable
fun TvButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(), contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.Button(onClick, modifier.tvFocus(shape), enabled, shape, colors, contentPadding = contentPadding, content = content)

@Composable
fun TvOutlinedButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(), border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.OutlinedButton(onClick, modifier.tvFocus(shape), enabled, shape, colors, border = border,
    contentPadding = contentPadding, content = content)

@Composable
fun TvTextButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(), contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.TextButton(onClick, modifier.tvFocus(shape), enabled, shape, colors, contentPadding = contentPadding, content = content)

@Composable
fun TvIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) =
    androidx.compose.material3.IconButton(onClick, modifier.tvFocus(CircleShape), enabled, content = content)

@Composable
fun TvSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) =
    androidx.compose.material3.Switch(checked, onCheckedChange, modifier.tvFocus(RoundedCornerShape(50)), enabled = enabled)

@Composable
fun TvFilterChip(
    selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null, trailingIcon: @Composable (() -> Unit)? = null,
    colors: SelectableChipColors = androidx.compose.material3.FilterChipDefaults.filterChipColors(),
) = androidx.compose.material3.FilterChip(selected, onClick, label, modifier.tvFocus(RoundedCornerShape(8.dp)), enabled,
    leadingIcon = leadingIcon, trailingIcon = trailingIcon, colors = colors)
