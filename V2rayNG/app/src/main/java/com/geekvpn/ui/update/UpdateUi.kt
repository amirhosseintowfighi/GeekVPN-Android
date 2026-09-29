package com.geekvpn.ui.update

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GeekSheet
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.update.UpdateOffer
import com.geekvpn.update.UpdateState
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import java.util.Locale

/** What the update sheet's buttons do. */
interface UpdateActions {
    fun onDownload()
    fun onCancel()
    fun onInstall()
    fun onLater()
    fun onDismiss()
}

/**
 * The strip at the top of Home while a newer version waits. A required
 * update cannot be closed; any other closes until the next version.
 */
@Composable
fun UpdateBanner(offer: UpdateOffer, onOpen: () -> Unit, onClose: () -> Unit) {
    val colors = Geek.colors
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.soft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GeekIcons.Download, contentDescription = null, tint = colors.onGlass, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.geek_update_available, offer.versionName),
                    style = Geek.type.row,
                    color = colors.onGlass,
                )
                Text(
                    stringResource(if (offer.required) R.string.geek_update_required else R.string.geek_update_banner_hint),
                    style = Geek.type.caption.copy(fontSize = 12.sp),
                    color = if (offer.required) colors.danger else colors.onGlassMuted,
                )
            }
            if (!offer.required) {
                val close = stringResource(R.string.geek_update_dismiss_description)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClick = onClose)
                        .semantics { contentDescription = close },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(GeekIcons.Close, contentDescription = null, tint = colors.onGlassMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** The account row's second line: where the update stands. */
@Composable
fun updateHint(state: UpdateState): String = when (state) {
    UpdateState.Checking -> stringResource(R.string.geek_update_checking)
    UpdateState.UpToDate -> stringResource(R.string.geek_update_up_to_date, BuildConfig.VERSION_NAME)
    is UpdateState.Available, is UpdateState.Ready -> stringResource(R.string.geek_update_available, state.offer!!.versionName)
    is UpdateState.Downloading -> downloadingText(state.progress)
    is UpdateState.Failed -> stringResource(state.reason)
    UpdateState.Idle -> stringResource(R.string.geek_update_check_hint, BuildConfig.VERSION_NAME)
}

@Composable
private fun downloadingText(progress: Float?): String =
    if (progress == null) {
        stringResource(R.string.geek_update_downloading_unknown)
    } else {
        stringResource(R.string.geek_update_downloading, (progress * 100).toInt())
    }

/** Release notes, download progress, and the one button that fits the moment. */
@Composable
fun BoxScope.UpdateSheet(state: UpdateState, needsPermission: Boolean, actions: UpdateActions) {
    val colors = Geek.colors
    val offer = state.offer ?: return
    GeekSheet(
        title = stringResource(R.string.geek_update_available, offer.versionName),
        subtitle = stringResource(R.string.geek_account_version, BuildConfig.VERSION_NAME),
        onDismiss = actions::onDismiss,
        closeLabel = stringResource(R.string.geek_sheet_close),
    ) {
        if (offer.required) {
            Text(stringResource(R.string.geek_update_required), style = Geek.type.body, color = colors.danger)
        }
        if (offer.notes.isNotBlank()) {
            Text(stringResource(R.string.geek_update_notes), style = Geek.type.label, color = colors.onGlass)
            Text(
                offer.notes.take(NOTES_LIMIT),
                style = Geek.type.body.copy(fontSize = 13.sp),
                color = colors.onGlassMuted,
            )
        }

        when (state) {
            is UpdateState.Downloading -> {
                ProgressBar(state.progress)
                Text(downloadingText(state.progress), style = Geek.type.caption, color = colors.onGlassMuted)
                GeekSecondaryButton(
                    text = stringResource(R.string.geek_update_cancel),
                    onClick = actions::onCancel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is UpdateState.Ready -> {
                if (needsPermission) {
                    Text(stringResource(R.string.geek_update_permission_hint), style = Geek.type.caption, color = colors.onGlassMuted)
                }
                GeekPrimaryButton(
                    text = stringResource(R.string.geek_update_install),
                    icon = GeekIcons.Check,
                    onClick = actions::onInstall,
                )
            }
            else -> {
                if (state is UpdateState.Failed) {
                    Text(stringResource(state.reason), style = Geek.type.caption, color = colors.danger)
                }
                val size = offer.apk.sizeBytes
                GeekPrimaryButton(
                    text = when {
                        state is UpdateState.Failed -> stringResource(R.string.geek_update_retry)
                        size > 0 -> stringResource(R.string.geek_update_download_size, megabytes(size, appLocale()))
                        else -> stringResource(R.string.geek_update_download)
                    },
                    icon = GeekIcons.Download,
                    onClick = actions::onDownload,
                )
                if (!offer.required) {
                    GeekSecondaryButton(
                        text = stringResource(R.string.geek_update_later),
                        onClick = actions::onLater,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float?) {
    val colors = Geek.colors
    val shown by animateFloatAsState(progress ?: 0f, label = "download")
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.track),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (progress == null) 0.15f else shown)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.action),
        )
    }
}

/** "۳۱٫۲": the number the size string wraps, in the app's digits. */
private fun megabytes(bytes: Long, locale: Locale): String = String.format(locale, "%.1f", bytes / 1_048_576.0)

private const val NOTES_LIMIT = 1_200
