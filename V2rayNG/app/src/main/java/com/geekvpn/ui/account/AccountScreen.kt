package com.geekvpn.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.auth.Session
import com.geekvpn.connection.RouteMode
import com.geekvpn.ui.common.PageTitle
import com.geekvpn.ui.common.SectionLabel
import com.geekvpn.ui.common.SettingRow
import com.geekvpn.ui.common.SettingsCard
import com.geekvpn.ui.common.SettingsDivider
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatNumber
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSwitch
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.home.label
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.update.updateHint
import com.geekvpn.update.UpdateState
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R

/** Where Account's rows lead; the activity owns the navigation. */
interface AccountActions {
    fun onWallet()
    fun onServers()
    fun onRoute()

    /** The clean-IP scanner, offered only where it applies. */
    fun onCleanIp()
    fun onAdvanced()
    fun onSupport()
    fun onAbout()
    fun onLogin()

    /** Opens the update sheet when a version waits, else checks now. */
    fun onUpdate()

    /** "گزارش مشکل": a support ticket with a technical report attached. */
    fun onReport()

    /** "تیکت‌های من": the customer's tickets and support's answers. */
    fun onTickets()

    /** "اتصال خودکار و Kill Switch". */
    fun onAutoConnect()

    /** "تست سرعت". */
    fun onSpeedTest()

    /** "عیب‌یاب اتصال". */
    fun onDiagnose() {}

    /** "دعوت از دوستان"; signed in only. */
    fun onReferral()

    /** "وضعیت سرورها", the bot's status list; signed in only. */
    fun onServerStatus() {}

    /** "سؤالات متداول", the bot's FAQ; signed in only. */
    fun onFaq() {}

    /** "اعلان‌ها", the bot's notification switches; signed in only. */
    fun onNotifications() {}

    /** "مصرف روزانه". */
    fun onUsage()
}

