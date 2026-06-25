package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.ui.PdfViewModel

class PdfViewerController(
    val webView: WebView?,
    val onJumpToPage: (Int) -> Unit = {}
) {
    var pageCount: Int = 0
    var currentPage: Int = 0
    var zoom: Float = 1.0f
    val maxZoom: Float = 4.0f
    val minZoom: Float = 0.5f

    fun jumpTo(page: Int) {
        currentPage = page
        webView?.post {
            webView.evaluateJavascript("if (window.PDFViewerApplication && window.PDFViewerApplication.page !== ${page + 1}) { window.PDFViewerApplication.page = ${page + 1}; }", null)
        }
        onJumpToPage(page)
    }

    fun jumpTo(page: Int, withAnimation: Boolean) {
        jumpTo(page)
    }

    fun zoomTo(zoomFactor: Float) {
        zoom = zoomFactor
        webView?.post {
            webView.evaluateJavascript("if (window.PDFViewerApplication && window.PDFViewerApplication.pdfViewer) { window.PDFViewerApplication.pdfViewer.currentScale = $zoomFactor; }", null)
        }
    }

    fun zoomWithAnimation(zoomFactor: Float) {
        zoomTo(zoomFactor)
    }

    fun invalidate() {
        // No-op for compatibility
    }

    private var tag: Any? = null
    fun setTag(t: Any?) { tag = t }
    fun getTag(): Any? = tag
}

fun PdfViewerController.setPageRotation(page: Int, rotation: Int) {
    val normRot = ((rotation % 360) + 360) % 360
    webView?.post {
        webView.evaluateJavascript("if (window.PDFViewerApplication) { window.PDFViewerApplication.rotatePages($normRot - (window.PDFViewerApplication.pdfViewer.pagesRotation || 0)); }", null)
    }
}

