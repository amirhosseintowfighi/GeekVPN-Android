package com.geekvpn.ui.info

import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geekvpn.GeekGraph
import com.geekvpn.api.ApiException
import com.geekvpn.api.FaqSection
import com.geekvpn.api.NotificationPreferences
import com.geekvpn.api.ServerStatus
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.Hairline
import com.geekvpn.ui.common.IndexRow
import com.geekvpn.ui.common.PageTitle
import com.geekvpn.ui.common.SettingRow
import com.geekvpn.ui.common.SettingsCard
import com.geekvpn.ui.common.SettingsDivider
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatNumber
import com.geekvpn.ui.components.BackdropLayout
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GeekSwitch
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What a screen read from the API: the value, or that reading it failed. */
data class Remote<T>(val value: T? = null, val failed: Boolean = false)

/** Loads one API answer for a screen and can try again; main process only, like every API call. */
class RemoteViewModel<T : Any>(private val what: String, private val load: suspend () -> T) : ViewModel() {
    private val state = MutableStateFlow(Remote<T>())
    val uiState: StateFlow<Remote<T>> = state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        state.value = state.value.copy(failed = false)
        viewModelScope.launch {
            state.value = try {
                Remote(load())
            } catch (e: ApiException) {
                LogUtil.w(AppConfig.TAG, "$what: loading failed (${e.status})", e)
                state.value.copy(failed = true)
            }
        }
    }

    companion object {
        fun <T : Any> factory(what: String, load: suspend () -> T) = viewModelFactory {
            initializer { RemoteViewModel(what, load) }
        }
    }
}

/**
 * A page opened from Account (Dark Swiss): the round back button, the Latin
 * eyebrow over the title, and [content] scrolling under them.
 */
@Composable
fun SubPage(
    title: String,
    eyebrow: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        GlassIconButton(GeekIcons.ChevronBack, stringResource(R.string.geek_servers_back_description), onBack)
        PageTitle(title, subtitle = subtitle, eyebrow = eyebrow)
        content()
    }
}

/** Loading, failed with a retry, or [content] with the value. */
@Composable
private fun <T> ColumnScope.RemoteContent(state: Remote<T>, failedText: Int, onRetry: () -> Unit, content: @Composable ColumnScope.(T) -> Unit) {
    val value = state.value
    when {
        value != null -> content(value)
        state.failed -> {
            Text(stringResource(failedText), style = Geek.type.body, color = Geek.colors.onBackgroundMuted)
            GeekSecondaryButton(stringResource(R.string.geek_tickets_retry), onRetry, Modifier.fillMaxWidth(), icon = GeekIcons.Refresh)
        }
        else -> Text(stringResource(R.string.geek_tickets_loading), style = Geek.type.body, color = Geek.colors.onBackgroundMuted)
    }
}

// -- server status ------------------------------------------------------------------

/** «وضعیت سرورها»: the same list the bot shows, from `GET /api/miniapp/servers`. */
class ServerStatusActivity : BaseComponentActivity() {
    private val viewModel: RemoteViewModel<List<ServerStatus>> by viewModels {
        RemoteViewModel.factory("Server status") { GeekGraph.api.serverStatus() }
    }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GeekBackdrop(layout = BackdropLayout.Horizon) {
                ServerStatusScreen(state, onRetry = viewModel::refresh, onBack = ::finish)
            }
        }
    }
}

