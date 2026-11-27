package com.bubble.screentools.lens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.model.SearchEngineItem
import com.bubble.screentools.model.SearchImageMethod
import com.bubble.screentools.model.VisualSearchEngine

/**
 * Multi-Engine Visual & Text Search Integration Manager.
 * Supports built-in and custom Visual Search engines (Google Lens, Yandex, TinEye, Bing Visual,
 * SauceNAO, trace.moe, Open With, custom web/app intent engines) and Text Search engines,
 * with offline detection, URL validation, safe FileProvider URIs, and clean fallbacks.
 */
class LensIntegrationManager(private val context: Context) {

    private val preferences by lazy { BubblePreferences(context) }

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

    fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun tryLaunchNativeGoogleLens(uri: Uri, mimeType: String = "image/png"): Boolean {
        return when (checkAvailability()) {
            LensAvailability.GOOGLE_LENS_APP -> tryStartTargetedShare(uri, PKG_GOOGLE_LENS, mimeType)
            LensAvailability.GOOGLE_APP_SEARCH -> tryStartTargetedShare(uri, PKG_GOOGLE_APP, mimeType)
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
        val registry = SearchEngineRegistry(preferences)
        val targetId = BubblePreferences.mapLegacyEngineToId(engine)
        val item = registry.getVisualEngines(enabledOnly = false).firstOrNull { it.id == targetId }
            ?: registry.getActiveVisualEngine()
        return launchVisualSearch(croppedBitmap, item)
    }

    fun launchVisualSearch(
        croppedBitmap: Bitmap,
        engineItem: SearchEngineItem
    ): VisualSearchDispatchResult {
        val id = isIndonesianLocale()
        val prepared = TemporaryImageProvider.savePreparedCrop(
            context = context,
            bitmap = croppedBitmap,
            qualityPreset = preferences.searchImageQuality,
            formatPref = preferences.searchImageFormat,
            compressionQuality = preferences.searchCompressionQuality
        ) ?: return VisualSearchDispatchResult.Error(
            if (id) "Gagal menyiapkan gambar sementara untuk pencarian."
            else "Failed to prepare temporary image for visual search."
        )

        if (preferences.rememberLastSearchEngine) {
            preferences.lastUsedVisualEngineId = engineItem.id
        }

        val requiresInternet = engineItem.imageMethod == SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD ||
            engineItem.imageMethod == SearchImageMethod.BROWSER_URL_ONLY
        if (requiresInternet && !isNetworkConnected()) {
            return VisualSearchDispatchResult.Error(
                if (id) "Tidak ada koneksi internet. Periksa jaringan lalu coba lagi."
                else "No internet connection. Check your network and try again."
            )
        }

        return when (engineItem.imageMethod) {
            SearchImageMethod.GOOGLE_LENS_NATIVE -> {
                launchGoogleLensNativeOrFallback(prepared.uri, prepared.mimeType)
            }
            SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD -> {
                if (!SearchEngineRegistry.isValidHttpUrl(engineItem.baseUrl)) {
                    return VisualSearchDispatchResult.Error(
                        if (id) "URL mesin pencari tidak valid: ${engineItem.name}"
                        else "Invalid search engine URL: ${engineItem.name}"
                    )
                }
                if (preferences.openSearchInExternalBrowser) {
                    launchBrowserOrFallback(engineItem.baseUrl, prepared.uri, prepared.mimeType, engineItem.name)
                } else {
                    runCatching {
                        VisualSearchWebActivity.launchItem(context, prepared.uri, prepared.mimeType, engineItem)
                        VisualSearchDispatchResult.LaunchedWebItem(engineItem.name)
                    }.getOrElse {
                        launchOpenWithFallback(
                            prepared.uri,
                            prepared.mimeType,
                            if (id) "Gagal membuka ${engineItem.name}. Pilih aplikasi untuk membuka gambar."
                            else "Failed to open ${engineItem.name}. Choose an app to open the image."
                        )
                    }
                }
            }
            SearchImageMethod.BROWSER_URL_ONLY -> {
                if (!SearchEngineRegistry.isValidHttpUrl(engineItem.baseUrl)) {
                    return VisualSearchDispatchResult.Error(
                        if (id) "URL mesin pencari tidak valid." else "Invalid search engine URL."
                    )
                }
                launchBrowserOrFallback(engineItem.baseUrl, prepared.uri, prepared.mimeType, engineItem.name)
            }
            SearchImageMethod.CUSTOM_INTENT_PACKAGE -> {
                val pkg = engineItem.targetPackage
                if (pkg.isBlank() || !isPackageInstalled(context.packageManager, pkg)) {
                    launchOpenWithFallback(
                        prepared.uri,
                        prepared.mimeType,
                        if (id) "Aplikasi target (${engineItem.name}) belum terpasang. Pilih aplikasi lain."
                        else "Target app (${engineItem.name}) is not installed. Choose another app."
                    )
                } else if (tryStartTargetedShare(prepared.uri, pkg, prepared.mimeType)) {
                    VisualSearchDispatchResult.LaunchedWebItem(engineItem.name)
                } else {
                    launchOpenWithFallback(
                        prepared.uri,
                        prepared.mimeType,
                        if (id) "${engineItem.name} tidak menerima berbagi gambar. Pilih aplikasi lain."
                        else "${engineItem.name} does not accept image handoff. Choose another app."
                    )
                }
            }
            SearchImageMethod.ANDROID_OPEN_WITH -> {
                launchOpenWithFallback(
                    prepared.uri,
                    prepared.mimeType,
                    if (id) "Pilih aplikasi penerima gambar." else "Choose an app to open the image."
                )
            }
        }
    }

    fun launchTextSearch(query: String, engineItem: SearchEngineItem? = null): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        val target = engineItem ?: SearchEngineRegistry(preferences).getActiveTextEngine()
        val url = SearchEngineRegistry.buildTextSearchUrl(target, trimmed) ?: return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun launchBrowserOrFallback(
        url: String,
        uri: Uri,
        mimeType: String,
        engineName: String
    ): VisualSearchDispatchResult {
        val id = isIndonesianLocale()
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(browserIntent)
            VisualSearchDispatchResult.LaunchedWebItem(engineName)
        }.getOrElse {
            launchOpenWithFallback(
                uri,
                mimeType,
                if (id) "Tidak ada browser yang dapat membuka $engineName."
                else "No browser available to open $engineName."
            )
        }
    }

