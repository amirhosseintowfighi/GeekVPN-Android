package com.geekvpn.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.util.QRCodeDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [text] as a QR code, drawn off the main thread. White behind the code
 * whatever the theme: scanners want dark on light.
 */
@Composable
fun QrCode(text: String, description: String, size: Dp, modifier: Modifier = Modifier) {
    val qr by produceState<Bitmap?>(null, text) {
        value = withContext(Dispatchers.Default) { QRCodeDecoder.createQRCode(text, QR_PX) }
    }
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = qr
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = description, modifier = Modifier.fillMaxWidth())
        } else {
            CircularProgressIndicator(color = Geek.colors.action, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

private const val QR_PX = 720