@Composable
fun ServerStatusScreen(state: Remote<List<ServerStatus>>, onRetry: () -> Unit, onBack: () -> Unit) {
    val locale = appLocale()
    SubPage(
        title = stringResource(R.string.geek_status_title),
        eyebrow = stringResource(R.string.geek_eyebrow_status),
        subtitle = stringResource(R.string.geek_status_subtitle),
        onBack = onBack,
    ) {
        RemoteContent(state, R.string.geek_status_failed, onRetry) { rows ->
            if (rows.isEmpty()) {
                Text(stringResource(R.string.geek_status_empty), style = Geek.type.body, color = Geek.colors.onBackgroundMuted)
            } else {
                Column {
                    Hairline()
                    rows.forEach { row ->
                        IndexRow(
                            code = row.flag?.takeIf { it.isNotBlank() } ?: stringResource(R.string.geek_code_server),
                            title = row.nameFa.orEmpty(),
                            hint = listOfNotNull(
                                row.locationFa?.takeIf { it.isNotBlank() },
                                row.loadPercent?.let { stringResource(R.string.geek_status_load, formatNumber(it.toLong(), locale)) },
                            ).joinToString(" · ").ifEmpty { null },
                            value = stringResource(healthLabel(row.health)),
                        )
                        Hairline()
                    }
                }
            }
        }
    }
}

fun healthLabel(health: String?): Int = when (health) {
    "healthy" -> R.string.geek_status_healthy
    "degraded" -> R.string.geek_status_degraded
    "down" -> R.string.geek_status_down
    "maintenance" -> R.string.geek_status_maintenance
    else -> R.string.geek_status_unknown
}

// -- FAQ ------------------------------------------------------------------------------

/** «سؤالات متداول»: the bot's questions, each opening its answer in place. */
class FaqActivity : BaseComponentActivity() {
    private val viewModel: RemoteViewModel<List<FaqSection>> by viewModels {
        RemoteViewModel.factory("FAQ") { GeekGraph.api.faq() }
    }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GeekBackdrop(layout = BackdropLayout.Bars) {
                FaqScreen(state, onRetry = viewModel::refresh, onBack = ::finish)
            }
        }
    }
}

