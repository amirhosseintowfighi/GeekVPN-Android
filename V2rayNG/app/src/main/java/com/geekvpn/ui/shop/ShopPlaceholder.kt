package com.geekvpn.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geekvpn.ui.common.PageTitle
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.R

/**
 * The shop tab until phase 6 builds Shop.html: points at the bot, where
 * buying and renewing already work. [onOpenBot] null when no bot is configured.
 */
@Composable
fun ShopPlaceholder(onOpenBot: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageTitle(stringResource(R.string.geek_shop_title))
        GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.geek_shop_soon), style = Geek.type.body, color = Geek.colors.onGlass)
                if (onOpenBot != null) {
                    GeekPrimaryButton(
                        text = stringResource(R.string.geek_shop_open_bot),
                        icon = GeekIcons.Telegram,
                        onClick = onOpenBot,
                    )
                }
            }
        }
    }
}