@Composable
fun AccountScreen(
    state: AccountUiState,
    route: RouteMode,
    autoServer: Boolean,
    logoutAsked: Boolean,
    actions: AccountActions,
    /** The selected config is a CDN-fronted direct one: offer the scanner. */
    showCleanIp: Boolean = false,
    /** Where the self-update stands; null in builds that cannot update themselves. */
    update: UpdateState? = null,
    onAutoUpdate: (Boolean) -> Unit,
    onAskLogout: (Boolean) -> Unit,
    onLogout: () -> Unit,
    /** "قفل برنامه". */
    onAppLock: (Boolean) -> Unit = {},
) {
    val colors = Geek.colors
    val signedIn = state.session as? Session.SignedIn
    val hasBot = BuildConfig.BOT_USERNAME.isNotEmpty()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PageTitle(stringResource(R.string.geek_account_page_title), eyebrow = stringResource(R.string.geek_eyebrow_account))
        Identity(state.session)

        if (signedIn != null) {
            WalletCard(state.balance, actions)
            SettingsCard {
                SettingRow(
                    icon = GeekIcons.Gift,
                    title = stringResource(R.string.geek_referral_title),
                    hint = stringResource(R.string.geek_referral_entry_hint),
                    onClick = actions::onReferral,
                )
            }
        }

        SectionLabel(stringResource(R.string.geek_account_connection), eyebrow = stringResource(R.string.geek_eyebrow_connection))
        SettingsCard {
            if (signedIn != null) {
                SettingRow(
                    icon = GeekIcons.Refresh,
                    title = stringResource(R.string.geek_account_auto_update),
                    hint = stringResource(R.string.geek_account_auto_update_hint),
                    onClick = { onAutoUpdate(!state.autoUpdate) },
                    role = Role.Switch,
                ) { GeekSwitch(checked = state.autoUpdate, onCheckedChange = null) }
                SettingsDivider()
            }
            SettingRow(
                icon = GeekIcons.Globe,
                title = stringResource(R.string.geek_account_server),
                hint = stringResource(if (autoServer) R.string.geek_home_server_auto else R.string.geek_home_server_manual),
                onClick = actions::onServers,
            )
            SettingsDivider()
            SettingRow(
                icon = GeekIcons.Route,
                title = stringResource(R.string.geek_account_route),
                hint = stringResource(route.label),
                onClick = actions::onRoute,
            )
            if (showCleanIp) {
                SettingsDivider()
                SettingRow(
                    icon = GeekIcons.Sparkle,
                    title = stringResource(R.string.geek_scan_title),
                    hint = stringResource(R.string.geek_scan_entry_hint),
                    onClick = actions::onCleanIp,
                )
            }
            SettingsDivider()
            SettingRow(
                icon = GeekIcons.ArrowDown,
                title = stringResource(R.string.geek_usage_title),
                hint = stringResource(R.string.geek_usage_entry_hint),
                onClick = actions::onUsage,
            )
            SettingsDivider()
            SettingRow(
                icon = GeekIcons.Search,
                title = stringResource(R.string.geek_diag_title),
                hint = stringResource(R.string.geek_diag_entry_hint),
                onClick = actions::onDiagnose,
            )
            SettingsDivider()
            SettingRow(
                icon = GeekIcons.Gauge,
                title = stringResource(R.string.geek_speed_title),
                hint = stringResource(R.string.geek_speed_entry_hint),
                onClick = actions::onSpeedTest,
            )
            SettingsDivider()
            SettingRow(
                icon = GeekIcons.Shield,
                title = stringResource(R.string.geek_auto_entry),
                hint = stringResource(R.string.geek_auto_entry_hint),
                onClick = actions::onAutoConnect,
            )
            SettingsDivider()
            SettingRow(
                icon = GeekIcons.Sliders,
                title = stringResource(R.string.geek_account_advanced),
                hint = stringResource(R.string.geek_account_advanced_hint),
                onClick = actions::onAdvanced,
            )
        }

        SectionLabel(stringResource(R.string.geek_account_security), eyebrow = stringResource(R.string.geek_eyebrow_security))
        SettingsCard {
            if (signedIn != null) {
                SettingRow(
                    icon = GeekIcons.Sparkle,
                    title = stringResource(R.string.geek_notify_title),
                    hint = stringResource(R.string.geek_notify_entry_hint),
                    onClick = actions::onNotifications,
                )
                SettingsDivider()
            }
            SettingRow(
                icon = GeekIcons.Lock,
                title = stringResource(R.string.geek_lock_setting),
                hint = stringResource(R.string.geek_lock_setting_hint),
                onClick = { onAppLock(!state.appLock) },
                role = Role.Switch,
            ) { GeekSwitch(checked = state.appLock, onCheckedChange = null) }
        }

        SectionLabel(stringResource(R.string.geek_account_support), eyebrow = stringResource(R.string.geek_eyebrow_support))
        SettingsCard {
            if (hasBot) {
                SettingRow(
                    icon = GeekIcons.Headset,
                    title = stringResource(R.string.geek_account_support_telegram),
                    hint = stringResource(R.string.geek_account_support_hint),
                    onClick = actions::onSupport,
                )
                SettingsDivider()
            }
            if (signedIn != null) {
                SettingRow(
                    icon = GeekIcons.Globe,
                    title = stringResource(R.string.geek_status_title),
                    hint = stringResource(R.string.geek_status_entry_hint),
                    onClick = actions::onServerStatus,
                )
                SettingsDivider()
                SettingRow(
                    icon = GeekIcons.Headset,
                    title = stringResource(R.string.geek_faq_title),
                    hint = stringResource(R.string.geek_faq_entry_hint),
                    onClick = actions::onFaq,
                )
                SettingsDivider()
                SettingRow(
                    icon = GeekIcons.List,
                    title = stringResource(R.string.geek_tickets_title),
                    hint = if (state.unreadTickets > 0) {
                        pluralStringResource(R.plurals.geek_tickets_unread_hint, state.unreadTickets, state.unreadTickets)
                    } else {
                        stringResource(R.string.geek_tickets_entry_hint)
                    },
                    onClick = actions::onTickets,
                )
                SettingsDivider()
            }
            SettingRow(
                icon = GeekIcons.Receipt,
                title = stringResource(R.string.geek_report_title),
                hint = stringResource(R.string.geek_report_entry_hint),
                onClick = actions::onReport,
            )
            SettingsDivider()
            if (update != null) {
                SettingRow(
                    icon = GeekIcons.Refresh,
                    title = stringResource(R.string.geek_update_title),
                    hint = updateHint(update),
                    onClick = actions::onUpdate,
                )
                SettingsDivider()
            }
            SettingRow(
                icon = GeekIcons.Download,
                title = stringResource(R.string.geek_account_about),
                hint = stringResource(R.string.geek_account_version, BuildConfig.VERSION_NAME),
                onClick = actions::onAbout,
            )
        }

        if (signedIn == null) {
            GeekPrimaryButton(text = stringResource(R.string.geek_account_login), icon = GeekIcons.Telegram, onClick = actions::onLogin)
        } else {
            // Account.dc.html: sign-out is a plain red line at the end, not a button that competes.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onAskLogout(true) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(GeekIcons.Logout, contentDescription = null, tint = colors.danger, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.geek_account_logout), style = Geek.type.button.copy(fontSize = 15.sp), color = colors.danger)
            }
        }
    }

    if (logoutAsked) {
        AlertDialog(
            onDismissRequest = { onAskLogout(false) },
            title = { Text(stringResource(R.string.geek_account_logout_title), style = Geek.type.sectionTitle) },
            text = { Text(stringResource(R.string.geek_account_logout_text), style = Geek.type.body) },
            confirmButton = {
                TextButton(onClick = onLogout) {
                    Text(stringResource(R.string.geek_account_logout_confirm), color = colors.danger, style = Geek.type.button)
                }
            },
            dismissButton = {
                TextButton(onClick = { onAskLogout(false) }) {
                    Text(stringResource(R.string.geek_account_cancel), style = Geek.type.button)
                }
            },
        )
    }
}

