package com.example.ui.components

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.PdfViewModel
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy

@Composable
fun PdfViewerWidget(
    pdfUriString: String,
    currentPage: Int,
    isNightMode: Boolean,
    isSwipeHorizontal: Boolean,
    onPageChanged: (Int, Int) -> Unit,
    onLoadComplete: (Int) -> Unit,
    onError: (Throwable) -> Unit,
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier,
    onPdfViewCreated: ((PDFView) -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    onLongPress: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null
) {
    val context = LocalContext.current
    var isLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

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

    Box(modifier = modifier.fillMaxSize()) {
        if (!isLoaded && loadError == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (loadError != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Error reading PDF: $loadError")
            }
        }

        AndroidView(
            factory = { ctx ->
                PDFView(ctx, null).apply {
                    onPdfViewCreated?.invoke(this)
                    
                    val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                            onTap?.invoke()
                            return true
                        }
                        
                        override fun onLongPress(e: android.view.MotionEvent) {
                            onLongPress?.invoke(androidx.compose.ui.geometry.Offset(e.x, e.y))
                        }
                    })
                    
                    setOnTouchListener { _, event ->
                        gestureDetector.onTouchEvent(event)
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

                        // 2. Exact configured properties
                        configurator
                            .enableSwipe(true)                        // vertical scroll enabled
                            .swipeHorizontal(isSwipeHorizontal)       // scroll direction = configured (default vertical)
                            .enableDoubletap(true)                    // double tap zooms in/out
                            .defaultPage(currentPage)                 // start from current page
                            .onLoad { totalPages ->
                                isLoaded = true
                                viewModel.setTotalPages(totalPages)
                                onLoadComplete(totalPages)
                            }
                            .onPageChange { page, total ->
                                viewModel.setCurrentPage(page)
                                onPageChanged(page, total)
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
                                true
                            }
                            .enableAnnotationRendering(true)          // renders PDF annotations
                            .enableAntialiasing(true)                 // smooth rendering, no pixelation
                            .spacing(8)                               // 8dp space between pages
                            .autoSpacing(false)
                            .pageFitPolicy(FitPolicy.WIDTH)           // fit width of screen
                            .nightMode(isNightMode)
                            .scrollHandle(DefaultScrollHandle(ctx))
                            .linkHandler(CustomLinkHandler(context, this)) // Custom link handler
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
}
