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
import java.io.File

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
    var loadError by remember { mutableStateOf<String?>(null) }

    val file = remember(pdfUriString) {
        val fileUri = Uri.parse(pdfUriString)
        val f: File? = when (fileUri.scheme) {
            "file" -> fileUri.path?.let { File(it) }
            "content" -> {
                try {
                    val pfd = context.contentResolver.openFileDescriptor(fileUri, "r")
                    val fd = pfd?.fileDescriptor
                    // نسخ الملف إلى cacheDir للوصول إليه عبر WebViewAssetLoader
                    val cacheFile = File(context.cacheDir, "pdf_viewer_temp.pdf")
                    pfd?.use {
                        java.io.FileInputStream(fd).use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    cacheFile
                } catch (e: Exception) {
                    Log.e("PdfViewerWidget", "Failed to copy content URI to cache", e)
                    null
                }
            }
            else -> null
        }
        if (f == null || !f.exists()) {
            loadError = "تعذّر قراءة الملف"
        }
        f
    }

    if (loadError == null && file != null) {
        AndroidView<WebView>(
            factory = { ctx: Context ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true

                    // no-op: PDFView not used with WebView implementation

                    addJavascriptInterface(object : Any() {
                        @JavascriptInterface
                        fun onAudioLinkClick(url: String) {
                            com.example.util.AudioPlayerManager.playAudio(url)
                        }

                        @JavascriptInterface
                        fun onExternalLinkClick(url: String) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            ctx.startActivity(intent)
                        }
                    }, "Android")

                    val assetLoader = WebViewAssetLoader.Builder()
                        .addPathHandler(
                            "/local_pdf/",
                            WebViewAssetLoader.InternalStoragePathHandler(ctx, file.parentFile ?: ctx.cacheDir)
                        )
                        .build()
                    val safeFileName = file.name

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val requestUrl = request?.url ?: return null
                            return assetLoader.shouldInterceptRequest(requestUrl)
                        }
                    }

                    // Load PDF.js with loaded file
                    val pdfUrl = "https://appassets.androidplatform.net/local_pdf/$safeFileName"
                    loadUrl("file:///android_asset/pdfjs/web/viewer.html?file=" + Uri.encode(pdfUrl))
                }
            },
            update = { webView: WebView ->
                // no-op
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
