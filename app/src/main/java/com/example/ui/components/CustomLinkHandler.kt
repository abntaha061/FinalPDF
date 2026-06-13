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

class CustomLinkHandler(
    private val context: Context,
    private val pdfView: PDFView,
    private val onLinkTapped: (RectF) -> Unit,
    private val onNavigateToWebView: ((String) -> Unit)? = null
) : LinkHandler {

    private var tts: android.speech.tts.TextToSpeech? = null

    init {
        try {
            tts = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    tts?.language = java.util.Locale.GERMAN
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

            // If the link contains audio file URL (mp3, wav, ogg, m4a, aac)
            if (lowerUri.endsWith(".mp3") || lowerUri.endsWith(".wav") || lowerUri.endsWith(".ogg") ||
                lowerUri.endsWith(".m4a") || lowerUri.endsWith(".aac")) {
                Log.d("CustomLinkHandler", "Tapped audio URL, sending to AudioPlayerManager: $uriString with autoPlay: $autoPlayAudio, vol: $audioVolume")
                if (autoPlayAudio) {
                    AudioPlayerManager.play(context, uriString, audioVolume)
                } else {
                    android.app.AlertDialog.Builder(context)
                        .setTitle("تشغيل الصوت")
                        .setMessage("هل تريد تشغيل هذا الملف الصوتي؟")
                        .setPositiveButton("تشغيل") { _, _ ->
                            AudioPlayerManager.play(context, uriString, audioVolume)
                        }
                        .setNegativeButton("إلغاء", null)
                        .show()
                }
            } else if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                Log.d("CustomLinkHandler", "Tapped http/https URI: $uriString with mode: $linkOpenMode")
                
                // Automatically speech-pronounce the word from the dictionary URL
                try {
                    val parsedUri = Uri.parse(uriString)
                    var lastSeg = parsedUri.lastPathSegment
                    if (uriString.contains("dict.cc", ignoreCase = true) || uriString.contains("dict.leo.org", ignoreCase = true)) {
                        val sParam = parsedUri.getQueryParameter("s") ?: parsedUri.getQueryParameter("search")
                        if (!sParam.isNullOrBlank()) {
                            lastSeg = sParam
                        }
                    }
                    if (!lastSeg.isNullOrEmpty()) {
                        val decodedWord = Uri.decode(lastSeg).trim()
                        if (decodedWord.isNotEmpty()) {
                            tts?.speak(decodedWord, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "LINK_TTS_ID")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CustomLinkHandler", "Error pronouncing word in web link", e)
                }

                when (linkOpenMode) {
                    "داخل التطبيق (WebView)" -> {
                        if (onNavigateToWebView != null) {
                            onNavigateToWebView.invoke(uriString)
                        } else {
                            openWebViewInDialog(context, uriString)
                        }
                    }
                    "اسأل في كل مرة" -> {
                        android.app.AlertDialog.Builder(context)
                            .setTitle("فتح الرابط")
                            .setMessage("اختر طريقة فتح الرابط:\n$uriString")
                            .setPositiveButton("داخل التطبيق") { _, _ ->
                                if (onNavigateToWebView != null) {
                                    onNavigateToWebView.invoke(uriString)
                                } else {
                                    openWebViewInDialog(context, uriString)
                                }
                            }
                            .setNegativeButton("المتصفح الخارجي") { _, _ ->
                                launchExternalBrowser(context, uriString)
                            }
                            .setNeutralButton("إلغاء", null)
                            .show()
                    }
                    else -> { // "المتصفح الافتراضي"
                        launchExternalBrowser(context, uriString)
                    }
                }
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
}
