package com.geekvpn.ui.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.geekvpn.ui.components.BackdropLayout
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.SpaceGrotesk
import com.v2ray.ang.R

/** Main.html: the brand hero on the backdrop, and the ways in on a milk-glass sheet. */
@Composable
fun LoginScreen(
    busy: Boolean,
    onTelegram: () -> Unit,
    onUsername: () -> Unit,
    onCreateAccount: () -> Unit,
    onGuest: () -> Unit,
) {
    LoginScaffold(
        hero = {
            HeroLogo(frame = 280.dp, logo = 170.dp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BrandWord()
                Text(
                    text = stringResource(R.string.geek_login_tagline),
                    style = Geek.type.body.copy(fontSize = 16.sp, lineHeight = 28.8.sp),
                    color = Geek.colors.onBackground.copy(alpha = 0.92f),
                )
                FeatureChips()
            }
        },
        sheet = {
            GeekPrimaryButton(
                text = stringResource(R.string.geek_login_telegram),
                icon = GeekIcons.Telegram,
                onClick = onTelegram,
                enabled = !busy,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GeekSecondaryButton(
                    text = stringResource(R.string.geek_login_username),
                    icon = GeekIcons.User,
                    onClick = onUsername,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                GeekSecondaryButton(
                    text = stringResource(R.string.geek_login_create),
                    icon = GeekIcons.Plus,
                    onClick = onCreateAccount,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.geek_login_guest),
                style = Geek.type.caption.copy(fontWeight = FontWeight.Bold),
                color = Geek.colors.link,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(Geek.shapes.checkbox)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onGuest)
                    .padding(8.dp),
            )
        },
    )
}

/**
 * The login screens' frame: backdrop with the glow behind the hero, the hero
 * content from the top (scrolling on short screens rather than sliding under
 * the sheet), and a milk-glass sheet on the bottom edge.
 */
@Composable
internal fun LoginScaffold(
    hero: @Composable ColumnScope.() -> Unit,
    sheet: @Composable ColumnScope.() -> Unit,
) {
    GeekBackdrop(layout = BackdropLayout.Hero) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
                content = hero,
            )
            GlassSurface(
                kind = GlassKind.Milk,
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = sheet,
                )
            }
        }
    }
}

/** The logo inside its glass square and dashed outer ring (Main.html's 342x280 hero). */
@Composable
internal fun HeroLogo(frame: Dp, logo: Dp, modifier: Modifier = Modifier) {
    // Proportions from the design: frame 280 holds a 220 square (rx 66) and a 260 ring (rx 78).
    val scale = frame.value / 280f
    Box(modifier.fillMaxWidth().height(frame), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(frame)) {
            val inner = (220 * scale).dp.toPx()
            val outer = (260 * scale).dp.toPx()
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                topLeft = Offset((size.width - inner) / 2, (size.height - inner) / 2),
                size = Size(inner, inner),
                cornerRadius = CornerRadius((66 * scale).dp.toPx()),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = Offset((size.width - inner) / 2, (size.height - inner) / 2),
                size = Size(inner, inner),
                cornerRadius = CornerRadius((66 * scale).dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset((size.width - outer) / 2, (size.height - outer) / 2),
                size = Size(outer, outer),
                cornerRadius = CornerRadius((78 * scale).dp.toPx()),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 8.dp.toPx())),
                ),
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_geek_logo),
            contentDescription = null,
            modifier = Modifier.size(logo),
        )
    }
}

/** "GeekVPN" in Space Grotesk. Latin, so left-to-right, but placed at the start edge. */
@Composable
private fun BrandWord() {
    Text(
        text = stringResource(R.string.geek_brand),
        style = Geek.type.body.copy(
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 46.sp,
            lineHeight = 46.sp,
            letterSpacing = (-1.5).sp,
            textDirection = TextDirection.Ltr,
        ),
        color = Geek.colors.onBackground,
    )
}

@Composable
private fun FeatureChips() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeatureChip(GeekIcons.Bolt, stringResource(R.string.geek_login_chip_fast))
        FeatureChip(GeekIcons.Lock, stringResource(R.string.geek_login_chip_secure))
        FeatureChip(GeekIcons.Telegram, stringResource(R.string.geek_login_chip_support))
    }
}

@Composable
internal fun FeatureChip(icon: ImageVector, text: String) {
    GlassSurface(kind = GlassKind.Clear, shape = RoundedCornerShape(11.dp)) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Geek.colors.onBackground, modifier = Modifier.size(15.dp))
            Text(
                text = text,
                style = Geek.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.em),
                color = Geek.colors.onBackground,
            )
        }
    }
}
