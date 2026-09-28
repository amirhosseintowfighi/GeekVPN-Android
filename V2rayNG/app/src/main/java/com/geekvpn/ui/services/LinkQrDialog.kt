package com.geekvpn.ui.services

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.R
import com.v2ray.ang.util.QRCodeDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A service's subscription link as a QR code, for setting it up on another
 * device (a PC client, a second phone), with copy and share. Anyone holding
 * the link uses the service's volume, which the dialog says.
 */
@Composable
fun LinkQrDialog(url: String, onCopy: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit) {
    val colors = Geek.colors
    val qr by produceState<Bitmap?>(null, url) {
        value = withContext(Dispatchers.Default) { QRCodeDecoder.createQRCode(url, QR_PX) }
    }
    val qrLabel = stringResource(R.string.geek_services_qr_description)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.geek_services_qr_title), style = Geek.type.sectionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // White behind the code whatever the theme: scanners want dark on light.
                Box(
                    modifier = Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = qr
                    if (bitmap != null) {
                        Image(bitmap.asImageBitmap(), contentDescription = qrLabel, modifier = Modifier.fillMaxWidth())
                    } else {
                        CircularProgressIndicator(color = colors.action, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
                Text(
                    url,
                    style = Geek.type.caption.copy(textDirection = TextDirection.Ltr),
                    color = colors.onGlassMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.geek_services_qr_warning), style = Geek.type.caption, color = colors.warning)
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

private const val QR_PX = 720
