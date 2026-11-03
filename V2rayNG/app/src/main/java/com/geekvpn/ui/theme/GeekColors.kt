package com.geekvpn.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** GeekVPN's colour tokens; every colour in the Geek UI must come from here. */
@Immutable
data class GeekColors(
    val isDark: Boolean,
    /** Screen background ("bg"). */
    val background: Color,
    /** Filled circles of the backdrop. */
    val backdropGlow: Color,
    val backdropGlowStrong: Color,
    /** Hairline rings of the backdrop. */
    val backdropLine: Color,
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
    /** Text links on glass ("شروع سریع بدون ثبت‌نام"). */
    val link: Color,
    /** "soft": chips, secondary tiles and inputs on milk glass. */
    val soft: Color,
    val softButton: Color,
    /** Inactive tracks: switch off, empty signal bars, hairlines on milk glass. */
    val track: Color,
    val checkboxBorder: Color,
    val success: Color,
    val successSoft: Color,
    /** The "connected" dot on the blue backdrop (Home-On.html), brighter than [success]. */
    val successBright: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val dangerBorder: Color,
    val shadow: Color,
    /** Behind a sheet (Route.html): the screen dims to navy. */
    val scrim: Color,
    /**
     * The tab bar's dock: "bg" nearly opaque, so cards scrolling under the bar
     * don't show through its clear-glass tiles.
     */
    val navDock: Color,
    /** Unselected choice chips on milk glass (Shop.html's white buttons). */
    val chip: Color,
    /** The shop's pay bar and its cyan button (Shop.html). */
    val payBar: Color,
    val onPayBar: Color,
    val payButton: Color,
    val onPayButton: Color,
    /** The card-to-card card (Deposit.html). */
    val bankCard: Color,
    val onBankCard: Color,
    val actionShadow: Color,
    /** The 1dp lines of the Swiss grid: under the header, between rows. */
    val hairline: Color,
)

/**
 * "Dark Swiss" (the GeekVPN Client canvas, Foundations.dc.html): a near-black
 * ground lit by one blue glow per screen, frosted glass only for the main
 * controls, and solid white for actions and selected states, with navy text on
 * them. There is one palette; the design has no light variant.
 */
internal val SwissGeekColors = GeekColors(
    isDark = true,
    background = Color(0xFF02040E),
    backdropGlow = Color(0xFF1A2FFF),
    backdropGlowStrong = Color(0xFF3D7BFF),
    backdropLine = Color(0xFFBFD8FF),
    // "آبی متن": the blue words in titles, links and accents.
    logoBlue = Color(0xFF4D8DFF),
    actionAccent = Color(0xFF07123A),
    action = Color(0xFFFFFFFF),
    onAction = Color(0xFF07123A),
    // Cards are frosted glass (see GlassSurface); this is the base under its gradient.
    milkGlass = Color(0x0FFFFFFF),
    milkGlassBorder = Color(0x66FFFFFF), // white @ 40%
    milkGlassHighlight = Color(0x73FFFFFF), // white @ 45%, the inset top edge
    clearGlass = Color(0x0FFFFFFF),
    clearGlassBorder = Color(0x66FFFFFF),
    clearGlassHighlight = Color(0x73FFFFFF),
    onBackground = Color(0xFFFFFFFF),
    onBackgroundMuted = Color(0xB8FFFFFF), // white @ 72%
    onGlass = Color(0xFFFFFFFF),
    onGlassMuted = Color(0xB8FFFFFF),
    link = Color(0xFF4D8DFF),
    soft = Color(0x1AFFFFFF), // white @ 10%: inputs, quiet chips
    softButton = Color(0x1FFFFFFF), // white @ 12%
    track = Color(0x29FFFFFF), // white @ 16%: progress tracks, the off switch
    checkboxBorder = Color(0x59FFFFFF), // white @ 35%
    success = Color(0xFF5BE3A8),
    successSoft = Color(0x2E5BE3A8),
    warning = Color(0xFFFFC25C),
    warningSoft = Color(0x2EFFC25C),
    successBright = Color(0xFF7DFFCB),
    danger = Color(0xFFFF8A9A),
    dangerSoft = Color(0x24FF8A9A),
    dangerBorder = Color(0x66FF8A9A),
    shadow = Color(0x73000418), // rgba(0,4,30,.45)
    scrim = Color(0xC7000208),
    navDock = Color(0xEB0A1138),
    chip = Color(0x14FFFFFF),
    payBar = Color(0xF20A1033),
    onPayBar = Color(0xFFFFFFFF),
    payButton = Color(0xFFFFFFFF),
    onPayButton = Color(0xFF07123A),
    bankCard = Color(0xFF0E1A55),
    onBankCard = Color(0xFFFFFFFF),
    actionShadow = Color(0x73000418),
    hairline = Color(0x38FFFFFF), // white @ 22%
)

/** The frosted glass of Foundations.dc.html: linear-gradient(155deg, white 22%, #78A0FF 10%, #2850DC 16%). */
internal val GlassGradient = listOf(Color(0x38FFFFFF), Color(0x1A78A0FF), Color(0x292850DC))

/** Sheets cover content, so their glass sits on an almost opaque navy. */
internal val SheetBase = Color(0xF5070C26)

val LocalGeekColors = staticCompositionLocalOf { SwissGeekColors }
