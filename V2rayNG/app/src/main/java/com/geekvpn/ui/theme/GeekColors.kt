package com.geekvpn.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * GeekVPN's colour tokens. The light values are Foundations.html ("Sky Lens")
 * verbatim; every other colour in the Geek UI must come from here.
 */
@Immutable
data class GeekColors(
    val isDark: Boolean,
    /** Screen background ("bg"). */
    val background: Color,
    /** Filled circles of the backdrop. */
    val backdropGlow: Color,
    val backdropGlowStrong: Color,
    /** Hairline rings of the backdrop and the faint logo watermark. */
    val backdropLine: Color,
    val watermark: Color,
    /** "logo blue": accents, switch knob, selected check. */
    val logoBlue: Color,
    /** Marks drawn on the [action] colour: the knob of an on switch, a check mark. */
    val actionAccent: Color,
    /** "navy": primary actions. */
    val action: Color,
    val onAction: Color,
    /** "milk glass": opaque-ish cards and sheets. */
    val milkGlass: Color,
    val milkGlassBorder: Color,
    val milkGlassHighlight: Color,
    /** "clear glass": 13% white tiles over the backdrop. */
    val clearGlass: Color,
    val clearGlassBorder: Color,
    val clearGlassHighlight: Color,
    /** Text on the backdrop and on clear glass. */
    val onBackground: Color,
    val onBackgroundMuted: Color,
    /** Text on milk glass. */
    val onGlass: Color,
    /** "text2": secondary text on milk glass. */
    val onGlassMuted: Color,
    /** "soft": chips, secondary tiles and inputs on milk glass. */
    val soft: Color,
    val softButton: Color,
    /** Inactive tracks: switch off, empty signal bars, hairlines on milk glass. */
    val track: Color,
    val checkboxBorder: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val dangerBorder: Color,
    val shadow: Color,
    val actionShadow: Color,
)

internal val LightGeekColors = GeekColors(
    isDark = false,
    background = Color(0xFF0870D4),
    backdropGlow = Color(0x8C00ACFE), // #00ACFE @ 55%
    backdropGlowStrong = Color(0x7300ACFE), // #00ACFE @ 45%
    backdropLine = Color(0xFFFFFFFF),
    watermark = Color(0x0FFFFFFF), // white @ 6%
    logoBlue = Color(0xFF00ACFE),
    actionAccent = Color(0xFF00ACFE),
    action = Color(0xFF062845),
    onAction = Color(0xFFFFFFFF),
    milkGlass = Color(0xE0FFFFFF), // white @ 88%
    milkGlassBorder = Color(0xF2FFFFFF), // white @ 95%
    milkGlassHighlight = Color(0xFFFFFFFF),
    clearGlass = Color(0x21FFFFFF), // white @ 13%
    clearGlassBorder = Color(0x57FFFFFF), // white @ 34%
    clearGlassHighlight = Color(0x73FFFFFF), // white @ 45%
    onBackground = Color(0xFFFFFFFF),
    onBackgroundMuted = Color(0xD9FFFFFF),
    onGlass = Color(0xFF062845),
    onGlassMuted = Color(0xFF3F5F7E),
    soft = Color(0xFFEAF5FD),
    softButton = Color(0xFFEEF5FB),
    track = Color(0xFFD5E3EF),
    checkboxBorder = Color(0xFFC9D9E7),
    success = Color(0xFF0E9F6E),
    successSoft = Color(0xFFE3F6EE),
    warning = Color(0xFFC77700),
    warningSoft = Color(0xFFFFF4E0),
    danger = Color(0xFFD93F48),
    dangerSoft = Color(0xFFFFF1F2),
    dangerBorder = Color(0xFFF7C9CD),
    shadow = Color(0x47022454), // rgba(2,36,84,.28)
    actionShadow = Color(0x59021838), // rgba(2,24,56,.35)
)

/**
 * Derived from the same tokens: the backdrop sinks from "bg" towards "navy",
 * milk glass becomes navy glass, and the action colour moves to "logo blue"
 * because navy on a navy card has no contrast. Status colours are lifted so
 * they keep ~4.5:1 on the dark glass.
 */
internal val DarkGeekColors = GeekColors(
    isDark = true,
    background = Color(0xFF041D38),
    backdropGlow = Color(0x5C0870D4), // bg @ 36%
    backdropGlowStrong = Color(0x4D00ACFE), // logo blue @ 30%
    backdropLine = Color(0xFFFFFFFF),
    watermark = Color(0x0AFFFFFF), // white @ 4%
    logoBlue = Color(0xFF00ACFE),
    actionAccent = Color(0xFF031B33),
    action = Color(0xFF00ACFE),
    onAction = Color(0xFF031B33),
    milkGlass = Color(0xE00B2A4A), // navy glass @ 88%
    milkGlassBorder = Color(0x26FFFFFF),
    milkGlassHighlight = Color(0x33FFFFFF),
    clearGlass = Color(0x17FFFFFF), // white @ 9%
    clearGlassBorder = Color(0x33FFFFFF),
    clearGlassHighlight = Color(0x40FFFFFF),
    onBackground = Color(0xFFFFFFFF),
    onBackgroundMuted = Color(0xC7FFFFFF),
    onGlass = Color(0xFFEAF5FD),
    onGlassMuted = Color(0xFF9DB7CF),
    soft = Color(0xFF11375C),
    softButton = Color(0xFF123B61),
    track = Color(0xFF24496D),
    checkboxBorder = Color(0xFF3B6187),
    success = Color(0xFF3CC896),
    successSoft = Color(0xFF0E3B33),
    warning = Color(0xFFF0A63A),
    warningSoft = Color(0xFF3D2E12),
    danger = Color(0xFFF26B73),
    dangerSoft = Color(0xFF40202A),
    dangerBorder = Color(0xFF6B2E3A),
    shadow = Color(0x66000814),
    actionShadow = Color(0x4D00ACFE),
)

val LocalGeekColors = staticCompositionLocalOf { LightGeekColors }
