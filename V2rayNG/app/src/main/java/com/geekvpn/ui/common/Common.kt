package com.geekvpn.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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

/** [formatDate] with the time of day: "۱۴۰۵/۰۷/۰۲، ۱۴:۳۰". */
fun formatDateTime(iso: String?, locale: Locale): String? {
    val instant = ServiceStatus.parseInstant(iso) ?: return null
    val uLocale = if (locale.language == "fa") ULocale("fa_IR@calendar=persian") else ULocale.forLocale(locale)
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, uLocale).format(Date(instant.toEpochMilli()))
}

/**
 * A decimal for the big number face: the locale's digits, but "." and ","
 * as separators. The Persian decimal separator (U+066B) breaks the number
 * face's line in two at display sizes ("۴۲٫" / "۷"); "۴۲.۷" reads the same.
 */
fun formatDecimal(value: Double, locale: Locale, maxFractionDigits: Int): String {
    val symbols = java.text.DecimalFormatSymbols.getInstance(locale).apply {
        decimalSeparator = '.'
        groupingSeparator = ','
    }
    return java.text.DecimalFormat("#,##0.#", symbols).apply {
        maximumFractionDigits = maxFractionDigits
        minimumFractionDigits = 0
    }.format(value)
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
 * Header of the tab screens (Home-Off.dc.html): the mark and "GeekVPN", the
 * wallet chip with the balance at the far end, and the hairline under them.
 * [onWallet] null hides the chip (guest mode, and the tabs without one).
 */
@Composable
fun GeekHeader(balance: Long?, onWallet: (() -> Unit)?) {
    val colors = Geek.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(painterResource(R.drawable.ic_geek_logo), contentDescription = null, modifier = Modifier.size(width = 39.dp, height = 19.dp))
            Text(
                text = stringResource(R.string.geek_brand),
                style = Geek.type.body.copy(
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = (-0.5).sp,
                    textDirection = TextDirection.Ltr,
                ),
                color = colors.onBackground,
            )
            // The brand word stays beside the mark; the wallet chip goes to the far end.
            Spacer(Modifier.weight(1f))
            if (onWallet != null) {
                WalletChip(balance, onWallet)
            }
        }
        Hairline()
    }
}

/** The balance on a glass pill, opening the wallet (Home-Off.dc.html, Shop.dc.html). */
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
            modifier = Modifier.height(44.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(GeekIcons.Card, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(15.dp))
            Text(
                text = stringResource(R.string.geek_home_balance, formatNumber(balance ?: 0, locale)),
                style = Geek.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = colors.onBackground,
            )
        }
    }
}

/** The 1dp white line of the Swiss grid, between header and page and between rows. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = Geek.colors.hairline)
}

/**
 * The Latin micro label of Dark Swiss ("STATUS · OFF", "MY SERVICES"): Space
 * Grotesk 10, upper case, tracked. Decorative: the Persian title next to it
 * says the same, so TalkBack skips it.
 */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = Geek.type.eyebrow.copy(letterSpacing = 0.24.em),
        color = Geek.colors.onBackgroundMuted,
        maxLines = 1,
        modifier = modifier.clearAndSetSemantics {},
    )
}

/** A screen's title: the Latin [eyebrow] over the heavy Persian title, and a muted line under it. */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null, eyebrow: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (eyebrow != null) Eyebrow(eyebrow)
        Text(text, style = Geek.type.pageTitle, color = Geek.colors.onBackground)
        if (subtitle != null) {
            Text(subtitle, style = Geek.type.body, color = Geek.colors.onBackgroundMuted)
        }
    }
}

/**
 * A label above a group of rows ("تنظیمات اتصال"). With [eyebrow] it is drawn
 * as the design's Latin micro label ("CONNECTION") and [text] is what TalkBack
 * reads, as a heading.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, eyebrow: String? = null) {
    if (eyebrow != null) {
        Text(
            text = eyebrow.uppercase(Locale.ROOT),
            style = Geek.type.eyebrow.copy(letterSpacing = 0.24.em),
            color = Geek.colors.onBackgroundMuted,
            maxLines = 1,
            modifier = modifier.padding(top = 6.dp).clearAndSetSemantics {
                contentDescription = text
                heading()
            },
        )
        return
    }
    Text(
        text = text,
        style = Geek.type.label.copy(fontSize = 13.sp),
        color = Geek.colors.onBackgroundMuted,
        modifier = modifier.padding(top = 6.dp).semantics { heading() },
    )
}

/**
 * Rows of the Swiss grid (Account.dc.html): no card, just hairlines above,
 * between and below the rows. [SettingsDivider] separates them.
 */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Hairline()
        content()
        Hairline()
    }
}

@Composable
fun SettingsDivider() {
    Hairline()
}

/**
 * One row of Account.dc.html: title and hint, and either a chevron (navigates)
 * or a trailing control. The whole row is one focus target. [icon] is drawn
 * small and muted at the start, as the index letters of the design's rows.
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
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                if (stateDescription != null) this.stateDescription = stateDescription
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.onBackgroundMuted, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Geek.type.row, color = colors.onGlass, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint != null) {
                Text(hint, style = Geek.type.caption, color = colors.onGlassMuted, maxLines = 2)
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(GeekIcons.ChevronRow, contentDescription = null, tint = colors.onGlassMuted, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * A row of the Swiss index (Home-Off.dc.html): a two-letter Latin [code] in
 * the start column, the title (and a hint), and a muted value at the end.
 * Clickable when [onClick] is set; the row is one focus target either way.
 */
@Composable
fun IndexRow(
    code: String,
    title: String,
    value: String?,
    modifier: Modifier = Modifier,
    hint: String? = null,
    latinValue: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = Geek.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = code.uppercase(Locale.ROOT),
            style = Geek.type.eyebrow.copy(letterSpacing = 0.18.em),
            color = colors.onBackgroundMuted,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(30.dp).clearAndSetSemantics {},
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Geek.type.row.copy(fontSize = 16.sp), color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint != null) {
                Text(hint, style = Geek.type.caption, color = colors.onBackgroundMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = if (latinValue) Geek.type.numberSmall else Geek.type.caption.copy(fontSize = 13.sp),
                color = colors.onBackgroundMuted,
                maxLines = 1,
            )
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(GeekIcons.ChevronRow, contentDescription = null, tint = colors.onBackgroundMuted, modifier = Modifier.size(16.dp))
        }
    }
}

/** A round glass icon button on the backdrop (the back button of Servers.dc.html). */
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
        shape = Geek.shapes.pill,
        modifier = modifier
            .size(44.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.align(Alignment.Center).size(18.dp))
    }
}
