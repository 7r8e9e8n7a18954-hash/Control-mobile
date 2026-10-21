package com.universalaiagent.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.universalaiagent.data.model.ImageAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class AttachmentStore(
    private val context: Context,
) {
    private val directory = File(context.filesDir, "attachments")

    suspend fun saveImage(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("The selected file is not a readable image.")
        }
        val sample = calculateSample(bounds.outWidth, bounds.outHeight, MAX_SIDE)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("Could not open the selected image.")
        val scaled = scaleDown(bitmap, MAX_SIDE)
        val bytes = compressJpeg(scaled)
        if (scaled !== bitmap) bitmap.recycle()
        scaled.recycle()
        directory.mkdirs()
        val file = File(directory, "img_${UUID.randomUUID()}.jpg")
        file.writeBytes(bytes)
        file.absolutePath
    }

    suspend fun loadAttachment(path: String): ImageAttachment? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        val bitmap = BitmapFactory.decodeFile(path) ?: return@withContext null
        val scaled = scaleDown(bitmap, MAX_SIDE)
        val bytes = compressJpeg(scaled)
        if (scaled !== bitmap) bitmap.recycle()
        scaled.recycle()
        ImageAttachment(bytes, "image/jpeg")
    }

    suspend fun delete(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { runCatching { File(it).delete() } }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { it.delete() }
    }

    fun decodePreview(path: String): Bitmap? {
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun compressJpeg(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
        return output.toByteArray()
    }

    private fun calculateSample(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        var largest = maxOf(width, height)
        while (largest / 2 >= maxSide) {
            largest /= 2
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val MAX_SIDE = 1280
    }
}
