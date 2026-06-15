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
    Eraser,
    Shapes,
    Stamp,
    Comment
}

enum class ShapeType {
    RECTANGLE,
    ELLIPSE,
    LINE,
    ARROW
}

sealed class AnnotationAction {
    data class Add(val annotation: AnnotationData) : AnnotationAction()
    data class Remove(val annotation: AnnotationData) : AnnotationAction()
    data class Move(val old: AnnotationData, val new: AnnotationData) : AnnotationAction()
}

sealed class AnnotationData {
    abstract val page: Int
    data class DrawPath(val points: List<Offset>, val color: Color, val stroke: Float, override val page: Int) : AnnotationData()
    data class Highlight(val rect: Rect, val color: Color, override val page: Int) : AnnotationData()
    data class TextNote(val text: String, val position: Offset, override val page: Int) : AnnotationData()
    data class StickyNote(val text: String, val position: Offset, override val page: Int) : AnnotationData()
    data class ShapeAnnotation(val type: ShapeType, val start: Offset, val end: Offset, val color: Color, val strokeWidth: Float, val filled: Boolean, override val page: Int) : AnnotationData()
    data class StampAnnotation(val text: String, val color: Color, val position: Offset, val rotation: Float = -15f, val filled: Boolean, override val page: Int, val scale: Float = 1.0f) : AnnotationData()
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

    // Shapes sub-states
    private val _selectedShapeType = MutableStateFlow(ShapeType.RECTANGLE)
    val selectedShapeType = _selectedShapeType.asStateFlow()

    private val _shapeFillEnabled = MutableStateFlow(false)
    val shapeFillEnabled = _shapeFillEnabled.asStateFlow()

    // Undo / Redo Stacks
    private val undoStack = mutableListOf<AnnotationAction>()
    private val redoStack = mutableListOf<AnnotationAction>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo = _canRedo.asStateFlow()

