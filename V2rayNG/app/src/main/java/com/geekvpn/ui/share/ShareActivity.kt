package com.geekvpn.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geekvpn.share.LanAddress
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GeekSwitch
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.components.QrCode
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity

/** "اشتراک VPN با دستگاه دیگر" ([ShareViewModel]). */
class ShareActivity : BaseComponentActivity() {
    private val viewModel: ShareViewModel by viewModels { ShareViewModel.factory() }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
    }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GeekBackdrop {
                ShareScreen(state, onToggle = viewModel::setSharing, onCopy = ::copy, onBack = ::finish)
            }
        }
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(getString(R.string.geek_share_title), text))
        Toast.makeText(this, R.string.geek_share_copied, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun ShareScreen(state: ShareUiState, onToggle: (Boolean) -> Unit, onCopy: (String) -> Unit, onBack: () -> Unit) {
    val colors = Geek.colors
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ChevronBack, stringResource(R.string.geek_servers_back_description), onBack)
            Text(stringResource(R.string.geek_share_title), style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onBackground)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.geek_share_intro), style = Geek.type.body, color = colors.onBackgroundMuted)

            GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.tile, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = state.sharing, enabled = state.loaded, role = Role.Switch, onValueChange = onToggle)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.geek_share_toggle), style = Geek.type.row.copy(fontSize = 15.sp), color = colors.onGlass, modifier = Modifier.weight(1f))
                    GeekSwitch(checked = state.sharing, onCheckedChange = null)
                }
            }

            if (state.sharing) {
                if (!state.connected) {
                    Note(R.string.geek_share_not_connected, danger = true)
                }
                if (state.addresses.isEmpty()) {
                    Note(R.string.geek_share_no_network, danger = true)
                }
                for (address in state.addresses) {
                    AddressCard(address, state.port, onCopy)
                }
                if (state.user != null) {
                    Text(
                        stringResource(R.string.geek_share_auth, state.user),
                        style = Geek.type.caption,
                        color = colors.onBackgroundMuted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                Text(stringResource(R.string.geek_share_steps), style = Geek.type.body, color = colors.onBackgroundMuted)
            }
            Note(R.string.geek_share_warning, danger = false)
        }
    }
}

@Composable
private fun AddressCard(address: LanAddress, port: Int, onCopy: (String) -> Unit) {
    val colors = Geek.colors
    val hostPort = "${address.ip}:$port"
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.tileLarge, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(kindName(address.kind)), style = Geek.type.label, color = colors.onGlassMuted)
            Text(
                hostPort,
                style = Geek.type.numberSmall.copy(fontSize = 22.sp, textDirection = TextDirection.Ltr),
                color = colors.onGlass,
            )
            QrCode(text = "socks5://$hostPort", description = stringResource(R.string.geek_share_qr_description, hostPort), size = 168.dp)
            GeekSecondaryButton(
                text = stringResource(R.string.geek_share_copy),
                icon = GeekIcons.Copy,
                onClick = { onCopy(hostPort) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Note(@StringRes text: Int, danger: Boolean) {
    Text(
        stringResource(text),
        style = Geek.type.caption.copy(fontSize = 12.sp),
        color = if (danger) Geek.colors.danger else Geek.colors.onBackgroundMuted,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@StringRes
private fun kindName(kind: LanAddress.Kind): Int = when (kind) {
    LanAddress.Kind.Wifi -> R.string.geek_share_kind_wifi
    LanAddress.Kind.Hotspot -> R.string.geek_share_kind_hotspot
    LanAddress.Kind.Usb -> R.string.geek_share_kind_usb
    LanAddress.Kind.Other -> R.string.geek_share_kind_other
}
