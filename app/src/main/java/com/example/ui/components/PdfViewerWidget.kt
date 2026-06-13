package com.example.ui.components

import android.net.Uri
import android.util.Log
import android.graphics.RectF
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.PdfViewModel
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.CustomLinkHandler
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.padding

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
    onNavigateToWebView: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val pageSpacing by viewModel.pageSpacing.collectAsState()
    var isLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var activeLinkHighlight by remember { mutableStateOf<RectF?>(null) }
    var targetAlpha by remember { mutableStateOf(0f) }
    val highlightAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(400),
        label = "HighlightAlpha",
        finishedListener = { alpha ->
            if (alpha == 0f) {
                activeLinkHighlight = null
            }
        }
    )
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val onLinkTapped: (RectF) -> Unit = { rect ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        activeLinkHighlight = rect
        targetAlpha = 1f
        coroutineScope.launch {
            kotlinx.coroutines.delay(100)
            targetAlpha = 0f
        }
    }

    // Pre-calculate page count for the .pages(...) configuration
    val pageCount = remember(pdfUriString) {
        try {
            val fileUri = Uri.parse(pdfUriString)
            context.contentResolver.openFileDescriptor(fileUri, "r")?.use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            } ?: 0
        } catch (e: Exception) {
            Log.e("PdfViewerWidget", "Failed reading pageCount standard", e)
            0
        }
    }

    var localPdfViewRef by remember { mutableStateOf<PDFView?>(null) }
    var showFastScrollBadge by remember { mutableStateOf(false) }

    LaunchedEffect(showFastScrollBadge) {
        if (showFastScrollBadge) {
            kotlinx.coroutines.delay(1000)
            showFastScrollBadge = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        if (!isLoaded && loadError == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // CenterTop Badge for Fast Scroll
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

        key(readingMode, isSwipeHorizontal, pdfUriString, pageSpacing, readingScrollMode, fitMode) {
            AndroidView(
                factory = { ctx ->
                    PDFView(ctx, null).apply {
                        localPdfViewRef = this
                        onPdfViewCreated?.invoke(this)
                        
                        // Set min, mid, and max zoom limits as requested
                        setMinZoom(0.5f)
                        setMidZoom(1.5f)
                        setMaxZoom(4.0f)

                        // Apply sepia filter or normal filter dynamically
                        if (readingMode == "sepia") {
                            val paint = android.graphics.Paint().apply {
                                colorFilter = android.graphics.ColorMatrixColorFilter(
                                    android.graphics.ColorMatrix(
                                        floatArrayOf(
                                            0.393f, 0.769f, 0.189f, 0f, 0f,
                                            0.349f, 0.686f, 0.168f, 0f, 0f,
                                            0.272f, 0.534f, 0.131f, 0f, 0f,
                                            0f,      0f,      0f,      1f, 0f
                                        )
                                    )
                                )
                            }
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint)
                        } else {
                            setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                        }
                        
                        setOnTouchListener { _, event ->
                            val action = event.actionMasked
                            if (action == android.view.MotionEvent.ACTION_MOVE || 
                                action == android.view.MotionEvent.ACTION_UP || 
                                action == android.view.MotionEvent.ACTION_POINTER_UP) {
                                onZoomChanged?.invoke(zoom)
                            }
                            false
                        }

                        try {
                            val fileUri = Uri.parse(pdfUriString)
                            val configurator = fromUri(fileUri)

                            // 1. Pages configuration
                            if (pageCount > 0) {
                                val pagesList = IntArray(pageCount) { it }
                                configurator.pages(*pagesList)
                            }

                            val isPaged = (readingScrollMode == "paged")

                            // 2. Exact configured properties
                            configurator
                                .enableSwipe(true)
                                .swipeHorizontal(if (isPaged) true else false)
                                .pageSnap(if (isPaged) true else false)
                                .autoSpacing(if (isPaged) true else false)
                                .pageFling(if (isPaged) true else false)
                                .enableDoubletap(true)                    // native double tap zoom enabled
                                .defaultPage(currentPage)                 // start from current page
                                .onLoad { totalPages ->
                                    isLoaded = true
                                    viewModel.setTotalPages(totalPages)
                                    if (fitMode == "actual") {
                                        this@apply.zoomTo(1.0f)
                                    }
                                    try {
                                        val toc = tableOfContents
                                        if (toc != null) {
                                            viewModel.setTableOfContents(toc)
                                        } else {
                                            viewModel.setTableOfContents(emptyList())
                                        }
                                    } catch (e: Exception) {
                                        Log.e("PdfViewerWidget", "Could not get table of contents", e)
                                        viewModel.setTableOfContents(emptyList())
                                    }
                                    val rot = viewModel.pageRotations[currentPage] ?: 0
                                    val normRot = ((rot % 360) + 360) % 360
                                    this@apply.rotation = normRot.toFloat()
                                    onLoadComplete(totalPages)
                                }
                                .onPageChange { page, total ->
                                    viewModel.setCurrentPage(page)
                                    onPageChanged(page, total)
                                    val rot = viewModel.pageRotations[page] ?: 0
                                    val normRot = ((rot % 360) + 360) % 360
                                    this@apply.rotation = normRot.toFloat()
                                }
                                .onError { error ->
                                    loadError = error.message ?: "Unknown error"
                                    viewModel.setError(error.message)
                                    onError(error)
                                }
                                .onPageError { page, error ->
                                    Log.e("PdfViewerWidget", "Error on page $page", error)
                                }
                                .onTap { e ->
                                    onTap?.invoke()
                                    false
                                }
                                .onLongPress { e ->
                                    onLongPress?.invoke(androidx.compose.ui.geometry.Offset(e.x, e.y))
                                }
                                .enableAnnotationRendering(true)          // renders PDF annotations
                                .enableAntialiasing(true)                 // smooth rendering, no pixelation
                                .spacing(pageSpacing.toInt())                               // custom space between pages
                                .pageFitPolicy(if (fitMode == "height") FitPolicy.HEIGHT else FitPolicy.WIDTH)
                                .nightMode(readingMode == "night")
                                .scrollHandle(DefaultScrollHandle(ctx))
                                .linkHandler(CustomLinkHandler(context, this, onLinkTapped, onNavigateToWebView)) // Custom link handler
                                .load()

                        } catch (e: Exception) {
                            Log.e("PdfViewerWidget", "Exception parsing URI", e)
                            loadError = e.message ?: "URI error"
                            viewModel.setError(e.message)
                            onError(e)
                        }
                    }
                },
                update = { pdfView ->
                    // Apply layout updates dynamically safely
                    if (isLoaded) {
                        try {
                            viewModel.pageRotations.forEach { (pageIdx, rot) ->
                                val normRot = ((rot % 360) + 360) % 360
                                pdfView.setPageRotation(pageIdx, normRot)
                            }
                            if (pdfView.currentPage != currentPage) {
                                pdfView.jumpTo(currentPage)
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        val density = LocalDensity.current
        activeLinkHighlight?.let { rect ->
            val leftDp = with(density) { rect.left.toDp() }
            val topDp = with(density) { rect.top.toDp() }
            val widthDp = with(density) { (rect.right - rect.left).toDp() }
            val heightDp = with(density) { (rect.bottom - rect.top).toDp() }

            Box(
                modifier = Modifier
                    .offset(x = leftDp, y = topDp)
                    .size(width = widthDp, height = heightDp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f * highlightAlpha))
            )
        }
    }
}

fun com.github.barteksc.pdfviewer.PDFView.setPageRotation(page: Int, rotation: Int) {
    if (page == this.currentPage) {
        val normRot = ((rotation % 360) + 360) % 360
        this.rotation = normRot.toFloat()
    }
}
