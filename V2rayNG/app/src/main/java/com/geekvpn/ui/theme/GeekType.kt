package com.geekvpn.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

/** Persian text: Yekan Bakh (FaNum), the design's face. SemiBold has no file of its own and resolves to Bold. */
val GeekText = FontFamily(
    Font(R.font.yekan_bakh_light, FontWeight.Light),
    Font(R.font.yekan_bakh_regular, FontWeight.Normal),
    Font(R.font.yekan_bakh_medium, FontWeight.Medium),
    Font(R.font.yekan_bakh_bold, FontWeight.Bold),
    Font(R.font.yekan_bakh_extrabold, FontWeight.ExtraBold),
    Font(R.font.yekan_bakh_black, FontWeight.Black),
)

/** Latin, latency, speeds and timers. */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/** The type scale of Foundations.dc.html (Dark Swiss). Sizes are the design's px as sp. */
@Immutable
data class GeekTypography(
    /** Page title, 34–36 heavy. */
    val pageTitle: TextStyle,
    /** Section title, 20. */
    val sectionTitle: TextStyle,
    /** Group label above a card, 14 extra-bold. */
    val label: TextStyle,
    /** Buttons and row titles, 14–15 bold. */
    val row: TextStyle,
    val button: TextStyle,
    /** Body copy. */
    val body: TextStyle,
    /** Captions, 12–13. */
    val caption: TextStyle,
    /** Navigation labels, 11. */
    val micro: TextStyle,
    /** Big numbers: the connection timer (32). */
    val numberLarge: TextStyle,
    /** Brand word and mid-size numbers (20). */
    val numberMedium: TextStyle,
    /** Latency next to signal bars, country codes (12). */
    val numberSmall: TextStyle,
    /**
     * The Latin micro label above titles and blocks ("STATUS · OFF"), Space Grotesk 10
     * tracked .22em. Callers upper-case the text, as the design's text-transform does.
     */
    val eyebrow: TextStyle,
    /** The hero line of a screen (Main.dc.html's "اینترنت آزاد، …"), 40–44 heavy. */
    val display: TextStyle,
)

internal val DefaultGeekTypography = GeekTypography(
    pageTitle = TextStyle(fontFamily = GeekText, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 41.sp),
    sectionTitle = TextStyle(fontFamily = GeekText, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
    label = TextStyle(fontFamily = GeekText, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp),
    row = TextStyle(fontFamily = GeekText, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    button = TextStyle(fontFamily = GeekText, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
    body = TextStyle(fontFamily = GeekText, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 22.sp),
    caption = TextStyle(fontFamily = GeekText, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    micro = TextStyle(fontFamily = GeekText, fontWeight = FontWeight.Bold, fontSize = 11.sp),
    // Numbers, timers and Latin read left to right even inside RTL text (the design's dir="ltr").
    numberLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 26.sp,
        textDirection = TextDirection.Ltr,
    ),
    numberMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 20.sp,
        textDirection = TextDirection.Ltr,
    ),
    numberSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 13.sp,
        textDirection = TextDirection.Ltr,
    ),
    eyebrow = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.22.em,
        textDirection = TextDirection.Ltr,
    ),
    display = TextStyle(fontFamily = GeekText, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 48.sp),
)

val LocalGeekTypography = staticCompositionLocalOf { DefaultGeekTypography }
