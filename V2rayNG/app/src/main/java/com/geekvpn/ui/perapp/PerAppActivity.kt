package com.geekvpn.ui.perapp

import android.os.Bundle
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.SettingsDivider
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekCheckbox
import com.geekvpn.ui.components.GeekSwitch
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.perappproxy.PerAppProxyViewModel
import com.v2ray.ang.util.AppIconFetcher
import com.v2ray.ang.util.Utils

/**
 * Per-app proxy ("تونل تفکیکی برنامه‌ها") in GeekVPN's design, on v2rayNG's own
 * [PerAppProxyViewModel]: the same stored list and the same restart signal,
 * only the screen is GeekVPN's.
 */
class PerAppActivity : BaseComponentActivity() {

    private val viewModel: PerAppProxyViewModel by viewModels()

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
        GeekTheme {
            GeekBackdrop {
                PerAppScreen(
                    apps = apps,
                    loading = loading,
                    selected = selected,
                    enabled = enabled,
                    bypass = bypass,
                    actions = object : PerAppActions {
                        override fun onBack() = finish()
                        override fun onEnabled(on: Boolean) = viewModel.setPerAppProxyEnabled(on)
                        override fun onBypass(on: Boolean) = viewModel.setBypassAppsEnabled(on)
                        override fun onToggle(packageName: String) = viewModel.toggle(packageName)
                        override fun onSearch(query: String) = viewModel.filterApps(query)
                        override fun onSelectAll() = viewModel.selectAll()
                        override fun onInvert() = viewModel.invertSelection()
                        override fun onAuto() = viewModel.selectProxyAppAuto(this@PerAppActivity)
                        override fun onImport() = viewModel.importProxyApp(Utils.getClipboard(applicationContext), this@PerAppActivity)
                        override fun onExport() {
                            Utils.setClipboard(applicationContext, viewModel.exportProxyApp())
                            toastSuccess(R.string.toast_success)
                        }
                    },
                )
            }
        }
    }
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
    fun onImport()
    fun onExport()
}

@Composable
fun PerAppScreen(
    apps: List<AppInfo>,
    loading: Boolean,
    selected: Set<String>,
    enabled: Boolean,
    bypass: Boolean,
    actions: PerAppActions,
) {
    val colors = Geek.colors
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ArrowForward, stringResource(R.string.geek_servers_back_description), actions::onBack)
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

@Composable
private fun Tools(actions: PerAppActions) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
