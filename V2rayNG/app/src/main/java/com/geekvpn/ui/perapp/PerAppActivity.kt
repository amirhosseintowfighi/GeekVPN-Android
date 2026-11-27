package com.geekvpn.ui.perapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.geekvpn.perapp.AppProfile
import com.geekvpn.perapp.AppProfileList
import com.geekvpn.perapp.IranianApps
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.SettingsDivider
import com.geekvpn.ui.components.ChoicePill
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekCheckbox
import com.geekvpn.ui.components.GeekSwitch
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.perappproxy.PerAppProxyViewModel
import com.v2ray.ang.util.AppIconFetcher
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Per-app proxy ("تونل تفکیکی برنامه‌ها") in GeekVPN's design, on v2rayNG's own
 * [PerAppProxyViewModel]: the same stored list and the same restart signal,
 * only the screen is GeekVPN's.
 */
class PerAppActivity : BaseComponentActivity() {

    private val viewModel: PerAppProxyViewModel by viewModels()
    private val profilesModel: AppProfilesViewModel by viewModels()

    /** «ذخیره به‌عنوان پروفایل»'s name dialog is open. */
    private var saveAsked by mutableStateOf(false)

    /** The profile whose removal is being confirmed. */
    private var removeAsked by mutableStateOf<AppProfile?>(null)

    /** The "Iranian apps direct" preset replaces the selection, so it asks first. */
    private var iranianAsked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadApps(this)
    }

    @Composable
    override fun ScreenContent() {
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val loading by viewModel.isLoading.collectAsStateWithLifecycle()
        val selected by viewModel.blacklist.collectAsStateWithLifecycle()
        val enabled by viewModel.perAppProxyEnabled.collectAsStateWithLifecycle()
        val bypass by viewModel.bypassApps.collectAsStateWithLifecycle()
        val profiles by profilesModel.profiles.collectAsStateWithLifecycle()
        GeekTheme {
            GeekBackdrop {
                PerAppScreen(
                    apps = apps,
                    loading = loading,
                    selected = selected,
                    enabled = enabled,
                    bypass = bypass,
                    profiles = profiles,
                    actions = object : PerAppActions {
                        override fun onBack() = finish()
                        override fun onEnabled(on: Boolean) = viewModel.setPerAppProxyEnabled(on)
                        override fun onBypass(on: Boolean) = viewModel.setBypassAppsEnabled(on)
                        override fun onToggle(packageName: String) = viewModel.toggle(packageName)
                        override fun onSearch(query: String) = viewModel.filterApps(query)
                        override fun onSelectAll() = viewModel.selectAll()
                        override fun onInvert() = viewModel.invertSelection()
                        override fun onAuto() = viewModel.selectProxyAppAuto(this@PerAppActivity)
                        override fun onIranianApps() {
                            iranianAsked = true
                        }
                        override fun onImport() = viewModel.importProxyApp(Utils.getClipboard(applicationContext), this@PerAppActivity)
                        override fun onExport() {
                            Utils.setClipboard(applicationContext, viewModel.exportProxyApp())
                            toastSuccess(R.string.toast_success)
                        }
                        override fun onApplyProfile(profile: AppProfile) = applyProfile(profile)
                        override fun onSaveProfile() {
                            saveAsked = true
                        }
                        override fun onRemoveProfile(profile: AppProfile) {
                            removeAsked = profile
                        }
                    },
                )
                if (saveAsked) {
                    SaveProfileDialog(
                        onSave = { name ->
                            if (profilesModel.save(name, viewModel.bypassApps.value, viewModel.blacklist.value)) {
                                saveAsked = false
                                Toast.makeText(this@PerAppActivity, R.string.geek_perapp_profile_saved, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@PerAppActivity, R.string.geek_perapp_profile_full, Toast.LENGTH_LONG).show()
                            }
                        },
                        onDismiss = { saveAsked = false },
                    )
                }
                removeAsked?.let { profile ->
                    RemoveProfileDialog(
                        name = profile.name,
                        onConfirm = {
                            removeAsked = null
                            profilesModel.remove(profile.id)
                        },
                        onDismiss = { removeAsked = null },
                    )
                }
                if (iranianAsked) {
                    IranianAppsDialog(
                        onConfirm = {
                            iranianAsked = false
                            applyIranianApps()
                        },
                        onDismiss = { iranianAsked = false },
                    )
                }
            }
        }
    }

    /**
     * A saved profile's mode and list, through v2rayNG's own toggles as
     * [applyIranianApps] does. Packages no longer installed stay in the list,
     * as v2rayNG keeps them; they simply match nothing.
     */
    private fun applyProfile(profile: AppProfile) {
        viewModel.setBypassAppsEnabled(profile.bypass)
        AppProfileList.toggles(viewModel.blacklist.value, profile.packages.toSet()).forEach(viewModel::toggle)
        viewModel.setPerAppProxyEnabled(true)
        Toast.makeText(this, getString(R.string.geek_perapp_profile_applied, profile.name), Toast.LENGTH_SHORT).show()
    }

    /**
     * Bypass mode with exactly the installed Iranian apps selected, through
     * v2rayNG's own toggles so its stored list and restart signal stay the
     * single source.
     */
    private fun applyIranianApps() {
        lifecycleScope.launch {
            val installed = try {
                AppManagerUtil.loadNetworkAppList(applicationContext).map { it.packageName }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Per-app: listing apps for the Iranian preset failed", e)
                emptyList()
            }
            val wanted = IranianApps.select(installed, packageName)
            if (wanted.isEmpty()) {
                Toast.makeText(this@PerAppActivity, R.string.geek_perapp_iran_none, Toast.LENGTH_LONG).show()
                return@launch
            }
            val current = viewModel.blacklist.value
            viewModel.setBypassAppsEnabled(true)
            ((current - wanted) + (wanted - current)).forEach(viewModel::toggle)
            viewModel.setPerAppProxyEnabled(true)
            Toast.makeText(
                this@PerAppActivity,
                resources.getQuantityString(R.plurals.geek_perapp_iran_done, wanted.size, wanted.size),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}

@Composable
private fun IranianAppsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.geek_perapp_iran), style = Geek.type.sectionTitle) },
        text = { Text(stringResource(R.string.geek_perapp_iran_text), style = Geek.type.body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.geek_perapp_iran_confirm), style = Geek.type.button) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.geek_account_cancel), style = Geek.type.button) }
        },
    )
}

