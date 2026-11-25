package com.bubble.screentools.lens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * FileProvider serving temporary cropped images exclusively from cacheDir/lens_temp/.
 * Never writes to external storage or MediaStore gallery.
 */
class TemporaryImageProvider : FileProvider() {

    companion object {
        private const val TEMP_DIR_NAME = "lens_temp"
        private const val MAX_FILE_AGE_MS = 5 * 60 * 1000L

        fun saveTemporaryCrop(context: Context, bitmap: Bitmap): Uri? {
            cleanupCache(context, forceDeleteAll = false)
            val dir = File(context.cacheDir, TEMP_DIR_NAME).apply { mkdirs() }
            val tempFile = File(dir, "bubble_lens_${System.currentTimeMillis()}.png")
            return runCatching {
                FileOutputStream(tempFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 92, stream)
                }
                getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            }.getOrNull()
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
