package com.geekvpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek

/**
 * The pill switch of Dark Swiss (44x26, knob 20). On: a white track with the
 * navy knob at the start, as Foundations.dc.html draws it; off: a faint glass
 * track with a white knob at the end. Its touch target is widened to 48dp.
 *
 * Pass [onCheckedChange] = null when a surrounding row owns the toggle action,
 * so the row and the switch do not become two focus targets.
 */
@Composable
fun GeekSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Geek.colors
    val track by animateColorAsState(if (checked) colors.action else colors.track, label = "track")
    val knob by animateColorAsState(if (checked) colors.actionAccent else colors.onBackground, label = "knob")
    val edge by animateColorAsState(if (checked) colors.action else colors.checkboxBorder, label = "edge")
    val bias by animateFloatAsState(if (checked) -1f else 1f, label = "bias")
    val toggle = if (onCheckedChange != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Box(modifier = modifier.then(toggle), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(Geek.shapes.switchTrack)
                .background(track)
                .border(1.dp, edge, Geek.shapes.switchTrack)
                .padding(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(BiasAlignment(bias, 0f))
                    .size(20.dp)
                    .clip(Geek.shapes.switchKnob)
                    .background(knob),
            )
        }
    }
}

/** The design's check box: action colour with an accent tick, or an empty rounded square. */
@Composable
fun GeekCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Geek.colors
    val shape = Geek.shapes.checkbox
    val toggle = if (onCheckedChange != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Box(modifier = modifier.then(toggle), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(shape)
                .then(
                    if (checked) Modifier.background(colors.action)
                    else Modifier.border(2.dp, colors.checkboxBorder, shape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(GeekIcons.Check, contentDescription = null, tint = colors.actionAccent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * The two-letter mark at the start of a server row (Servers.dc.html): the
 * country code in tracked Space Grotesk, white when it marks the selected
 * server. Anything that is not a country code is shown as written, cut to
 * four characters. Decorative; the row it sits in names the server.
 */
@Composable
fun CountryBadge(
    code: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val colors = Geek.colors
    Box(modifier = modifier.width(34.dp), contentAlignment = Alignment.CenterEnd) {
        Text(
            text = code.uppercase().take(4),
            style = Geek.type.eyebrow.copy(letterSpacing = 0.18.em, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) colors.onBackground else colors.onBackgroundMuted,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}