interface PerAppActions {
    fun onBack()
    fun onEnabled(on: Boolean)
    fun onBypass(on: Boolean)
    fun onToggle(packageName: String)
    fun onSearch(query: String)
    fun onSelectAll()
    fun onInvert()
    fun onAuto()

    /** "برنامه‌های ایرانی مستقیم": asks, then selects the Iranian apps in bypass mode. */
    fun onIranianApps()
    fun onImport()
    fun onExport()

    /** «پروفایل برنامه‌ها»: put a saved selection back. */
    fun onApplyProfile(profile: AppProfile)

    /** Asks for a name, then saves the current selection. */
    fun onSaveProfile()

    /** Asks, then removes a saved profile. */
    fun onRemoveProfile(profile: AppProfile)
}

@Composable
fun PerAppScreen(
    apps: List<AppInfo>,
    loading: Boolean,
    selected: Set<String>,
    enabled: Boolean,
    bypass: Boolean,
    actions: PerAppActions,
    profiles: List<AppProfile> = emptyList(),
) {
    val colors = Geek.colors
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ChevronBack, stringResource(R.string.geek_servers_back_description), actions::onBack)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.geek_route_per_app), style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onBackground)
                Text(
                    stringResource(R.string.geek_perapp_selected, selected.size),
                    style = Geek.type.caption,
                    color = colors.onBackgroundMuted,
                )
            }
            if (loading) {
                CircularProgressIndicator(color = colors.onBackground, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        }
        GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.sheet, modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                item(key = "switches") { Switches(enabled, bypass, actions) }
                item(key = "profiles") { Profiles(profiles, AppProfileList.active(profiles, bypass, selected).takeIf { enabled }, actions) }
                item(key = "search") {
                    SearchField(query) {
                        query = it
                        actions.onSearch(it)
                    }
                }
                item(key = "tools") { Tools(actions) }
                items(apps, key = { it.packageName }) { app ->
                    AppRow(app, checked = app.packageName in selected, onToggle = { actions.onToggle(app.packageName) })
                }
            }
        }
    }
}

@Composable
private fun Switches(enabled: Boolean, bypass: Boolean, actions: PerAppActions) {
    val colors = Geek.colors
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
        Column {
            SwitchRow(stringResource(R.string.per_app_proxy_settings_enable), stringResource(R.string.geek_perapp_enable_hint), enabled, actions::onEnabled)
            SettingsDivider()
            SwitchRow(
                stringResource(R.string.switch_bypass_apps_mode),
                stringResource(if (bypass) R.string.geek_perapp_mode_bypass else R.string.geek_perapp_mode_proxy),
                bypass,
                actions::onBypass,
            )
        }
    }
    Text(
        stringResource(R.string.geek_perapp_note),
        style = Geek.type.caption.copy(fontSize = 12.sp),
        color = colors.onGlassMuted,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, hint: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Geek.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onChange(!on) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Geek.type.row.copy(fontSize = 14.sp), color = colors.onGlass)
            Text(hint, style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onGlassMuted)
        }
        GeekSwitch(checked = on, onCheckedChange = null)
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val colors = Geek.colors
    val hint = stringResource(R.string.geek_perapp_search)
    GlassSurface(kind = GlassKind.Milk, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(GeekIcons.Search, contentDescription = null, tint = colors.onGlassMuted, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(hint, style = Geek.type.body, color = colors.onGlassMuted)
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = Geek.type.body.copy(color = colors.onGlass),
                    cursorBrush = SolidColor(colors.action),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
                )
            }
        }
    }
}

