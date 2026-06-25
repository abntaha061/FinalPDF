package com.example.ui.components.pdf

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceResponse

/**
 * ResourceLoader that serves PDF.js legacy files directly from the application's local assets.
 */
class AssetResourceLoader(
    private val context: Context,
    private val onError: (String, Exception) -> Unit = { _, _ -> }
) : ResourceLoader {

    override fun canHandle(uri: Uri): Boolean {
        return uri.host == "app.local" && (uri.path?.startsWith("/pdfjs/") == true)
    }

    override fun shouldInterceptRequest(uri: Uri): WebResourceResponse? {
        val path = uri.path ?: return null
        try {
            // Remove leading "/pdfjs/" to get the relative asset path
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

            return WebResourceResponse(mimeType, "UTF-8", inputStream)
        } catch (e: Exception) {
            Log.e("AssetResourceLoader", "Failed to load asset: $path", e)
            onError(path, e)
            return null
        }
    }
}
