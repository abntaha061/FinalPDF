package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.util.AudioPlayerManager
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.link.LinkHandler
import com.github.barteksc.pdfviewer.model.LinkTapEvent

class CustomLinkHandler(
    private val context: Context,
    private val pdfView: PDFView
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

        if (!uriString.isNullOrEmpty()) {
            val lowerUri = uriString.lowercase()
            // If the link contains audio file URL (mp3, wav, ogg)
            if (lowerUri.endsWith(".mp3") || lowerUri.endsWith(".wav") || lowerUri.endsWith(".ogg")) {
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
