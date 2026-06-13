package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AnnotationTool {
    None,
    Pen,
    Highlighter,
    TextNote,
    StickyNote,
    Eraser
}

sealed class AnnotationData {
    abstract val page: Int
    data class DrawPath(val points: List<Offset>, val color: Color, val stroke: Float, override val page: Int) : AnnotationData()
    data class Highlight(val rect: Rect, val color: Color, override val page: Int) : AnnotationData()
    data class TextNote(val text: String, val position: Offset, override val page: Int) : AnnotationData()
    data class StickyNote(val text: String, val position: Offset, override val page: Int) : AnnotationData()
}

class AnnotationViewModel : ViewModel() {
    private val _currentTool = MutableStateFlow(AnnotationTool.None)
    val currentTool = _currentTool.asStateFlow()

    private val _currentColor = MutableStateFlow(Color(0xFFFF3F3F)) // default Red
    val currentColor = _currentColor.asStateFlow()

    private val _strokeWidth = MutableStateFlow(4f) // default 4dp (Fine is 2dp, Normal is 4dp, Thick is 7dp, Extra is 12dp)
    val strokeWidth = _strokeWidth.asStateFlow()

    private val _annotations = MutableStateFlow<List<AnnotationData>>(emptyList())
    val annotations = _annotations.asStateFlow()

    // Canvas size tracker for coordinate scaling during saving
    var canvasWidth = 1f
    var canvasHeight = 1f

    fun setTool(tool: AnnotationTool) {
        _currentTool.value = tool
    }

    fun setColor(color: Color) {
        _currentColor.value = color
    }

    fun setStrokeWidth(width: Float) {
        _strokeWidth.value = width
    }

    fun addAnnotation(annotation: AnnotationData) {
        _annotations.value = _annotations.value + annotation
    }

    fun removeAnnotation(annotation: AnnotationData) {
        _annotations.value = _annotations.value - annotation
    }

    fun clearAnnotations() {
        _annotations.value = emptyList()
    }

    fun eraseAt(position: Offset, page: Int) {
        // Remove any DrawPath whose points come within 20dp of position
        // Remove Highlight rects that intersect the 20dp eraser position
        val threshold = 20f
        val currentList = _annotations.value
        val newList = currentList.filterNot { annotation ->
            if (annotation.page != page) return@filterNot false
            when (annotation) {
                is AnnotationData.DrawPath -> {
                    annotation.points.any { pt ->
                        (pt - position).getDistance() <= threshold
                    }
                }
                is AnnotationData.Highlight -> {
                    val rect = annotation.rect
                    // Expand rect by threshold to check intersection with position circle
                    rect.left - threshold <= position.x &&
                            rect.right + threshold >= position.x &&
                            rect.top - threshold <= position.y &&
                            rect.bottom + threshold >= position.y
                }
                is AnnotationData.TextNote -> {
                    (annotation.position - position).getDistance() <= threshold
                }
                is AnnotationData.StickyNote -> {
                    (annotation.position - position).getDistance() <= threshold
                }
            }
        }
        if (newList.size != currentList.size) {
            _annotations.value = newList
        }
    }

