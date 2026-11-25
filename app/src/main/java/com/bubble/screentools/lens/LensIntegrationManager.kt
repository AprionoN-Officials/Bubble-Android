package com.bubble.screentools.lens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import com.bubble.screentools.model.VisualSearchEngine

/**
 * Multi-Engine Visual Search Integration.
 * - Default Primary: Google Lens native app (falls back to Google App or Android Open With / Sharesheet).
 * - Alternatives: Yandex Images, TinEye, and Android Sharesheet.
 */
class LensIntegrationManager(private val context: Context) {

    enum class LensAvailability {
        GOOGLE_LENS_APP,
        GOOGLE_APP_SEARCH,
        SHARESHEET_FALLBACK
    }

    fun checkAvailability(): LensAvailability {
        val pm = context.packageManager
        if (isPackageInstalled(pm, PKG_GOOGLE_LENS) && canHandleImageSend(pm, PKG_GOOGLE_LENS)) {
            return LensAvailability.GOOGLE_LENS_APP
        }
        if (isPackageInstalled(pm, PKG_GOOGLE_APP) && canHandleImageSend(pm, PKG_GOOGLE_APP)) {
            return LensAvailability.GOOGLE_APP_SEARCH
        }
        return LensAvailability.SHARESHEET_FALLBACK
    }

    fun tryLaunchNativeGoogleLens(uri: Uri): Boolean {
        return when (checkAvailability()) {
            LensAvailability.GOOGLE_LENS_APP -> tryStartTargetedShare(uri, PKG_GOOGLE_LENS)
            LensAvailability.GOOGLE_APP_SEARCH -> tryStartTargetedShare(uri, PKG_GOOGLE_APP)
            LensAvailability.SHARESHEET_FALLBACK -> false
        }
    }

    private fun isIndonesianLocale(): Boolean {
        val lang = context.resources.configuration.locales[0]?.language?.lowercase() ?: return false
        return lang == "id" || lang == "in"
    }

    fun launchVisualSearch(
        croppedBitmap: Bitmap,
        engine: VisualSearchEngine = VisualSearchEngine.GOOGLE_LENS
    ): VisualSearchDispatchResult {
        val id = isIndonesianLocale()
        val uri = TemporaryImageProvider.saveTemporaryCrop(context, croppedBitmap)
            ?: return VisualSearchDispatchResult.Error(
                if (id) "Gagal menyiapkan gambar sementara untuk pencarian."
                else "Failed to prepare temporary image for visual search."
            )

        return when (engine) {
            VisualSearchEngine.GOOGLE_LENS -> launchGoogleLensNativeOrFallback(uri)
            VisualSearchEngine.YANDEX,
            VisualSearchEngine.TINEYE -> {
                runCatching {
                    VisualSearchWebActivity.launch(context, uri, engine)
                    VisualSearchDispatchResult.LaunchedWebEngine(engine)
                }.getOrElse {
                    launchOpenWithFallback(
                        uri,
                        if (id) "Gagal membuka ${engine.displayName}. Pilih aplikasi untuk membuka gambar."
                        else "Failed to open ${engine.displayName}. Choose an app to open the image."
                    )
                }
            }
            VisualSearchEngine.SHARESHEET -> {
                launchOpenWithFallback(
                    uri,
                    if (id) "Pilih aplikasi penerima gambar." else "Choose an app to open the image."
                )
            }
        }
    }

    private fun launchGoogleLensNativeOrFallback(uri: Uri): VisualSearchDispatchResult {
        val id = isIndonesianLocale()
        return when (checkAvailability()) {
            LensAvailability.GOOGLE_LENS_APP -> {
                if (tryStartTargetedShare(uri, PKG_GOOGLE_LENS)) {
                    VisualSearchDispatchResult.LaunchedGoogleLens
                } else {
                    launchOpenWithFallback(
                        uri,
                        if (id) "Google Lens tidak merespons. Pilih aplikasi untuk membuka gambar."
                        else "Google Lens did not respond. Choose an app to open the image."
                    )
                }
            }
            LensAvailability.GOOGLE_APP_SEARCH -> {
                if (tryStartTargetedShare(uri, PKG_GOOGLE_APP)) {
                    VisualSearchDispatchResult.LaunchedGoogleApp
                } else {
                    launchOpenWithFallback(
                        uri,
                        if (id) "Google App tidak merespons. Pilih aplikasi untuk membuka gambar."
                        else "Google App did not respond. Choose an app to open the image."
                    )
                }
            }
            LensAvailability.SHARESHEET_FALLBACK -> {
                launchOpenWithFallback(
                    uri,
                    if (id) "Google Lens belum terpasang. Pilih Yandex/TinEye di pengaturan atau pilih aplikasi untuk membuka gambar."
                    else "Google Lens is not installed. Select Yandex/TinEye in settings or choose an app to open the image."
                )
            }
        }
    }

    private fun tryStartTargetedShare(uri: Uri, targetPackage: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage(targetPackage)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun launchOpenWithFallback(uri: Uri, notice: String): VisualSearchDispatchResult {
        val id = isIndonesianLocale()
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooserTitle = if (id) "Pilih aplikasi untuk membuka" else "Open with"
        val chooser = Intent.createChooser(viewIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            VisualSearchDispatchResult.OpenedSharesheet(notice)
        }.getOrElse {
            VisualSearchDispatchResult.Error(
                if (id) "Tidak ada aplikasi penerima gambar yang tersedia."
                else "No compatible app is available to open this image."
            )
        }
    }

    private fun canHandleImageSend(pm: PackageManager, pkg: String): Boolean {
        val probe = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            setPackage(pkg)
        }
        val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(probe, 0)
        }
        return matches.isNotEmpty()
    }

    private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            true
        }.getOrDefault(false)
    }

    sealed class VisualSearchDispatchResult {
        data object LaunchedGoogleLens : VisualSearchDispatchResult()
        data object LaunchedGoogleApp : VisualSearchDispatchResult()
        data class LaunchedWebEngine(val engine: VisualSearchEngine) : VisualSearchDispatchResult()
        data class OpenedSharesheet(val fallbackNotice: String) : VisualSearchDispatchResult()
        data class Error(val message: String) : VisualSearchDispatchResult()
    }

    companion object {
        const val PKG_GOOGLE_LENS = "com.google.ar.lens"
        const val PKG_GOOGLE_APP = "com.google.android.googlequicksearchbox"
    }
}
