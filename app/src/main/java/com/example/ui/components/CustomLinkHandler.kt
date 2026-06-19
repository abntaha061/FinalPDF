package com.github.barteksc.pdfviewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.graphics.RectF
import com.example.util.AudioPlayerManager
import com.github.barteksc.pdfviewer.link.LinkHandler
import com.github.barteksc.pdfviewer.model.LinkTapEvent
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

import com.example.util.pdfReaderDataStore

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class CustomLinkHandler(
    private val context: Context,
    private val pdfView: PDFView,
    private val pdfUriString: String,
    private val onLinkTapped: (RectF) -> Unit,
    private val onNavigateToWebView: ((String) -> Unit)? = null
) : LinkHandler {

    private var tts: android.speech.tts.TextToSpeech? = null

    init {
        try {
            tts = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    tts?.language = java.util.Locale.GERMAN
                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            AudioPlayerManager.setSpeechState(null, null, false)
                        }
                        override fun onError(utteranceId: String?) {
                            AudioPlayerManager.setSpeechState(null, null, false)
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.e("CustomLinkHandler", "Failed to initialize TTS", e)
        }
    }

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

            // Read preferences synchronously
            val (autoPlayAudio, audioVolume, linkOpenMode) = try {
                val preferences = runBlocking { context.pdfReaderDataStore.data.first() }
                val autoPlay = preferences[androidx.datastore.preferences.core.booleanPreferencesKey("auto_play_audio")] ?: true
                val vol = preferences[androidx.datastore.preferences.core.floatPreferencesKey("audio_volume")] ?: 1.0f
                val mode = preferences[androidx.datastore.preferences.core.stringPreferencesKey("link_open_mode")] ?: "المتصفح الافتراضي"
                Triple(autoPlay, vol, mode)
            } catch (e: Exception) {
                Log.e("CustomLinkHandler", "Error reading datastore in custom link handler", e)
                Triple(true, 1.0f, "المتصفح الافتراضي")
            }

            // Check if the link is an audio URL or points to an audio stream
            val isAudioUrl = lowerUri.endsWith(".mp3") || lowerUri.endsWith(".wav") || lowerUri.endsWith(".ogg") ||
                    lowerUri.endsWith(".m4a") || lowerUri.endsWith(".aac") ||
                    lowerUri.contains("dictvoice") || lowerUri.contains("pronunciation") ||
                    lowerUri.contains("voice") || lowerUri.contains("/audio/") || lowerUri.contains("/sound/")

            if (isAudioUrl) {
                Log.d("CustomLinkHandler", "Tapped audio URL, sending to AudioPlayerManager: $uriString with autoPlay: $autoPlayAudio, vol: $audioVolume")
                val extractedWord = getWordAtLink(link?.bounds, event, page)
                if (autoPlayAudio) {
                    AudioPlayerManager.play(context, uriString, audioVolume, wordText = extractedWord, rect = finalRect)
                } else {
                    android.app.AlertDialog.Builder(context)
                        .setTitle("تشغيل الصوت")
                        .setMessage("هل تريد تشغيل هذا الملف الصوتي؟")
                        .setPositiveButton("تشغيل") { _, _ ->
                            AudioPlayerManager.play(context, uriString, audioVolume, wordText = extractedWord, rect = finalRect)
                        }
                        .setNegativeButton("إلغاء", null)
                        .show()
                }
            } else if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                Log.d("CustomLinkHandler", "Tapped http/https URI: $uriString, launching external browser")
                launchExternalBrowser(context, uriString)
            }
        } else if (destPageIdx != null) {
            Log.d("CustomLinkHandler", "Tapped internal destination page index: $destPageIdx, jumping PDFView")
            pdfView.jumpTo(destPageIdx)
        }
    }

    private fun openWebViewInDialog(context: Context, url: String) {
        try {
            val webView = android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = android.webkit.WebViewClient()
                loadUrl(url)
            }
            android.app.AlertDialog.Builder(context)
                .setView(webView)
                .setPositiveButton("إغلاق", null)
                .show()
        } catch (e: Exception) {
            Log.e("CustomLinkHandler", "Failed to show webView dialog", e)
            launchExternalBrowser(context, url)
        }
    }

    private fun launchExternalBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("CustomLinkHandler", "Error launching system browser intent", e)
        }
    }

    private fun getWordAtLink(bounds: RectF?, event: LinkTapEvent, pageIndex: Int): String {
        try {
            if (pdfUriString.isBlank()) return ""
            val pdfUri = Uri.parse(pdfUriString)
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    if (pageIndex >= document.numberOfPages) return ""
                    val pageObj = document.getPage(pageIndex)
                    val mediaBox = pageObj.mediaBox ?: PDRectangle(0f, 0f, 595f, 842f)
                    val pageHeight = mediaBox.height

                    val stripper = PDFTextStripperByArea()
                    stripper.sortByPosition = true

                    var wordText = ""
                    if (bounds != null) {
                        val left = bounds.left
                        val right = bounds.right
                        val top = pageHeight - bounds.top
                        val bottom = pageHeight - bounds.bottom
                        val yMin = Math.min(top, bottom)
                        val yHeight = Math.abs(bottom - top)
                        val xWidth = Math.abs(right - left)

                        val rect = android.graphics.RectF(
                            Math.max(0f, left - 4f),
                            Math.max(0f, yMin - 4f),
                            xWidth + 8f,
                            yHeight + 8f
                        )
                        stripper.addRegion("link_area", rect)
                        stripper.extractRegions(pageObj)
                        wordText = stripper.getTextForRegion("link_area")?.trim() ?: ""
                    }

                    if (wordText.isBlank()) {
                        val tx = event.originalX
                        val ty = pageHeight - event.originalY
                        val rectTap = android.graphics.RectF(
                            Math.max(0f, tx - 40f),
                            Math.max(0f, ty - 10f),
                            80f,
                            20f
                        )
                        val stripperTap = PDFTextStripperByArea()
                        stripperTap.sortByPosition = true
                        stripperTap.addRegion("tap_area", rectTap)
                        stripperTap.extractRegions(pageObj)
                        wordText = stripperTap.getTextForRegion("tap_area")?.trim() ?: ""
                    }

                    if (wordText.isNotBlank()) {
                        val lines = wordText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                        if (lines.isNotEmpty()) {
                            val merged = lines.joinToString(" ")
                            if (merged.length < 80) {
                                return merged
                            }
                            val words = merged.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
                            if (words.isNotEmpty()) {
                                return words.joinToString(" ")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CustomLinkHandler", "Error extracting text in link region", e)
        }
        return ""
    }

    companion object {
        fun isTapOnLink(pdfView: PDFView, x: Float, y: Float): Boolean {
            try {
                val pdfViewClass = pdfView.javaClass
                val mappedMethod = pdfViewClass.methods.firstOrNull { 
                    it.name.contains("mappedScreenToPage", ignoreCase = true) && it.parameterTypes.size == 2
                } ?: pdfViewClass.declaredMethods.firstOrNull {
                    it.name.contains("mappedScreenToPage", ignoreCase = true) && it.parameterTypes.size == 2
                }
                
                if (mappedMethod != null) {
                    mappedMethod.isAccessible = true
                    val point = mappedMethod.invoke(pdfView, x, y) as? android.graphics.PointF
                    if (point != null) {
                        val pdfFileField = pdfViewClass.declaredFields.firstOrNull { it.name == "pdfFile" }
                            ?: pdfViewClass.fields.firstOrNull { it.name == "pdfFile" }
                        if (pdfFileField != null) {
                            pdfFileField.isAccessible = true
                            val pdfFile = pdfFileField.get(pdfView)
                            if (pdfFile != null) {
                                val getLinkAtMethod = pdfFile.javaClass.methods.firstOrNull {
                                    it.name.contains("getLink", ignoreCase = true) && it.parameterTypes.size == 2
                                } ?: pdfFile.javaClass.declaredMethods.firstOrNull {
                                    it.name.contains("getLink", ignoreCase = true) && it.parameterTypes.size == 2
                                }
                                if (getLinkAtMethod != null) {
                                    getLinkAtMethod.isAccessible = true
                                    val link = getLinkAtMethod.invoke(pdfFile, point.x, point.y)
                                    return link != null
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CustomLinkHandler", "Error checking isTapOnLink in companion object via reflection", e)
            }
            return false
        }
    }
}
