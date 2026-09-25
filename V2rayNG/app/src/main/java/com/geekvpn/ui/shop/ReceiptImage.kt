package com.geekvpn.ui.shop

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A receipt photo, made small enough to upload over a slow mobile link and
 * still readable by the operator: at most [MAX_EDGE] pixels on its long side,
 * as JPEG. Blocking; call it off the main thread.
 */
object ReceiptImage {
    const val MAX_EDGE = 1600
    private const val QUALITY = 85

    /** Null when the picked file is not an image Android can decode. */
    @Throws(IOException::class)
    fun read(resolver: ContentResolver, uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE) }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val (width, height) = fitted(decoded.width, decoded.height, MAX_EDGE)
        val scaled = if (width != decoded.width) Bitmap.createScaledBitmap(decoded, width, height, true) else decoded
        return try {
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                out.toByteArray()
            }
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }

    /** The largest power of two that keeps the long side at or above [maxEdge] after decoding. */
    fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    /** [width] x [height] scaled down, keeping its shape, so the long side is at most [maxEdge]. */
    fun fitted(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        val long = max(width, height)
        if (long <= maxEdge) return width to height
        val ratio = maxEdge.toDouble() / long
        return (width * ratio).roundToInt().coerceAtLeast(1) to (height * ratio).roundToInt().coerceAtLeast(1)
    }
}