@Composable
private fun Identity(session: Session) {
    val colors = Geek.colors
    val locale = appLocale()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(colors.softButton).border(1.dp, colors.checkboxBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(GeekIcons.User, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            val name = when (session) {
                is Session.SignedIn -> session.user.displayName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.geek_account_user_fallback, formatNumber(session.user.telegramId ?: 0, locale))
                else -> stringResource(R.string.geek_account_guest)
            }
            Text(name, style = Geek.type.row.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold), color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (session is Session.SignedIn) {
                Text(stringResource(R.string.geek_account_telegram_connected), style = Geek.type.caption, color = colors.onBackgroundMuted)
            }
        }
    }
}

/** Account.dc.html's wallet: a glass card, the balance in heavy type, top-up in white. */
@Composable
private fun WalletCard(balance: Long?, actions: AccountActions) {
    val colors = Geek.colors
    val locale = appLocale()
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.geek_eyebrow_wallet).uppercase(java.util.Locale.ROOT),
                style = Geek.type.eyebrow.copy(fontSize = 9.sp),
                color = colors.onGlassMuted,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Text(formatNumber(balance ?: 0, locale), style = Geek.type.pageTitle.copy(fontSize = 32.sp, fontWeight = FontWeight.Black), color = colors.onGlass)
                Text(stringResource(R.string.geek_account_toman), style = Geek.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = colors.onGlassMuted, modifier = Modifier.padding(bottom = 6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WalletButton(
                    text = stringResource(R.string.geek_account_topup),
                    icon = null,
                    primary = true,
                    onClick = actions::onWallet,
                    modifier = Modifier.weight(1f),
                )
                WalletButton(
                    text = stringResource(R.string.geek_account_transactions),
                    icon = null,
                    primary = false,
                    onClick = actions::onWallet,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WalletButton(text: String, icon: ImageVector?, primary: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = Geek.colors
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(Geek.shapes.pill)
            .then(
                if (primary) Modifier.background(colors.action)
                else Modifier.background(colors.softButton).border(1.5.dp, colors.clearGlassBorder, Geek.shapes.pill),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        val fg = if (primary) colors.onAction else colors.onGlass
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, style = Geek.type.label, color = fg, maxLines = 1)
    }
}
