package com.geekvpn.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.ui.common.Eyebrow
import com.geekvpn.ui.common.Hairline
import com.geekvpn.ui.common.IndexRow
import com.geekvpn.ui.components.BackdropLayout
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.SpaceGrotesk
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R

/**
 * Main.dc.html: the mark and version over a hairline, the heavy hero line,
 * the three promises as an index, and the ways in at the foot.
 */
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
            Text(
                text = heroText(),
                style = Geek.type.display.copy(fontSize = 42.sp, lineHeight = 52.sp),
                color = Geek.colors.onBackground,
            )
            Column {
                Hairline()
                IndexRow(code = "01", title = stringResource(R.string.geek_login_chip_fast), value = null)
                Hairline()
                IndexRow(code = "02", title = stringResource(R.string.geek_login_chip_secure), value = null)
                Hairline()
                IndexRow(code = "03", title = stringResource(R.string.geek_login_chip_support), value = null)
                Hairline()
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
                    onClick = onUsername,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                GeekSecondaryButton(
                    text = stringResource(R.string.geek_login_create),
                    onClick = onCreateAccount,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.geek_login_guest),
                style = Geek.type.caption.copy(fontSize = 13.sp, textDecoration = TextDecoration.Underline),
                color = Geek.colors.onBackgroundMuted,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(Geek.shapes.checkbox)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onGuest)
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        },
    )
}

/** "اینترنت آزاد، / سریع و امن / با یک لمس." with the last line in the text blue. */
@Composable
private fun heroText(): AnnotatedString {
    val hero = stringResource(R.string.geek_login_hero)
    val accent = stringResource(R.string.geek_login_hero_accent)
    val blue = Geek.colors.logoBlue
    return buildAnnotatedString {
        append(hero)
        append("\n")
        withStyle(SpanStyle(color = blue)) { append(accent) }
    }
}

/**
 * The login screens' frame (Main.dc.html): the mark, "GeekVPN" and the
 * version over a hairline, then [hero], and [sheet] at the foot of the
 * screen. Everything scrolls together on a short screen, so a landscape TV
 * keeps its buttons.
 */
@Composable
internal fun LoginScaffold(
    hero: @Composable ColumnScope.() -> Unit,
    sheet: @Composable ColumnScope.() -> Unit,
) {
    GeekBackdrop(layout = BackdropLayout.Column) {
        // imePadding: a field in the sheet rides above the keyboard.
        BoxWithConstraints(Modifier.fillMaxSize().imePadding().statusBarsPadding().navigationBarsPadding()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    BrandRow()
                    Hairline()
                    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(22.dp), content = hero)
                }
                Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = sheet)
            }
        }
    }
}

/** The mark, "GeekVPN" and "CLIENT · V1.0" (Main.dc.html's first line). */
@Composable
private fun BrandRow() {
    Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.ic_geek_logo), contentDescription = null, modifier = Modifier.size(width = 44.dp, height = 22.dp))
        Text(
            text = stringResource(R.string.geek_brand),
            style = Geek.type.body.copy(
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = (-0.5).sp,
                textDirection = TextDirection.Ltr,
            ),
            color = Geek.colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        Eyebrow(stringResource(R.string.geek_eyebrow_client, BuildConfig.VERSION_NAME))
    }
}

/** The mark in a glass circle with a faint disc, as the connect button draws it. */
@Composable
internal fun HeroLogo(frame: Dp, logo: Dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(frame), contentAlignment = Alignment.Center) {
        GlassSurface(kind = GlassKind.Milk, shape = CircleShape, modifier = Modifier.size(frame)) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(frame * 0.6f)
                    .clip(CircleShape)
                    .background(Geek.colors.onBackground.copy(alpha = 0.10f))
                    .border(1.dp, Geek.colors.onBackground.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_geek_logo),
                    contentDescription = null,
                    modifier = Modifier.size(width = logo * 0.6f, height = logo * 0.3f),
                )
            }
        }
    }
}
