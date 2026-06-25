package com.example.ui.components.pdf

import android.webkit.JavascriptInterface

/**
 * JavaScript interface to bridge communication between PDF.js in WebView and Jetpack Compose.
 */
class WebInterface(
    private val currentPageProvider: () -> Int,
    private val onPageChangedCallback: (Int, Int) -> Unit,
    private val onLoadCompleteCallback: (Int) -> Unit,
    private val onTextSelectedCallback: (String) -> Unit,
    private val onLongPressCallback: (Float, Float) -> Unit,
    private val onTapCallback: () -> Unit,
    private val onAudioLinkClickCallback: (String) -> Unit,
    private val onExternalLinkClickCallback: (String) -> Unit
) {
    @JavascriptInterface
    fun getCurrentPage(): Int {
        return currentPageProvider()
    }

    @JavascriptInterface
    fun onPageChanged(page: Int, pageCount: Int) {
        onPageChangedCallback(page, pageCount)
    }

    @JavascriptInterface
    fun onLoadComplete(pageCount: Int) {
        onLoadCompleteCallback(pageCount)
    }

    @JavascriptInterface
    fun onTextSelected(text: String) {
        onTextSelectedCallback(text)
    }

    @JavascriptInterface
    fun onLongPress(clientX: Float, clientY: Float) {
        onLongPressCallback(clientX, clientY)
    }

    @JavascriptInterface
    fun onTap() {
        onTapCallback()
    }

    @JavascriptInterface
    fun onAudioLinkClick(url: String) {
        onAudioLinkClickCallback(url)
    }

    @JavascriptInterface
    fun onExternalLinkClick(url: String) {
        onExternalLinkClickCallback(url)
    }
}
