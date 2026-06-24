package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.graphics.RectF
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.PdfViewModel
import com.github.barteksc.pdfviewer.PDFView
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.padding
import androidx.webkit.WebViewAssetLoader
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PdfViewerWidget(
    pdfUriString: String,
    currentPage: Int,
    readingMode: String,
    isSwipeHorizontal: Boolean,
    readingScrollMode: String,
    fitMode: String,
    onPageChanged: (Int, Int) -> Unit,
    onLoadComplete: (Int) -> Unit,
    onError: (Throwable) -> Unit,
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier,
    onPdfViewCreated: ((PDFView) -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    onLongPress: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onZoomChanged: ((Float) -> Unit)? = null,
    onNavigateToWebView: ((String) -> Unit)? = null,
    onGestureTriggered: ((com.example.data.GestureType, androidx.compose.ui.geometry.Offset?) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val pageSpacing by viewModel.pageSpacing.collectAsState()
    var isLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var resolvedFile by remember { mutableStateOf<File?>(null) }
    var fileResolved by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    val onLinkTapped: (RectF) -> Unit = { rect ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    var containerWidth by remember { mutableStateOf(0f) }
    var containerHeight by remember { mutableStateOf(0f) }

    var localWebViewRef by remember { mutableStateOf<WebView?>(null) }
    var showFastScrollBadge by remember { mutableStateOf(false) }

    var touchTime by remember { mutableStateOf(0L) }
    var isMultiTap by remember { mutableStateOf(false) }
    var startX by remember { mutableStateOf(0f) }
    var startY by remember { mutableStateOf(0f) }
    var startX2 by remember { mutableStateOf(0f) }
    var startY2 by remember { mutableStateOf(0f) }
    var initialTwoFingerPageY by remember { mutableStateOf(0f) }
    var lastTapTime by remember { mutableStateOf(0L) }

    LaunchedEffect(showFastScrollBadge) {
        if (showFastScrollBadge) {
            kotlinx.coroutines.delay(1000)
            showFastScrollBadge = false
        }
    }

    LaunchedEffect(pdfUriString) {
        val fileUri = Uri.parse(pdfUriString)
        val file: File? = when (fileUri.scheme) {
            "file" -> fileUri.path?.let { File(it) }
            "content" -> withContext(Dispatchers.IO) {
                try {
                    val cacheFile = File(context.cacheDir, "pdf_viewer_temp.pdf")
                    context.contentResolver.openInputStream(fileUri)?.use { input ->
                        cacheFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    cacheFile
                } catch (e: Exception) {
                    Log.e("PdfViewerWidget", "Failed to copy content URI to cache", e)
                    null
                }
            }
            else -> null
        }
        if (file == null || !file.exists()) {
            loadError = "تعذّر قراءة الملف"
        } else {
            resolvedFile = file
        }
        fileResolved = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                containerWidth = size.width.toFloat()
                containerHeight = size.height.toFloat()
            }
            .pointerInput(onGestureTriggered) {
                if (onGestureTriggered == null) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val changes = event.changes
                        
                        if (changes.size < 2 && !isMultiTap) {
                            continue
                        }
                        
                        when (event.type) {
                            androidx.compose.ui.input.pointer.PointerEventType.Press -> {
                                touchTime = System.currentTimeMillis()
                                if (changes.size >= 2) {
                                    isMultiTap = true
                                    startX = changes[0].position.x
                                    startY = changes[0].position.y
                                    startX2 = changes[1].position.x
                                    startY2 = changes[1].position.y
                                    initialTwoFingerPageY = (startY + startY2) / 2
                                } else if (changes.size == 1 && !isMultiTap) {
                                    startX = changes[0].position.x
                                    startY = changes[0].position.y
                                }
                            }
                            androidx.compose.ui.input.pointer.PointerEventType.Release -> {
                                val duration = System.currentTimeMillis() - touchTime
                                val endX = if (changes.isNotEmpty()) changes[0].position.x else startX
                                val endY = if (changes.isNotEmpty()) changes[0].position.y else startY
                                val dx = endX - startX
                                val dy = endY - startY
                                
                                if (isMultiTap) {
                                    val endX2 = if (changes.size >= 2) changes[1].position.x else startX2
                                    val endY2 = if (changes.size >= 2) changes[1].position.y else startY2
                                    val finalTwoFingerY = (endY + endY2) / 2
                                    val deltaYTwo = finalTwoFingerY - initialTwoFingerPageY
                                    
                                    if (deltaYTwo < -150f) {
                                        onGestureTriggered(com.example.data.GestureType.TWO_FINGER_SWIPE_UP, null)
                                    } else if (deltaYTwo > 150f) {
                                        onGestureTriggered(com.example.data.GestureType.TWO_FINGER_SWIPE_DOWN, null)
                                    } else if (duration < 400) {
                                        onGestureTriggered(com.example.data.GestureType.TWO_FINGER_TAP, null)
                                    }
                                    isMultiTap = false
                                } else {
                                    if (kotlin.math.abs(dx) < 20f && kotlin.math.abs(dy) < 20f) {
                                        if (duration > 500) {
                                            onGestureTriggered(com.example.data.GestureType.LONG_PRESS, androidx.compose.ui.geometry.Offset(endX, endY))
                                        } else {
                                            val now = System.currentTimeMillis()
                                            if (now - lastTapTime < 300) {
                                                onGestureTriggered(com.example.data.GestureType.DOUBLE_TAP, null)
                                                lastTapTime = 0L
                                            } else {
                                                lastTapTime = now
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(260)
                                                    if (lastTapTime == now) {
                                                        onGestureTriggered(com.example.data.GestureType.SINGLE_TAP, null)
                                                        lastTapTime = 0L
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        if (!fileResolved || (!isLoaded && loadError == null)) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedVisibility(
            visible = showFastScrollBadge,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                shadowElevation = 6.dp
            ) {
                Text(
                    text = "تمرير سريع ⚡",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        if (loadError != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Error reading PDF: $loadError")
            }
        }

        if (fileResolved && resolvedFile != null) {
            key(readingMode, isSwipeHorizontal, pdfUriString, pageSpacing, readingScrollMode, fitMode) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            localWebViewRef = this
                            
                            // no-op: PDFView not used with WebView implementation

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                setSupportZoom(true)
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                allowFileAccess = true
                            }

                            // Bridge for Audio Links and Page Changes
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onAudioLinkClick(url: String) {
                                    if (url.endsWith(".mp3") || url.endsWith(".wav") || url.endsWith(".ogg") || url.endsWith(".m4a")) {
                                        onLinkTapped(RectF())
                                        com.example.util.AudioPlayerManager.playAudio(url)
                                    } else {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        ctx.startActivity(intent)
                                    }
                                }

                                @JavascriptInterface
                                fun onPageRendered(page: Int, total: Int) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        if (!isLoaded) {
                                            isLoaded = true
                                            viewModel.setTotalPages(total)
                                            onLoadComplete(total)
                                        }
                                        viewModel.setCurrentPage(page - 1)
                                        onPageChanged(page - 1, total)
                                    }
                                }
                            }, "AndroidBridge")

                            val file = resolvedFile ?: return@apply

                            val assetLoader = WebViewAssetLoader.Builder()
                                .addPathHandler(
                                    "/local_pdf/",
                                    WebViewAssetLoader.InternalStoragePathHandler(ctx, file.parentFile ?: ctx.cacheDir)
                                )
                                .addPathHandler(
                                    "/pdfjs/",
                                    WebViewAssetLoader.AssetsPathHandler(ctx)
                                )
                                .build()
                            val safeFileName = file.name

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    return assetLoader.shouldInterceptRequest(request.url)
                                }
                            }

                            webChromeClient = WebChromeClient()

                            // Injecting PDF.js via HTML string
                            val htmlContent = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=4.0, user-scalable=yes">
                                    <script src="https://appassets.androidplatform.net/pdfjs/pdf.min.js"></script>
                                    <style>
                                        body { margin: 0; padding: 0; background: ${if(readingMode == "night") "#121212" else if (readingMode == "sepia") "#f4ecd8" else "#ffffff"}; display: flex; flex-direction: column; align-items: center; }
                                        .pdf-page-container { position: relative; margin-bottom: ${pageSpacing}px; }
                                        canvas { display: block; box-shadow: 0 2px 8px rgba(0,0,0,0.2); }
                                        .textLayer { position: absolute; left: 0; top: 0; right: 0; bottom: 0; overflow: hidden; opacity: 0.2; line-height: 1.0; }
                                        .textLayer ::selection { background: rgba(0, 123, 255, 0.3); }
                                        .annotationLayer { position: absolute; left: 0; top: 0; right: 0; bottom: 0; pointer-events: none; }
                                        .annotationLayer section { position: absolute; cursor: pointer; pointer-events: auto; }
                                    </style>
                                </head>
                                <body>
                                    <div id="pdf-viewer"></div>
                                    <script>
                                        pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://appassets.androidplatform.net/pdfjs/pdf.worker.min.js';
                                        const url = 'https://appassets.androidplatform.net/local_pdf/$safeFileName';
                                        const viewer = document.getElementById('pdf-viewer');

                                        pdfjsLib.getDocument(url).promise.then(pdf => {
                                            const totalPages = pdf.numPages;
                                            for (let pageNum = 1; pageNum <= totalPages; pageNum++) {
                                                pdf.getPage(pageNum).then(page => {
                                                    const screenWidth = window.innerWidth;
                                                    const defaultViewport = page.getViewport({ scale: 1.0 });
                                                    const scale = screenWidth / defaultViewport.width;
                                                    const viewport = page.getViewport({ scale: scale });
                                                    
                                                    const container = document.createElement('div');
                                                    container.className = 'pdf-page-container';
                                                    container.style.width = viewport.width + 'px';
                                                    container.style.height = viewport.height + 'px';
                                                    
                                                    const canvas = document.createElement('canvas');
                                                    const context = canvas.getContext('2d');
                                                    canvas.height = viewport.height;
                                                    canvas.width = viewport.width;
                                                    container.appendChild(canvas);
                                                    viewer.appendChild(container);

                                                    page.render({ canvasContext: context, viewport: viewport }).promise.then(() => {
                                                        // Text Layer for Perfect Selection
                                                        const textLayerDiv = document.createElement('div');
                                                        textLayerDiv.className = 'textLayer';
                                                        container.appendChild(textLayerDiv);
                                                        page.getTextContent().then(textContent => {
                                                            pdfjsLib.renderTextLayer({
                                                                textContent: textContent,
                                                                container: textLayerDiv,
                                                                viewport: viewport,
                                                                textDivs: []
                                                            });
                                                        });

                                                        // Annotation Layer for Audio Links
                                                        page.getAnnotations().then(annotations => {
                                                            const annotationLayerDiv = document.createElement('div');
                                                            annotationLayerDiv.className = 'annotationLayer';
                                                            container.appendChild(annotationLayerDiv);
                                                            pdfjsLib.AnnotationLayer.render({
                                                                viewport: viewport.clone({ dontFlip: true }),
                                                                div: annotationLayerDiv,
                                                                annotations: annotations,
                                                                page: page,
                                                                linkService: {
                                                                    getDestinationHash: () => '',
                                                                    getAnchorUrl: () => '',
                                                                    executeNamedAction: () => {}
                                                                },
                                                                downloadManager: null
                                                            });

                                                            // Intercept clicks
                                                            annotationLayerDiv.addEventListener('click', (e) => {
                                                                const target = e.target.closest('a');
                                                                if (target && target.href) {
                                                                    e.preventDefault();
                                                                    window.AndroidBridge.onAudioLinkClick(target.href);
                                                                }
                                                            });
                                                        });
                                                        
                                                        window.AndroidBridge.onPageRendered(pageNum, totalPages);
                                                    });
                                                });
                                            }
                                        }).catch(err => {
                                            console.error(err);
                                        });
                                    </script>
                                </body>
                                </html>
                            """.trimIndent()

                            loadDataWithBaseURL("https://appassets.androidplatform.net", htmlContent, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        if (isLoaded) {
                            // Night / Sepia mode updates via JavaScript evaluation dynamically
                            val filterCss = when (readingMode) {
                                "night" -> "invert(1) hue-rotate(180deg) brightness(0.85)"
                                "sepia" -> "sepia(0.6) brightness(0.9)"
                                else -> "none"
                            }
                            webView.evaluateJavascript("document.body.style.filter = '$filterCss';", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        val density = LocalDensity.current
        val globalHighlightRect by com.example.util.AudioPlayerManager.highlightedRect.collectAsState()
        globalHighlightRect?.let { rect ->
            val leftDp = with(density) { rect.left.toDp() }
            val topDp = with(density) { rect.top.toDp() }
            val widthDp = with(density) { (rect.right - rect.left).toDp() }
            val heightDp = with(density) { (rect.bottom - rect.top).toDp() }

            Box(
                modifier = Modifier
                    .offset(x = leftDp, y = topDp)
                    .size(width = widthDp, height = heightDp)
                    .background(Color(0xFF2196F3).copy(alpha = 0.35f))
            )
        }
    }
}