@Composable
fun PdfViewerWidget(
    pdfUriString: String,
    currentPage: Int,
    readingMode: String,
    isSwipeHorizontal: Boolean,
    readingScrollMode: String,
    fitMode: String,
    onPageChanged: (Int, Int) -> Unit,
    onLoadComplete: (Int) -> Unit,
    onError: (Throwable) -> Unit,
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier,
    onPdfViewCreated: ((PdfViewerController) -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    onLongPress: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onZoomChanged: ((Float) -> Unit)? = null,
    onNavigateToWebView: ((String) -> Unit)? = null,
    onGestureTriggered: ((com.example.data.GestureType, androidx.compose.ui.geometry.Offset?) -> Unit)? = null,
    onTextSelected: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var controllerInstance by remember { mutableStateOf<PdfViewerController?>(null) }
    var isPageFinished by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewInstance = this
                    val ctrl = PdfViewerController(this)
                    controllerInstance = ctrl
                    onPdfViewCreated?.invoke(ctrl)

                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        allowFileAccessFromFileURLs = true
                        allowUniversalAccessFromFileURLs = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                        mediaPlaybackRequiresUserGesture = false

                        // Safe and modern way to disable force dark & algorithmic darkening
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, false)
                        }
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                            @Suppress("DEPRECATION")
                            WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_OFF)
                        }
                    }

                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            if (consoleMessage != null) {
                                android.util.Log.d("PdfViewerJS", "${consoleMessage.messageLevel()}: ${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                            }
                            return true
                        }
                    }

                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun getCurrentPage(): Int {
                            return currentPage
                        }

                        @android.webkit.JavascriptInterface
                        fun onPageChanged(page: Int, pageCount: Int) {
                            post {
                                controllerInstance?.currentPage = page
                                controllerInstance?.pageCount = pageCount
                                onPageChanged(page, pageCount)
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun onLoadComplete(pageCount: Int) {
                            post {
                                controllerInstance?.pageCount = pageCount
                                onLoadComplete(pageCount)
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun onTextSelected(text: String) {
                            post {
                                onTextSelected?.invoke(text)
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun onLongPress(clientX: Float, clientY: Float) {
                            post {
                                val density = ctx.resources.displayMetrics.density
                                val screenX = clientX * density
                                val screenY = clientY * density
                                onLongPress?.invoke(androidx.compose.ui.geometry.Offset(screenX, screenY))
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun onTap() {
                            post {
                                onTap?.invoke()
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun onAudioLinkClick(url: String) {
                            post {
                                onNavigateToWebView?.invoke(url)
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun onExternalLinkClick(url: String) {
                            post {
                                onNavigateToWebView?.invoke(url)
                            }
                        }
                    }, "Android")

                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            android.util.Log.e("PDFJS_ERROR", "Error: ${error?.description} for ${request?.url}")
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            val url = request?.url ?: return null
                            if (url.host == "app.local") {
                                val path = url.path ?: ""
                                if (path == "/current_pdf.pdf") {
                                    try {
                                        val uri = Uri.parse(pdfUriString)
                                        val inputStream = if (uri.scheme == "content") {
                                            context.contentResolver.openInputStream(uri)
                                        } else {
                                            java.io.FileInputStream(java.io.File(uri.path ?: pdfUriString))
                                        }
                                        if (inputStream != null) {
                                            return android.webkit.WebResourceResponse(
                                                "application/pdf",
                                                "UTF-8",
                                                inputStream
                                            )
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("PdfViewerWidget", "Failed to load current PDF stream", e)
                                    }
                                } else if (path.startsWith("/pdfjs/")) {
                                    try {
                                        // Remove leading "/pdfjs/" to get asset path
                                        val assetPath = path.substring(1) // e.g., "pdfjs/web/viewer.html"
                                        val inputStream = context.assets.open(assetPath)
                                        
                                        // Determine MIME type
                                        val mimeType = when {
                                            path.endsWith(".html") -> "text/html"
                                            path.endsWith(".css") -> "text/css"
                                            path.endsWith(".js") -> "application/javascript"
                                            path.endsWith(".json") -> "application/json"
                                            path.endsWith(".svg") -> "image/svg+xml"
                                            path.endsWith(".png") -> "image/png"
                                            path.endsWith(".wasm") -> "application/wasm"
                                            else -> "application/octet-stream"
                                        }
                                        
                                        return android.webkit.WebResourceResponse(mimeType, "UTF-8", inputStream)
                                    } catch (e: Exception) {
                                        android.util.Log.e("PdfViewerWidget", "Failed to load asset: $path", e)
                                    }
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isPageFinished = true

                            val jsInjection = """
                                // Listen for native browser selection changes
                                document.addEventListener('selectionchange', function() {
                                    var selection = window.getSelection().toString();
                                    if (selection) {
                                        Android.onTextSelected(selection);
                                    }
                                });

                                // Detect quick taps (ignoring interactive buttons or existing selections)
                                document.addEventListener('click', function(e) {
                                    if (e.target.tagName !== 'INPUT' && e.target.tagName !== 'BUTTON') {
                                        var selection = window.getSelection().toString();
                                        if (!selection) {
                                            Android.onTap();
                                        }
                                    }
                                });

                                // Support long press coordinate forwarding
                                document.addEventListener('contextmenu', function(e) {
                                    Android.onLongPress(e.clientX, e.clientY);
                                });

                                // Set up event bus listener for loading progress and page swaps
                                function setupEventBusListeners() {
                                    if (window.PDFViewerApplication && window.PDFViewerApplication.eventBus) {
                                        window.PDFViewerApplication.eventBus.on('pagechanging', function(evt) {
                                            Android.onPageChanged(evt.pageNumber - 1, window.PDFViewerApplication.pagesCount);
                                        });
                                        window.PDFViewerApplication.eventBus.on('pagesinit', function() {
                                            Android.onLoadComplete(window.PDFViewerApplication.pagesCount);
                                        });
                                    } else {
                                        setTimeout(setupEventBusListeners, 50);
                                    }
                                }
                                setupEventBusListeners();
                            """.trimIndent()
                            view?.evaluateJavascript(jsInjection, null)

                            if (readingMode == "night") {
                                val nightStyleJs = """
                                    var style = document.getElementById('night-mode-style');
                                    if (!style) {
                                        style = document.createElement('style');
                                        style.id = 'night-mode-style';
                                        style.innerHTML = '#viewerContainer { background-color: #121212 !important; } .page, .thumbnail { filter: invert(0.9) hue-rotate(180deg) !important; } .textLayer { mix-blend-mode: difference; }';
                                        document.head.appendChild(style);
                                    }
                                """.trimIndent()
                                view?.evaluateJavascript(nightStyleJs, null)
                            }
                        }
                    }

                    // Load viewer.html under app.local custom HTTPS origin!
                    loadUrl("https://app.local/pdfjs/web/viewer.html?file=https://app.local/current_pdf.pdf#page=${currentPage + 1}&zoom=page-width")
                }
            },
            update = { webView ->
                val jsPage = "if (window.PDFViewerApplication && window.PDFViewerApplication.page !== ${currentPage + 1}) { window.PDFViewerApplication.page = ${currentPage + 1}; }"
                webView.evaluateJavascript(jsPage, null)

                if (readingMode == "night") {
                    val nightStyleJs = """
                        var style = document.getElementById('night-mode-style');
                        if (!style) {
                            style = document.createElement('style');
                            style.id = 'night-mode-style';
                            style.innerHTML = '#viewerContainer { background-color: #121212 !important; } .page, .thumbnail { filter: invert(0.9) hue-rotate(180deg) !important; } .textLayer { mix-blend-mode: difference; }';
                            document.head.appendChild(style);
                        }
                    """.trimIndent()
                    webView.evaluateJavascript(nightStyleJs, null)
                } else {
                    val dayStyleJs = """
                        var style = document.getElementById('night-mode-style');
                        if (style) {
                            style.remove();
                        }
                    """.trimIndent()
                    webView.evaluateJavascript(dayStyleJs, null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
