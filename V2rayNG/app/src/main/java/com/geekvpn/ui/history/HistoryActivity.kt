package com.geekvpn.ui.history

import android.app.Application
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geekvpn.connection.ConnectionHistory
import com.geekvpn.connection.ConnectionRecord
import com.geekvpn.connection.ServerNames
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.IndexRow
import com.geekvpn.ui.common.SectionLabel
import com.geekvpn.ui.common.SettingsCard
import com.geekvpn.ui.common.SettingsDivider
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatDate
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.geekvpn.ui.usage.UsageChart
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

data class HistoryUiState(val records: List<ConnectionRecord> = emptyList(), val loaded: Boolean = false)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val records = withContext(Dispatchers.IO) { ConnectionHistory.open().records() }
            state.value = HistoryUiState(records, loaded = true)
        }
    }

    fun clear() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ConnectionHistory.open().clear() }
            state.value = HistoryUiState(emptyList(), loaded = true)
        }
    }
}

/** «تاریخچه‌ی اتصال» ([ConnectionHistory]): this phone's connections of the last 30 days. */
class HistoryActivity : BaseComponentActivity() {
    private val viewModel: HistoryViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        // The VPN process writes the open connection every half minute.
        viewModel.load()
    }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GeekBackdrop {
                HistoryScreen(state, now = System.currentTimeMillis(), onClear = viewModel::clear, onBack = ::finish)
            }
        }
    }
}

@Composable
fun HistoryScreen(state: HistoryUiState, now: Long, onClear: () -> Unit, onBack: () -> Unit, zone: ZoneId = ZoneId.systemDefault()) {
    val colors = Geek.colors
    val locale = appLocale()
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ChevronBack, stringResource(R.string.geek_servers_back_description), onBack)
            Text(stringResource(R.string.geek_history_title), style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onBackground)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.loaded && state.records.isEmpty()) {
                Text(stringResource(R.string.geek_history_empty), style = Geek.type.caption, color = colors.onBackgroundMuted)
            }
            state.records.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.forEach { (day, records) ->
                SectionLabel(dayLabel(day, LocalDate.now(zone), locale))
                SettingsCard {
                    records.forEachIndexed { index, record ->
                        if (index > 0) SettingsDivider()
                        HistoryRow(record, now, zone, locale)
                    }
                }
            }
            if (state.records.isNotEmpty()) {
                GeekSecondaryButton(stringResource(R.string.geek_history_clear), onClick = onClear, modifier = Modifier.fillMaxWidth())
            }
            Text(
                stringResource(R.string.geek_history_note),
                style = Geek.type.caption.copy(fontSize = 12.sp),
                color = colors.onBackgroundMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale): String = when (day) {
    today -> stringResource(R.string.geek_usage_today)
    today.minusDays(1) -> stringResource(R.string.geek_history_yesterday)
    else -> formatDate("${day}T12:00:00Z", locale).orEmpty()
}

@Composable
private fun HistoryRow(record: ConnectionRecord, now: Long, zone: ZoneId, locale: Locale) {
    val server = record.lastServer ?: record.server
    val time = DateTimeFormatter.ofPattern("HH:mm", locale).withDecimalStyle(DecimalStyle.of(locale)).withZone(zone)
    val start = time.format(Instant.ofEpochMilli(record.startedAt))
    val span = if (record.open) {
        stringResource(R.string.geek_history_span_open, start)
    } else {
        stringResource(R.string.geek_history_span, start, time.format(Instant.ofEpochMilli(record.endedAt ?: record.startedAt)))
    }
    val details = listOfNotNull(
        span,
        HistoryFormat.duration(record.duration(now), locale),
        if (record.failovers > 0) pluralStringResource(R.plurals.geek_history_failovers, record.failovers, record.failovers) else null,
        when (record.endReason) {
            ConnectionRecord.End.DataCap -> stringResource(R.string.geek_history_end_cap)
            ConnectionRecord.End.Interrupted -> stringResource(R.string.geek_history_end_interrupted)
            else -> null
        },
    ).joinToString(" · ")
    val (size, unit) = UsageChart.size(record.bytes, locale)
    val unitLabel = stringResource(
        when (unit) {
            UsageChart.SizeUnit.Kib -> R.string.geek_usage_kb
            UsageChart.SizeUnit.Mib -> R.string.geek_usage_mb
            UsageChart.SizeUnit.Gib -> R.string.geek_usage_gb
        },
    )
    IndexRow(
        code = server?.let { ServerNames.countryCode(it) } ?: "··",
        title = server?.let { ServerNames.title(it) } ?: stringResource(R.string.geek_history_unknown_server),
        value = "$size $unitLabel",
        hint = details,
    )
}

/** Pure formatting, for the tests. */
object HistoryFormat {
    /** "01:15:04" in the locale's digits, as Home's end-of-session message. */
    fun duration(ms: Long, locale: Locale): String {
        val seconds = ms / 1000
        return String.format(locale, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
}
