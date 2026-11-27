package com.bubble.screentools.lens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.bubble.screentools.model.SearchImageFormat
import com.bubble.screentools.model.SearchImageQuality
import java.io.File
import java.io.FileOutputStream

/**
 * FileProvider serving temporary cropped images exclusively from `cacheDir/lens_temp/`.
 * Never writes temporary search crops to external storage or MediaStore gallery.
 */
class TemporaryImageProvider : FileProvider() {

    data class PreparedImage(
        val uri: Uri,
        val file: File,
        val mimeType: String,
        val sizeBytes: Long
    )

    companion object {
        private const val TEMP_DIR_NAME = "lens_temp"
        private const val MAX_FILE_AGE_MS = 5 * 60 * 1000L

        fun saveTemporaryCrop(
            context: Context,
            bitmap: Bitmap,
            qualityPreset: SearchImageQuality = SearchImageQuality.BALANCED,
            formatPref: SearchImageFormat = SearchImageFormat.AUTO,
            compressionQuality: Int = qualityPreset.jpegQuality
        ): Uri? {
            return savePreparedCrop(context, bitmap, qualityPreset, formatPref, compressionQuality)?.uri
        }

        fun savePreparedCrop(
            context: Context,
            bitmap: Bitmap,
            qualityPreset: SearchImageQuality = SearchImageQuality.BALANCED,
            formatPref: SearchImageFormat = SearchImageFormat.AUTO,
            compressionQuality: Int = qualityPreset.jpegQuality
        ): PreparedImage? {
            cleanupCache(context, forceDeleteAll = false)
            val dir = File(context.cacheDir, TEMP_DIR_NAME).apply { mkdirs() }

            val resolvedFormat = when (formatPref) {
                SearchImageFormat.AUTO -> if (qualityPreset == SearchImageQuality.ORIGINAL) {
                    SearchImageFormat.PNG
                } else {
                    SearchImageFormat.JPEG
                }
                else -> formatPref
            }

            val (ext, mime, compressFormat) = when (resolvedFormat) {
                SearchImageFormat.JPEG -> Triple("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG)
                SearchImageFormat.WEBP -> {
                    val webpFmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    Triple("webp", "image/webp", webpFmt)
                }
                else -> Triple("png", "image/png", Bitmap.CompressFormat.PNG)
            }

            val tempFile = File(dir, "bubble_lens_${System.currentTimeMillis()}.$ext")
            return runCatching {
                val safeQuality = compressionQuality.coerceIn(50, 100)
                // If converting a transparent masked bitmap to JPEG, flatten onto white cleanly (no black alpha fill)
                val encodeBitmap = if (resolvedFormat == SearchImageFormat.JPEG && bitmap.hasAlpha()) {
                    Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { flat ->
                        val canvas = Canvas(flat)
                        canvas.drawColor(Color.WHITE)
                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                    }
                } else {
                    bitmap
                }
                try {
                    FileOutputStream(tempFile).use { stream ->
                        encodeBitmap.compress(compressFormat, safeQuality, stream)
                    }
                } finally {
                    if (encodeBitmap !== bitmap && !encodeBitmap.isRecycled) {
                        encodeBitmap.recycle()
                    }
                }
                val uri = getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                PreparedImage(
                    uri = uri,
                    file = tempFile,
                    mimeType = mime,
                    sizeBytes = tempFile.length()
                )
            }.getOrNull()
        }

        fun getUriForUpdateApk(context: Context, apkFile: File): Uri {
            return getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        }

        fun cleanupCache(context: Context, forceDeleteAll: Boolean = false) {
            val dir = File(context.cacheDir, TEMP_DIR_NAME)
            if (!dir.exists()) return
            val now = System.currentTimeMillis()
            dir.listFiles()?.forEach { file ->
                val isExpired = (now - file.lastModified()) > MAX_FILE_AGE_MS
                if (forceDeleteAll || (isExpired && file.name.startsWith("bubble_lens_"))) {
                    file.delete()
                }
            }
        }
    }
}
