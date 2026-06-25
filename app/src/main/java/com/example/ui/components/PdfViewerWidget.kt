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

    var pdfBase64 by remember(pdfUriString) { mutableStateOf<String?>(null) }

    LaunchedEffect(pdfUriString) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val uri = Uri.parse(pdfUriString)
                val inputStream = if (uri.scheme == "content") {
                    context.contentResolver.openInputStream(uri)
                } else {
                    java.io.FileInputStream(java.io.File(uri.path ?: pdfUriString))
                }
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    pdfBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                } else {
                    onError(Exception("Could not read PDF bytes"))
                }
            } catch (e: Exception) {
                android.util.Log.e("PdfViewerWidget", "Failed to load PDF bytes", e)
                onError(e)
            }
        }
    }

    LaunchedEffect(isPageFinished, pdfBase64) {
        val base64 = pdfBase64
        val webView = webViewInstance
        if (isPageFinished && base64 != null && webView != null) {
            webView.evaluateJavascript("if (window.loadPdfFromAndroid) { window.loadPdfFromAndroid(); }", null)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewInstance = this
                    val ctrl = PdfViewerController(this)
                    controllerInstance = ctrl
                    onPdfViewCreated?.invoke(ctrl)

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

                        // Disable algorithmic darkening on newer versions
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= 33) { // Build.VERSION_CODES.TIRAMISU
                                val method = settings.javaClass.getMethod("setAlgorithmicDarkeningAllowed", Boolean::class.javaPrimitiveType)
                                method.invoke(settings, false)
                            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                @Suppress("DEPRECATION")
                                forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
                            }
                        } catch (e: Throwable) {
                            android.util.Log.e("PdfViewerWidget", "Failed to disable force dark", e)
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
                        fun getPdfBase64(): String {
                            return pdfBase64 ?: ""
                        }

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

                                window.loadPdfFromAndroid = function() {
                                    if (window.PDFViewerApplication && typeof window.PDFViewerApplication.open === 'function') {
                                        try {
                                            var base64Data = Android.getPdfBase64();
                                            if (base64Data) {
                                                var binaryString = window.atob(base64Data);
                                                var len = binaryString.length;
                                                var bytes = new Uint8Array(len);
                                                for (var i = 0; i < len; i++) {
                                                    bytes[i] = binaryString.charCodeAt(i);
                                                }
                                                window.PDFViewerApplication.open({ data: bytes }).then(function() {
                                                    var startPage = Android.getCurrentPage();
                                                    if (window.PDFViewerApplication.page !== startPage + 1) {
                                                        window.PDFViewerApplication.page = startPage + 1;
                                                    }
                                                });
                                            }
                                        } catch (e) {
                                            console.error("Error loading PDF via JS: " + e.message);
                                        }
                                    } else {
                                        setTimeout(window.loadPdfFromAndroid, 50);
                                    }
                                };
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

                    // Load viewer.html from local assets!
                    loadUrl("file:///android_asset/pdfjs/web/viewer.html")
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
