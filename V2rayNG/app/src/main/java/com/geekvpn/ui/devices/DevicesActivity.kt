package com.geekvpn.ui.devices

import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geekvpn.GeekGraph
import com.geekvpn.account.OwnerText
import com.geekvpn.account.ownerFailure
import com.geekvpn.api.ApiException
import com.geekvpn.api.DeviceSession
import com.geekvpn.ui.common.Hairline
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatDateTime
import com.geekvpn.ui.components.BackdropLayout
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.info.SubPage
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** `GET /devices` and `POST /devices/{id}/revoke`. */
interface DevicesPort {
    suspend fun list(): List<DeviceSession>
    suspend fun revoke(deviceId: String)

    /** This phone: the ordinary sign-out, which also stops its push and drops its services. */
    fun signOutHere()
}

data class DevicesUiState(
    val devices: List<DeviceSession>? = null,
    val failed: Boolean = false,
    /** The device being signed out. */
    val busyId: String? = null,
)

sealed interface DevicesEvent {
    data object Revoked : DevicesEvent
    data object SignedOutHere : DevicesEvent
    data class Failed(val text: OwnerText) : DevicesEvent
}

/**
 * The bot's «دستگاه‌های متصل»: every app signed in to this account, and a
 * sign-out for each. Signing this phone out goes through the normal
 * sign-out rather than the revoke, so its push token is forgotten first,
 * while the session can still ask.
 */
class DevicesViewModel(
    private val port: DevicesPort,
    /** LogUtil in the app; tests run without MMKV. */
    private val log: (String, Throwable) -> Unit = { message, e -> LogUtil.w(AppConfig.TAG, message, e) },
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val state = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = state.asStateFlow()

    private val eventChannel = Channel<DevicesEvent>(Channel.BUFFERED)
    val events: Flow<DevicesEvent> = eventChannel.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        state.update { it.copy(failed = false) }
        viewModelScope.launch {
            try {
                val devices = port.list()
                state.update { it.copy(devices = devices, failed = false) }
            } catch (e: IOException) {
                log("Devices: loading failed (${(e as? ApiException)?.status})", e)
                state.update { it.copy(failed = true) }
            }
        }
    }

    fun revoke(device: DeviceSession) {
        val id = device.deviceId ?: return
        if (state.value.busyId != null) return
        if (device.current == true) {
            port.signOutHere()
            eventChannel.trySend(DevicesEvent.SignedOutHere)
            return
        }
        state.update { it.copy(busyId = id) }
        viewModelScope.launch {
            try {
                port.revoke(id)
                state.update { current -> current.copy(devices = current.devices?.filterNot { it.deviceId == id }, busyId = null) }
                eventChannel.send(DevicesEvent.Revoked)
            } catch (e: IOException) {
                log("Devices: revoking $id failed (${(e as? ApiException)?.status})", e)
                val gone = (e as? ApiException)?.status == 404
                state.update { current ->
                    // Already signed out somewhere else: the row goes too.
                    current.copy(devices = if (gone) current.devices?.filterNot { it.deviceId == id } else current.devices, busyId = null)
                }
                eventChannel.send(DevicesEvent.Failed(ownerFailure(e)))
            }
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                DevicesViewModel(
                    object : DevicesPort {
                        override suspend fun list() = GeekGraph.api.devices()
                        override suspend fun revoke(deviceId: String) = GeekGraph.api.revokeDevice(deviceId)
                        override fun signOutHere() = GeekGraph.signOut()
                    },
                )
            }
        }
    }
}

