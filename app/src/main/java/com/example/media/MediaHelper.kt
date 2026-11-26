package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class PreparedImage(
    val jpegBytes: ByteArray,
    val width: Int,
    val height: Int
)

data class PreparedVideo(
    val thumbJpegBytes: ByteArray,
    val videoBytesForCloud: ByteArray?,
    val localVideoPath: String,
    val durationMs: Long,
    val totalSizeBytes: Long
)

object MediaHelper {
    private const val MAX_IMAGE_DIM = 860
    private const val MAX_CLOUD_IMAGE_BYTES = 260_000
    private const val MAX_CLOUD_VIDEO_BYTES = 390_000

    suspend fun prepareImageFromUri(context: Context, uri: Uri): PreparedImage? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }

            var sampleSize = 1
            val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            while (maxDim / sampleSize > MAX_IMAGE_DIM * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                ?: return@withContext null

            val scaled = scaleDownBitmap(rawBitmap, MAX_IMAGE_DIM)
            if (scaled != rawBitmap) rawBitmap.recycle()

            var quality = 78
            var bytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                quality -= 10
            } while (bytes.size > MAX_CLOUD_IMAGE_BYTES && quality >= 30)

            val w = scaled.width
            val h = scaled.height
            scaled.recycle()
            PreparedImage(jpegBytes = bytes, width = w, height = h)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun prepareVideoFromUri(context: Context, uri: Uri): PreparedVideo? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val cacheFolder = File(context.filesDir, "kripto_videos").apply { mkdirs() }
            val destFile = File(cacheFolder, "vid_${UUID.randomUUID()}.mp4")

            var totalBytes = 0L
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                        totalBytes += read
                    }
                }
            } ?: return@withContext null

            val retriever = MediaMetadataRetriever()
            var durationMs = 0L
            var thumbBytes = ByteArray(0)
            try {
                retriever.setDataSource(destFile.absolutePath)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val scaledThumb = scaleDownBitmap(frame, 480)
                    val out = ByteArrayOutputStream()
                    scaledThumb.compress(Bitmap.CompressFormat.JPEG, 68, out)
                    thumbBytes = out.toByteArray()
                    if (scaledThumb != frame) frame.recycle()
                    scaledThumb.recycle()
                }
            } finally {
                retriever.release()
            }

            val cloudBytes = if (totalBytes <= MAX_CLOUD_VIDEO_BYTES) {
                destFile.readBytes()
            } else {
                null
            }

            PreparedVideo(
                thumbJpegBytes = thumbBytes,
                videoBytesForCloud = cloudBytes,
                localVideoPath = destFile.absolutePath,
                durationMs = durationMs,
                totalSizeBytes = totalBytes
            )
        } catch (_: Exception) {
            null
        }
    }

    fun writeTempVideoForPlayback(context: Context, messageId: String, base64Video: String): File? {
        return try {
            val bytes = Base64.decode(base64Video, Base64.NO_WRAP)
            val tempDir = File(context.cacheDir, "decrypted_videos").apply { mkdirs() }
            val outFile = File(tempDir, "play_$messageId.mp4")
            if (!outFile.exists() || outFile.length() != bytes.size.toLong()) {
                FileOutputStream(outFile).use { it.write(bytes) }
            }
            outFile
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleDownBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDimension && h <= maxDimension) return bitmap
        val ratio = minOf(maxDimension.toFloat() / w, maxDimension.toFloat() / h)
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