    suspend fun saveAnnotationsToPdf(
        context: Context,
        fileUri: Uri,
        totalPages: Int
    ): File? = withContext(Dispatchers.IO) {
        try {
            // Initialize PDFBox Loader first
            PDFBoxResourceLoader.init(context.applicationContext)

            val inputStream = context.contentResolver.openInputStream(fileUri) ?: return@withContext null
            val document = PDDocument.load(inputStream)

            val grouped = _annotations.value.groupBy { it.page }

            grouped.forEach { (pageIndex, pageAnnotations) ->
                if (pageIndex in 0 until totalPages) {
                    val page = document.getPage(pageIndex)
                    val mediaBox = page.mediaBox
                    val pageWidth = mediaBox.width
                    val pageHeight = mediaBox.height

                    // Create content stream for overlaying paths & highlights
                    // Standard appending mode append style
                    val contentStream = PDPageContentStream(
                        document,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    )

                    pageAnnotations.forEach { annotation ->
                        when (annotation) {
                            is AnnotationData.DrawPath -> {
                                val r = annotation.color.red
                                val g = annotation.color.green
                                val b = annotation.color.blue
                                contentStream.setStrokingColor(r, g, b)
                                contentStream.setLineWidth(annotation.stroke)

                                val points = annotation.points
                                if (points.isNotEmpty()) {
                                    val firstPt = points.first()
                                    val fx = firstPt.x / canvasWidth * pageWidth
                                    val fy = (canvasHeight - firstPt.y) / canvasHeight * pageHeight
                                    contentStream.moveTo(fx, fy)
                                    for (i in 1 until points.size) {
                                        val pt = points[i]
                                        val x = pt.x / canvasWidth * pageWidth
                                        val y = (canvasHeight - pt.y) / canvasHeight * pageHeight
                                        contentStream.lineTo(x, y)
                                    }
                                    contentStream.stroke()
                                }
                            }
                            is AnnotationData.Highlight -> {
                                val r = annotation.color.red
                                val g = annotation.color.green
                                val b = annotation.color.blue
                                contentStream.setNonStrokingColor(r, g, b)

                                // Set semi-transparent graphics state
                                val graphicsState = PDExtendedGraphicsState().apply {
                                    nonStrokingAlphaConstant = 0.35f
                                    strokingAlphaConstant = 0.35f
                                }
                                contentStream.setGraphicsStateParameters(graphicsState)

                                val rect = annotation.rect
                                val pdfLeft = rect.left / canvasWidth * pageWidth
                                val pdfRight = rect.right / canvasWidth * pageWidth
                                val pdfBottom = (canvasHeight - rect.bottom) / canvasHeight * pageHeight
                                val pdfTop = (canvasHeight - rect.top) / canvasHeight * pageHeight

                                val pdfWidth = pdfRight - pdfLeft
                                val pdfHeight = pdfTop - pdfBottom

                                contentStream.addRect(pdfLeft, pdfBottom, pdfWidth, pdfHeight)
                                contentStream.fill()
                            }
                            else -> {
                                // Handled as PDAnnotation below
                            }
                        }
                    }
                    contentStream.close()

                    // Add Interactive Text & Sticky Notes as Native PDF Annotations
                    pageAnnotations.forEach { annotation ->
                        val pdfLeft = when (annotation) {
                            is AnnotationData.TextNote -> annotation.position.x / canvasWidth * pageWidth
                            is AnnotationData.StickyNote -> annotation.position.x / canvasWidth * pageWidth
                            else -> 0f
                        }
                        val pdfTop = when (annotation) {
                            is AnnotationData.TextNote -> (canvasHeight - annotation.position.y) / canvasHeight * pageHeight
                            is AnnotationData.StickyNote -> (canvasHeight - annotation.position.y) / canvasHeight * pageHeight
                            else -> 0f
                        }

                        when (annotation) {
                            is AnnotationData.StickyNote -> {
                                val pdfAnnotation = PDAnnotationText().apply {
                                    val rect = PDRectangle().apply {
                                        lowerLeftX = pdfLeft
                                        lowerLeftY = pdfTop - 24f
                                        upperRightX = pdfLeft + 24f
                                        upperRightY = pdfTop
                                    }
                                    rectangle = rect
                                    contents = annotation.text
                                    setOpen(false)
                                }
                                page.annotations.add(pdfAnnotation)
                            }
                            is AnnotationData.TextNote -> {
                                // Add as FreeText or standard annotation with contents
                                val pdfAnnotation = PDAnnotationText().apply {
                                    val rect = PDRectangle().apply {
                                        lowerLeftX = pdfLeft
                                        lowerLeftY = pdfTop - 32f
                                        upperRightX = pdfLeft + 80f
                                        upperRightY = pdfTop
                                    }
                                    rectangle = rect
                                    contents = annotation.text
                                    setOpen(true)
                                }
                                page.annotations.add(pdfAnnotation)
                            }
                            else -> {}
                        }
                    }
                }
            }

            val outputFile = File(
                context.getExternalFilesDir(null),
                "annotated_${System.currentTimeMillis()}.pdf"
            )
            document.save(outputFile)
            document.close()
            outputFile
        } catch (e: Exception) {
            Log.e("AnnotationViewModel", "Error saving annotations", e)
            null
        }
    }
}