    private fun launchGoogleLensNativeOrFallback(uri: Uri, mimeType: String): VisualSearchDispatchResult {
        val id = isIndonesianLocale()
        return when (checkAvailability()) {
            LensAvailability.GOOGLE_LENS_APP -> {
                if (tryStartTargetedShare(uri, PKG_GOOGLE_LENS, mimeType)) {
                    VisualSearchDispatchResult.LaunchedGoogleLens
                } else {
                    launchOpenWithFallback(
                        uri,
                        mimeType,
                        if (id) "Google Lens tidak merespons. Pilih aplikasi untuk membuka gambar."
                        else "Google Lens did not respond. Choose an app to open the image."
                    )
                }
            }
            LensAvailability.GOOGLE_APP_SEARCH -> {
                if (tryStartTargetedShare(uri, PKG_GOOGLE_APP, mimeType)) {
                    VisualSearchDispatchResult.LaunchedGoogleApp
                } else {
                    launchOpenWithFallback(
                        uri,
                        mimeType,
                        if (id) "Google App tidak merespons. Pilih aplikasi untuk membuka gambar."
                        else "Google App did not respond. Choose an app to open the image."
                    )
                }
            }
            LensAvailability.SHARESHEET_FALLBACK -> {
                launchOpenWithFallback(
                    uri,
                    mimeType,
                    if (id) "Google Lens belum terpasang. Pilih Yandex/TinEye/Bing di pengaturan atau pilih aplikasi lain."
                    else "Google Lens is not installed. Select Yandex/TinEye/Bing in settings or choose another app."
                )
            }
        }
    }

    private fun tryStartTargetedShare(uri: Uri, targetPackage: String, mimeType: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage(targetPackage)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun launchOpenWithFallback(uri: Uri, mimeType: String, notice: String): VisualSearchDispatchResult {
        val id = isIndonesianLocale()
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
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
            type = "image/*"
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
        data class LaunchedWebItem(val engineName: String) : VisualSearchDispatchResult()
        data class OpenedSharesheet(val fallbackNotice: String) : VisualSearchDispatchResult()
        data class Error(val message: String) : VisualSearchDispatchResult()
    }

    companion object {
        const val PKG_GOOGLE_LENS = "com.google.ar.lens"
        const val PKG_GOOGLE_APP = "com.google.android.googlequicksearchbox"
    }
}
