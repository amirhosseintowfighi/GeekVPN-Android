package com.geekvpn.ui.services

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geekvpn.ui.components.QrCode
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.R

/**
 * A service's subscription link as a QR code, for setting it up on another
 * device (a PC client, a second phone), with copy and share. Anyone holding
 * the link uses the service's volume, which the dialog says.
 */
@Composable
fun LinkQrDialog(
    url: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    /** «لینک جدید», asked once more before it runs; null hides it. */
    onRotate: (() -> Unit)? = null,
) {
    val colors = Geek.colors
    val qrLabel = stringResource(R.string.geek_services_qr_description)
    var confirmRotate by rememberSaveable { mutableStateOf(false) }
    if (confirmRotate && onRotate != null) {
        AlertDialog(
            onDismissRequest = { confirmRotate = false },
            title = { Text(stringResource(R.string.geek_rotate_title), style = Geek.type.sectionTitle) },
            text = { Text(stringResource(R.string.geek_rotate_text), style = Geek.type.body) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRotate = false
                    onRotate()
                }) { Text(stringResource(R.string.geek_rotate_confirm), style = Geek.type.button, color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRotate = false }) { Text(stringResource(R.string.geek_account_cancel), style = Geek.type.button) }
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.geek_services_qr_title), style = Geek.type.sectionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                QrCode(url, qrLabel, 220.dp)
                Text(
                    url,
                    style = Geek.type.caption.copy(textDirection = TextDirection.Ltr),
                    color = colors.onGlassMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.geek_services_qr_warning), style = Geek.type.caption, color = colors.warning)
                if (onRotate != null) {
                    TextButton(onClick = { confirmRotate = true }) {
                        Text(stringResource(R.string.geek_rotate_action), style = Geek.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.link)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onShare) { Text(stringResource(R.string.geek_services_share), style = Geek.type.button) }
        },
        dismissButton = {
            TextButton(onClick = onCopy) { Text(stringResource(R.string.geek_services_copy), style = Geek.type.button) }
        },
    )
}