/**
 * «پروفایل برنامه‌ها»: one pill per saved selection (white when it is the
 * current one), then «ذخیره». The current profile also offers its removal.
 */
@Composable
private fun Profiles(profiles: List<AppProfile>, active: AppProfile?, actions: PerAppActions) {
    val colors = Geek.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.geek_perapp_profiles), style = Geek.type.label, color = colors.onGlassMuted, modifier = Modifier.padding(horizontal = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            profiles.forEach { profile ->
                ChoicePill(selected = profile == active, onClick = { actions.onApplyProfile(profile) }) { color ->
                    Text(profile.name, style = Geek.type.button.copy(fontSize = 14.sp), color = color, maxLines = 1, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            ChoicePill(selected = false, onClick = actions::onSaveProfile, dashed = true, role = Role.Button) { color ->
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(GeekIcons.Plus, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.geek_perapp_profile_save), style = Geek.type.button.copy(fontSize = 14.sp), color = color, maxLines = 1)
                }
            }
        }
        if (active != null) {
            Chip(stringResource(R.string.geek_perapp_profile_remove, active.name)) { actions.onRemoveProfile(active) }
        }
    }
}

@Composable
private fun SaveProfileDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = Geek.colors
    var name by rememberSaveable { mutableStateOf("") }
    val label = stringResource(R.string.geek_perapp_profile_name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.geek_perapp_profile_save_title), style = Geek.type.sectionTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.geek_perapp_profile_save_text), style = Geek.type.body)
                GlassSurface(kind = GlassKind.Milk, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                        if (name.isEmpty()) Text(label, style = Geek.type.body, color = colors.onGlassMuted)
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it.take(AppProfileList.MAX_NAME) },
                            singleLine = true,
                            textStyle = Geek.type.body.copy(color = colors.onGlass),
                            cursorBrush = SolidColor(colors.action),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = AppProfileList.cleanName(name).isNotEmpty()) {
                Text(stringResource(R.string.geek_perapp_profile_save_confirm), style = Geek.type.button)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.geek_account_cancel), style = Geek.type.button) }
        },
    )
}

@Composable
private fun RemoveProfileDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.geek_perapp_profile_remove, name), style = Geek.type.sectionTitle) },
        text = { Text(stringResource(R.string.geek_perapp_profile_remove_text), style = Geek.type.body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.geek_perapp_profile_remove_confirm), style = Geek.type.button) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.geek_account_cancel), style = Geek.type.button) }
        },
    )
}

@Composable
private fun Tools(actions: PerAppActions) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(stringResource(R.string.geek_perapp_iran), actions::onIranianApps)
        Chip(stringResource(R.string.menu_item_select_proxy_app), actions::onAuto)
        Chip(stringResource(R.string.menu_item_select_all), actions::onSelectAll)
        Chip(stringResource(R.string.menu_item_invert_selection), actions::onInvert)
        Chip(stringResource(R.string.menu_item_import_proxy_app), actions::onImport)
        Chip(stringResource(R.string.menu_item_export_proxy_app), actions::onExport)
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    val colors = Geek.colors
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.chip)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onGlass, maxLines = 1)
    }
}

@Composable
private fun AppRow(app: AppInfo, checked: Boolean, onToggle: () -> Unit) {
    val colors = Geek.colors
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        ImageRequest.Builder(context)
            .data("appicon:${app.packageName}")
            .fetcherFactory(AppIconFetcher.Factory(context))
            .build()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Geek.shapes.row)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = icon,
            contentDescription = null,
            modifier = Modifier.size(38.dp),
            contentScale = ContentScale.Fit,
            error = painterResource(R.drawable.ic_image_24dp),
            fallback = painterResource(R.drawable.ic_image_24dp),
        )
        Column(Modifier.weight(1f)) {
            Text(app.appName, style = Geek.type.row.copy(fontSize = 14.sp), color = colors.onGlass, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = Geek.type.caption.copy(fontSize = 11.sp), color = colors.onGlassMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        GeekCheckbox(checked = checked, onCheckedChange = null)
    }
}
