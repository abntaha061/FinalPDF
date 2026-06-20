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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.PdfViewModel
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.CustomLinkHandler
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
    onNavigateToWebView: ((String) -> Unit)? = null,
    onGestureTriggered: ((com.example.data.GestureType, androidx.compose.ui.geometry.Offset?) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val pageSpacing by viewModel.pageSpacing.collectAsState()
    val orientation = LocalConfiguration.current.orientation
    val isLandscape = (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)

    var isLoaded by remember(pdfUriString, orientation) { mutableStateOf(false) }
    var loadError by remember(pdfUriString, orientation) { mutableStateOf<String?>(null) }

    val haptic = LocalHapticFeedback.current

    val onLinkTapped: (RectF) -> Unit = { rect ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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

    var containerWidth by remember { mutableStateOf(0f) }
    var containerHeight by remember { mutableStateOf(0f) }

    val pageSizes = remember(pdfUriString) {
        val sizes = mutableMapOf<Int, Pair<Float, Float>>()
        try {
            val fileUri = Uri.parse(pdfUriString)
            context.contentResolver.openFileDescriptor(fileUri, "r")?.use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            sizes[i] = Pair(page.width.toFloat(), page.height.toFloat())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PdfViewerWidget", "Failed reading page sizes via PdfRenderer", e)
        }
        sizes
    }

    val getFitScaleForPage = { pageW: Float, pageH: Float ->
        if (containerWidth <= 0f || containerHeight <= 0f) {
            1.0f
        } else {
            val containerRatio = containerWidth / containerHeight
            val pageRatio = pageW / pageH
            if (isLandscape) {
                // Landscape orientation: Fit Width mode is the requirement. Since pageFitPolicy is strictly set to FitPolicy.WIDTH in landscape option, returning 1.0f guarantees the page scales to fit the width perfectly edge-to-edge.
                1.0f
            } else {
                // Portrait orientation: Contain fit logic
                if (fitMode == "height") {
                    if (pageRatio > containerRatio) containerRatio / pageRatio else 1.0f
                } else { // default or "width"
                    if (pageRatio < containerRatio) pageRatio / containerRatio else 1.0f
                }
            }
        }
    }

    val updateMinZoomForPage: (PDFView, Int) -> Unit = { pdfView, pageIndex ->
        if (containerWidth > 0f && containerHeight > 0f) {
            val size = pageSizes[pageIndex]
            if (size != null && size.first > 0f && size.second > 0f) {
                val fitScale = getFitScaleForPage(size.first, size.second)
                pdfView.setMinZoom(fitScale)
                if (pdfView.zoom < fitScale) {
                    pdfView.zoomTo(fitScale)
                    onZoomChanged?.invoke(fitScale)
                }
            }
        }
    }

    var localPdfViewRef by remember { mutableStateOf<PDFView?>(null) }
    var showFastScrollBadge by remember { mutableStateOf(false) }

    var scrollPositionOffset by remember(pdfUriString) { mutableStateOf(0.0f) }
    var scrollCurrentPage by remember(pdfUriString) { mutableStateOf(currentPage) }
    var scrollEventId by remember(pdfUriString) { mutableStateOf(0L) }
    var isScrollingActive by remember(pdfUriString) { mutableStateOf(false) }

    LaunchedEffect(scrollEventId) {
        if (isScrollingActive) {
            kotlinx.coroutines.delay(350L) // Fast auto-hide stop delay (target ~350ms)
            isScrollingActive = false
        }
    }

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
                        
                        // If it's a single finger event, do NOT intercept it in the Initial pass!
                        // This ensures that native single-finger scrolling and double-tap zoom 
                        // in PDFView work with 100% native responsiveness and smoothness.
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
                                    // Multi-touch gestures
                                    val endX2 = if (changes.size >= 2) changes[1].position.x else startX2
                                    val endY2 = if (changes.size >= 2) changes[1].position.y else startY2
                                    val finalTwoFingerY = (endY + endY2) / 2
                                    val deltaYTwo = finalTwoFingerY - initialTwoFingerPageY
                                    
                                    if (deltaYTwo < -150f) {
                                        // TWO_FINGER_SWIPE_UP
                                        onGestureTriggered(com.example.data.GestureType.TWO_FINGER_SWIPE_UP, null)
                                    } else if (deltaYTwo > 150f) {
                                        // TWO_FINGER_SWIPE_DOWN
                                        onGestureTriggered(com.example.data.GestureType.TWO_FINGER_SWIPE_DOWN, null)
                                    } else if (duration < 400) {
                                        // TWO_FINGER_TAP
                                        onGestureTriggered(com.example.data.GestureType.TWO_FINGER_TAP, null)
                                    }
                                    isMultiTap = false
                                } else {
                                    // No custom single-finger swipe actions here to avoid breaking native PDF scroll physics.
                                    // Standard single tap, long press, and zoom are natively handled by PDFView configurator callbacks.
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

        key(readingMode, isSwipeHorizontal, pdfUriString, pageSpacing, readingScrollMode, fitMode, orientation) {
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
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        }
                        
                        // Removed custom touch listener to prevent breaking the native pinch-to-zoom/scroll drag and drop interactions of the PDFView.


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
                                .autoSpacing(false)                       // Disable autoSpacing so that custom pageSpacing setting is precisely and cleanly respected (like WPS Office)
                                .pageFling(isPaged)                       // Enable pageFling only in paged mode for smooth momentum scrolling
                                .enableDoubletap(true)                    // native double tap zoom enabled
                                .defaultPage(currentPage)                 // start from current page
                                .onLoad { totalPages ->
                                    isLoaded = true
                                    viewModel.setTotalPages(totalPages)
                                    updateMinZoomForPage(this@apply, currentPage)
                                    if (fitMode == "actual") {
                                        this@apply.zoomTo(1.0f)
                                    } else {
                                        // Set initial zoom to the dynamic contain-fit scale
                                        val size = pageSizes[currentPage]
                                        if (size != null && size.first > 0f && size.second > 0f) {
                                            val fitScale = getFitScaleForPage(size.first, size.second)
                                            this@apply.zoomTo(fitScale)
                                            onZoomChanged?.invoke(fitScale)
                                        }
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
                                    updateMinZoomForPage(this@apply, page)
                                    scrollCurrentPage = page
                                }
                                .onPageScroll { page, positionOffset ->
                                    onZoomChanged?.invoke(this@apply.zoom)
                                    scrollCurrentPage = page
                                    scrollPositionOffset = positionOffset
                                    scrollEventId++
                                    isScrollingActive = true
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
                                    val isLink = com.github.barteksc.pdfviewer.CustomLinkHandler.isTapOnLink(this@apply, e.x, e.y)
                                     if (isLink) return@onTap true
                                    if (!isLink) {
                                        onTap?.invoke()
                                    }
                                    true
                                }
                                .onLongPress { e ->
                                    onLongPress?.invoke(androidx.compose.ui.geometry.Offset(e.x, e.y))
                                }
                                .enableAnnotationRendering(true)          // renders PDF annotations
                                .enableAntialiasing(true)                  // Sharp and smooth text rendering
                                
                                .spacing(pageSpacing.toInt())                               // custom space between pages
                                .pageFitPolicy(if (isLandscape) FitPolicy.WIDTH else (if (fitMode == "height") FitPolicy.HEIGHT else FitPolicy.WIDTH))
                                .nightMode(readingMode == "night")
                                .linkHandler(CustomLinkHandler(context, this, pdfUriString, onLinkTapped, onNavigateToWebView)) // Custom link handler
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
                            updateMinZoomForPage(pdfView, currentPage)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
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
                    .background(Color(0xFF2196F3).copy(alpha = 0.35f)) // Translucent light-blue visual shade highlight
            )
        }

        // Real-time dynamic vertical scroll-position page indicator badge
        val showBadge = isScrollingActive && pageCount > 0
        AnimatedVisibility(
            visible = showBadge,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            val coercedOffset = scrollPositionOffset.coerceIn(0f, 1f)
            val globalScrollFraction = if (pageCount > 1) {
                ((scrollCurrentPage + coercedOffset) / pageCount.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val badgeHeight = 36.dp
            
            val verticalOffset = remember(globalScrollFraction, containerHeight) {
                val containerHeightPx = containerHeight
                val badgeHeightPx = with(density) { badgeHeight.toPx() }
                // Constrain the offset so the badge stays elegantly within the screen boundaries with a 16dp defensive margin at top and bottom
                val marginPx = with(density) { 16.dp.toPx() }
                val maxOffsetPx = (containerHeightPx - badgeHeightPx - (marginPx * 2f)).coerceAtLeast(0f)
                val offsetPx = (globalScrollFraction * maxOffsetPx) + marginPx
                with(density) { offsetPx.toDp() }
            }

            Surface(
                modifier = Modifier
                    .offset(y = verticalOffset)
                    .padding(end = 12.dp)
                    .testTag("scroll_page_indicator"),
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = "${scrollCurrentPage + 1} / $pageCount",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

fun com.github.barteksc.pdfviewer.PDFView.setPageRotation(page: Int, rotation: Int) {
    if (page == this.currentPage) {
        val normRot = ((rotation % 360) + 360) % 360
        this.rotation = normRot.toFloat()
    }
}
