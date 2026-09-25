package com.geekvpn.ui.about

import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.geekvpn.scanner.CfScanNative
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.SectionLabel
import com.geekvpn.ui.common.SettingRow
import com.geekvpn.ui.common.SettingsCard
import com.geekvpn.ui.common.SettingsDivider
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekSheet
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "درباره‌ی برنامه" in GeekVPN's design: version (with the Xray core and
 * cfscan it carries), the license, and the open-source projects inside it,
 * as their licenses (GPL-3.0, MPL-2.0, MIT) require.
 */
class AboutActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            GeekBackdrop { AboutScreen(onBack = ::finish) }
        }
    }
}

/** An open-source project GeekVPN is built on. */
private data class Credit(val name: String, val license: String, val url: String, val role: Int)

private val credits = listOf(
    Credit("v2rayNG", "GPL-3.0", "https://github.com/2dust/v2rayNG", R.string.geek_about_role_v2rayng),
    Credit("Xray-core", "MPL-2.0", "https://github.com/XTLS/Xray-core", R.string.geek_about_role_xray),
    Credit("hev-socks5-tunnel", "MIT", "https://github.com/heiher/hev-socks5-tunnel", R.string.geek_about_role_hev),
    Credit("cf-scanner", "MIT", "https://github.com/radioactiveAHM/cf-scanner", R.string.geek_about_role_cfscan),
)

@Composable
private fun androidx.compose.foundation.layout.BoxScope.AboutScreen(onBack: () -> Unit) {
    val colors = Geek.colors
    val context = LocalContext.current
    var licenses by rememberSaveable { mutableStateOf(false) }
    // The core's version loads its native library: off the main thread.
    val versions by produceState(initialValue = "") {
        value = withContext(Dispatchers.IO) { "${CoreNativeManager.getLibVersion()}, cfscan ${CfScanNative.version()}" }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ArrowForward, stringResource(R.string.geek_servers_back_description), onBack)
            Text(stringResource(R.string.geek_account_about), style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onBackground)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Image(painterResource(R.drawable.ic_geek_logo), contentDescription = null, modifier = Modifier.size(72.dp))
                    Text(stringResource(R.string.app_name), style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onGlass)
                    // v2rayNG's About showed the same line; CI reads the cfscan version off it.
                    Text(
                        "v${BuildConfig.VERSION_NAME} ($versions)",
                        style = Geek.type.caption,
                        color = colors.onGlassMuted,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.geek_about_license),
                        style = Geek.type.caption.copy(fontSize = 12.sp),
                        color = colors.onGlassMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            SectionLabel(stringResource(R.string.geek_about_built_on))
            SettingsCard {
                credits.forEachIndexed { index, credit ->
                    if (index > 0) SettingsDivider()
                    SettingRow(
                        icon = GeekIcons.Link,
                        title = "${credit.name} · ${credit.license}",
                        hint = stringResource(credit.role),
                        onClick = { Utils.openUri(context, credit.url) },
                    )
                }
            }

            SettingsCard {
                SettingRow(
                    icon = GeekIcons.List,
                    title = stringResource(R.string.title_oss_license),
                    hint = stringResource(R.string.geek_about_licenses_hint),
                    onClick = { licenses = true },
                )
            }
        }
    }

    if (licenses) {
        GeekSheet(
            title = stringResource(R.string.title_oss_license),
            onDismiss = { licenses = false },
            closeLabel = stringResource(R.string.geek_sheet_close),
        ) {
            LicensesPage(Modifier.fillMaxWidth().height(480.dp))
        }
    }
}

/** The dependency licence page the build generates (`open_source_licenses.html`). */
@Composable
private fun LicensesPage(modifier: Modifier) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                loadUrl("file:///android_asset/open_source_licenses.html")
            }
        },
        modifier = modifier,
    )
}
