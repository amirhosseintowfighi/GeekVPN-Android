package com.geekvpn.ui.promo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.api.AppPromo
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatDate
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.R

/**
 * The operator's offer ([com.geekvpn.promo.Promos]): title, a line, the coupon
 * code and the last day. The tap opens the shop with the code filled in.
 */
@Composable
fun PromoBanner(promo: AppPromo, onOpen: () -> Unit, onClose: () -> Unit) {
    val colors = Geek.colors
    val locale = appLocale()
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.warningSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GeekIcons.Gift, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(promo.titleFa.orEmpty(), style = Geek.type.row, color = colors.onGlass)
                promo.bodyFa?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onGlassMuted)
                }
                val code = promo.couponCode
                val until = formatDate(promo.until?.let { day -> "${day}T12:00:00Z" }, locale)
                if (code != null || until != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (code != null) {
                            Text(
                                code,
                                style = Geek.type.caption.copy(textDirection = TextDirection.Ltr),
                                color = colors.onGlass,
                                modifier = Modifier
                                    .clip(Geek.shapes.badge)
                                    .background(colors.soft)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        if (until != null) {
                            Text(stringResource(R.string.geek_promo_until, until), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onGlassMuted)
                        }
                    }
                }
            }
            val close = stringResource(R.string.geek_promo_dismiss_description)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = close },
                contentAlignment = Alignment.Center,
            ) {
                Icon(GeekIcons.Close, contentDescription = null, tint = colors.onGlassMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}
