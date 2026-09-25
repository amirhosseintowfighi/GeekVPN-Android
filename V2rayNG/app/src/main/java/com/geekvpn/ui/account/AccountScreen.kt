package com.geekvpn.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.auth.Session
import com.geekvpn.connection.RouteMode
import com.geekvpn.ui.common.SectionLabel
import com.geekvpn.ui.common.SettingRow
import com.geekvpn.ui.common.SettingsCard
import com.geekvpn.ui.common.SettingsDivider
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatNumber
import com.geekvpn.ui.components.GeekSwitch
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.home.label
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
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
    fun onProfiles()
    fun onSupport()
    fun onAbout()
    fun onLogin()
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
    onAutoUpdate: (Boolean) -> Unit,
    onTheme: (ThemeChoice) -> Unit,
    onAskLogout: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    val colors = Geek.colors
    val signedIn = state.session as? Session.SignedIn
    val hasBot = BuildConfig.BOT_USERNAME.isNotEmpty()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Identity(state.session)

        if (signedIn != null) {
            WalletCard(state.balance, actions)
        }

        SectionLabel(stringResource(R.string.geek_account_connection))
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
                icon = GeekIcons.Sliders,
                title = stringResource(R.string.geek_account_advanced),
                hint = stringResource(R.string.geek_account_advanced_hint),
                onClick = actions::onAdvanced,
            )
            SettingsDivider()
            SettingRow(
                icon = GeekIcons.Folder,
                title = stringResource(R.string.geek_account_profiles),
                hint = stringResource(R.string.geek_account_profiles_hint),
                onClick = actions::onProfiles,
            )
        }

        SectionLabel(stringResource(R.string.geek_account_appearance))
        ThemeSelector(state.theme, onTheme)

        SectionLabel(stringResource(R.string.geek_account_support))
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
            SettingRow(
                icon = GeekIcons.Download,
                title = stringResource(R.string.geek_account_about),
                hint = stringResource(R.string.geek_account_version, BuildConfig.VERSION_NAME),
                onClick = actions::onAbout,
            )
        }

        GlassSurface(
            kind = GlassKind.Clear,
            shape = Geek.shapes.button,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(role = Role.Button) { if (signedIn == null) actions.onLogin() else onAskLogout(true) },
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(GeekIcons.Logout, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(if (signedIn != null) R.string.geek_account_logout else R.string.geek_account_login),
                    style = Geek.type.button.copy(fontSize = 14.sp),
                    color = colors.onBackground,
                )
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassSurface(kind = GlassKind.Milk, shape = RoundedCornerShape(20.dp), modifier = Modifier.size(64.dp)) {
            Icon(GeekIcons.User, contentDescription = null, tint = colors.onGlass, modifier = Modifier.align(Alignment.Center).size(28.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val name = when (session) {
                is Session.SignedIn -> session.user.displayName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.geek_account_user_fallback, formatNumber(session.user.telegramId ?: 0, locale))
                else -> stringResource(R.string.geek_account_guest)
            }
            Text(name, style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (session is Session.SignedIn) {
                GlassSurface(kind = GlassKind.Clear, shape = Geek.shapes.badge) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(GeekIcons.Telegram, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.geek_account_telegram_connected), style = Geek.type.micro, color = colors.onBackground)
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletCard(balance: Long?, actions: AccountActions) {
    val colors = Geek.colors
    val locale = appLocale()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(Geek.shapes.card)
            .background(colors.action)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(GeekIcons.Wallet, contentDescription = null, tint = colors.onAction.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.geek_account_wallet), style = Geek.type.caption, color = colors.onAction.copy(alpha = 0.8f))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(formatNumber(balance ?: 0, locale), style = Geek.type.pageTitle, color = colors.onAction)
                Text(stringResource(R.string.geek_account_toman), style = Geek.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.onAction, modifier = Modifier.padding(bottom = 6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WalletButton(
                    text = stringResource(R.string.geek_account_topup),
                    icon = GeekIcons.Plus,
                    primary = true,
                    onClick = actions::onWallet,
                    modifier = Modifier.weight(2f),
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
            .height(46.dp)
            .clip(Geek.shapes.button)
            .background(if (primary) colors.logoBlue else colors.onAction.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        val fg = if (primary) colors.action else colors.onAction
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, style = Geek.type.caption.copy(fontWeight = FontWeight.Bold), color = fg, maxLines = 1)
    }
}

@Composable
private fun ThemeSelector(current: ThemeChoice, onSelect: (ThemeChoice) -> Unit) {
    val colors = Geek.colors
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemeChoice.entries.forEach { choice ->
                val selected = choice == current
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(Geek.shapes.button)
                        .background(if (selected) colors.action else colors.softButton)
                        .selectable(selected = selected, role = Role.RadioButton) { onSelect(choice) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    val fg = if (selected) colors.onAction else colors.onGlass
                    Icon(choice.icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                    Text(stringResource(choice.label), style = Geek.type.caption.copy(fontWeight = FontWeight.Bold), color = fg)
                }
            }
        }
    }
}

private val ThemeChoice.icon: ImageVector
    get() = when (this) {
        ThemeChoice.Auto -> GeekIcons.ThemeAuto
        ThemeChoice.Light -> GeekIcons.Sun
        ThemeChoice.Dark -> GeekIcons.Moon
    }

private val ThemeChoice.label: Int
    get() = when (this) {
        ThemeChoice.Auto -> R.string.geek_theme_auto
        ThemeChoice.Light -> R.string.geek_theme_light
        ThemeChoice.Dark -> R.string.geek_theme_dark
    }
