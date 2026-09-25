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

/** Persian text. Static weights, bundled in res/font by branding/gen_fonts.py. */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_light, FontWeight.Light),
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_extrabold, FontWeight.ExtraBold),
)

/** Latin, latency, speeds and timers. */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/** The type scale of Foundations.html ("تایپوگرافی"). Sizes are the design's px as sp. */
@Immutable
data class GeekTypography(
    /** Page title, 26–30. */
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
)

internal val DefaultGeekTypography = GeekTypography(
    pageTitle = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp),
    sectionTitle = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
    label = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp),
    row = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    button = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    body = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    caption = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    micro = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 11.sp),
    // Numbers, timers and Latin read left to right even inside RTL text (the design's dir="ltr").
    numberLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.02).em,
        textDirection = TextDirection.Ltr,
    ),
    numberMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.025).em,
        textDirection = TextDirection.Ltr,
    ),
    numberSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
        textDirection = TextDirection.Ltr,
    ),
)

val LocalGeekTypography = staticCompositionLocalOf { DefaultGeekTypography }