    private fun updateUndoRedoStates() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun recordAction(action: AnnotationAction) {
        undoStack.add(action)
        redoStack.clear() // clear redo on new action
        if (undoStack.size > 50) undoStack.removeAt(0) // limit history
        updateUndoRedoStates()
    }

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        when (action) {
            is AnnotationAction.Add -> _annotations.value = _annotations.value - action.annotation
            is AnnotationAction.Remove -> _annotations.value = _annotations.value + action.annotation
            is AnnotationAction.Move -> {
                _annotations.value = _annotations.value - action.new + action.old
            }
        }
        redoStack.add(action)
        updateUndoRedoStates()
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        when (action) {
            is AnnotationAction.Add -> _annotations.value = _annotations.value + action.annotation
            is AnnotationAction.Remove -> _annotations.value = _annotations.value - action.annotation
            is AnnotationAction.Move -> {
                _annotations.value = _annotations.value - action.old + action.new
            }
        }
        undoStack.add(action)
        updateUndoRedoStates()
    }

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

    fun setShapeType(type: ShapeType) {
        _selectedShapeType.value = type
    }

    fun setShapeFillEnabled(enabled: Boolean) {
        _shapeFillEnabled.value = enabled
    }

    fun addAnnotation(annotation: AnnotationData, record: Boolean = true) {
        _annotations.value = _annotations.value + annotation
        if (record) {
            recordAction(AnnotationAction.Add(annotation))
        }
    }

    fun removeAnnotation(annotation: AnnotationData, record: Boolean = true) {
        _annotations.value = _annotations.value - annotation
        if (record) {
            recordAction(AnnotationAction.Remove(annotation))
        }
    }

    fun moveAnnotation(old: AnnotationData, new: AnnotationData, record: Boolean = true) {
        _annotations.value = _annotations.value - old + new
        if (record) {
            recordAction(AnnotationAction.Move(old, new))
        }
    }

    fun clearAnnotations() {
        _annotations.value = emptyList()
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoStates()
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
                is AnnotationData.ShapeAnnotation -> {
                    (annotation.start - position).getDistance() <= threshold ||
                            (annotation.end - position).getDistance() <= threshold
                }
                is AnnotationData.StampAnnotation -> {
                    (annotation.position - position).getDistance() <= threshold
                }
            }
        }
        if (newList.size != currentList.size) {
            val removedOnes = currentList.filterNot { newList.contains(it) }
            removedOnes.forEach { recordAction(AnnotationAction.Remove(it)) }
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
                            is AnnotationData.ShapeAnnotation -> {
                                val r = annotation.color.red
                                val g = annotation.color.green
                                val b = annotation.color.blue
                                contentStream.setStrokingColor(r, g, b)
                                contentStream.setLineWidth(annotation.strokeWidth)
                                
                                val fx = annotation.start.x / canvasWidth * pageWidth
                                val fy = (canvasHeight - annotation.start.y) / canvasHeight * pageHeight
                                val tx = annotation.end.x / canvasWidth * pageWidth
                                val ty = (canvasHeight - annotation.end.y) / canvasHeight * pageHeight

                                when (annotation.type) {
                                    ShapeType.LINE -> {
                                        contentStream.moveTo(fx, fy)
                                        contentStream.lineTo(tx, ty)
                                        contentStream.stroke()
                                    }
                                    ShapeType.ARROW -> {
                                        contentStream.moveTo(fx, fy)
                                        contentStream.lineTo(tx, ty)
                                        contentStream.stroke()
                                        // Draw arrowhead approx
                                        val angle = Math.atan2((fy - ty).toDouble(), (fx - tx).toDouble())
                                        val len = 15f
                                        val angle1 = angle + Math.PI / 6
                                        val angle2 = angle - Math.PI / 6
                                        contentStream.moveTo(tx, ty)
                                        contentStream.lineTo((tx + len * Math.cos(angle1)).toFloat(), (ty + len * Math.sin(angle1)).toFloat())
                                        contentStream.stroke()
                                        contentStream.moveTo(tx, ty)
                                        contentStream.lineTo((tx + len * Math.cos(angle2)).toFloat(), (ty + len * Math.sin(angle2)).toFloat())
                                        contentStream.stroke()
                                    }
                                    ShapeType.RECTANGLE -> {
                                        val left = Math.min(fx, tx)
                                        val bottom = Math.min(fy, ty)
                                        val width = Math.abs(fx - tx)
                                        val height = Math.abs(fy - ty)
                                        if (annotation.filled) {
                                            contentStream.setNonStrokingColor(r, g, b)
                                            val oldGs = PDExtendedGraphicsState().apply {
                                                nonStrokingAlphaConstant = 0.3f
                                            }
                                            contentStream.setGraphicsStateParameters(oldGs)
                                            contentStream.addRect(left, bottom, width, height)
                                            contentStream.fill()
                                        }
                                        contentStream.addRect(left, bottom, width, height)
                                        contentStream.stroke()
                                    }
                                    ShapeType.ELLIPSE -> {
                                        val left = Math.min(fx, tx)
                                        val bottom = Math.min(fy, ty)
                                        val width = Math.abs(fx - tx)
                                        val height = Math.abs(fy - ty)
                                        contentStream.addRect(left, bottom, width, height)
                                        contentStream.stroke()
                                    }
                                }
                            }
                            is AnnotationData.StampAnnotation -> {
                                val r = annotation.color.red
                                val g = annotation.color.green
                                val b = annotation.color.blue
                                val sx = annotation.position.x / canvasWidth * pageWidth
                                val sy = (canvasHeight - annotation.position.y) / canvasHeight * pageHeight
                                
                                val textWidth = annotation.text.length * 10f
                                contentStream.setStrokingColor(r, g, b)
                                contentStream.setLineWidth(2f)
                                contentStream.addRect(sx - textWidth / 2 - 10f, sy - 15f, textWidth + 20f, 30f)
                                contentStream.stroke()
                                
                                contentStream.beginText()
                                contentStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 12f)
                                contentStream.setNonStrokingColor(r, g, b)
                                contentStream.newLineAtOffset(sx - textWidth / 2, sy - 5f)
                                contentStream.showText(annotation.text)
                                contentStream.endText()
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

    suspend fun exportSummaryPdf(
        context: Context,
        fileName: String,
        allAnnotations: List<AnnotationData>,
        allComments: List<com.example.data.CommentEntity>
    ): File? = withContext(Dispatchers.IO) {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            val summaryDoc = PDDocument()
            val page = com.tom_roush.pdfbox.pdmodel.PDPage(PDRectangle.A4)
            summaryDoc.addPage(page)
            
            val contentStream = PDPageContentStream(summaryDoc, page)
            
            // Draw title
            contentStream.beginText()
            contentStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 16f)
            contentStream.newLineAtOffset(50f, 750f)
            contentStream.showText("Annotation & Comments Summary")
            contentStream.endText()

            contentStream.beginText()
            contentStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 10f)
            contentStream.newLineAtOffset(50f, 730f)
            contentStream.showText("File: $fileName")
            contentStream.endText()

            var yOffset = 690f

            fun writeLine(text: String) {
                if (yOffset > 50f) {
                    contentStream.beginText()
                    contentStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 11f)
                    contentStream.newLineAtOffset(50f, yOffset)
                    // Sanitation to avoid PDFBox character mapping errors
                    val safeText = text.map { if (it.code in 32..126) it else ' ' }.joinToString("").trim()
                    if (safeText.isNotEmpty()) {
                        contentStream.showText(safeText)
                    }
                    contentStream.endText()
                    yOffset -= 25f
                }
            }

            // Write annotations
            if (allAnnotations.isNotEmpty()) {
                writeLine("--- Annotations ---")
                allAnnotations.forEach { ann ->
                    val typeStr = when (ann) {
                        is AnnotationData.DrawPath -> "Freehand drawing"
                        is AnnotationData.Highlight -> "Text Highlight"
                        is AnnotationData.TextNote -> "Text Note: ${ann.text}"
                        is AnnotationData.StickyNote -> "Sticky Note: ${ann.text}"
                        is AnnotationData.ShapeAnnotation -> "Shape ${ann.type.name}"
                        is AnnotationData.StampAnnotation -> "Stamp: ${ann.text}"
                    }
                    writeLine("Page ${ann.page + 1}: $typeStr")
                }
            }

            // Write comments
            if (allComments.isNotEmpty()) {
                writeLine("")
                writeLine("--- Comment Threads ---")
                allComments.forEach { comment ->
                    val isReply = comment.parentId != null
                    val prefix = if (isReply) "  -> Reply" else "Comment"
                    writeLine("Page ${comment.pageNumber + 1} - $prefix [${comment.authorName}]: ${comment.text}")
                }
            }

            contentStream.close()
            
            val baseName = fileName.substringBeforeLast(".")
            val outputDir = context.getExternalFilesDir(null) ?: context.cacheDir
            val outputFile = File(outputDir, "${baseName}_annotations_summary.pdf")
            summaryDoc.save(outputFile)
            summaryDoc.close()
            outputFile
        } catch (e: Exception) {
            Log.e("AnnotationViewModel", "Error exporting summary", e)
            null
        }
    }
}
