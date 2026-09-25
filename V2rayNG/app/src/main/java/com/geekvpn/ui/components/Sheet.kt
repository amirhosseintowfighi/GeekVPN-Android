package com.geekvpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek

/**
 * A milk-glass sheet from the bottom over a navy scrim (Wallet.html,
 * Deposit.html): handle, title and subtitle, a close button when
 * [closeLabel] is given, then [content], scrolling when it is taller than
 * most of the screen. Tapping the scrim calls [onDismiss].
 */
@Composable
fun BoxScope.GeekSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    closeLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Geek.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
    BoxWithConstraints(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
        val limit = maxHeight * 0.92f
        GlassSurface(
            kind = GlassKind.Milk,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = limit),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.checkboxBorder),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = Geek.type.sectionTitle, color = colors.onGlass)
                        if (subtitle != null) {
                            Text(subtitle, style = Geek.type.caption, color = colors.onGlassMuted)
                        }
                    }
                    if (closeLabel != null) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(colors.soft)
                                .clickable(role = Role.Button, onClickLabel = closeLabel, onClick = onDismiss)
                                .semantics { contentDescription = closeLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(GeekIcons.Close, contentDescription = null, tint = colors.link, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                content()
            }
        }
    }
}
