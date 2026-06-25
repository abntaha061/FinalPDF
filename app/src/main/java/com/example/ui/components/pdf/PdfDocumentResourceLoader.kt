package com.example.ui.components.pdf

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceResponse
import java.io.File
import java.io.FileInputStream

/**
 * ResourceLoader that intercepts requests to "https://app.local/current_pdf.pdf"
 * and streams the actual PDF file being viewed.
 */
class PdfDocumentResourceLoader(
    private val context: Context,
    private val pdfUriStringProvider: () -> String,
    private val onError: (Exception) -> Unit = {}
) {

    fun canHandle(uri: Uri): Boolean {
        return uri.host == "appassets.androidplatform.net" && uri.path == "/current_pdf.pdf"
    }

    fun shouldInterceptRequest(uri: Uri): WebResourceResponse? {
        try {
            val pdfUriString = pdfUriStringProvider()
            val pdfUri = Uri.parse(pdfUriString)
            val inputStream = if (pdfUri.scheme == "content") {
                context.contentResolver.openInputStream(pdfUri)
            } else {
                FileInputStream(File(pdfUri.path ?: pdfUriString))
            }

            if (inputStream != null) {
                val response = WebResourceResponse(
                    "application/pdf",
                    "UTF-8",
                    inputStream
                )
                response.responseHeaders = mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
                    "Access-Control-Allow-Headers" to "Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With"
                )
                return response
            }
        } catch (e: Exception) {
            Log.e("PdfDocResourceLoader", "Failed to load current PDF stream", e)
            onError(e)
        }
        return null
    }
}
