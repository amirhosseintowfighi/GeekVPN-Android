package com.geekvpn.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/** Corner radii used by the design. */
@Immutable
data class GeekShapes(
    val card: RoundedCornerShape = RoundedCornerShape(26.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    val row: RoundedCornerShape = RoundedCornerShape(22.dp),
    val tileLarge: RoundedCornerShape = RoundedCornerShape(26.dp),
    val tile: RoundedCornerShape = RoundedCornerShape(22.dp),
    // Every button of Dark Swiss is a full pill.
    val primaryButton: RoundedCornerShape = RoundedCornerShape(50),
    val button: RoundedCornerShape = RoundedCornerShape(50),
    val pill: RoundedCornerShape = RoundedCornerShape(50),
    val badge: RoundedCornerShape = RoundedCornerShape(50),
    val switchTrack: RoundedCornerShape = RoundedCornerShape(50),
    val switchKnob: RoundedCornerShape = RoundedCornerShape(50),
    val checkbox: RoundedCornerShape = RoundedCornerShape(8.dp),
)

val LocalGeekShapes = staticCompositionLocalOf { GeekShapes() }

/** Entry point for the Geek UI tokens inside [GeekTheme]. */
object Geek {
    val colors: GeekColors
        @Composable @ReadOnlyComposable get() = LocalGeekColors.current
    val type: GeekTypography
        @Composable @ReadOnlyComposable get() = LocalGeekTypography.current
    val shapes: GeekShapes
        @Composable @ReadOnlyComposable get() = LocalGeekShapes.current
}

/**
 * The GeekVPN theme: always Dark Swiss, whatever v2rayNG's own light/dark
 * setting says (the design has a single, dark palette).
 *
 * A Material colour scheme and typography are derived from the Geek tokens so
 * Material widgets used inside Geek screens (text fields, dialogs, ripples)
 * match without restyling each one.
 */
@Composable
fun GeekTheme(content: @Composable () -> Unit) {
    val colors = SwissGeekColors
    val type = DefaultGeekTypography

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Every Geek screen sits on the dark ground, so bar icons are always light.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    val scheme = darkColorScheme(
        primary = colors.action,
        onPrimary = colors.onAction,
        secondary = colors.logoBlue,
        background = colors.background,
        onBackground = colors.onBackground,
        surface = SheetBase,
        onSurface = colors.onGlass,
        surfaceVariant = colors.soft,
        onSurfaceVariant = colors.onGlassMuted,
        outline = colors.checkboxBorder,
        error = colors.danger,
    )
    val material = Typography().let { base ->
        Typography(
            displayLarge = base.displayLarge.copy(fontFamily = Vazirmatn),
            displayMedium = base.displayMedium.copy(fontFamily = Vazirmatn),
            displaySmall = base.displaySmall.copy(fontFamily = Vazirmatn),
            headlineLarge = base.headlineLarge.copy(fontFamily = Vazirmatn),
            headlineMedium = base.headlineMedium.copy(fontFamily = Vazirmatn),
            headlineSmall = base.headlineSmall.copy(fontFamily = Vazirmatn),
            titleLarge = base.titleLarge.copy(fontFamily = Vazirmatn),
            titleMedium = base.titleMedium.copy(fontFamily = Vazirmatn),
            titleSmall = base.titleSmall.copy(fontFamily = Vazirmatn),
            bodyLarge = base.bodyLarge.copy(fontFamily = Vazirmatn),
            bodyMedium = base.bodyMedium.copy(fontFamily = Vazirmatn),
            bodySmall = base.bodySmall.copy(fontFamily = Vazirmatn),
            labelLarge = base.labelLarge.copy(fontFamily = Vazirmatn),
            labelMedium = base.labelMedium.copy(fontFamily = Vazirmatn),
            labelSmall = base.labelSmall.copy(fontFamily = Vazirmatn),
        )
    }

    CompositionLocalProvider(
        LocalGeekColors provides colors,
        LocalGeekTypography provides type,
        LocalGeekShapes provides GeekShapes(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = material, content = content)
    }
}
