package com.bubble.screentools.lens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.model.VisualSearchEngine
import com.bubble.screentools.ui.theme.BubbleExpressiveTheme
import com.bubble.screentools.ui.theme.BubblePalette

/**
 * In-App Visual Search WebView supporting Yandex Images and TinEye
 * with automatic image file injection and Material You Expressive engine switcher pills.
 */
class VisualSearchWebActivity : AppCompatActivity() {

    private var webViewRef: WebView? = null
    private var imageUri: Uri? = null
    private var imageBase64: String? = null
    private var currentEngine by mutableStateOf(VisualSearchEngine.YANDEX)
    private var loadProgress by mutableIntStateOf(0)
    private var hasInjectedForCurrentPage = false

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val uriStr = intent.getStringExtra(EXTRA_IMAGE_URI)
        imageUri = uriStr?.let { Uri.parse(it) }
        currentEngine =
            runCatching {
                VisualSearchEngine.valueOf(
                    intent.getStringExtra(EXTRA_ENGINE) ?: VisualSearchEngine.YANDEX.name,
                )
            }.getOrDefault(VisualSearchEngine.YANDEX)

        if (imageUri == null) {
            Toast.makeText(this, getString(R.string.toast_image_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        imageBase64 = loadBase64FromUri(imageUri!!)
        val preferences = BubblePreferences(this)

        val engines =
            listOf(
                VisualSearchEngine.GOOGLE_LENS,
                VisualSearchEngine.YANDEX,
                VisualSearchEngine.TINEYE,
            )

        setContent {
            val systemDark = isSystemInDarkTheme()
            val darkTheme =
                when (preferences.themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> systemDark
                }

            BubbleExpressiveTheme(
                darkTheme = darkTheme,
                pureBlack = preferences.pureBlack,
                palette = BubblePalette.fromKey(preferences.themePalette),
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    topBar = {
                        Column {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = currentEngine.displayName,
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.btn_close),
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = {
                                            val url = webViewRef?.url ?: getEngineUrl(currentEngine)
                                            runCatching {
                                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.OpenInBrowser,
                                            contentDescription = stringResource(R.string.menu_browser),
                                        )
                                    }
                                    IconButton(onClick = { openImageWithExternalApp() }) {
                                        Icon(
                                            imageVector = Icons.Outlined.OpenInNew,
                                            contentDescription = stringResource(R.string.menu_open_in_app),
                                        )
                                    }
                                },
                                colors =
                                    TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                    ),
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(engines) { engine ->
                                    FilterChip(
                                        selected = engine == currentEngine,
                                        onClick = {
                                            if (engine == VisualSearchEngine.GOOGLE_LENS) {
                                                val uri = imageUri ?: return@FilterChip
                                                val lensManager = LensIntegrationManager(this@VisualSearchWebActivity)
                                                if (lensManager.tryLaunchNativeGoogleLens(uri)) {
                                                    finish()
                                                } else {
                                                    Toast.makeText(
                                                        this@VisualSearchWebActivity,
                                                        getString(R.string.toast_lens_not_installed),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            } else {
                                                loadEngine(engine)
                                            }
                                        },
                                        label = { Text(engine.shortLabel) },
                                        shape = RoundedCornerShape(50),
                                    )
                                }
                            }

                            if (loadProgress in 1..99) {
                                LinearProgressIndicator(
                                    progress = { loadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth().height(3.dp),
                                )
                            }
                        }
                    },
                ) { padding ->
                    AndroidView(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = true
                                settings.allowContentAccess = true
                                settings.loadsImagesAutomatically = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

                                webChromeClient =
                                    object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            loadProgress = newProgress
                                        }

                                        override fun onShowFileChooser(
                                            webView: WebView?,
                                            filePathCallback: ValueCallback<Array<Uri>>?,
                                            fileChooserParams: FileChooserParams?,
                                        ): Boolean {
                                            val uri = imageUri
                                            if (uri != null && filePathCallback != null) {
                                                filePathCallback.onReceiveValue(arrayOf(uri))
                                                return true
                                            }
                                            return false
                                        }
                                    }

                                webViewClient =
                                    object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?,
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

                                webViewRef = this
                                hasInjectedForCurrentPage = false
                                loadUrl(getEngineUrl(currentEngine))
                            }
                        },
                    )
                }
            }
        }
    }

    private fun loadEngine(engine: VisualSearchEngine) {
        currentEngine = engine
        hasInjectedForCurrentPage = false
        webViewRef?.stopLoading()
        webViewRef?.loadUrl(getEngineUrl(engine))
    }

    private fun getEngineUrl(engine: VisualSearchEngine): String =
        when (engine) {
            VisualSearchEngine.TINEYE -> "https://tineye.com/"
            else -> "https://yandex.com/images/"
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
        val js =
            """
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

    private fun loadBase64FromUri(uri: Uri): String? =
        runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }.getOrNull()

    private fun openImageWithExternalApp() {
        val uri = imageUri ?: return
        val viewIntent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/png")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val lang = resources.configuration.locales[0]?.language?.lowercase()
        val title = if (lang == "id" || lang == "in") "Pilih aplikasi untuk membuka" else "Open with"
        startActivity(Intent.createChooser(viewIntent, title))
    }

    override fun onDestroy() {
        webViewRef?.stopLoading()
        webViewRef?.destroy()
        webViewRef = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_IMAGE_URI = "extra_image_uri"
        private const val EXTRA_ENGINE = "extra_engine"

        fun launch(context: Context, imageUri: Uri, engine: VisualSearchEngine) {
            val intent =
                Intent(context, VisualSearchWebActivity::class.java).apply {
                    putExtra(EXTRA_IMAGE_URI, imageUri.toString())
                    putExtra(EXTRA_ENGINE, engine.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        }
    }
}
