package com.example.ui.components

import android.webkit.JavascriptInterface

class WebInterface(
    private val onLoadSuccess: (Int) -> Unit = {},
    private val onPageChange: (Int) -> Unit = {},
    private val onTextSelected: (String, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> }
) {
    @JavascriptInterface
    fun onLoadSuccess(totalPages: Int) {
        onLoadSuccess(totalPages)
    }

    @JavascriptInterface
    fun onPageChange(pageIdx: Int) {
        onPageChange(pageIdx)
    }

    @JavascriptInterface
    fun onTextSelected(text: String, x: Float, y: Float, width: Float, height: Float) {
        onTextSelected(text, x, y, width, height)
    }
}