/** «دستگاه‌های واردشده»: the bot's device list, from `GET /api/miniapp/devices`. */
class DevicesActivity : BaseComponentActivity() {
    private val viewModel: DevicesViewModel by viewModels { DevicesViewModel.factory() }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                viewModel.events.collect { event ->
                    when (event) {
                        DevicesEvent.Revoked -> Toast.makeText(this@DevicesActivity, R.string.geek_devices_revoked, Toast.LENGTH_SHORT).show()
                        // Home sees the session end and goes back to the login screens.
                        DevicesEvent.SignedOutHere -> finish()
                        is DevicesEvent.Failed -> Toast.makeText(
                            this@DevicesActivity,
                            event.text.text ?: getString(event.text.res),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
            GeekBackdrop(layout = BackdropLayout.Bars) {
                DevicesScreen(state, onRevoke = viewModel::revoke, onRetry = viewModel::refresh, onBack = ::finish)
            }
        }
    }
}

@Composable
fun DevicesScreen(state: DevicesUiState, onRevoke: (DeviceSession) -> Unit, onRetry: () -> Unit, onBack: () -> Unit) {
    val colors = Geek.colors
    var asking by rememberSaveable { mutableStateOf<String?>(null) }
    val target = state.devices?.firstOrNull { it.deviceId == asking }
    if (target != null) {
        val current = target.current == true
        AlertDialog(
            onDismissRequest = { asking = null },
            title = {
                Text(
                    stringResource(if (current) R.string.geek_devices_signout_here_title else R.string.geek_devices_revoke_title),
                    style = Geek.type.sectionTitle,
                )
            },
            text = {
                Text(
                    if (current) stringResource(R.string.geek_devices_signout_here_text) else stringResource(R.string.geek_devices_revoke_text, target.name.orEmpty()),
                    style = Geek.type.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    asking = null
                    onRevoke(target)
                }) { Text(stringResource(R.string.geek_devices_revoke), style = Geek.type.button, color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { asking = null }) { Text(stringResource(R.string.geek_account_cancel), style = Geek.type.button) }
            },
        )
    }

    SubPage(
        title = stringResource(R.string.geek_devices_title),
        eyebrow = stringResource(R.string.geek_eyebrow_devices),
        subtitle = stringResource(R.string.geek_devices_subtitle),
        onBack = onBack,
    ) {
        val devices = state.devices
        when {
            devices != null && devices.isEmpty() ->
                Text(stringResource(R.string.geek_devices_empty), style = Geek.type.body, color = colors.onBackgroundMuted)
            devices != null -> Column {
                Hairline()
                devices.forEach { device ->
                    DeviceRow(device, busy = state.busyId == device.deviceId, enabled = state.busyId == null) { asking = device.deviceId }
                    Hairline()
                }
            }
            state.failed -> {
                Text(stringResource(R.string.geek_devices_failed), style = Geek.type.body, color = colors.onBackgroundMuted)
                GeekSecondaryButton(stringResource(R.string.geek_tickets_retry), onRetry, Modifier.fillMaxWidth(), icon = GeekIcons.Refresh)
            }
            else -> Text(stringResource(R.string.geek_tickets_loading), style = Geek.type.body, color = colors.onBackgroundMuted)
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceSession, busy: Boolean, enabled: Boolean, onRevoke: () -> Unit) {
    val colors = Geek.colors
    val locale = appLocale()
    val current = device.current == true
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                device.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.geek_devices_unnamed),
                style = Geek.type.row,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                if (current) stringResource(R.string.geek_devices_this_phone) else null,
                device.platform?.takeIf { it.isNotBlank() }?.let(::platformLabel),
                device.appVersion?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.geek_devices_version, it) },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = Geek.type.caption, color = if (current) colors.link else colors.onBackgroundMuted)
            }
            formatDateTime(device.lastSeenAt, locale)?.let {
                Text(stringResource(R.string.geek_devices_last_seen, it), style = Geek.type.caption, color = colors.onBackgroundMuted)
            }
        }
        TextButton(onClick = onRevoke, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(
                stringResource(if (busy) R.string.geek_devices_revoking else if (current) R.string.geek_devices_signout_here else R.string.geek_devices_revoke),
                style = Geek.type.button,
                color = colors.danger,
            )
        }
    }
}

/** The platform word the sign-in sent ("android"), as a name. */
fun platformLabel(platform: String): String = when (platform.lowercase(java.util.Locale.ROOT)) {
    "android" -> "Android"
    "ios" -> "iOS"
    "windows" -> "Windows"
    "macos" -> "macOS"
    "linux" -> "Linux"
    else -> platform
}
