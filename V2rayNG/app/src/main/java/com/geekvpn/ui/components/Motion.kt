package com.geekvpn.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/**
 * GeekVPN's motion: short, eased, and in the reading direction. Durations
 * follow the system's animator scale, so "remove animations" in the
 * accessibility settings turns all of it off.
 */
object GeekMotion {
    const val PAGE_MS = 320
    const val FADE_OUT_MS = 180
    const val SHEET_MS = 300

    /** A card or section that comes and goes: it unfolds and fades in. */
    val Reveal: EnterTransition = expandVertically(tween(PAGE_MS)) + fadeIn(tween(PAGE_MS))
    val Conceal: ExitTransition = shrinkVertically(tween(PAGE_MS)) + fadeOut(tween(FADE_OUT_MS))
}

/**
 * Moving to the next page (a tab further along, or a screen opened on top)
 * brings it in from the side the reading goes towards: from the left in
 * Persian, from the right in English. Going back is the mirror image.
 */
fun <S> AnimatedContentTransitionScope<S>.geekPage(forward: Boolean, rtl: Boolean): ContentTransform {
    val from = if (forward != rtl) 1 else -1
    val spec = tween<IntOffset>(GeekMotion.PAGE_MS, easing = FastOutSlowInEasing)
    return (slideInHorizontally(spec) { width -> from * width / 5 } + fadeIn(tween(GeekMotion.PAGE_MS))) togetherWith
        (slideOutHorizontally(spec) { width -> -from * width / 5 } + fadeOut(tween(GeekMotion.FADE_OUT_MS)))
}
