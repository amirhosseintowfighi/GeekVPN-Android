package com.geekvpn.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.v2ray.ang.ui.compose.resolveDarkTheme

/** Corner radii used by the design. */
@Immutable
data class GeekShapes(
    val card: RoundedCornerShape = RoundedCornerShape(24.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val row: RoundedCornerShape = RoundedCornerShape(22.dp),
    val tileLarge: RoundedCornerShape = RoundedCornerShape(20.dp),
    val tile: RoundedCornerShape = RoundedCornerShape(17.dp),
    val primaryButton: RoundedCornerShape = RoundedCornerShape(18.dp),
    val button: RoundedCornerShape = RoundedCornerShape(16.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(13.dp),
    val badge: RoundedCornerShape = RoundedCornerShape(12.dp),
    val switchTrack: RoundedCornerShape = RoundedCornerShape(9.dp),
    val switchKnob: RoundedCornerShape = RoundedCornerShape(7.dp),
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
 * The GeekVPN theme. Light, dark or following the system is the same setting
 * the advanced (v2rayNG) screens use, [com.v2ray.ang.ui.compose.ThemeManager],
 * so the Account screen and Settings can never disagree.
 *
 * A Material colour scheme and Vazirmatn typography are derived from the Geek
 * tokens so Material widgets used inside Geek screens (text fields, dialogs,
 * ripples) match without restyling each one.
 */
@Composable
fun GeekTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkGeekColors else LightGeekColors
    val type = DefaultGeekTypography

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Every Geek screen sits on the blue (or navy) backdrop, so bar icons are always light.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.action,
            onPrimary = colors.onAction,
            secondary = colors.logoBlue,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.milkGlass,
            onSurface = colors.onGlass,
            surfaceVariant = colors.soft,
            onSurfaceVariant = colors.onGlassMuted,
            outline = colors.track,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.action,
            onPrimary = colors.onAction,
            secondary = colors.logoBlue,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.milkGlass,
            onSurface = colors.onGlass,
            surfaceVariant = colors.soft,
            onSurfaceVariant = colors.onGlassMuted,
            outline = colors.track,
            error = colors.danger,
        )
    }
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
