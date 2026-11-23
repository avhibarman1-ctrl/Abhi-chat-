package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class PickedMediaPayload(
    val mediaType: String, // "IMAGE" or "VIDEO"
    val dataUri: String,
    val durationSec: Long = 0L
)

object MediaUtils {
    private const val MAX_DIMENSION = 720
    private const val MAX_BYTES = 220_000

    suspend fun processPickedMedia(context: Context, uri: Uri): PickedMediaPayload? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri).orEmpty()
            if (mimeType.startsWith("video/")) {
                extractVideoThumbnail(context, uri)
            } else {
                compressImageToDataUri(context, uri)?.let { dataUri ->
                    PickedMediaPayload(mediaType = "IMAGE", dataUri = dataUri, durationSec = 0L)
                }
            }
        }

    suspend fun compressImageToDataUri(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
                val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, MAX_DIMENSION)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return@withContext null

                val scaled = scaleBitmap(decoded, MAX_DIMENSION)
                if (scaled !== decoded) decoded.recycle()
                val encoded = encodeJpegToBase64(scaled)
                scaled.recycle()
                "data:image/jpeg;base64,$encoded"
            } catch (_: Exception) {
                null
            }
        }

    private fun extractVideoThumbnail(context: Context, uri: Uri): PickedMediaPayload? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val thumbDataUri = if (frame != null) {
                val scaled = scaleBitmap(frame, 640)
                if (scaled !== frame) frame.recycle()
                val encoded = encodeJpegToBase64(scaled)
                scaled.recycle()
                "data:image/jpeg;base64,$encoded"
            } else {
                ""
            }
            PickedMediaPayload(
                mediaType = "VIDEO",
                dataUri = thumbDataUri,
                durationSec = (durationMs / 1000L).coerceAtLeast(1L).coerceAtMost(3600L)
            )
        } catch (_: Exception) {
            PickedMediaPayload(mediaType = "VIDEO", dataUri = "", durationSec = 5L)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val largest = max(width, height)
        if (largest > maxDim) {
            while (largest / inSampleSize > maxDim * 2) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val largest = max(width, height)
        if (largest <= maxDim || width <= 0 || height <= 0) return bitmap
        val ratio = maxDim.toFloat() / largest.toFloat()
        val targetW = (width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun encodeJpegToBase64(bitmap: Bitmap): String {
        var quality = 78
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 12
        } while (bytes.size > MAX_BYTES && quality >= 26)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decodeDataUriBitmap(dataUri: String): Bitmap? {
        if (!dataUri.startsWith("data:image")) return null
        val commaIdx = dataUri.indexOf(',')
        if (commaIdx < 0) return null
        return try {
            val raw = Base64.decode(dataUri.substring(commaIdx + 1), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(raw, 0, raw.size)
        } catch (_: Exception) {
            null
        }
    }
}
