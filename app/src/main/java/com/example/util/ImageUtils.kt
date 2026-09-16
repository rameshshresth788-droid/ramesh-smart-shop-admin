package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    /** Creates a fresh temp file + content:// URI (via FileProvider) for the camera app to write into. */
    fun createCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "captured_images").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Loads a bitmap from a URI (camera capture or gallery pick) and corrects
     * orientation using EXIF data, since some cameras/galleries return the
     * pixels un-rotated with only an orientation tag set.
     */
    fun loadBitmapFixOrientation(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver

        val original = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null

        val rotationDegrees = try {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }

        if (rotationDegrees == 0f) return original

        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        return Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true).also {
            if (it !== original) original.recycle()
        }
    }

    /** Downscales large bitmaps so we don't send huge payloads to the AI or backend. */
    fun downscale(bitmap: Bitmap, maxDimension: Int = 1280): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDimension && h <= maxDimension) return bitmap
        val ratio = minOf(maxDimension.toFloat() / w, maxDimension.toFloat() / h)
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    fun bitmapToBase64Jpeg(bitmap: Bitmap, quality: Int = 82): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    /** Writes the (already correctly-oriented) bitmap to a temp JPEG file for multipart upload. */
    fun bitmapToTempFile(context: Context, bitmap: Bitmap, quality: Int = 90): File {
        val dir = File(context.cacheDir, "upload_tmp").apply { mkdirs() }
        val file = File(dir, "upload_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return file
    }
}
