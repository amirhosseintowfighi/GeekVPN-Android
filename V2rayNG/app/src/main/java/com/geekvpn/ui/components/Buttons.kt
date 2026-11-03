package com.geekvpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.ui.theme.Geek

/**
 * The white call-to-action of Dark Swiss ("ورود با تلگرام"): a 56dp pill, its
 * label and icon centred in navy, with a deep shadow under it.
 */
@Composable
fun GeekPrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Geek.colors
    val shape = Geek.shapes.primaryButton
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(14.dp, shape, ambientColor = colors.actionShadow, spotColor = colors.actionShadow)
            .clip(shape)
            .background(colors.action)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = Geek.type.button.copy(fontSize = 17.sp),
            color = colors.onAction,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(icon, contentDescription = null, tint = colors.onAction, modifier = Modifier.size(20.dp))
    }
}

/** The glass pill ("ساخت حساب", "نام کاربری"), 52dp, white text. */
@Composable
fun GeekSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = Geek.colors
    FlatButton(
        text = text,
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        background = colors.softButton,
        content = colors.onGlass,
        border = colors.clearGlassBorder,
    )
}

/** The glass pill in the danger colour ("قطع اتصال", "خروج از حساب"), 52dp. */
@Composable
fun GeekDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = Geek.colors
    FlatButton(
        text = text,
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        background = colors.softButton,
        content = colors.danger,
        border = colors.clearGlassBorder,
    )
}

@Composable
private fun FlatButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    background: Color,
    content: Color,
    border: Color?,
) {
    val shape = Geek.shapes.button
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.5.dp, border, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(19.dp))
        }
        Text(
            text = text,
            style = Geek.type.button.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
