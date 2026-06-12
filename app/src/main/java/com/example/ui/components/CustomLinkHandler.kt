package com.github.barteksc.pdfviewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.graphics.RectF
import com.example.util.AudioPlayerManager
import com.github.barteksc.pdfviewer.link.LinkHandler
import com.github.barteksc.pdfviewer.model.LinkTapEvent

class CustomLinkHandler(
    private val context: Context,
    private val pdfView: PDFView,
    private val onLinkTapped: (RectF) -> Unit
) : LinkHandler {

    override fun handleLinkEvent(event: LinkTapEvent) {
        val link = event.link
        if (link == null) {
            Log.d("CustomLinkHandler", "Tapped link event has no link")
            return
        }

        val uriString = link.uri
        val destPageIdx = link.destPageIdx

        Log.d("CustomLinkHandler", "PDF Link tapped for debug: uri = $uriString, destPageIdx = $destPageIdx")

        // Map link bounds to screen coordinate RectF using the library's getMappedLinkRect via reflection
        val page = pdfView.currentPage
        var mappedRect: RectF? = null
        try {
            val pdfFile = pdfView.pdfFile
            if (pdfFile != null) {
                val methods = pdfFile.javaClass.declaredMethods
                for (m in methods) {
                    if (m.name.contains("rect", ignoreCase = true) || m.name.contains("link", ignoreCase = true)) {
                        Log.d("CustomLinkHandler", "PdfFile method: ${m.name}(${m.parameterTypes.joinToString { it.simpleName }})")
                    }
                }
                val targetMethod = methods.firstOrNull { 
                    (it.name.contains("MappedLink", ignoreCase = true) || it.name.contains("LinkRect", ignoreCase = true)) &&
                    it.parameterTypes.size == 2
                }
                if (targetMethod != null) {
                    Log.d("CustomLinkHandler", "Invoking mapping method dynamically: ${targetMethod.name}")
                    targetMethod.isAccessible = true
                    mappedRect = targetMethod.invoke(pdfFile, page, link) as? RectF
                }
            }
        } catch (e: Exception) {
            Log.e("CustomLinkHandler", "Error mapping link via reflection", e)
        }

        val finalRect = mappedRect ?: RectF(
            event.originalX - 25f, 
            event.originalY - 25f, 
            event.originalX + 25f, 
            event.originalY + 25f
        )
        onLinkTapped(finalRect)

        if (!uriString.isNullOrEmpty()) {
            val lowerUri = uriString.lowercase()
            // If the link contains audio file URL (mp3, wav, ogg, m4a, aac)
            if (lowerUri.endsWith(".mp3") || lowerUri.endsWith(".wav") || lowerUri.endsWith(".ogg") ||
                lowerUri.endsWith(".m4a") || lowerUri.endsWith(".aac")) {
                Log.d("CustomLinkHandler", "Tapped audio URL, sending to AudioPlayerManager: $uriString")
                AudioPlayerManager.play(context, uriString)
            } else if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                Log.d("CustomLinkHandler", "Tapped http/https URI, launching system browser: $uriString")
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("CustomLinkHandler", "Error launching system browser intent for: $uriString", e)
                }
            }
        } else if (destPageIdx != null) {
            Log.d("CustomLinkHandler", "Tapped internal destination page index: $destPageIdx, jumping PDFView")
            pdfView.jumpTo(destPageIdx)
        }
    }
}
