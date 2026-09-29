package com.geekvpn.ui.common

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Phones, tablets and TVs: what the screens change for each. */
object DeviceKind {
    /** Android TV / Google TV: a remote, no touch, usually no Telegram. */
    fun isTv(context: Context): Boolean =
        context.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

/** Widest a screen's content column gets: on a tablet or a TV, cards stay phone-shaped and centred. */
val CONTENT_MAX_WIDTH = 600.dp

/** Caps a content column at [CONTENT_MAX_WIDTH]; the caller centres it. */
fun Modifier.contentWidth(): Modifier = widthIn(max = CONTENT_MAX_WIDTH)
