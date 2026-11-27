package com.bubble.screentools.lens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.model.SearchEngineItem
import com.bubble.screentools.model.SearchImageMethod
import com.bubble.screentools.model.VisualSearchEngine
import com.bubble.screentools.theme.ThemeManager
import com.bubble.screentools.util.CompatibilityHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-App Multi-Engine Visual Search WebView supporting Yandex Images, TinEye,
 * Bing Visual Search, SauceNAO, trace.moe, and custom web visual search URLs.
 * Never attempts to bypass CAPTCHA or authentication gates; users can interact directly or open in browser.
 */
class VisualSearchWebActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var toolbar: MaterialToolbar
    private lateinit var preferences: BubblePreferences
    private lateinit var registry: SearchEngineRegistry

    private var imageUri: Uri? = null
    private var imageMimeType: String = "image/png"
    private var imageBase64: String? = null
    private var currentItem: SearchEngineItem? = null
    private var hasInjectedForCurrentPage = false
    @Volatile
    private var currentPageUrl: String? = null
    private val blockedAdsCount = AtomicInteger(0)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = BubblePreferences(this)
        ThemeManager.applyToActivityBeforeContent(this, preferences)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        CompatibilityHelper.applySystemBarAppearance(window, this)

        registry = SearchEngineRegistry(preferences)

        val uriStr = intent.getStringExtra(EXTRA_IMAGE_URI)
        imageUri = uriStr?.let { Uri.parse(it) }
        imageMimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "image/png"

        val requestedId = intent.getStringExtra(EXTRA_ENGINE_ID)
        val requestedName = intent.getStringExtra(EXTRA_ENGINE_NAME)
        val requestedUrl = intent.getStringExtra(EXTRA_ENGINE_URL)

        val allVisual = registry.getVisualEngines(enabledOnly = false)
        currentItem = when {
            !requestedId.isNullOrBlank() -> allVisual.firstOrNull { it.id == requestedId }
            !requestedUrl.isNullOrBlank() -> SearchEngineItem(
                id = "adhoc",
                name = requestedName ?: "Visual Search",
                type = com.bubble.screentools.model.SearchEngineType.VISUAL,
                baseUrl = requestedUrl,
                imageMethod = SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD
            )
            else -> {
                val legacy = runCatching {
                    VisualSearchEngine.valueOf(intent.getStringExtra(EXTRA_ENGINE) ?: VisualSearchEngine.YANDEX.name)
                }.getOrDefault(VisualSearchEngine.YANDEX)
                val mappedId = BubblePreferences.mapLegacyEngineToId(legacy)
                allVisual.firstOrNull { it.id == mappedId } ?: allVisual.firstOrNull()
            }
        }

        if (imageUri == null || currentItem == null) {
            Toast.makeText(this, getString(R.string.toast_image_not_found), Toast.LENGTH_SHORT).show()
            closeAndReturnToPreviousApp()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    closeAndReturnToPreviousApp()
                }
            }
        })

        imageBase64 = loadBase64FromUri(imageUri!!)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        toolbar = MaterialToolbar(this).apply {
            title = currentItem?.name ?: "Visual Search"
            setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
            setNavigationOnClickListener { closeAndReturnToPreviousApp() }
            menu.add(0, MENU_OPEN_BROWSER, 0, getString(R.string.menu_browser))
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu.add(0, MENU_OPEN_WITH, 1, getString(R.string.menu_open_in_app))
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_OPEN_BROWSER -> {
                        val url = webView.url ?: currentItem?.baseUrl.orEmpty()
                        if (SearchEngineRegistry.isValidHttpUrl(url)) {
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        }
                        true
                    }
                    MENU_OPEN_WITH -> {
                        openImageWithExternalApp()
                        true
                    }
                    else -> false
                }
            }
        }
        root.addView(toolbar)

        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(12), dp(4), dp(12), dp(6))
        }
        val chipGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSingleLine = true
        }
        val switcherEngines = registry.getVisualEngines(enabledOnly = true)
            .filter {
                it.imageMethod == SearchImageMethod.GOOGLE_LENS_NATIVE ||
                    it.imageMethod == SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD
            }
        for (engine in switcherEngines) {
            val chip = Chip(this).apply {
                text = engine.name
                isCheckable = true
                isChecked = (engine.id == currentItem?.id)
                setOnClickListener {
                    if (engine.imageMethod == SearchImageMethod.GOOGLE_LENS_NATIVE) {
                        val uri = imageUri ?: return@setOnClickListener
                        val lensManager = LensIntegrationManager(this@VisualSearchWebActivity)
                        if (lensManager.tryLaunchNativeGoogleLens(uri, imageMimeType)) {
                            closeAndReturnToPreviousApp()
                        } else {
                            Toast.makeText(
                                this@VisualSearchWebActivity,
                                getString(R.string.toast_lens_not_installed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        loadEngineItem(engine)
                    }
                }
            }
            chipGroup.addView(chip)
        }
        chipScroll.addView(chipGroup)
        root.addView(chipScroll)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(3)
            )
            max = 100
        }
        root.addView(progressBar)

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.loadsImagesAutomatically = true
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    val uri = imageUri
                    if (uri != null && filePathCallback != null) {
                        filePathCallback.onReceiveValue(arrayOf(uri))
                        return true
                    }
                    return false
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    currentPageUrl = url
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    if (preferences.webAdBlockerEnabled && request != null) {
                        val reqUrl = request.url?.toString()
                        if (WebViewAdBlocker.shouldBlockRequest(reqUrl, currentPageUrl, request.isForMainFrame)) {
                            recordBlockedAd()
                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    val lower = url.lowercase()
                    if (preferences.webAdBlockerEnabled &&
                        WebViewAdBlocker.shouldBlockRequest(url, currentPageUrl, isMainFrame = true)
                    ) {
                        recordBlockedAd()
                        return true
                    }
                    if (lower.startsWith("http://") || lower.startsWith("https://")) {
                        return false
                    }
                    if (lower.startsWith("javascript:") || lower.startsWith("file:") ||
                        lower.startsWith("content:") || lower.startsWith("data:") ||
                        lower.startsWith("vbscript:") || lower.startsWith("about:")
                    ) {
                        return true
                    }
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    return true
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        Toast.makeText(
                            this@VisualSearchWebActivity,
                            getString(R.string.search_web_load_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    currentPageUrl = url
                    if (preferences.webAdBlockerEnabled) {
                        view?.evaluateJavascript(WebViewAdBlocker.COSMETIC_AD_HIDE_JS, null)
                    }
                    val active = currentItem ?: return
                    if (!hasInjectedForCurrentPage && url != null && isEngineLandingPage(url, active)) {
                        hasInjectedForCurrentPage = true
                        injectAutoUploadScript(view)
                    }
                }
            }
        }
        root.addView(webView)
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        currentItem?.let { loadEngineItem(it) }
    }

    private fun loadEngineItem(item: SearchEngineItem) {
        currentItem = item
        if (preferences.rememberLastSearchEngine) {
            preferences.lastUsedVisualEngineId = item.id
        }
        blockedAdsCount.set(0)
        toolbar.title = item.name
        toolbar.subtitle = null
        currentPageUrl = item.baseUrl
        hasInjectedForCurrentPage = false
        webView.stopLoading()
        if (SearchEngineRegistry.isValidHttpUrl(item.baseUrl)) {
            webView.loadUrl(item.baseUrl)
        }
    }

    private fun recordBlockedAd() {
        val count = blockedAdsCount.incrementAndGet()
        runOnUiThread {
            if (!isFinishing && !isDestroyed && ::toolbar.isInitialized) {
                toolbar.subtitle = getString(R.string.web_adblocker_badge_fmt, count)
            }
        }
    }

    private fun closeAndReturnToPreviousApp() {
        finishAndRemoveTask()
    }

    private fun isEngineLandingPage(url: String, item: SearchEngineItem): Boolean {
        val lower = url.lowercase()
        return when (item.id) {
            SearchEngineRegistry.ID_YANDEX -> lower.contains("yandex.") && lower.contains("/images") && !lower.contains("cbir")
            SearchEngineRegistry.ID_TINEYE -> lower.contains("tineye.com") && !lower.contains("/search/")
            SearchEngineRegistry.ID_BING_VISUAL -> lower.contains("bing.com/visualsearch") && !lower.contains("insights")
            SearchEngineRegistry.ID_SAUCENAO -> lower.contains("saucenao.com") && !lower.contains("search.php")
            SearchEngineRegistry.ID_TRACE_MOE -> lower.contains("trace.moe")
            else -> lower.startsWith(item.baseUrl.lowercase().trimEnd('/'))
        }
    }

    private fun injectAutoUploadScript(view: WebView?) {
        val b64 = imageBase64 ?: return
        val mime = imageMimeType
        val ext = when {
            mime.contains("jpeg") -> "jpg"
            mime.contains("webp") -> "webp"
            else -> "png"
        }
        val js = """
            (function() {
                function tryInject(attempt) {
                    var inputs = Array.from(document.querySelectorAll('input[type="file"]'));
                    if (inputs.length === 0) {
                        var triggers = document.querySelectorAll(
                            'button[aria-label*="image" i], button[aria-label*="camera" i], ' +
                            'button[aria-label*="upload" i], .cbir-icon, #sb_sbi'
                        );
                        if (attempt === 1 && triggers.length > 0) {
                            try { triggers[0].click(); } catch (e) {}
                        }
                        if (attempt < 8) {
                            setTimeout(function() { tryInject(attempt + 1); }, 450);
                        }
                        return;
                    }
                    try {
                        var byteChars = atob('$b64');
                        var byteNumbers = new Array(byteChars.length);
                        for (var i = 0; i < byteChars.length; i++) {
                            byteNumbers[i] = byteChars.charCodeAt(i);
                        }
                        var blob = new Blob([new Uint8Array(byteNumbers)], { type: '$mime' });
                        var file = new File([blob], 'bubble_crop.$ext', { type: '$mime' });
                        var dt = new DataTransfer();
                        dt.items.add(file);
                        var targetInput = inputs[0];
                        targetInput.files = dt.files;
                        targetInput.dispatchEvent(new Event('input', { bubbles: true }));
                        targetInput.dispatchEvent(new Event('change', { bubbles: true }));
                        if (targetInput.form && !window.location.hostname.includes('trace.moe')) {
                            setTimeout(function() {
                                try { targetInput.form.submit(); } catch (e) {}
                            }, 350);
                        }
                    } catch (err) {}
                }
                setTimeout(function() { tryInject(1); }, 350);
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    private fun loadBase64FromUri(uri: Uri): String? {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }.getOrNull()
    }

    private fun openImageWithExternalApp() {
        val uri = imageUri ?: return
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, imageMimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val lang = resources.configuration.locales[0]?.language?.lowercase()
        val title = if (lang == "id" || lang == "in") "Pilih aplikasi untuk membuka" else "Open with"
        runCatching {
            startActivity(Intent.createChooser(viewIntent, title))
        }.onFailure {
            Toast.makeText(this, getString(R.string.overlay_no_app_for_link), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_IMAGE_URI = "extra_image_uri"
        private const val EXTRA_MIME_TYPE = "extra_mime_type"
        private const val EXTRA_ENGINE = "extra_engine"
        private const val EXTRA_ENGINE_ID = "extra_engine_id"
        private const val EXTRA_ENGINE_NAME = "extra_engine_name"
        private const val EXTRA_ENGINE_URL = "extra_engine_url"
        private const val MENU_OPEN_BROWSER = 101
        private const val MENU_OPEN_WITH = 102

        fun launch(context: Context, imageUri: Uri, engine: VisualSearchEngine) {
            val intent = Intent(context, VisualSearchWebActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_URI, imageUri.toString())
                putExtra(EXTRA_ENGINE, engine.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun launchItem(context: Context, imageUri: Uri, mimeType: String, item: SearchEngineItem) {
            val intent = Intent(context, VisualSearchWebActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_URI, imageUri.toString())
                putExtra(EXTRA_MIME_TYPE, mimeType)
                putExtra(EXTRA_ENGINE_ID, item.id)
                putExtra(EXTRA_ENGINE_NAME, item.name)
                putExtra(EXTRA_ENGINE_URL, item.baseUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
