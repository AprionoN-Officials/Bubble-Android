package com.bubble.screentools.lens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bubble.screentools.model.VisualSearchEngine
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * In-App Visual Search WebView supporting Yandex Images and TinEye
 * with automatic image file injection and engine switcher chips.
 */
class VisualSearchWebActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var toolbar: MaterialToolbar

    private var imageUri: Uri? = null
    private var imageBase64: String? = null
    private var currentEngine: VisualSearchEngine = VisualSearchEngine.YANDEX
    private var hasInjectedForCurrentPage = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        com.bubble.screentools.util.CompatibilityHelper.applySystemBarAppearance(window, this)

        val uriStr = intent.getStringExtra(EXTRA_IMAGE_URI)
        imageUri = uriStr?.let { Uri.parse(it) }
        currentEngine = runCatching {
            VisualSearchEngine.valueOf(
                intent.getStringExtra(EXTRA_ENGINE) ?: VisualSearchEngine.YANDEX.name
            )
        }.getOrDefault(VisualSearchEngine.YANDEX)

        if (imageUri == null) {
            Toast.makeText(this, getString(com.bubble.screentools.R.string.toast_image_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        imageBase64 = loadBase64FromUri(imageUri!!)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        toolbar = MaterialToolbar(this).apply {
            title = currentEngine.displayName
            setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
            setNavigationOnClickListener { finish() }
            menu.add(0, MENU_OPEN_BROWSER, 0, getString(com.bubble.screentools.R.string.menu_browser))
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu.add(0, MENU_OPEN_WITH, 1, getString(com.bubble.screentools.R.string.menu_open_in_app))
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_OPEN_BROWSER -> {
                        val url = webView.url ?: getEngineUrl(currentEngine)
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
        val engines = listOf(
            VisualSearchEngine.GOOGLE_LENS,
            VisualSearchEngine.YANDEX,
            VisualSearchEngine.TINEYE
        )
        for (engine in engines) {
            val chip = Chip(this).apply {
                text = engine.shortLabel
                isCheckable = true
                isChecked = (engine == currentEngine)
                setOnClickListener {
                    if (engine == VisualSearchEngine.GOOGLE_LENS) {
                        val uri = imageUri ?: return@setOnClickListener
                        val lensManager = LensIntegrationManager(this@VisualSearchWebActivity)
                        if (lensManager.tryLaunchNativeGoogleLens(uri)) {
                            finish()
                        } else {
                            Toast.makeText(
                                this@VisualSearchWebActivity,
                                getString(com.bubble.screentools.R.string.toast_lens_not_installed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        loadEngine(engine)
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
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    return if (url.startsWith("http://") || url.startsWith("https://")) {
                        false
                    } else {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        true
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!hasInjectedForCurrentPage && url != null && isEngineLandingPage(url, currentEngine)) {
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

        loadEngine(currentEngine)
    }

    private fun loadEngine(engine: VisualSearchEngine) {
        currentEngine = engine
        toolbar.title = engine.displayName
        hasInjectedForCurrentPage = false
        webView.stopLoading()
        webView.loadUrl(getEngineUrl(engine))
    }

    private fun getEngineUrl(engine: VisualSearchEngine): String {
        return when (engine) {
            VisualSearchEngine.TINEYE -> "https://tineye.com/"
            else -> "https://yandex.com/images/"
        }
    }

    private fun isEngineLandingPage(url: String, engine: VisualSearchEngine): Boolean {
        val lower = url.lowercase()
        return when (engine) {
            VisualSearchEngine.YANDEX -> lower.contains("yandex.") && lower.contains("/images") && !lower.contains("cbir")
            VisualSearchEngine.TINEYE -> lower.contains("tineye.com") && !lower.contains("/search/")
            else -> false
        }
    }

    private fun injectAutoUploadScript(view: WebView?) {
        val b64 = imageBase64 ?: return
        val js = """
            (function() {
                function tryInject(attempt) {
                    var inputs = Array.from(document.querySelectorAll('input[type="file"]'));
                    if (inputs.length === 0) {
                        var triggers = document.querySelectorAll(
                            'button[aria-label*="image" i], button[aria-label*="camera" i], ' +
                            'button[aria-label*="upload" i], .cbir-icon'
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
                        var blob = new Blob([new Uint8Array(byteNumbers)], { type: 'image/png' });
                        var file = new File([blob], 'bubble_crop.png', { type: 'image/png' });
                        var dt = new DataTransfer();
                        dt.items.add(file);
                        var targetInput = inputs[0];
                        targetInput.files = dt.files;
                        targetInput.dispatchEvent(new Event('input', { bubbles: true }));
                        targetInput.dispatchEvent(new Event('change', { bubbles: true }));
                        if (targetInput.form) {
                            setTimeout(function() {
                                try { targetInput.form.submit(); } catch (e) {}
                            }, 300);
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
            setDataAndType(uri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val lang = resources.configuration.locales[0]?.language?.lowercase()
        val title = if (lang == "id" || lang == "in") "Pilih aplikasi untuk membuka" else "Open with"
        startActivity(Intent.createChooser(viewIntent, title))
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_IMAGE_URI = "extra_image_uri"
        private const val EXTRA_ENGINE = "extra_engine"
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
    }
}
