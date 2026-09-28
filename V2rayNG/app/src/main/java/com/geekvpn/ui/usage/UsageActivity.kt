package com.geekvpn.ui.usage

import android.app.Application
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatDate
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.geekvpn.usage.DailyUsage
import com.geekvpn.usage.DayUsage
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
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
        val format = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = if (value < 10) 1 else 0
            minimumFractionDigits = 0
        }
        return format.format(value) to unit
    }

    enum class SizeUnit { Kib, Mib, Gib }

    private const val KIB = 1024L
    private const val MIB = KIB * 1024
    private const val GIB = MIB * 1024
}

data class UsageUiState(val range: Int = 7, val days: List<DayUsage> = emptyList())

class UsageViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow(UsageUiState())
    val uiState: StateFlow<UsageUiState> = state.asStateFlow()

    init {
        setRange(7)
    }

    fun setRange(days: Int) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { DailyUsage.open().lastDays(LocalDate.now(), days) }
            state.value = UsageUiState(days, list)
        }
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
                UsageScreen(state, onRange = viewModel::setRange, onBack = ::finish)
            }
        }
    }
}

@Composable
fun UsageScreen(state: UsageUiState, onRange: (Int) -> Unit, onBack: () -> Unit) {
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
                stringResource(R.string.geek_usage_note),
                style = Geek.type.caption.copy(fontSize = 12.sp),
                color = colors.onBackgroundMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
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
            style = if (big) Geek.type.numberLarge.copy(fontSize = 40.sp) else Geek.type.numberSmall,
            color = Geek.colors.onGlass,
            // One line: the Persian decimal separator is a break opportunity.
            maxLines = 1,
            softWrap = false,
        )
        Text(stringResource(unitLabel(unit)), style = Geek.type.caption, color = Geek.colors.onGlassMuted)
    }
}

private fun unitLabel(unit: UsageChart.SizeUnit): Int = when (unit) {
    UsageChart.SizeUnit.Kib -> R.string.geek_usage_kb
    UsageChart.SizeUnit.Mib -> R.string.geek_usage_mb
    UsageChart.SizeUnit.Gib -> R.string.geek_usage_gb
}
