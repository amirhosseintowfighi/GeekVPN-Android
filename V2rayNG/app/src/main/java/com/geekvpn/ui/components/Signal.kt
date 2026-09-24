package com.geekvpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekColors
import com.v2ray.ang.R

enum class SignalTone { Good, Fair, Poor, Unknown }

/** How many of the four bars light up, and in which colour. */
data class Signal(val bars: Int, val tone: SignalTone)

/**
 * Latency to signal bars, as drawn in Servers.html: 96–142ms four green,
 * 168–233ms three green, 388ms two amber, 410ms one red. The cut points sit
 * between those samples. A non-positive delay is v2rayNG's "not tested" (0)
 * or "failed" (-1): no bars.
 */
object SignalQuality {
    const val GOOD_MAX_MS = 150L
    const val OK_MAX_MS = 250L
    const val FAIR_MAX_MS = 400L

    fun of(delayMs: Long): Signal = when {
        delayMs <= 0L -> Signal(0, SignalTone.Unknown)
        delayMs <= GOOD_MAX_MS -> Signal(4, SignalTone.Good)
        delayMs <= OK_MAX_MS -> Signal(3, SignalTone.Good)
        delayMs <= FAIR_MAX_MS -> Signal(2, SignalTone.Fair)
        else -> Signal(1, SignalTone.Poor)
    }
}

private fun GeekColors.of(tone: SignalTone): Color = when (tone) {
    SignalTone.Good -> success
    SignalTone.Fair -> warning
    SignalTone.Poor -> danger
    SignalTone.Unknown -> track
}

/** Four rising bars (5, 9, 13, 17dp), always laid out left to right. */
@Composable
fun SignalBars(signal: Signal, modifier: Modifier = Modifier) {
    val colors = Geek.colors
    val lit = colors.of(signal.tone)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.height(17.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(5, 9, 13, 17).forEachIndexed { index, height ->
                Box(
                    Modifier
                        .size(width = 4.dp, height = height.dp)
                        .background(if (index < signal.bars) lit else colors.track, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/**
 * "121ms" followed by its bars, read out as one phrase. Latin digits for ms,
 * as in the design.
 */
@Composable
fun LatencyIndicator(delayMs: Long, modifier: Modifier = Modifier) {
    val signal = SignalQuality.of(delayMs)
    val description = if (delayMs > 0) {
        stringResource(R.string.geek_latency_ms, delayMs)
    } else {
        stringResource(R.string.geek_latency_unknown)
    }
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = if (delayMs > 0) "${delayMs}ms" else "—",
                style = Geek.type.numberSmall,
                color = Geek.colors.onGlassMuted,
                // Fixed width keeps the bars of a list in one column.
                modifier = Modifier.width(LatencyTextWidth),
            )
        }
        SignalBars(signal)
    }
}

private val LatencyTextWidth = 46.dp
