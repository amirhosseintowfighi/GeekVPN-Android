package com.geekvpn.ui.usage

import android.app.Application
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geekvpn.GeekGraph
import com.geekvpn.api.UsageDayResponse
import com.geekvpn.auth.Session
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatDate
import com.geekvpn.ui.common.formatDecimal
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.geekvpn.usage.DailyUsage
import com.geekvpn.usage.DayUsage
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

/** Totals and bar heights for the chart; pure, for the tests. */
object UsageChart {
    data class Summary(val total: Long, val average: Long, val peak: Long)

    fun summary(days: List<DayUsage>): Summary {
        val total = days.sumOf { it.bytes }
        return Summary(total, if (days.isEmpty()) 0 else total / days.size, days.maxOfOrNull { it.bytes } ?: 0)
    }

    /** Each day's bar as a share of the busiest day; 0 for an empty chart. */
    fun heights(days: List<DayUsage>): List<Float> {
        val peak = days.maxOfOrNull { it.bytes } ?: 0
        return days.map { if (peak <= 0) 0f else it.bytes.toFloat() / peak }
    }

    /** "۱٫۲ گیگ" style: the unit and one decimal from the size. */
    fun size(bytes: Long, locale: Locale): Pair<String, SizeUnit> {
        val (value, unit) = when {
            bytes >= GIB -> bytes / GIB.toDouble() to SizeUnit.Gib
            bytes >= MIB -> bytes / MIB.toDouble() to SizeUnit.Mib
            else -> bytes / KIB.toDouble() to SizeUnit.Kib
        }
        return formatDecimal(value, locale, if (value < 10) 1 else 0) to unit
    }

    enum class SizeUnit { Kib, Mib, Gib }

    /** One day of `GET …/usage-days`; null for a day the app cannot read. */
    fun fromServer(day: UsageDayResponse): DayUsage? {
        val date = try {
            LocalDate.parse(day.day ?: return null)
        } catch (_: java.time.format.DateTimeParseException) {
            return null
        }
        return DayUsage(date, (day.usedMib ?: 0L).coerceAtLeast(0L) * MIB)
    }

    private const val KIB = 1024L
    private const val MIB = KIB * 1024
    private const val GIB = MIB * 1024
}

/** Whose traffic the chart shows: this phone ([DailyUsage]) or one account service (the server's readings). */
data class UsageSource(val subscriptionId: String?, val title: String?) {
    companion object {
        val Phone = UsageSource(null, null)
    }
}

data class UsageUiState(
    val range: Int = 7,
    val days: List<DayUsage> = emptyList(),
    val sources: List<UsageSource> = listOf(UsageSource.Phone),
    val source: UsageSource = UsageSource.Phone,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

class UsageViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow(UsageUiState(sources = sources()))
    val uiState: StateFlow<UsageUiState> = state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun setRange(days: Int) {
        state.value = state.value.copy(range = days)
        load()
    }

    fun setSource(source: UsageSource) {
        state.value = state.value.copy(source = source)
        load()
    }

    private fun load() {
        val current = state.value
        // A newer choice replaces the answer still on its way.
        loadJob?.cancel()
        state.value = current.copy(loading = current.source.subscriptionId != null, failed = false)
        loadJob = viewModelScope.launch {
            val id = current.source.subscriptionId
            val list = if (id == null) {
                withContext(Dispatchers.IO) { DailyUsage.open().lastDays(LocalDate.now(), current.range) }
            } else {
                try {
                    GeekGraph.api.usageDays(id, current.range).mapNotNull { UsageChart.fromServer(it) }
                } catch (e: java.io.IOException) {
                    LogUtil.w(AppConfig.TAG, "Usage: service history failed", e)
                    state.value = state.value.copy(days = emptyList(), loading = false, failed = true)
                    return@launch
                }
            }
            state.value = state.value.copy(days = list, loading = false, failed = false)
        }
    }

    /** This phone, then every account service (signed in only: the history is on the server). */
    private fun sources(): List<UsageSource> {
        if (GeekGraph.session.session.value !is Session.SignedIn) return listOf(UsageSource.Phone)
        val services = GeekGraph.accountStore.services.value
            .filter { !it.subscriptionId.isNullOrBlank() }
            .map { UsageSource(it.subscriptionId, it.productNameFa ?: it.planNameFa) }
        return listOf(UsageSource.Phone) + services
    }
}

/** "مصرف روزانه" ([DailyUsage]): this phone's VPN traffic per day. */
class UsageActivity : BaseComponentActivity() {
    private val viewModel: UsageViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GeekBackdrop {
                UsageScreen(state, onRange = viewModel::setRange, onSource = viewModel::setSource, onBack = ::finish)
            }
        }
    }
}

