package com.bubble.screentools.ui.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.bubble.screentools.lens.TemporaryImageProvider
import com.bubble.screentools.model.SearchImageFormat
import com.bubble.screentools.model.SearchImageQuality
import java.io.File
import java.io.FileOutputStream

/**
 * Lightweight, 100% functional implementations of supporting Action Panel image tools:
 * - Copy Selected Image (via FileProvider ClipData URI)
 * - Save Selected Image (to Pictures/BubbleCaptures via MediaStore)
 * - Share Selected Image (via native Android ACTION_SEND chooser)
 */
object OverlayExtraActionsHelper {

    fun copyImageToClipboard(context: Context, bitmap: Bitmap): Boolean {
        val uri = TemporaryImageProvider.saveTemporaryCrop(
            context = context,
            bitmap = bitmap,
            qualityPreset = SearchImageQuality.ORIGINAL,
            formatPref = SearchImageFormat.PNG,
            compressionQuality = 100
        ) ?: return false
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clip = ClipData.newUri(context.contentResolver, "Bubble Selection", uri)
        cm.setPrimaryClip(clip)
        return true
    }

    fun saveImageToGallery(context: Context, bitmap: Bitmap): Boolean {
        val fileName = "Bubble_${System.currentTimeMillis()}.png"
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BubbleCaptures")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "BubbleCaptures"
                ).apply { mkdirs() }
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                true
            }
        }.getOrDefault(false)
    }

    fun shareImage(context: Context, bitmap: Bitmap, chooserTitle: String): Boolean {
        val uri = TemporaryImageProvider.saveTemporaryCrop(
            context = context,
            bitmap = bitmap,
            qualityPreset = SearchImageQuality.ORIGINAL,
            formatPref = SearchImageFormat.PNG,
            compressionQuality = 100
        ) ?: return false
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }
}
