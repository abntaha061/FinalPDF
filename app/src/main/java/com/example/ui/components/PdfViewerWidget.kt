package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.example.ui.PdfViewModel
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.util.FitPolicy

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
    onPdfViewCreated: ((PDFView) -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    onLongPress: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onZoomChanged: ((Float) -> Unit)? = null,
    onNavigateToWebView: ((String) -> Unit)? = null,
    onGestureTriggered: ((com.example.data.GestureType, androidx.compose.ui.geometry.Offset?) -> Unit)? = null,
    onTextSelected: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pdfViewInstance by remember { mutableStateOf<PDFView?>(null) }

    // Initialize WebViewAssetLoader once
    val assetLoader = remember(context, pdfUriString) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/pdf/", object : WebViewAssetLoader.PathHandler {
                override fun handle(path: String): WebResourceResponse? {
                    try {
                        val uri = Uri.parse(pdfUriString)
                        val inputStream = if (uri.scheme == "content") {
                            context.contentResolver.openInputStream(uri)
                        } else {
                            val filePath = uri.path ?: uri.toString()
                            java.io.FileInputStream(java.io.File(filePath))
                        }
                        if (inputStream != null) {
                            return WebResourceResponse(
                                "application/pdf",
                                null,
                                inputStream
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PdfViewerWidget", "Failed to stream PDF to WebViewAssetLoader: $pdfUriString", e)
                    }
                    return null
                }
            })
            .build()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Hidden PDFView to track state, pageCount, search results, and ensure backward compatibility
        AndroidView(
            factory = { ctx ->
                PDFView(ctx, null).apply {
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    pdfViewInstance = this
                    onPdfViewCreated?.invoke(this)

                    val uri = Uri.parse(pdfUriString)
                    val fitPolicy = when (fitMode) {
                        "width" -> FitPolicy.WIDTH
                        "fit" -> FitPolicy.HEIGHT
                        else -> FitPolicy.BOTH
                    }

                    fromUri(uri)
                        .defaultPage(currentPage)
                        .onPageChange { page, pageCount ->
                            // Update WebView if it is initialized and current page diverges
                            webViewInstance?.post {
                                webViewInstance?.evaluateJavascript(
                                    "if (window.PDFViewerApplication && window.PDFViewerApplication.page !== ${page + 1}) { window.PDFViewerApplication.page = ${page + 1}; }",
                                    null
                                )
                            }
                            onPageChanged(page, pageCount)
                        }
                        .onLoad { nbPages ->
                            onLoadComplete(nbPages)
                        }
                        .onError { t ->
                            onError(t)
                        }
                        .enableSwipe(true)
                        .swipeHorizontal(isSwipeHorizontal)
                        .nightMode(readingMode == "night")
                        .pageFitPolicy(fitPolicy)
                        .load()
                }
            },
            update = { pdfView ->
                if (pdfView.currentPage != currentPage) {
                    pdfView.jumpTo(currentPage)
                }
            },
            modifier = Modifier.size(1.dp).align(Alignment.TopStart)
        )

        // 2. Foreground WebView using Mozilla PDF.js for extremely high-precision text selection and beautiful rendering
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewInstance = this
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
                    }

                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onPageChanged(page: Int, pageCount: Int) {
                            post {
                                if (pdfViewInstance?.currentPage != page) {
                                    pdfViewInstance?.jumpTo(page)
                                }
                                onPageChanged(page, pageCount)
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun onLoadComplete(pageCount: Int) {
                            post {
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
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val requestUrl = request?.url ?: return null
                            
                            // 1. First intercept with WebViewAssetLoader
                            val response = assetLoader.shouldInterceptRequest(requestUrl)
                            if (response != null) {
                                return response
                            }

                            // 2. Fallback check for PDF requests
                            val path = requestUrl.path ?: ""
                            if (path.contains("pdf/document.pdf")) {
                                try {
                                    val uri = Uri.parse(pdfUriString)
                                    val inputStream = if (uri.scheme == "content") {
                                        ctx.contentResolver.openInputStream(uri)
                                    } else {
                                        val filePath = uri.path ?: uri.toString()
                                        java.io.FileInputStream(java.io.File(filePath))
                                    }
                                    if (inputStream != null) {
                                        return WebResourceResponse(
                                            "application/pdf",
                                            null,
                                            inputStream
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("PdfViewerWidget", "Failed to stream PDF directly: $pdfUriString", e)
                                }
                            }
                            return null
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)

                            // Inject event listeners for text selection, tapping and custom long press selection
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

                            // Apply night-mode theme style overlay if active
                            if (readingMode == "night") {
                                val nightStyleJs = """
                                    var style = document.getElementById('night-mode-style');
                                    if (!style) {
                                        style = document.createElement('style');
                                        style.id = 'night-mode-style';
                                        style.innerHTML = 'html { filter: invert(0.9) hue-rotate(180deg); background-color: #121212 !important; } .textLayer { mix-blend-mode: difference; }';
                                        document.head.appendChild(style);
                                    }
                                """.trimIndent()
                                view?.evaluateJavascript(nightStyleJs, null)
                            }
                        }
                    }

                    // Use HTTPS appassets.androidplatform.net schema with zoom and page parameters
                    val viewerUrl = "https://appassets.androidplatform.net/assets/pdfjs/web/viewer.html" +
                        "?file=https://appassets.androidplatform.net/pdf/document.pdf" +
                        "#page=${currentPage + 1}&zoom=page-width"
                    loadUrl(viewerUrl)
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
                            style.innerHTML = 'html { filter: invert(0.9) hue-rotate(180deg); background-color: #121212 !important; } .textLayer { mix-blend-mode: difference; }';
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

fun com.github.barteksc.pdfviewer.PDFView.setPageRotation(page: Int, rotation: Int) {
    if (page == this.currentPage) {
        val normRot = ((rotation % 360) + 360) % 360
        this.rotation = normRot.toFloat()
    }
}
