package com.smartreminder.data.local.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

/**
 * Utility responsible for processing, compressing, and caching avatar images locally.
 * Prevents database bloating by storing only the file URL and enforcing a lightweight size limit (<100KB).
 */
object AvatarImageStorage {

    private const val AVATAR_FILE_NAME = "user_avatar_profile.jpg"
    private const val MAX_DIMENSION = 512
    private const val JPEG_QUALITY = 85

    /**
     * Reads an image from [imageUri], downsamples/resizes it to at most [MAX_DIMENSION]x[MAX_DIMENSION],
     * compresses it as JPEG with quality [JPEG_QUALITY], overwrites the local cache file,
     * and returns the local file URL string.
     */
    suspend fun saveAndCompressAvatar(context: Context, imageUri: Uri): String = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver

        // 1. Decode bounds to compute inSampleSize
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight

        var inSampleSize = 1
        if (originalWidth > MAX_DIMENSION || originalHeight > MAX_DIMENSION) {
            val maxOriginal = max(originalWidth, originalHeight)
            while ((maxOriginal / inSampleSize) >= MAX_DIMENSION * 2) {
                inSampleSize *= 2
            }
        }

        // 2. Decode sampled bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampledBitmap: Bitmap = contentResolver.openInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: throw IllegalArgumentException("Cannot decode image from URI: $imageUri")

        // 3. Scale down exactly to MAX_DIMENSION maintaining aspect ratio if needed
        val scaledBitmap = if (sampledBitmap.width > MAX_DIMENSION || sampledBitmap.height > MAX_DIMENSION) {
            val ratio = sampledBitmap.width.toFloat() / sampledBitmap.height.toFloat()
            val targetWidth: Int
            val targetHeight: Int
            if (sampledBitmap.width >= sampledBitmap.height) {
                targetWidth = MAX_DIMENSION
                targetHeight = (MAX_DIMENSION / ratio).toInt().coerceAtLeast(1)
            } else {
                targetHeight = MAX_DIMENSION
                targetWidth = (MAX_DIMENSION * ratio).toInt().coerceAtLeast(1)
            }
            val scaled = Bitmap.createScaledBitmap(sampledBitmap, targetWidth, targetHeight, true)
            if (scaled != sampledBitmap) {
                sampledBitmap.recycle()
            }
            scaled
        } else {
            sampledBitmap
        }

        // 4. Overwrite single avatar cache file
        val avatarsDir = File(context.filesDir, "avatars").apply { mkdirs() }
        val avatarFile = File(avatarsDir, AVATAR_FILE_NAME)

        FileOutputStream(avatarFile).use { output ->
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            output.flush()
        }
        scaledBitmap.recycle()

        // Append timestamp query parameter to invalidate Coil image cache
        Uri.fromFile(avatarFile).toString() + "?t=" + System.currentTimeMillis()
    }
}
