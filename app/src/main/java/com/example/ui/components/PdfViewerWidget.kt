package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.example.ui.PdfViewModel
import com.github.barteksc.pdfviewer.PDFView

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
    onGestureTriggered: ((com.example.data.GestureType, androidx.compose.ui.geometry.Offset?) -> Unit)? = null
) {
    val context = LocalContext.current

    if (pdfUriString.isNotEmpty()) {
        AndroidView<WebView>(
            factory = { ctx: Context ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true

                    // no-op: PDFView not used with WebView implementation

                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    addJavascriptInterface(object : Any() {
                        @JavascriptInterface
                        fun onPageChanged(page: Int, total: Int) {
                            mainHandler.post {
                                onPageChanged(page, total)
                            }
                        }

                        @JavascriptInterface
                        fun onLoadComplete(total: Int) {
                            mainHandler.post {
                                onLoadComplete(total)
                            }
                        }

                        @JavascriptInterface
                        fun onAudioLinkClick(url: String) {
                            mainHandler.post {
                                com.example.util.AudioPlayerManager.playAudio(url)
                            }
                        }

                        @JavascriptInterface
                        fun onExternalLinkClick(url: String) {
                            mainHandler.post {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("PdfViewerWidget", "Failed to open link: $url", e)
                                }
                            }
                        }
                    }, "Android")

                    val assetLoader = WebViewAssetLoader.Builder()
                        .addPathHandler(
                            "/assets/",
                            WebViewAssetLoader.AssetsPathHandler(ctx)
                        )
                        .build()

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val requestUrl = request?.url ?: return null
                            val path = requestUrl.path ?: ""

                            // Stream PDF file request to WebView on background thread
                            if (path.endsWith("/local_pdf/document.pdf") || path.contains("/local_pdf/")) {
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
                                    Log.e("PdfViewerWidget", "Failed to stream PDF: $pdfUriString", e)
                                }
                            }

                            return assetLoader.shouldInterceptRequest(requestUrl)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            
                            // Inject event listeners to communicate page and load updates back to Compose
                            view?.evaluateJavascript("""
                                (function() {
                                    function setupListeners() {
                                        if (window.PDFViewerApplication && window.PDFViewerApplication.eventBus) {
                                            window.PDFViewerApplication.eventBus.on('pagechanging', function(evt) {
                                                Android.onPageChanged(evt.pageNumber - 1, window.PDFViewerApplication.pagesCount);
                                            });
                                            window.PDFViewerApplication.eventBus.on('pagesinit', function() {
                                                Android.onLoadComplete(window.PDFViewerApplication.pagesCount);
                                            });
                                            console.log('PDF.js event listeners attached successfully!');
                                        } else {
                                            setTimeout(setupListeners, 100);
                                        }
                                    }
                                    setupListeners();
                                })();
                            """.trimIndent(), null)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.endsWith(".mp3") || url.endsWith(".wav") || url.contains("audio")) {
                                com.example.util.AudioPlayerManager.playAudio(url)
                                return true
                            }
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                ctx.startActivity(intent)
                                return true
                            } catch (e: Exception) {
                                Log.e("PdfViewerWidget", "Failed to override url load: $url", e)
                            }
                            return false
                        }
                    }

                    val pdfUrl = "https://appassets.androidplatform.net/local_pdf/document.pdf"
                    val zoomParam = when (fitMode) {
                        "width" -> "page-width"
                        "fit" -> "page-fit"
                        else -> "auto"
                    }
                    val initialPage = currentPage + 1
                    val viewerUrl = "https://appassets.androidplatform.net/assets/pdfjs/web/viewer.html?file=" +
                                    Uri.encode(pdfUrl) + "#page=$initialPage&zoom=$zoomParam"
                    loadUrl(viewerUrl)
                }
            },
            update = { webView: WebView ->
                val targetPage = currentPage + 1
                val isNight = (readingMode == "night")
                webView.evaluateJavascript("""
                    if (window.PDFViewerApplication && window.PDFViewerApplication.page !== $targetPage) {
                        window.PDFViewerApplication.page = $targetPage;
                    }
                    if ($isNight) {
                        document.documentElement.style.filter = 'invert(0.9) hue-rotate(180deg)';
                    } else {
                        document.documentElement.style.filter = 'none';
                    }
                """.trimIndent(), null)
            },
            modifier = modifier.fillMaxSize()
        )
    }
}

fun com.github.barteksc.pdfviewer.PDFView.setPageRotation(page: Int, rotation: Int) {
    if (page == this.currentPage) {
        val normRot = ((rotation % 360) + 360) % 360
        this.rotation = normRot.toFloat()
    }
}
