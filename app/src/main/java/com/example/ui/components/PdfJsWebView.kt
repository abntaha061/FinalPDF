package com.example.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.util.ContentResourceLoader
import com.example.util.AssetResourceLoader

class PdfJsWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var pdfView: android.view.View? = null

    init {
        // Transparent background
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            textZoom = 100
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Forward the touch event to the native PDFView underneath.
        // This ensures scrolling, continuous scroll and pinch-to-zoom always behave natively.
        pdfView?.dispatchTouchEvent(event)
        
        // Also let the WebView process the event for text selection, selection handles, etc.
        return super.onTouchEvent(event)
    }
}

class PdfJsWebViewClient(
    private val context: Context,
    private val pdfUriString: String
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url ?: return null
        val path = url.path ?: ""

        return when {
            // Intercept PDF document
            url.host == "localpdf" && path.endsWith("document.pdf") -> {
                val inputStream = ContentResourceLoader.openContentUri(context, pdfUriString)
                if (inputStream != null) {
                    WebResourceResponse("application/pdf", "UTF-8", inputStream)
                } else null
            }
            // Intercept local HTML/JS assets
            url.host == "localpdf" && (path.endsWith("pdf_viewer.html") || path.endsWith("helper_main.js")) -> {
                val fileName = path.removePrefix("/")
                val inputStream = AssetResourceLoader.openAsset(context, fileName)
                if (inputStream != null) {
                    val mimeType = if (fileName.endsWith(".html")) "text/html" else "application/javascript"
                    WebResourceResponse(mimeType, "UTF-8", inputStream)
                } else null
            }
            else -> super.shouldInterceptRequest(view, request)
        }
    }
}