@Composable
fun UsageScreen(state: UsageUiState, onRange: (Int) -> Unit, onSource: (UsageSource) -> Unit, onBack: () -> Unit) {
    val colors = Geek.colors
    val locale = appLocale()
    val summary = UsageChart.summary(state.days)
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ArrowForward, stringResource(R.string.geek_servers_back_description), onBack)
            Text(stringResource(R.string.geek_usage_title), style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onBackground)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.sources.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.sources.forEach { source ->
                        SourceChip(
                            text = source.title ?: stringResource(
                                if (source.subscriptionId == null) R.string.geek_usage_source_phone else R.string.geek_usage_source_service,
                            ),
                            selected = source == state.source,
                            onClick = { onSource(source) },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to R.string.geek_usage_week, 30 to R.string.geek_usage_month).forEach { (days, label) ->
                    val selected = state.range == days
                    GlassSurface(
                        kind = if (selected) GlassKind.Milk else GlassKind.Clear,
                        shape = Geek.shapes.pill,
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp).clip(Geek.shapes.pill).clickable(role = Role.Tab) { onRange(days) },
                    ) {
                        Text(
                            stringResource(label),
                            style = Geek.type.button.copy(fontSize = 14.sp),
                            color = if (selected) colors.onGlass else colors.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }

            GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.tileLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.geek_usage_total), style = Geek.type.label, color = colors.onGlassMuted)
                    SizeText(summary.total, locale, big = true)
                    when {
                        state.loading -> Text(
                            stringResource(R.string.geek_usage_loading),
                            style = Geek.type.caption,
                            color = colors.onGlassMuted,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        state.failed -> Text(
                            stringResource(R.string.geek_usage_failed),
                            style = Geek.type.caption,
                            color = colors.danger,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Chart(state.days, locale)
                    if (state.days.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(formatDate("${state.days.first().day}T12:00:00Z", locale).orEmpty(), style = Geek.type.micro, color = colors.onGlassMuted, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.geek_usage_today), style = Geek.type.micro, color = colors.onGlassMuted)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(stringResource(R.string.geek_usage_average), summary.average, locale, Modifier.weight(1f))
                Stat(stringResource(R.string.geek_usage_peak), summary.peak, locale, Modifier.weight(1f))
            }
            Text(
                stringResource(if (state.source.subscriptionId == null) R.string.geek_usage_note else R.string.geek_usage_note_service),
                style = Geek.type.caption.copy(fontSize = 12.sp),
                color = colors.onBackgroundMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun SourceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Geek.colors
    GlassSurface(
        kind = if (selected) GlassKind.Milk else GlassKind.Clear,
        shape = Geek.shapes.pill,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(Geek.shapes.pill)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        Text(
            text,
            style = Geek.type.button.copy(fontSize = 14.sp),
            color = if (selected) colors.onGlass else colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp).widthIn(max = 200.dp),
        )
    }
}

@Composable
private fun Chart(days: List<DayUsage>, locale: Locale) {
    val colors = Geek.colors
    val heights = UsageChart.heights(days)
    val total = UsageChart.summary(days).total
    val (value, unit) = UsageChart.size(total, locale)
    val description = stringResource(R.string.geek_usage_chart_description, days.size, value, stringResource(unitLabel(unit)))
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }) {
        if (heights.isEmpty()) return@Canvas
        val slot = size.width / heights.size
        val barWidth = slot * 0.6f
        heights.forEachIndexed { index, fraction ->
            // Days run in reading order: in Persian the latest day is at the left.
            val slotIndex = if (layoutDirection == LayoutDirection.Rtl) heights.size - 1 - index else index
            val barHeight = (size.height * fraction).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = if (index == heights.lastIndex) colors.action else colors.soft,
                topLeft = Offset(slotIndex * slot + (slot - barWidth) / 2, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
    }
}

@Composable
private fun Stat(label: String, bytes: Long, locale: Locale, modifier: Modifier) {
    val colors = Geek.colors
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.tile, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = Geek.type.caption, color = colors.onGlassMuted, maxLines = 1)
            SizeText(bytes, locale, big = false)
        }
    }
}

@Composable
private fun SizeText(bytes: Long, locale: Locale, big: Boolean) {
    val (value, unit) = UsageChart.size(bytes, locale)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            value,
            // No negative tracking and no clipping: with Persian digits (a fallback
            // face) the measured width came out short and the last digit was cut.
            style = if (big) Geek.type.numberLarge.copy(fontSize = 40.sp, letterSpacing = 0.sp) else Geek.type.numberSmall,
            color = Geek.colors.onGlass,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
        Text(stringResource(unitLabel(unit)), style = Geek.type.caption, color = Geek.colors.onGlassMuted)
    }
}

private fun unitLabel(unit: UsageChart.SizeUnit): Int = when (unit) {
    UsageChart.SizeUnit.Kib -> R.string.geek_usage_kb
    UsageChart.SizeUnit.Mib -> R.string.geek_usage_mb
    UsageChart.SizeUnit.Gib -> R.string.geek_usage_gb
}
