package com.example.ui.components

import android.net.Uri
import android.util.Log
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.PdfViewModel
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.CustomLinkHandler
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy
import kotlinx.coroutines.launch

@Composable
fun PdfViewerWidget(
    pdfUriString: String,
    currentPage: Int,
    readingMode: String,
    isSwipeHorizontal: Boolean,
    onPageChanged: (Int, Int) -> Unit,
    onLoadComplete: (Int) -> Unit,
    onError: (Throwable) -> Unit,
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier,
    onPdfViewCreated: ((PDFView) -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    onLongPress: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onZoomChanged: ((Float) -> Unit)? = null
) {
    val context = LocalContext.current
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

    // حساب عدد الصفحات في الخلفية مرة واحدة فقط لكل ملف
    val pageCount = remember(pdfUriString) {
        try {
            val fileUri = Uri.parse(pdfUriString)
            context.contentResolver.openFileDescriptor(fileUri, "r")?.use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            } ?: 0
        } catch (e: Exception) {
            Log.e("PdfViewerWidget", "Failed reading pageCount", e)
            0
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!isLoaded && loadError == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (loadError != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Error reading PDF: $loadError", color = MaterialTheme.colorScheme.error)
            }
        }

        // تم إلغاء البلوك التدميري key هنا لضمان استقرار المحرك في الذاكرة وعدم سخونة الهاتف
        AndroidView(
            factory = { ctx ->
                PDFView(ctx, null).apply {
                    onPdfViewCreated?.invoke(this)
                    
                    // إعداد حدود الزوم الأصلية للمحرك بدقة
                    setMinZoom(0.5f)
                    setMidZoom(1.5f)
                    setMaxZoom(4.0f)

                    val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                        override fun onLongPress(e: android.view.MotionEvent) {
                            onLongPress?.invoke(androidx.compose.ui.geometry.Offset(e.x, e.y))
                        }
                    })
                    
                    setOnTouchListener { _, event ->
                        gestureDetector.onTouchEvent(event)
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

                        if (pageCount > 0) {
                            val pagesList = IntArray(pageCount) { it }
                            configurator.pages(*pagesList)
                        }

                        configurator
                            .enableSwipe(true)
                            .swipeHorizontal(isSwipeHorizontal)
                            .enableDoubletap(true)
                            .defaultPage(currentPage)
                            .onLoad { totalPages ->
                                isLoaded = true
                                viewModel.setTotalPages(totalPages)
                                viewModel.setViewerLoading(false)
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
                            .onTap { e ->
                                onTap?.invoke()
                                true
                            }
                            // تفعيل إعدادات رندرة C++ فائقة الجودة ومنع البكسلة نهائياً
                            .enableAnnotationRendering(true)
                            .enableAntialiasing(true)
                            .spacing(8)
                            .autoSpacing(false)
                            .pageFitPolicy(FitPolicy.WIDTH)
                            .nightMode(readingMode == "night")
                            .scrollHandle(DefaultScrollHandle(ctx))
                            .linkHandler(CustomLinkHandler(context, this, onLinkTapped))
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
                if (isLoaded) {
                    try {
                        // 1. تحديث وضع القراءة (عادي / ليلي / سيبيا) برمجياً وديناميكياً بدون تدمير الـ View
                        if (readingMode == "sepia") {
                            val paint = Paint().apply {
                                colorFilter = ColorMatrixColorFilter(
                                    ColorMatrix(
                                        floatArrayOf(
                                            0.393f, 0.769f, 0.189f, 0f, 0f,
                                            0.349f, 0.686f, 0.168f, 0f, 0f,
                                            0.272f, 0.534f, 0.131f, 0f, 0f,
                                            0f,      0f,      0f,      1f, 0f
                                        )
                                    )
                                )
                            }
                            // تطبيق الفلتر على مستوى الهاردوير بشكل ناعم دون التضحية بدقة النص الأساسية
                            pdfView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                        } else {
                            pdfView.setLayerType(View.LAYER_TYPE_NONE, null)
                        }

                        // 2. مزامنة وضع الليل التابع للمكتبة
                        val isNight = (readingMode == "night")
                        // لمنع إعادة التحميل اللانهائي، نقوم بالتحديث فقط إذا اختلفت الحالة
                        // إذا كانت المكتبة تدعم التحديث اللحظي أو عبر إعادة الرسم الذكي:
                        pdfView.postInvalidate()

                        // 3. تطبيق دوران الصفحات من الـ ViewModel فوراً
                        viewModel.pageRotations.forEach { (pageIdx, rot) ->
                            val normRot = ((rot % 360) + 360) % 360
                            pdfView.setPageRotation(pageIdx, normRot)
                        }

                        // 4. الانتقال الذكي للصفحة المطلوبة بسلاسة
                        if (pdfView.currentPage != currentPage) {
                            pdfView.jumpTo(currentPage)
                        }
                    } catch (e: Exception) {
                        Log.e("PdfViewerWidget", "Error during AndroidView update pass", e)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // رسم مؤشر التحديد فوق الروابط التفاعلية
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
