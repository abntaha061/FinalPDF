package com.example.ui.components.pdf

import android.net.Uri
import android.webkit.WebResourceResponse

/**
 * Interface representing a component capable of handling and intercepting local requests.
 */
interface ResourceLoader {
    /**
     * Checks if this loader can handle the given URI.
     */
    fun canHandle(uri: Uri): Boolean

    /**
     * Intercepts the request and returns a WebResourceResponse, or null if it cannot be loaded.
     */
    fun shouldInterceptRequest(uri: Uri): WebResourceResponse?
}
