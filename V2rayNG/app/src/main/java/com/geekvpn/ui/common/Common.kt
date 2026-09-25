package com.geekvpn.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.SpaceGrotesk
import com.v2ray.ang.R
import android.icu.text.DateFormat
import android.icu.util.ULocale
import com.geekvpn.connection.ServiceStatus
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** The app language's locale: it picks Persian or Latin digits. */
@Composable
fun appLocale(): Locale = LocalLocale.current.platformLocale

/** "۱۲٬۵۰۰" in Persian, "12,500" in English. */
fun formatNumber(value: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)

/**
 * A server timestamp as a short date: "۱۴۰۵/۰۷/۰۲" (Solar Hijri) in Persian,
 * the locale's own calendar otherwise. Null when [iso] does not parse.
 */
fun formatDate(iso: String?, locale: Locale): String? {
    val instant = ServiceStatus.parseInstant(iso) ?: return null
    val uLocale = if (locale.language == "fa") ULocale("fa_IR@calendar=persian") else ULocale.forLocale(locale)
    return DateFormat.getDateInstance(DateFormat.SHORT, uLocale).format(Date(instant.toEpochMilli()))
}

/** One decimal, dropped when it is zero: "۱۲٫۴", "۴۰". */
fun formatGib(value: Double, locale: Locale): String {
    val format = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }
    return format.format(value)
}

/**
 * Header of the tab screens (Home-Off.html): logo, "GeekVPN", and the wallet
 * chip with the balance. [onWallet] null hides the chip (guest mode).
 */
@Composable
fun GeekHeader(balance: Long?, onWallet: (() -> Unit)?) {
    val colors = Geek.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource(R.drawable.ic_geek_logo), contentDescription = null, modifier = Modifier.size(34.dp))
        Text(
            text = stringResource(R.string.geek_brand),
            style = Geek.type.body.copy(
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = (-0.5).sp,
                textDirection = TextDirection.Ltr,
            ),
            color = colors.onBackground,
        )
        // The brand word stays beside the logo; the wallet chip goes to the far end.
        Spacer(Modifier.weight(1f))
        if (onWallet != null) {
            WalletChip(balance, onWallet)
        }
    }
}

/** The balance on clear glass, opening the wallet (Home-Off.html, Shop.html). */
@Composable
fun WalletChip(balance: Long?, onClick: () -> Unit) {
    val colors = Geek.colors
    val locale = appLocale()
    GlassSurface(
        kind = GlassKind.Clear,
        shape = Geek.shapes.pill,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.height(44.dp).padding(start = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(colors.onBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GeekIcons.Wallet, contentDescription = null, tint = colors.action, modifier = Modifier.size(16.dp))
            }
            Text(
                text = stringResource(R.string.geek_home_balance, formatNumber(balance ?: 0, locale)),
                style = Geek.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.onBackground,
            )
        }
    }
}

/** A tab screen's page title on the backdrop ("سرویس‌های من"). */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier) {
        Text(text, style = Geek.type.pageTitle, color = Geek.colors.onBackground)
        if (subtitle != null) {
            Text(subtitle, style = Geek.type.caption, color = Geek.colors.onBackgroundMuted)
        }
    }
}

/** A label above a card group ("تنظیمات اتصال"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Geek.type.label,
        color = Geek.colors.onBackground,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

/** A milk-glass card holding [SettingRow]s separated by hairlines. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = Geek.colors.soft,
    )
}

/**
 * One row of Account.html: icon tile, title and hint, and either a chevron
 * (navigates) or a trailing control. The whole row is one focus target.
 */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    hint: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
    role: Role = Role.Button,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = Geek.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                if (stateDescription != null) this.stateDescription = stateDescription
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.soft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.onGlass, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Geek.type.row, color = colors.onGlass, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint != null) {
                Text(hint, style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onGlassMuted, maxLines = 2)
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(GeekIcons.ChevronStart, contentDescription = null, tint = colors.onGlassMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/** A small round-cornered icon button on the backdrop (Servers.html header). */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: GlassKind = GlassKind.Clear,
    enabled: Boolean = true,
) {
    val tint = if (kind == GlassKind.Milk) Geek.colors.onGlass else Geek.colors.onBackground
    GlassSurface(
        kind = kind,
        shape = Geek.shapes.tile,
        modifier = modifier
            .size(46.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.align(Alignment.Center).size(22.dp))
    }
}
