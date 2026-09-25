package com.geekvpn.ui.catalog

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.ui.components.CountryBadge
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekBottomNav
import com.geekvpn.ui.components.GeekCheckbox
import com.geekvpn.ui.components.GeekDangerButton
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GeekSwitch
import com.geekvpn.ui.components.GeekTab
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.components.LatencyIndicator
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity

/**
 * Debug-only showcase of every Geek UI component in light and dark, for design
 * review and for the CI emulator screenshots (start it with `--ez dark true`
 * for the dark theme). Its state is presentation-only and never persisted.
 */
class CatalogActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        var dark by rememberSaveable { mutableStateOf(intent.getBooleanExtra(EXTRA_DARK, false)) }
        GeekTheme(darkTheme = dark) {
            CatalogScreen(dark = dark, onDarkChange = { dark = it })
        }
    }

    companion object {
        const val EXTRA_DARK = "dark"
    }
}

@Composable
private fun CatalogScreen(dark: Boolean, onDarkChange: (Boolean) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(GeekTab.Home) }
    var switchA by rememberSaveable { mutableStateOf(true) }
    var switchB by rememberSaveable { mutableStateOf(false) }
    var checkA by rememberSaveable { mutableStateOf(true) }
    var checkB by rememberSaveable { mutableStateOf(false) }
    val colors = Geek.colors

    GeekBackdrop {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_geek_logo),
                        contentDescription = null,
                        tint = colors.onBackground,
                        modifier = Modifier.size(34.dp),
                    )
                    Text(
                        stringResource(R.string.geek_catalog_title),
                        style = Geek.type.sectionTitle,
                        color = colors.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(if (dark) R.string.geek_catalog_theme_dark else R.string.geek_catalog_theme_light),
                        style = Geek.type.caption,
                        color = colors.onBackground,
                    )
                    GeekSwitch(checked = dark, onCheckedChange = onDarkChange)
                }

                Section(R.string.geek_catalog_palette) {
                    val swatches = listOf(
                        colors.background, colors.logoBlue, colors.action, colors.milkGlass, colors.soft,
                        colors.onGlassMuted, colors.success, colors.warning, colors.danger,
                    )
                    swatches.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { swatch ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(34.dp)
                                            .clip(Geek.shapes.badge)
                                            .background(swatch)
                                            .border(1.dp, colors.track, Geek.shapes.badge),
                                    )
                                    Text(
                                        swatch.hex(),
                                        style = Geek.type.numberSmall.copy(fontSize = 10.sp),
                                        color = colors.onGlassMuted,
                                        maxLines = 1,
                                    )
                                }
                            }
                            // Keep the last, shorter row's swatches the same width.
                            repeat(5 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                    Text(stringResource(R.string.geek_catalog_type_page), style = Geek.type.pageTitle, color = colors.onGlass)
                    Text(stringResource(R.string.geek_catalog_type_section), style = Geek.type.sectionTitle, color = colors.onGlass)
                    Text(stringResource(R.string.geek_catalog_type_row), style = Geek.type.row, color = colors.onGlass)
                    Text(stringResource(R.string.geek_catalog_type_caption), style = Geek.type.caption, color = colors.onGlassMuted)
                    Text("00:12:48 · 121ms", style = Geek.type.numberLarge, color = colors.onGlass)
                }

                Section(R.string.geek_catalog_buttons) {
                    GeekPrimaryButton(
                        text = stringResource(R.string.geek_catalog_sample_primary),
                        icon = GeekIcons.Telegram,
                        onClick = {},
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GeekSecondaryButton(
                            text = stringResource(R.string.geek_catalog_sample_secondary),
                            icon = GeekIcons.Plus,
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        )
                        GeekDangerButton(
                            text = stringResource(R.string.geek_catalog_sample_danger),
                            icon = GeekIcons.Unlink,
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        )
                    }
                    GeekSecondaryButton(
                        text = stringResource(R.string.geek_catalog_sample_disabled),
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Section(R.string.geek_catalog_controls) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GeekSwitch(checked = switchA, onCheckedChange = { switchA = it })
                        GeekSwitch(checked = switchB, onCheckedChange = { switchB = it })
                        GeekCheckbox(checked = checkA, onCheckedChange = { checkA = it })
                        GeekCheckbox(checked = checkB, onCheckedChange = { checkB = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CountryBadge("DE", selected = true)
                        CountryBadge("US")
                    }
                }

                Section(R.string.geek_catalog_signal) {
                    listOf(96L, 168L, 388L, 410L, 0L).forEach { ms -> LatencyIndicator(ms) }
                }

                Text(stringResource(R.string.geek_catalog_glass), style = Geek.type.label, color = colors.onBackground)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassSurface(kind = GlassKind.Milk, modifier = Modifier.weight(1f).height(90.dp)) {
                        Text(
                            stringResource(R.string.geek_catalog_glass_milk),
                            style = Geek.type.row,
                            color = colors.onGlass,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    GlassSurface(kind = GlassKind.Clear, modifier = Modifier.weight(1f).height(90.dp)) {
                        Text(
                            stringResource(R.string.geek_catalog_glass_clear),
                            style = Geek.type.row,
                            color = colors.onBackground,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
            GeekBottomNav(selected = tab, onSelect = { tab = it })
        }
    }
}

@Composable
private fun Section(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(kind = GlassKind.Milk, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(title), style = Geek.type.label, color = Geek.colors.onGlass)
            content()
        }
    }
}

private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