@Composable
fun FaqScreen(state: Remote<List<FaqSection>>, onRetry: () -> Unit, onBack: () -> Unit) {
    SubPage(title = stringResource(R.string.geek_faq_title), eyebrow = stringResource(R.string.geek_eyebrow_faq), onBack = onBack) {
        RemoteContent(state, R.string.geek_faq_failed, onRetry) { sections ->
            sections.forEach { section ->
                Text(
                    section.titleFa.orEmpty(),
                    style = Geek.type.label.copy(fontSize = 13.sp),
                    color = Geek.colors.onBackgroundMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Column {
                    Hairline()
                    section.entries.orEmpty().forEach { entry ->
                        FaqItem(entry.questionFa.orEmpty(), entry.answerFa.orEmpty())
                        Hairline()
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    val colors = Geek.colors
    var open by rememberSaveable(question) { mutableStateOf(false) }
    val state = stringResource(if (open) R.string.geek_faq_open else R.string.geek_faq_closed)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { open = !open }
            .semantics { stateDescription = state }
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(question, style = Geek.type.row, color = colors.onBackground, modifier = Modifier.weight(1f))
            Icon(
                GeekIcons.ChevronRow,
                contentDescription = null,
                tint = colors.onBackgroundMuted,
                modifier = Modifier.size(16.dp).rotate(if (open) -90f else 0f),
            )
        }
        AnimatedVisibility(open) {
            Text(answer, style = Geek.type.body, color = colors.onBackground.copy(alpha = 0.85f))
        }
    }
}

// -- notification switches -------------------------------------------------------------

/** One switch of [NotificationPreferences], by the key the server uses. */
enum class NotificationSwitch(val title: Int, val hint: Int) {
    Expiry(R.string.geek_notify_expiry, R.string.geek_notify_expiry_hint),
    Traffic(R.string.geek_notify_traffic, R.string.geek_notify_traffic_hint),
    Promos(R.string.geek_notify_promos, R.string.geek_notify_promos_hint),
    News(R.string.geek_notify_news, R.string.geek_notify_news_hint),
    QuietHours(R.string.geek_notify_quiet, R.string.geek_notify_quiet_hint),
    ;

    fun of(preferences: NotificationPreferences): Boolean = when (this) {
        Expiry -> preferences.expiry
        Traffic -> preferences.traffic
        Promos -> preferences.promos
        News -> preferences.news
        QuietHours -> preferences.quietHours
    } != false

    /** [preferences] with this switch set to [on], every other one as it was (missing ones on). */
    fun set(preferences: NotificationPreferences, on: Boolean): NotificationPreferences {
        val base = preferences.normalized()
        return when (this) {
            Expiry -> base.copy(expiry = on)
            Traffic -> base.copy(traffic = on)
            Promos -> base.copy(promos = on)
            News -> base.copy(news = on)
            QuietHours -> base.copy(quietHours = on)
        }
    }
}

data class NotificationsUiState(
    val preferences: NotificationPreferences? = null,
    val failed: Boolean = false,
    val saving: Boolean = false,
)

/** Reads the switches, and saves each change at once; a failed save puts the switch back. */
class NotificationsViewModel(
    private val load: suspend () -> NotificationPreferences,
    private val save: suspend (NotificationPreferences) -> NotificationPreferences,
    private val logFailure: (String, ApiException) -> Unit,
) : ViewModel() {
    private val state = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        state.value = state.value.copy(failed = false)
        viewModelScope.launch {
            state.value = try {
                NotificationsUiState(preferences = load().normalized())
            } catch (e: ApiException) {
                logFailure("loading", e)
                state.value.copy(failed = true)
            }
        }
    }

    fun set(switch: NotificationSwitch, on: Boolean) {
        val before = state.value.preferences ?: return
        if (state.value.saving) return
        val after = switch.set(before, on)
        state.value = state.value.copy(preferences = after, saving = true)
        viewModelScope.launch {
            state.value = try {
                state.value.copy(preferences = save(after).normalized(), saving = false)
            } catch (e: ApiException) {
                logFailure("saving", e)
                state.value.copy(preferences = before, saving = false)
            }
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                NotificationsViewModel(
                    load = { GeekGraph.api.notificationPreferences() },
                    save = { GeekGraph.api.saveNotificationPreferences(it) },
                    logFailure = { step, e -> LogUtil.w(AppConfig.TAG, "Notifications: $step failed (${e.status})", e) },
                )
            }
        }
    }
}

/** «اعلان‌ها»: the bot's notification switches; they decide what the bot and the app's push send. */
class NotificationsActivity : BaseComponentActivity() {
    private val viewModel: NotificationsViewModel by viewModels { NotificationsViewModel.factory() }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GeekBackdrop(layout = BackdropLayout.Bars) {
                NotificationsScreen(state, onSet = viewModel::set, onRetry = viewModel::refresh, onBack = ::finish)
            }
        }
    }
}

@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onSet: (NotificationSwitch, Boolean) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    SubPage(
        title = stringResource(R.string.geek_notify_title),
        eyebrow = stringResource(R.string.geek_eyebrow_notifications),
        subtitle = stringResource(R.string.geek_notify_subtitle),
        onBack = onBack,
    ) {
        RemoteContent(Remote(state.preferences, state.failed), R.string.geek_notify_failed, onRetry) { preferences ->
            SettingsCard {
                NotificationSwitch.entries.forEachIndexed { index, switch ->
                    if (index > 0) SettingsDivider()
                    val on = switch.of(preferences)
                    SettingRow(
                        icon = if (switch == NotificationSwitch.QuietHours) GeekIcons.Clock else GeekIcons.Sparkle,
                        title = stringResource(switch.title),
                        hint = stringResource(switch.hint),
                        onClick = { onSet(switch, !on) },
                        role = Role.Switch,
                    ) { GeekSwitch(checked = on, onCheckedChange = null, enabled = !state.saving) }
                }
            }
            Text(
                stringResource(R.string.geek_notify_footer),
                style = Geek.type.caption.copy(fontWeight = FontWeight.Medium),
                color = Geek.colors.onBackgroundMuted,
            )
        }
    }
}
