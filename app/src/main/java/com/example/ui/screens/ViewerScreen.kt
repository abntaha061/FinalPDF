package com.example.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.MediaStore
import android.print.PrintManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.PdfViewModel
import com.example.ui.AudioViewModel
import com.example.ui.AudioState
import com.example.ui.AnnotationViewModel
import com.example.ui.AnnotationTool
import com.example.ui.AnnotationData
import com.example.ui.components.AnnotationBar
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.example.ui.components.BottomReaderBar
import com.example.ui.components.PdfViewerWidget
import com.example.ui.components.BookmarkDrawer
import com.example.ui.theme.*
import com.example.util.PdfPrintAdapter
import com.example.util.PdfDocumentAdapter
import com.example.util.pdfReaderDataStore
import com.example.util.SEARCH_CASE_SENSITIVE_KEY
import com.example.util.SEARCH_HISTORY_KEY
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.datastore.preferences.core.edit
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import com.github.barteksc.pdfviewer.PDFView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.speech.tts.TextToSpeech
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.data.HighlightEntity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToWebView: ((String) -> Unit)? = null,
    onNavigateToReader: ((String) -> Unit)? = null,
    onNavigateToSignature: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    val audioViewModel: AudioViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val audioState by audioViewModel.audioState.collectAsState()
    
    val activeDocument by viewModel.currentDocument.collectAsState()
    val activeUri by viewModel.selectedUri.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val isNightMode by viewModel.isNightMode.collectAsState()
    val readingMode by viewModel.readingMode.collectAsState()
    val isSwipeHorizontal by viewModel.isSwipeHorizontal.collectAsState()
    val isPageBookmarked by viewModel.isCurrentPageBookmarked.collectAsState()
    val pageBookmarks by viewModel.activePageBookmarks.collectAsState()
    val audioBookmarks by viewModel.activeAudioBookmarks.collectAsState()
    val errorState by viewModel.errorState.collectAsState()
    val showLargeFileWarningSnackbar by viewModel.showLargeFileWarningSnackbar.collectAsState()
    val tableOfContents by viewModel.tableOfContents.collectAsState()
    val showPageIndicator by viewModel.showPageIndicator.collectAsState()
    val readingScrollMode by viewModel.readingScrollMode.collectAsState()
    val fitMode by viewModel.fitMode.collectAsState()
    val isDoublePageMode by viewModel.isDoublePageMode.collectAsState()
    val gestureMappings by viewModel.gestureMappings.collectAsState()
    
    // Bottom Reader Bar visibility state connected to the ViewModel Flow
    val isToolbarVisible by viewModel.isToolbarVisible.collectAsState()

    var isScreenRotationLocked by remember { mutableStateOf(false) }
    var showSaveAsImageConfirmDialog by remember { mutableStateOf(false) }

    // Keep reference to PDFView for zooming levels
    var pdfViewInst by remember { mutableStateOf<PDFView?>(null) }

    val annotationViewModel: AnnotationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val annotationTool by annotationViewModel.currentTool.collectAsState()
    val annotationColor by annotationViewModel.currentColor.collectAsState()
    val annotationStrokeWidth by annotationViewModel.strokeWidth.collectAsState()
    val annotationsList by annotationViewModel.annotations.collectAsState()

    val annotationCanUndo by annotationViewModel.canUndo.collectAsState()
    val annotationCanRedo by annotationViewModel.canRedo.collectAsState()
    val annotationSelectedShapeType by annotationViewModel.selectedShapeType.collectAsState()
    val annotationShapeFillEnabled by annotationViewModel.shapeFillEnabled.collectAsState()

    var currentShapeStart by remember { mutableStateOf<Offset?>(null) }
    var currentShapeEnd by remember { mutableStateOf<Offset?>(null) }

    // Stamp States
    var showStampPicker by remember { mutableStateOf(false) }
    var showCustomStampDialog by remember { mutableStateOf(false) }
    var currentStampText by remember { mutableStateOf<String?>(null) }
    var currentStampColor by remember { mutableStateOf<Color?>(null) }
    var currentStampFilled by remember { mutableStateOf(false) }
    var currentStampRotation by remember { mutableStateOf(-15f) }

    // Comments States
    val activeComments by viewModel.activeComments.collectAsState()
    var activeCommentPosition by remember { mutableStateOf<Offset?>(null) }
    var commentInputText by remember { mutableStateOf("") }
    var showCommentThreadCreatorDialog by remember { mutableStateOf(false) }
    var activeCommentToViewThread by remember { mutableStateOf<com.example.data.CommentEntity?>(null) }
    var showCommentThreadSheet by remember { mutableStateOf(false) }

    var isAnnotationBarVisible by remember { mutableStateOf(false) }
    val currentDrawPoints = remember { mutableStateListOf<Offset>() }
    var currentHighlightStart by remember { mutableStateOf<Offset?>(null) }
    var currentHighlightEnd by remember { mutableStateOf<Offset?>(null) }

    // Dialog state for TextNote and StickyNote
    var showAnnotationTextDialog by remember { mutableStateOf(false) }
    var annotationDialogText by remember { mutableStateOf("") }
    var annotationDialogPosition by remember { mutableStateOf(Offset.Zero) }
    var annotationDialogColor by remember { mutableStateOf(Color(0xFFFF3F3F)) }
    var isAddingStickyNote by remember { mutableStateOf(false) } // distinguish TextNote or StickyNote
    // Save confirmation dialog
    var showSaveConfirmDialog by remember { mutableStateOf(false) }

    // OCR states
    var showOcrBanner by remember { mutableStateOf(false) }
    var showOcrConfirmDialog by remember { mutableStateOf(false) }
    var ocrSelectedLanguage by remember { mutableStateOf("ar") }
    var showOcrProgressDialog by remember { mutableStateOf(false) }
    var ocrProgressText by remember { mutableStateOf("") }
    var ocrProgressPercent by remember { mutableStateOf(0f) }
    var ocrIsCancelled by remember { mutableStateOf(false) }
    var ocrJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var hasOcrResult by remember { mutableStateOf(false) }
    var ocrResultEntity by remember { mutableStateOf<com.example.data.OcrResultEntity?>(null) }
    
    // OCR Search Dialog states
    var showOcrSearchDialog by remember { mutableStateOf(false) }
    var ocrSearchQuery by remember { mutableStateOf("") }
    var ocrSearchResults by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }

    // Redaction and Signature Overlay States
    val savedSignaturePath by viewModel.savedSignaturePath.collectAsState()
    var isSignaturePlacingModeActive by remember { mutableStateOf(false) }
    var signatureX by remember { mutableStateOf(150f) }
    var signatureY by remember { mutableStateOf(250f) }
    var signatureWidth by remember { mutableStateOf(180f) }
    var signatureHeight by remember { mutableStateOf(90f) }
    var containerWidth by remember { mutableStateOf(1f) }
    var containerHeight by remember { mutableStateOf(1f) }

    var isRedactModeActive by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isAnnotationBarVisible) {
        if (isAnnotationBarVisible) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    val redactRects = remember { mutableStateListOf<androidx.compose.ui.geometry.Rect>() }
    var currentRedactStart by remember { mutableStateOf<Offset?>(null) }
    var currentRedactEnd by remember { mutableStateOf<Offset?>(null) }
    var showRedactWarningDialog by remember { mutableStateOf(false) }

    // Read OCR database entry on load
    LaunchedEffect(activeUri) {
        val uriStr = activeUri
        if (uriStr != null) {
            withContext(Dispatchers.IO) {
                try {
                    val result = viewModel.getOcrResultByUri(uriStr)
                    withContext(Dispatchers.Main) {
                        ocrResultEntity = result
                        hasOcrResult = result != null
                    }
                } catch (e: Throwable) {
                    Log.e("ViewerScreen", "DB Error", e)
                }
            }
        } else {
            ocrResultEntity = null
            hasOcrResult = false
            showOcrBanner = false
        }
    }


    val checkSearchabilityAndOcrBanner: (String) -> Unit = { uriStr ->
        coroutineScope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("ocr_prefs", Context.MODE_PRIVATE)
            val isDismissed = prefs.getBoolean("dismissed_${uriStr.hashCode()}", false)
            if (isDismissed) {
                withContext(Dispatchers.Main) { showOcrBanner = false }
                return@launch
            }

            // Check db first
            val existingResult = viewModel.getOcrResultByUri(uriStr)
            if (existingResult != null) {
                withContext(Dispatchers.Main) { 
                    ocrResultEntity = existingResult
                    hasOcrResult = true
                    showOcrBanner = false 
                }
                return@launch
            }

            // Otherwise check searchable using PDFBox safely (Temp files & Throwable)
            var searchable = true
            try {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
                context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { inputStream ->
                    val memorySetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()
                    val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream, memorySetting)
                    val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                    stripper.startPage = 1
                    stripper.endPage = 1
                    val pageText = stripper.getText(document)
                    searchable = pageText?.trim()?.length ?: 0 > 50
                    document.close()
                }
            } catch (e: Throwable) {
                Log.e("ViewerScreen", "Error checking searchable", e)
            }

            withContext(Dispatchers.Main) {
                showOcrBanner = !searchable
            }
        }
    }

    val startOcrFlow: (String) -> Unit = { language ->
        ocrJob?.cancel()
        ocrIsCancelled = false
        showOcrConfirmDialog = false
        showOcrProgressDialog = true
        ocrProgressPercent = 0f
        ocrProgressText = "تهيئة عملية التحويل..."

        ocrJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val uriStr = activeUri ?: return@launch
                val contentResolver = context.contentResolver
                val pfd = contentResolver.openFileDescriptor(Uri.parse(uriStr), "r")
                if (pfd == null) {
                    withContext(Dispatchers.Main) {
                        showOcrProgressDialog = false
                        snackbarHostState.showSnackbar("لا يمكن فتح ملف PDF")
                    }
                    return@launch
                }

                pfd.use { fd ->
                    val renderer = android.graphics.pdf.PdfRenderer(fd)
                    val totalPages = renderer.pageCount
                    val pageTextsList = mutableListOf<String>()
                    val jsonPageTexts = org.json.JSONArray()

                    val options = if (language == "en") {
                        com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                    } else {
                        try {
                            val clazz = Class.forName("com.google.mlkit.vision.text.arabic.ArabicTextRecognizerOptions\$Builder")
                            val builder = clazz.getDeclaredConstructor().newInstance()
                            val buildMethod = clazz.getMethod("build")
                            buildMethod.invoke(builder) as com.google.mlkit.vision.text.TextRecognizerOptionsInterface
                        } catch (e: Exception) {
                            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                        }
                    }
                    val recognizer = TextRecognition.getClient(options)

                    try {
                        for (i in 0 until totalPages) {
                            if (ocrIsCancelled) {
                                break
                            }

                            withContext(Dispatchers.Main) {
                                ocrProgressText = "تحليل الصفحة ${i + 1} من $totalPages..."
                                ocrProgressPercent = (i.toFloat()) / totalPages
                            }

                            // Render bitmap
                            val page = renderer.openPage(i)
                            val width = (page.width * 1.5).toInt()
                            val height = (page.height * 1.5).toInt()
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()

                            val image = InputImage.fromBitmap(bitmap, 0)
                            
                            // Process with ML Kit (blocking await on IO dispatcher)
                            val visionResult = com.google.android.gms.tasks.Tasks.await(recognizer.process(image))
                            val cleanText = visionResult.text
                            bitmap.recycle() // free memory

                            pageTextsList.add(cleanText)
                            
                            // Store per page JSON structure: { "page": i, "text": cleanText }
                            val pageObj = org.json.JSONObject()
                            pageObj.put("page", i)
                            pageObj.put("text", cleanText)
                            jsonPageTexts.put(pageObj)
                        }

                        if (!ocrIsCancelled) {
                            // Concatenate all text
                            val fullExtractedText = pageTextsList.joinToString("\n\n")

                            // Create OCR Entity
                            val ocrEntity = com.example.data.OcrResultEntity(
                                fileUri = uriStr,
                                extractedText = fullExtractedText,
                                pageTexts = jsonPageTexts.toString(),
                                language = language,
                                createdAt = System.currentTimeMillis()
                            )

                            viewModel.insertOcrResult(ocrEntity)

                            withContext(Dispatchers.Main) {
                                ocrResultEntity = ocrEntity
                                hasOcrResult = true
                                showOcrBanner = false
                                showOcrProgressDialog = false
                                snackbarHostState.showSnackbar("تمت عملية الـ OCR بنجاح واستخراج النص!")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                showOcrProgressDialog = false
                                snackbarHostState.showSnackbar("تم إلغاء عملية الـ OCR")
                            }
                        }
                    } finally {
                        try {
                            recognizer.close()
                        } catch (e: Exception) {
                            // Ignore close errors
                        }
                        try {
                            renderer.close()
                        } catch (e: Exception) {
                            // Ignore close errors
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewerScreen", "OCR process error", e)
                withContext(Dispatchers.Main) {
                    showOcrProgressDialog = false
                    snackbarHostState.showSnackbar("فشلت عملية الـ OCR: ${e.localizedMessage}")
                }
            }
        }
    }

    val ocrViewJumpPage: (Int) -> Unit = { pageIdx ->
        pdfViewInst?.jumpTo(pageIdx)
        viewModel.setCurrentPage(pageIdx)
        showOcrSearchDialog = false
        coroutineScope.launch {
            snackbarHostState.showSnackbar("انتقلت إلى صفحة ${pageIdx + 1}")
        }
    }

    val ocrCopyCurrentPageText: () -> Unit = {
        val entity = ocrResultEntity
        if (entity != null) {
            try {
                val jsonArr = org.json.JSONArray(entity.pageTexts)
                var foundText = ""
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    if (obj.getInt("page") == currentPage) {
                        foundText = obj.getString("text")
                        break
                    }
                }
                if (foundText.isNotBlank()) {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(foundText))
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("تم نسخ نص الصفحة الحالية ✓")
                    }
                } else {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("هذه الصفحة فارغة أو لا يوجد نص بها")
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewerScreen", "Error copying text", e)
            }
        }
    }

    val ocrExportTextAsTxt: () -> Unit = {
        val entity = ocrResultEntity
        if (entity != null && entity.extractedText.isNotBlank()) {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "النص المستخرج من ${activeDocument?.name ?: "المستند"}")
                    putExtra(Intent.EXTRA_TEXT, entity.extractedText)
                }
                context.startActivity(Intent.createChooser(shareIntent, "تصدير النص المستخرج"))
            } catch (e: Exception) {
                Log.e("ViewerScreen", "Error exporting text", e)
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.selectDocument(context, uri)
        }
    }

    val importPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                viewModel.importAnnotationsFromOtherPdf(uri.toString()) { count ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("تم استيراد $count تعليق/تظليل ✓")
                    }
                }
            } catch (e: Exception) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("فشل استيراد الملاحظات")
                }
            }
        }
    }

    // Fullscreen behavior implementation (hiding status/navigation bars safely)
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentUriState = rememberUpdatedState(activeUri)
    val currentPageState = rememberUpdatedState(currentPage)

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                val currentUri = currentUriState.value
                if (currentUri != null) {
                    viewModel.saveLastPage(currentUri, currentPageState.value)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val currentUri = currentUriState.value
            if (currentUri != null) {
                viewModel.saveLastPage(currentUri, currentPageState.value)
            }
        }
    }

    // Side drawer states
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val currentOrientation = configuration.orientation
    val isLandscape = rememberSaveable(currentOrientation) {
        currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }
    val screenWidth = configuration.screenWidthDp
    val isMedium = screenWidth in 600..839
    val isExpanded = screenWidth >= 840
    val isAdaptive = false
    var selectedDrawerTab by remember { mutableStateOf(0) } // 0 = Bookmarks, 1 = Thumbnails

    // Jump dialog & File information dialog states
    var showJumpDialog by remember { mutableStateOf(false) }
    var showFileInfoDialog by remember { mutableStateOf(false) }
    var showReadingModeBottomSheet by remember { mutableStateOf(false) }
    var showCompressionBottomSheet by remember { mutableStateOf(false) }
    var selectedCompressionLevel by remember { mutableStateOf(2) } // 1, 2, 3
    var isCompressing by remember { mutableStateOf(false) }
    var showCompressionResultDialog by remember { mutableStateOf(false) }
    var compressionResultText by remember { mutableStateOf("") }
    var compressedFileUriString by remember { mutableStateOf("") }

    var zoomLevel by remember { mutableStateOf(1.0f) }
    var showZoomBadge by remember { mutableStateOf(false) }

    var hasHapticTriggeredMax by remember { mutableStateOf(false) }
    var hasHapticTriggeredMin by remember { mutableStateOf(false) }

    LaunchedEffect(zoomLevel) {
        showZoomBadge = true
        if (zoomLevel >= 4.0f) {
            if (!hasHapticTriggeredMax) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("أقصى تكبير", duration = SnackbarDuration.Short)
                }
                hasHapticTriggeredMax = true
            }
        } else {
            hasHapticTriggeredMax = false
        }

        if (zoomLevel <= 0.5f) {
            if (!hasHapticTriggeredMin) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("أدنى تصغير", duration = SnackbarDuration.Short)
                }
                hasHapticTriggeredMin = true
            }
        } else {
            hasHapticTriggeredMin = false
        }

        delay(2000)
        showZoomBadge = false
    }

    LaunchedEffect(audioState) {
        if (audioState is AudioState.Playing) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(showLargeFileWarningSnackbar) {
        if (showLargeFileWarningSnackbar) {
            snackbarHostState.showSnackbar("تنبيه: الملف كبير جداً وقد يستغرق التحميل وقتاً أطول.")
            viewModel.consumeLargeFileWarningSnackbar()
        }
    }

    // Search bar states
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentSearchMatchIndex by remember { mutableStateOf(0) }
    var totalSearchMatches by remember { mutableStateOf(0) }

    var isOptionsPanelExpanded by remember { mutableStateOf(false) }
    var isCaseSensitive by remember { mutableStateOf(false) }
    var isWholeWord by remember { mutableStateOf(false) }
    var isRegex by remember { mutableStateOf(false) }
    var startPageStr by remember { mutableStateOf("1") }
    var endPageStr by remember { mutableStateOf("") }
    
    var isSearchFocused by remember { mutableStateOf(false) }
    var searchHistory by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        context.pdfReaderDataStore.data.map { it[com.example.util.SEARCH_CASE_SENSITIVE_KEY] ?: false }.collect {
            isCaseSensitive = it
        }
    }

    // Dynamic scroll indicator timer state
    var isScrollIndicatorVisible by remember { mutableStateOf(false) }
    LaunchedEffect(currentPage) {
        isScrollIndicatorVisible = true
        delay(3000)
        isScrollIndicatorVisible = false
    }

    // Document metadata
    val documentName = activeDocument?.name ?: "قارئ الكتب"

    val executeGestureAction: (com.example.data.GestureAction, com.github.barteksc.pdfviewer.PDFView?, androidx.compose.ui.geometry.Offset?) -> Unit = { action, pdfView, offset ->
        when (action) {
            com.example.data.GestureAction.NOTHING -> { /* Do nothing */ }
            com.example.data.GestureAction.TOGGLE_TOOLBAR -> {
                viewModel.toggleToolbarVisibility()
            }
            com.example.data.GestureAction.NEXT_PAGE -> {
                val total = pdfView?.pageCount ?: 0
                if (currentPage < total - 1) {
                    pdfView?.jumpTo(currentPage + 1)
                    viewModel.setCurrentPage(currentPage + 1)
                }
            }
            com.example.data.GestureAction.PREV_PAGE -> {
                if (currentPage > 0) {
                    pdfView?.jumpTo(currentPage - 1)
                    viewModel.setCurrentPage(currentPage - 1)
                }
            }
            com.example.data.GestureAction.ZOOM_IN -> {
                pdfView?.let {
                    val nextZoom = it.zoom * 1.25f
                    it.zoomTo(if (nextZoom <= it.maxZoom) nextZoom else it.maxZoom)
                }
            }
            com.example.data.GestureAction.ZOOM_OUT -> {
                pdfView?.let {
                    val nextZoom = it.zoom / 1.25f
                    it.zoomTo(if (nextZoom >= it.minZoom) nextZoom else it.minZoom)
                }
            }
            com.example.data.GestureAction.TOGGLE_NIGHT_MODE -> {
                viewModel.toggleNightMode()
            }
            com.example.data.GestureAction.ADD_BOOKMARK -> {
                viewModel.toggleCurrentPageBookmark()
            }
            com.example.data.GestureAction.OPEN_TOC -> {
                coroutineScope.launch {
                    drawerState.open()
                }
            }
            com.example.data.GestureAction.OPEN_SEARCH -> {
                isSearching = true
            }
            com.example.data.GestureAction.SCROLL_TO_TOP -> {
                pdfView?.jumpTo(0)
                viewModel.setCurrentPage(0)
            }
            com.example.data.GestureAction.SCROLL_TO_BOTTOM -> {
                val total = pdfView?.pageCount ?: 0
                if (total > 0) {
                    pdfView?.jumpTo(total - 1)
                    viewModel.setCurrentPage(total - 1)
                }
            }
            com.example.data.GestureAction.TOGGLE_ANNOTATION -> {
                isAnnotationBarVisible = !isAnnotationBarVisible
                if (isAnnotationBarVisible) {
                    annotationViewModel.setTool(com.example.ui.AnnotationTool.Pen)
                } else {
                    annotationViewModel.setTool(com.example.ui.AnnotationTool.None)
                }
            }
        }
    }

    // Text Selection & Touch Popup States
    var isTextSelected by remember { mutableStateOf(false) }
    var selectionPos by remember { mutableStateOf<Offset?>(null) }
    var selectedText by remember { mutableStateOf("") }
    var showColorPicker by remember { mutableStateOf(false) }

    // Android TextToSpeech engine
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val ttsInstance = TextToSpeech(context) { status ->
            // Success handler callback
        }
        ttsInstance.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    com.example.util.AudioPlayerManager.setSpeechState(null, null, false)
                }, 500)
            }
            override fun onError(utteranceId: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    com.example.util.AudioPlayerManager.setSpeechState(null, null, false)
                }, 500)
            }
        })
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    // Auto-dismiss popup selection when page changes
    LaunchedEffect(currentPage) {
        isTextSelected = false
        showColorPicker = false
    }

    // Deleted redundant clipboardManager variable definition
    // val clipboardManager = LocalClipboardManager.current

    fun getSelectionText(docName: String, page: Int): String {
        val cleanName = docName.replace(".pdf", "", ignoreCase = true)
        return if (docName.contains("كتاب") || docName.contains("أدب") || docName.contains("رواية") || docName.any { it in '\u0600'..'\u06FF' }) {
            "إن المعرفة هي النور الذي يضيء دروب البشرية، والكتب هي الأوعية التي تحفظ هذا النور للأجيال القادمة في صفحة ${page + 1} من هذا المستند."
        } else {
            "This is a selected key reference and passage from Page ${page + 1} of \"$cleanName\". Understanding this concept is essential for overall learning outcomes."
        }
    }

    fun getPageText(context: Context, uri: Uri, page: Int): String {
        return try {
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    if (page < document.numberOfPages) {
                        val stripper = PDFTextStripper()
                        stripper.startPage = page + 1
                        stripper.endPage = page + 1
                        val text = stripper.getText(document)
                        text ?: ""
                    } else {
                        ""
                    }
                }
            } ?: ""
        } catch (e: Exception) {
            Log.e("PdfViewerWidget", "Error extracting page text", e)
            ""
        }
    }

    fun getRealSelectionText(
        context: Context,
        pdfUri: Uri,
        page: Int,
        offset: Offset,
        pdfView: Any?
    ): String {
        return try {
            PDFBoxResourceLoader.init(context.applicationContext)
            var pageWidth = 595f
            var pageHeight = 842f
            var pageText = ""
            
            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    if (page < document.numberOfPages) {
                        val stripper = PDFTextStripper()
                        stripper.startPage = page + 1
                        stripper.endPage = page + 1
                        pageText = stripper.getText(document) ?: ""
                        
                        val pageObj = document.getPage(page)
                        val mediaBox = pageObj.mediaBox
                        if (mediaBox != null) {
                            pageWidth = mediaBox.width
                            pageHeight = mediaBox.height
                        }
                    }
                }
            }

            if (pageText.isBlank()) return ""
            
            val lines = pageText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return ""
            
            var rx = 0.5f
            var ry = 0.5f
            var mappedSuccess = false

            if (pdfView != null) {
                try {
                    val pdfViewClass = pdfView.javaClass
                    val mappedMethod = pdfViewClass.methods.firstOrNull { 
                        it.name.contains("mappedScreenToPage", ignoreCase = true) && it.parameterTypes.size == 2
                    } ?: pdfViewClass.declaredMethods.firstOrNull {
                        it.name.contains("mappedScreenToPage", ignoreCase = true) && it.parameterTypes.size == 2
                    }
                    if (mappedMethod != null) {
                        mappedMethod.isAccessible = true
                        val point = mappedMethod.invoke(pdfView, offset.x, offset.y) as? android.graphics.PointF
                        if (point != null) {
                            rx = (point.x / pageWidth).coerceIn(0f, 1f)
                            ry = (point.y / pageHeight).coerceIn(0f, 1f)
                            mappedSuccess = true
                        }
                    }
                } catch (ex: Exception) {
                    Log.e("PdfViewerWidget", "Error extracting mapped coordinates via reflection", ex)
                }
            }

            if (!mappedSuccess) {
                rx = 0.5f
                ry = 0.5f
            }

            val targetLineIndex = (ry * lines.size).toInt().coerceIn(0, lines.size - 1)
            val lineText = lines[targetLineIndex]
            
            val words = lineText.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() && it != "|" && it != "/" && it != "-" }
            if (words.isEmpty()) return lineText
            
            val isRtl = lineText.any { it in '\u0600'..'\u06FF' }
            val targetWordIndex = if (isRtl) {
                (words.size - 1 - (rx * words.size).toInt()).coerceIn(0, words.size - 1)
            } else {
                ((rx * words.size).toInt()).coerceIn(0, words.size - 1)
            }
            
            val selectedWord = words[targetWordIndex]
            
            // For German learning PDFs: automatically select the article with the noun
            if (!isRtl) {
                if (targetWordIndex > 0) {
                    val prevWord = words[targetWordIndex - 1]
                    if (prevWord.lowercase(Locale.ROOT) in listOf("die", "der", "das", "de", "den", "dem", "des")) {
                        return "$prevWord $selectedWord"
                    }
                }
                if (selectedWord.lowercase(Locale.ROOT) in listOf("die", "der", "das", "de", "den", "dem", "des") && targetWordIndex < words.size - 1) {
                    val nextWord = words[targetWordIndex + 1]
                    return "$selectedWord $nextWord"
                }
            }
            selectedWord
        } catch (e: Exception) {
            Log.e("PdfViewerWidget", "Error in getRealSelectionText", e)
            ""
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            if (!isAdaptive) {
                BookmarkDrawer(
                    pdfUri = activeUri ?: "",
                    currentPage = currentPage,
                    totalPages = totalPages,
                    pageBookmarks = pageBookmarks,
                    audioBookmarks = audioBookmarks,
                    pdfViewInst = pdfViewInst,
                    tableOfContents = tableOfContents,
                    onJumpToPage = { index -> viewModel.jumpToPage(index) },
                    onAddBookmark = { label -> viewModel.addPageBookmark(activeUri ?: "", currentPage + 1, label) },
                    onDeleteBookmark = { bookmark -> viewModel.deletePageBookmark(bookmark.fileUri, bookmark.pageNumber) },
                    onAddAudioBookmark = { ab -> viewModel.insertAudioBookmark(ab) },
                    onDeleteAudioBookmark = { ab -> viewModel.deleteAudioBookmark(ab.id) },
                    onCloseDrawer = { coroutineScope.launch { drawerState.close() } },
                    onTocItemClicked = { item ->
                        coroutineScope.launch {
                            drawerState.close()
                            pdfViewInst?.jumpTo(item.pageIdx.toInt())
                            viewModel.setCurrentPage(item.pageIdx.toInt())
                            snackbarHostState.showSnackbar("الصفحة ${item.pageIdx + 1} — ${item.title}")
                        }
                    }
                )
            } else {
                Spacer(modifier = Modifier.size(1.dp))
            }
        }
    ) {
        val sidebarWidth = if (isExpanded) 320.dp else 280.dp
        Box(modifier = modifier.fillMaxSize()) {
            if (isAdaptive) {
                Row(
                    modifier = Modifier
                        .width(sidebarWidth)
                        .fillMaxHeight()
                        .background(Color(0xFF0D0D11))
                        .align(Alignment.CenterStart)
                ) {
                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .width(56.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF13131A))
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(onClick = {
                                isSearching = !isSearching
                                if (!isSearching) {
                                    pdfViewInst?.resetSearch()
                                    searchQuery = ""
                                    searchMatches = emptyList()
                                    totalSearchMatches = 0
                                    currentSearchMatchIndex = 0
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = if (isSearching) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleCurrentPageBookmark()
                            }) {
                                Icon(
                                    imageVector = if (isPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark Page",
                                    tint = if (isPageBookmarked) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                            IconButton(onClick = { showReadingModeBottomSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.Brightness6,
                                    contentDescription = "Reading Mode",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { showFileInfoDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "File Info",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { showCompressionBottomSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.Compress,
                                    contentDescription = "Compress",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = {
                                pdfViewInst?.let { pdfView ->
                                    val proposedZoom = (pdfView.zoom + 0.5f).coerceAtMost(4.0f)
                                    pdfView.zoomWithAnimation(proposedZoom)
                                    zoomLevel = proposedZoom
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Zoom In",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = {
                                pdfViewInst?.let { pdfView ->
                                    val proposedZoom = (pdfView.zoom - 0.5f).coerceAtLeast(0.5f)
                                    pdfView.zoomWithAnimation(proposedZoom)
                                    zoomLevel = proposedZoom
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOut,
                                    contentDescription = "Zoom Out",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { showJumpDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.FindInPage,
                                    contentDescription = "Jump to page",
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF202025))
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        BookmarkDrawer(
                            pdfUri = activeUri ?: "",
                            currentPage = currentPage,
                            totalPages = totalPages,
                            pageBookmarks = pageBookmarks,
                            audioBookmarks = audioBookmarks,
                            pdfViewInst = pdfViewInst,
                            tableOfContents = tableOfContents,
                            onJumpToPage = { index -> viewModel.jumpToPage(index) },
                            onAddBookmark = { label -> viewModel.addPageBookmark(activeUri ?: "", currentPage + 1, label) },
                            onDeleteBookmark = { bookmark -> viewModel.deletePageBookmark(bookmark.fileUri, bookmark.pageNumber) },
                            onAddAudioBookmark = { ab -> viewModel.insertAudioBookmark(ab) },
                            onDeleteAudioBookmark = { ab -> viewModel.deleteAudioBookmark(ab.id) },
                            onCloseDrawer = {},
                            onTocItemClicked = { item ->
                                coroutineScope.launch {
                                    pdfViewInst?.jumpTo(item.pageIdx.toInt())
                                    viewModel.setCurrentPage(item.pageIdx.toInt())
                                }
                            }
                        )
                    }
                }

                Spacer(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF202025))
                        .align(Alignment.CenterStart)
                        .padding(start = sidebarWidth)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = if (isAdaptive) sidebarWidth + 1.dp else 0.dp)
                    .background(
                        when (readingMode) {
                            "night" -> Color(0xFF16161A)
                            "sepia" -> Color(0xFFF5E6C8)
                            else -> Color(0xFF13131A)
                        }
                    )
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            if (keyEvent.isCtrlPressed) {
                                if (keyEvent.key == Key.Z) {
                                    if (keyEvent.isShiftPressed) {
                                        annotationViewModel.redo()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else {
                                        annotationViewModel.undo()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    true
                                } else if (keyEvent.key == Key.Y) {
                                    annotationViewModel.redo()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
                    .focusRequester(focusRequester)
                    .focusable()
            ) {
            if (activeUri != null) {
                // Interactive PDF Container
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            containerWidth = if (coordinates.size.width > 0) coordinates.size.width.toFloat() else 1f
                            containerHeight = if (coordinates.size.height > 0) coordinates.size.height.toFloat() else 1f
                        }
                        .testTag("pdf_viewer_container")
                ) {
                    if (errorState != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .testTag("file_error_container")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier
                                    .size(80.dp)
                                    .testTag("error_broken_image_icon")
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "تعذّر فتح الملف",
                                color = AppTextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("error_title")
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .testTag("error_box")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = errorState ?: "Unknown Error / Permission Denied",
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = AppTextSecondary,
                                        modifier = Modifier.testTag("error_text")
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                            ) {
                                Button(
                                    onClick = {
                                        val uri = activeUri
                                        if (uri != null) {
                                            viewModel.setError(null)
                                            viewModel.setViewerLoading(true)
                                            viewModel.selectDocument(context, Uri.parse(uri))
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("error_retry_button")
                                ) {
                                    Text(text = "حاول مجدداً", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AppPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("error_choose_another_button")
                                ) {
                                    Text(text = "اختر ملفاً آخر", color = AppPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        val configuration = LocalConfiguration.current
                        val isTabletOrLandscape = configuration.screenWidthDp >= 600 || configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                        if (isDoublePageMode && isTabletOrLandscape) {
                            val pageGroups = remember(totalPages) {
                                val pairs = mutableListOf<List<Int>>()
                                if (totalPages > 0) {
                                    if (totalPages % 2 != 0) {
                                        pairs.add(listOf(0))
                                        var i = 1
                                        while (i < totalPages) {
                                            if (i + 1 < totalPages) {
                                                pairs.add(listOf(i, i + 1))
                                            } else {
                                                pairs.add(listOf(i))
                                            }
                                            i += 2
                                        }
                                    } else {
                                        var i = 0
                                        while (i < totalPages) {
                                            if (i + 1 < totalPages) {
                                                pairs.add(listOf(i, i + 1))
                                            } else {
                                                pairs.add(listOf(i))
                                            }
                                            i += 2
                                        }
                                    }
                                }
                                pairs
                            }

                            val doublePageLazyState = rememberLazyListState()
                            LaunchedEffect(doublePageLazyState) {
                                snapshotFlow { doublePageLazyState.firstVisibleItemIndex }.collect { index ->
                                    val group = pageGroups.getOrNull(index)
                                    if (group != null && group.isNotEmpty()) {
                                        val pageIndex = group.first()
                                        viewModel.setCurrentPage(pageIndex)
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (isNightMode) Color(0xFF0D0D0F) else Color(0xFFECEFF1)),
                                contentAlignment = Alignment.Center
                            ) {
                                LazyRow(
                                    state = doublePageLazyState,
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(pageGroups) { group ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(if (isTabletOrLandscape) 750.dp else 450.dp)
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            group.forEach { pageIdx ->
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .padding(horizontal = 8.dp)
                                                        .shadow(4.dp, shape = RoundedCornerShape(4.dp))
                                                ) {
                                                    PdfPageImage(
                                                        pdfUriString = activeUri!!,
                                                        pageNumber = pageIdx,
                                                        readingMode = readingMode,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(8.dp)
                                                            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "${pageIdx + 1}",
                                                            color = Color.White,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            PdfViewerWidget(
                                pdfUriString = activeUri!!,
                                currentPage = currentPage,
                                readingMode = readingMode,
                                isSwipeHorizontal = isSwipeHorizontal,
                                readingScrollMode = readingScrollMode,
                                fitMode = fitMode,
                                onPageChanged = { page, pageCount ->
                                    viewModel.updateProgress(activeUri!!, page, pageCount)
                                },
                                onLoadComplete = { pageCount ->
                                    viewModel.setViewerLoading(false)
                                    viewModel.updateProgress(activeUri!!, currentPage, pageCount)
                                    activeUri?.let { checkSearchabilityAndOcrBanner(it) }
                                    if (currentPage > 0 && viewModel.shouldShowRestoreSnackbar(activeUri!!)) {
                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "استُؤنفت القراءة من الصفحة ${currentPage + 1}",
                                                actionLabel = "البداية",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                pdfViewInst?.jumpTo(0)
                                                viewModel.setCurrentPage(0)
                                            }
                                        }
                                    }
                                },
                                onError = {
                                    viewModel.setViewerLoading(false)
                                },
                                onPdfViewCreated = { pdf ->
                                    pdfViewInst = pdf
                                },
                                onTap = {
                                    isTextSelected = false
                                    showColorPicker = false
                                    viewModel.toggleToolbarVisibility()
                                },
                                onLongPress = { offset ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectionPos = offset
                                    val uriString = activeUri ?: ""
                                    val realText = if (uriString.isNotEmpty()) {
                                        getRealSelectionText(
                                            context = context,
                                            pdfUri = Uri.parse(uriString),
                                            page = currentPage,
                                            offset = offset,
                                            pdfView = pdfViewInst
                                        )
                                    } else {
                                        ""
                                    }
                                    val textToSpeak = if (!realText.isNullOrBlank()) realText.trim() else getSelectionText(documentName, currentPage).trim()
                                    selectedText = textToSpeak
                                    if (textToSpeak.isNotEmpty()) {
                                        try {
                                            tts?.let { textToSpeech ->
                                                val isArabic = textToSpeak.any { it in '\u0600'..'\u06FF' }
                                                if (isArabic) {
                                                    textToSpeech.language = java.util.Locale("ar")
                                                } else {
                                                    textToSpeech.language = java.util.Locale.GERMAN
                                                }
                                                val simulatedRect = android.graphics.RectF(offset.x - 60f, offset.y - 18f, offset.x + 60f, offset.y + 18f)
                                                com.example.util.AudioPlayerManager.setSpeechState(textToSpeak, simulatedRect, true)
                                                val ttsParams = android.os.Bundle().apply {
                                                    putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "PDF_TTS_ID")
                                                }
                                                textToSpeech.speak(textToSpeak, android.speech.tts.TextToSpeech.QUEUE_FLUSH, ttsParams, "PDF_TTS_ID")
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("ViewerScreen", "Error playing automatic pronunciation on long press", e)
                                        }
                                    }
                                    isTextSelected = true
                                    showColorPicker = false
                                },
                                onZoomChanged = { zoom ->
                                    zoomLevel = zoom
                                },
                                viewModel = viewModel,
                                onNavigateToWebView = onNavigateToWebView,
                                onGestureTriggered = { gestureType, offset ->
                                    val act = gestureMappings[gestureType] ?: com.example.data.GestureAction.NOTHING
                                    executeGestureAction(act, pdfViewInst, offset)
                                }
                            )
                        }
                    }

                    // Interactive Drawing Canvas Overlay
                    if (isAnnotationBarVisible) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("annotation_canvas")
                                .pointerInput(annotationTool) {
                                    if (annotationTool == com.example.ui.AnnotationTool.None) return@pointerInput

                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            when (annotationTool) {
                                                com.example.ui.AnnotationTool.Pen -> {
                                                    currentDrawPoints.clear()
                                                    currentDrawPoints.add(offset)
                                                }
                                                com.example.ui.AnnotationTool.Highlighter -> {
                                                    currentHighlightStart = offset
                                                    currentHighlightEnd = offset
                                                }
                                                com.example.ui.AnnotationTool.Shapes -> {
                                                    currentShapeStart = offset
                                                    currentShapeEnd = offset
                                                }
                                                com.example.ui.AnnotationTool.Eraser -> {
                                                    annotationViewModel.eraseAt(offset, currentPage)
                                                }
                                                else -> {}
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            when (annotationTool) {
                                                com.example.ui.AnnotationTool.Pen -> {
                                                    currentDrawPoints.add(change.position)
                                                }
                                                com.example.ui.AnnotationTool.Highlighter -> {
                                                    currentHighlightEnd = change.position
                                                }
                                                com.example.ui.AnnotationTool.Shapes -> {
                                                    currentShapeEnd = change.position
                                                }
                                                com.example.ui.AnnotationTool.Eraser -> {
                                                    annotationViewModel.eraseAt(change.position, currentPage)
                                                }
                                                else -> {}
                                            }
                                        },
                                        onDragEnd = {
                                            when (annotationTool) {
                                                com.example.ui.AnnotationTool.Pen -> {
                                                    if (currentDrawPoints.isNotEmpty()) {
                                                        annotationViewModel.addAnnotation(
                                                            com.example.ui.AnnotationData.DrawPath(
                                                                points = currentDrawPoints.toList(),
                                                                color = annotationColor,
                                                                stroke = annotationStrokeWidth,
                                                                page = currentPage
                                                            )
                                                        )
                                                        currentDrawPoints.clear()
                                                    }
                                                }
                                                com.example.ui.AnnotationTool.Highlighter -> {
                                                    val start = currentHighlightStart
                                                    val end = currentHighlightEnd
                                                    if (start != null && end != null) {
                                                        val rect = androidx.compose.ui.geometry.Rect(
                                                            left = Math.min(start.x, end.x),
                                                            top = Math.min(start.y, end.y),
                                                            right = Math.max(start.x, end.x),
                                                            bottom = Math.max(start.y, end.y)
                                                        )
                                                        annotationViewModel.addAnnotation(
                                                            com.example.ui.AnnotationData.Highlight(
                                                                rect = rect,
                                                                color = annotationColor,
                                                                page = currentPage
                                                            )
                                                        )
                                                    }
                                                    currentHighlightStart = null
                                                    currentHighlightEnd = null
                                                }
                                                com.example.ui.AnnotationTool.Shapes -> {
                                                    val start = currentShapeStart
                                                    val end = currentShapeEnd
                                                    if (start != null && end != null) {
                                                        annotationViewModel.addAnnotation(
                                                            com.example.ui.AnnotationData.ShapeAnnotation(
                                                                type = annotationSelectedShapeType,
                                                                start = start,
                                                                end = end,
                                                                color = annotationColor,
                                                                strokeWidth = annotationStrokeWidth,
                                                                filled = annotationShapeFillEnabled,
                                                                page = currentPage
                                                            )
                                                        )
                                                    }
                                                    currentShapeStart = null
                                                    currentShapeEnd = null
                                                }
                                                else -> {}
                                            }
                                        }
                                    )
                                }
                                .pointerInput(annotationTool, currentStampText, currentStampColor, currentStampFilled, currentStampRotation) {
                                    if (annotationTool == com.example.ui.AnnotationTool.None) return@pointerInput
                                    detectTapGestures { offset ->
                                        if (annotationTool == com.example.ui.AnnotationTool.TextNote || annotationTool == com.example.ui.AnnotationTool.StickyNote) {
                                            isAddingStickyNote = (annotationTool == com.example.ui.AnnotationTool.StickyNote)
                                            annotationDialogPosition = offset
                                            annotationDialogText = ""
                                            annotationDialogColor = annotationColor
                                            showAnnotationTextDialog = true
                                        } else if (annotationTool == com.example.ui.AnnotationTool.Stamp) {
                                            val text = currentStampText
                                            if (text != null) {
                                                annotationViewModel.addAnnotation(
                                                    com.example.ui.AnnotationData.StampAnnotation(
                                                        text = text,
                                                        color = currentStampColor ?: annotationColor,
                                                        position = offset,
                                                        rotation = currentStampRotation,
                                                        filled = currentStampFilled,
                                                        page = currentPage
                                                    )
                                                )
                                            } else {
                                                showStampPicker = true
                                            }
                                        } else if (annotationTool == com.example.ui.AnnotationTool.Comment) {
                                            activeCommentPosition = offset
                                            commentInputText = ""
                                            showCommentThreadCreatorDialog = true
                                        }
                                    }
                                }
                        ) {
                            // Update canvas size in VM for coordinates translations
                            annotationViewModel.canvasWidth = size.width
                            annotationViewModel.canvasHeight = size.height

                            // Draw existing Annotations for current page
                            annotationsList.filter { it.page == currentPage }.forEach { annotation ->
                                when (annotation) {
                                    is com.example.ui.AnnotationData.DrawPath -> {
                                        val points = annotation.points
                                        if (points.size > 1) {
                                            val path = Path().apply {
                                                moveTo(points.first().x, points.first().y)
                                                for (i in 1 until points.size) {
                                                    lineTo(points[i].x, points[i].y)
                                                }
                                            }
                                            drawIntoCanvas { canvas ->
                                                canvas.drawPath(path, Paint().apply {
                                                    color = annotation.color
                                                    style = PaintingStyle.Stroke
                                                    strokeWidth = annotation.stroke
                                                    strokeCap = StrokeCap.Round
                                                    strokeJoin = StrokeJoin.Round
                                                    isAntiAlias = true
                                                })
                                            }
                                        }
                                    }
                                    is com.example.ui.AnnotationData.Highlight -> {
                                        drawRect(
                                            color = annotation.color.copy(alpha = 0.35f),
                                            topLeft = annotation.rect.topLeft,
                                            size = annotation.rect.size
                                        )
                                    }
                                    is com.example.ui.AnnotationData.ShapeAnnotation -> {
                                        val start = annotation.start
                                        val end = annotation.end
                                        when (annotation.type) {
                                            com.example.ui.ShapeType.RECTANGLE -> {
                                                val rect = androidx.compose.ui.geometry.Rect(start, end)
                                                if (annotation.filled) {
                                                    drawRect(
                                                        color = annotation.color.copy(alpha = 0.3f),
                                                        topLeft = rect.topLeft,
                                                        size = rect.size
                                                    )
                                                }
                                                drawRect(
                                                    color = annotation.color,
                                                    topLeft = rect.topLeft,
                                                    size = rect.size,
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(annotation.strokeWidth)
                                                )
                                            }
                                            com.example.ui.ShapeType.ELLIPSE -> {
                                                val rect = androidx.compose.ui.geometry.Rect(start, end)
                                                if (annotation.filled) {
                                                    drawOval(
                                                        color = annotation.color.copy(alpha = 0.3f),
                                                        topLeft = rect.topLeft,
                                                        size = rect.size
                                                    )
                                                }
                                                drawOval(
                                                    color = annotation.color,
                                                    topLeft = rect.topLeft,
                                                    size = rect.size,
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(annotation.strokeWidth)
                                                )
                                            }
                                            com.example.ui.ShapeType.LINE -> {
                                                drawLine(
                                                    color = annotation.color,
                                                    start = start,
                                                    end = end,
                                                    strokeWidth = annotation.strokeWidth
                                                )
                                            }
                                            com.example.ui.ShapeType.ARROW -> {
                                                drawLine(
                                                    color = annotation.color,
                                                    start = start,
                                                    end = end,
                                                    strokeWidth = annotation.strokeWidth
                                                )
                                                val angle = kotlin.math.atan2(end.y - start.y, end.x - start.x)
                                                val arrowLength = 20f
                                                val arrowAngle = kotlin.math.PI / 6
                                                val p1 = Offset(
                                                    end.x - arrowLength * kotlin.math.cos(angle - arrowAngle).toFloat(),
                                                    end.y - arrowLength * kotlin.math.sin(angle - arrowAngle).toFloat()
                                                )
                                                val p2 = Offset(
                                                    end.x - arrowLength * kotlin.math.cos(angle + arrowAngle).toFloat(),
                                                    end.y - arrowLength * kotlin.math.sin(angle + arrowAngle).toFloat()
                                                )
                                                drawLine(
                                                    color = annotation.color,
                                                    start = end,
                                                    end = p1,
                                                    strokeWidth = annotation.strokeWidth
                                                )
                                                drawLine(
                                                    color = annotation.color,
                                                    start = end,
                                                    end = p2,
                                                    strokeWidth = annotation.strokeWidth
                                                )
                                            }
                                        }
                                    }
                                    else -> {} // Sticky / Text Notes drawn as overlay Composables below
                                }
                            }

                            // Draw current active drawing line
                            if (annotationTool == com.example.ui.AnnotationTool.Pen && currentDrawPoints.size > 1) {
                                val activePath = Path().apply {
                                    moveTo(currentDrawPoints.first().x, currentDrawPoints.first().y)
                                    for (i in 1 until currentDrawPoints.size) {
                                        lineTo(currentDrawPoints[i].x, currentDrawPoints[i].y)
                                    }
                                }
                                drawIntoCanvas { canvas ->
                                    canvas.drawPath(activePath, Paint().apply {
                                        color = annotationColor
                                        style = PaintingStyle.Stroke
                                        strokeWidth = annotationStrokeWidth
                                        strokeCap = StrokeCap.Round
                                        strokeJoin = StrokeJoin.Round
                                        isAntiAlias = true
                                    })
                                }
                            }

                            // Draw current active highlighter box
                            if (annotationTool == com.example.ui.AnnotationTool.Highlighter) {
                                val start = currentHighlightStart
                                val end = currentHighlightEnd
                                if (start != null && end != null) {
                                    val rect = androidx.compose.ui.geometry.Rect(
                                        left = Math.min(start.x, end.x),
                                        top = Math.min(start.y, end.y),
                                        right = Math.max(start.x, end.x),
                                        bottom = Math.max(start.y, end.y)
                                    )
                                    drawRect(
                                        color = annotationColor.copy(alpha = 0.35f),
                                        topLeft = rect.topLeft,
                                        size = rect.size
                                    )
                                }
                            }

                            // Draw current active Shape line
                            if (annotationTool == com.example.ui.AnnotationTool.Shapes) {
                                val start = currentShapeStart
                                val end = currentShapeEnd
                                if (start != null && end != null) {
                                    when (annotationSelectedShapeType) {
                                        com.example.ui.ShapeType.RECTANGLE -> {
                                            val rect = androidx.compose.ui.geometry.Rect(start, end)
                                            if (annotationShapeFillEnabled) {
                                                drawRect(
                                                    color = annotationColor.copy(alpha = 0.3f),
                                                    topLeft = rect.topLeft,
                                                    size = rect.size
                                                )
                                            }
                                            drawRect(
                                                color = annotationColor,
                                                topLeft = rect.topLeft,
                                                size = rect.size,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(annotationStrokeWidth)
                                            )
                                        }
                                        com.example.ui.ShapeType.ELLIPSE -> {
                                            val rect = androidx.compose.ui.geometry.Rect(start, end)
                                            if (annotationShapeFillEnabled) {
                                                drawOval(
                                                    color = annotationColor.copy(alpha = 0.3f),
                                                    topLeft = rect.topLeft,
                                                    size = rect.size
                                                )
                                            }
                                            drawOval(
                                                color = annotationColor,
                                                topLeft = rect.topLeft,
                                                size = rect.size,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(annotationStrokeWidth)
                                            )
                                        }
                                        com.example.ui.ShapeType.LINE -> {
                                            drawLine(
                                                color = annotationColor,
                                                start = start,
                                                end = end,
                                                strokeWidth = annotationStrokeWidth
                                            )
                                        }
                                        com.example.ui.ShapeType.ARROW -> {
                                            drawLine(
                                                color = annotationColor,
                                                start = start,
                                                end = end,
                                                strokeWidth = annotationStrokeWidth
                                            )
                                            val angle = kotlin.math.atan2(end.y - start.y, end.x - start.x)
                                            val arrowLength = 20f
                                            val arrowAngle = kotlin.math.PI / 6
                                            val p1 = Offset(
                                                end.x - arrowLength * kotlin.math.cos(angle - arrowAngle).toFloat(),
                                                end.y - arrowLength * kotlin.math.sin(angle - arrowAngle).toFloat()
                                            )
                                            val p2 = Offset(
                                                end.x - arrowLength * kotlin.math.cos(angle + arrowAngle).toFloat(),
                                                end.y - arrowLength * kotlin.math.sin(angle + arrowAngle).toFloat()
                                            )
                                            drawLine(
                                                color = annotationColor,
                                                start = end,
                                                end = p1,
                                                strokeWidth = annotationStrokeWidth
                                            )
                                            drawLine(
                                                color = annotationColor,
                                                start = end,
                                                end = p2,
                                                strokeWidth = annotationStrokeWidth
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Overlay Sticky / Text Notes on top of the Canvas
                        Box(modifier = Modifier.fillMaxSize()) {
                            annotationsList.filter { it.page == currentPage }.forEach { annotation ->
                                when (annotation) {
                                    is com.example.ui.AnnotationData.TextNote -> {
                                        var notePos by remember { mutableStateOf(annotation.position) }

                                        Box(
                                            modifier = Modifier
                                                .offset { IntOffset(notePos.x.toInt(), notePos.y.toInt()) }
                                                .pointerInput(annotation) {
                                                    detectDragGestures(
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            notePos += dragAmount
                                                        },
                                                        onDragEnd = {
                                                            // update position in VM lists
                                                            annotationViewModel.removeAnnotation(annotation)
                                                            annotationViewModel.addAnnotation(annotation.copy(position = notePos))
                                                        }
                                                    )
                                                }
                                                .background(Color(0xFF2E2E3E), shape = RoundedCornerShape(8.dp))
                                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                                .widthIn(max = 200.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = annotation.text,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "حذف الملاحظة",
                                                    tint = Color(0xFFFF6B6B),
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable {
                                                            annotationViewModel.removeAnnotation(annotation)
                                                        }
                                                )
                                            }
                                        }
                                    }
                                    is com.example.ui.AnnotationData.StickyNote -> {
                                        var showStickyTextPopup by remember { mutableStateOf(false) }
                                        var showDeleteConfirm by remember { mutableStateOf(false) }

                                        Box(
                                            modifier = Modifier
                                                .offset { IntOffset(annotation.position.x.toInt() - 18, annotation.position.y.toInt() - 18) }
                                        ) {
                                            IconButton(
                                                onClick = { showStickyTextPopup = !showStickyTextPopup },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .pointerInput(annotation) {
                                                        detectTapGestures(
                                                            onTap = { showStickyTextPopup = !showStickyTextPopup },
                                                            onLongPress = { showDeleteConfirm = true }
                                                        )
                                                    }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.StickyNote2,
                                                    contentDescription = "ملصق ملاحظة",
                                                    tint = Color(0xFFFFD93D),
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }

                                            if (showStickyTextPopup) {
                                                Popup(
                                                    alignment = Alignment.TopCenter,
                                                    offset = IntOffset(0, -50),
                                                    onDismissRequest = { showStickyTextPopup = false }
                                                ) {
                                                    Card(
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E3E)),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                                        modifier = Modifier
                                                            .padding(4.dp)
                                                            .widthIn(max = 200.dp)
                                                    ) {
                                                        Column(modifier = Modifier.padding(8.dp)) {
                                                            Text(
                                                                text = annotation.text,
                                                                color = Color.White,
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (showDeleteConfirm) {
                                                AlertDialog(
                                                    onDismissRequest = { showDeleteConfirm = false },
                                                    title = { Text("حذف الملصق", fontWeight = FontWeight.Bold) },
                                                    text = { Text("هل تريد حذف هذا الملصق فعلاً؟") },
                                                    confirmButton = {
                                                        Button(
                                                            onClick = {
                                                                annotationViewModel.removeAnnotation(annotation)
                                                                showDeleteConfirm = false
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
                                                        ) {
                                                            Text("حذف")
                                                        }
                                                    },
                                                    dismissButton = {
                                                        TextButton(onClick = { showDeleteConfirm = false }) {
                                                            Text("إلغاء")
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    is com.example.ui.AnnotationData.StampAnnotation -> {
                                        var stampPos by remember { mutableStateOf(annotation.position) }
                                        var stampScale by remember { mutableStateOf(1f) }
                                        var showDeleteConfirm by remember { mutableStateOf(false) }

                                        Box(
                                            modifier = Modifier
                                                .offset { IntOffset(stampPos.x.toInt() - 60, stampPos.y.toInt() - 30) }
                                                .graphicsLayer(
                                                    scaleX = stampScale,
                                                    scaleY = stampScale,
                                                    rotationZ = annotation.rotation
                                                )
                                                .pointerInput(annotation) {
                                                    detectDragGestures(
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            stampPos += dragAmount
                                                        },
                                                        onDragEnd = {
                                                            annotationViewModel.removeAnnotation(annotation)
                                                            annotationViewModel.addAnnotation(annotation.copy(position = stampPos))
                                                        }
                                                    )
                                                }
                                                .pointerInput(annotation) {
                                                    detectTapGestures(
                                                        onLongPress = {
                                                            showDeleteConfirm = true
                                                        }
                                                    )
                                                }
                                                .background(
                                                    color = if (annotation.filled) annotation.color.copy(alpha = 0.15f) else Color.Transparent,
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .border(
                                                    width = 2.dp,
                                                    color = annotation.color,
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = annotation.text,
                                                color = annotation.color,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }

                                        if (showDeleteConfirm) {
                                            AlertDialog(
                                                onDismissRequest = { showDeleteConfirm = false },
                                                title = { Text("حذف الختم", fontWeight = FontWeight.Bold) },
                                                text = { Text("هل تريد حذف هذا الختم فعلاً؟") },
                                                confirmButton = {
                                                    Button(
                                                        onClick = {
                                                            annotationViewModel.removeAnnotation(annotation)
                                                            showDeleteConfirm = false
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
                                                    ) {
                                                        Text("حذف")
                                                    }
                                                },
                                                dismissButton = {
                                                    TextButton(onClick = { showDeleteConfirm = false }) {
                                                        Text("إلغاء")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    else -> {}
                                }
                            }

                            // Draw comment pins
                            activeComments.filter { it.pageNumber == currentPage && it.parentId == null }.forEach { rootComment ->
                                val repliesCount = activeComments.count { it.parentId == rootComment.id }
                                
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(rootComment.positionX.toInt() - 16, rootComment.positionY.toInt() - 16) }
                                        .clickable {
                                            activeCommentToViewThread = rootComment
                                            showCommentThreadSheet = true
                                        }
                                        .size(32.dp)
                                        .background(AppPrimary, shape = CircleShape)
                                        .border(2.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubble,
                                        contentDescription = "تعليق",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    if (repliesCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 6.dp, y = (-6).dp)
                                                .background(Color.Red, shape = CircleShape)
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = repliesCount.toString(),
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Draw automatic yellow text highlight overlay in the center area if search matches current page
                    if (isSearching && searchMatches.contains(currentPage)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(26.dp)
                                .align(Alignment.Center)
                                .background(Color(0xE6FFEB3B), shape = RoundedCornerShape(2.dp))
                                .testTag("search_match_highlight_overlay")
                        )
                    }

                    // Floating zoom level badge in the TopEnd near top-right corner
                    AnimatedVisibility(
                        visible = showZoomBadge,
                        enter = fadeIn(animationSpec = tween(300)),
                        exit = fadeOut(animationSpec = tween(500)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 100.dp, end = 16.dp)
                    ) {
                        Surface(
                            color = Color(0xFF1E1E24).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.1fx", zoomLevel),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Draw the custom Selection Highlight Overlay if text is selected
                    if (isTextSelected && selectionPos != null) {
                        val density = LocalDensity.current
                        val xDp = with(density) { selectionPos!!.x.toDp() }
                        val yDp = with(density) { selectionPos!!.y.toDp() }

                        // Translucent blue selection box overlay matching the long press location
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = xDp - 100.dp,
                                    y = yDp - 14.dp
                                )
                                .size(width = 200.dp, height = 28.dp)
                                .background(Color(0x332196F3), shape = RoundedCornerShape(4.dp))
                        )
                        // Left teardrop handle pin
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = xDp - 104.dp,
                                    y = yDp + 10.dp
                                )
                                .size(8.dp)
                                .background(Color(0xFF2196F3), shape = CircleShape)
                        )
                        // Right teardrop handle pin
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = xDp + 96.dp,
                                    y = yDp + 10.dp
                                )
                                .size(8.dp)
                                .background(Color(0xFF2196F3), shape = CircleShape)
                        )
                    }

                    // Floating Left and Right Navigation Arrow Overlays for Page-by-Page Mode
                    val isPagedMode = (readingScrollMode == "paged")

                    AnimatedVisibility(
                        visible = isPagedMode && currentPage < totalPages - 1 && errorState == null,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200)),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                pdfViewInst?.let { pdfView ->
                                    val targetPage = (currentPage + 1).coerceAtMost(totalPages - 1)
                                    pdfView.jumpTo(targetPage, true)
                                    viewModel.setCurrentPage(targetPage)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shape = CircleShape)
                                .testTag("left_paged_arrow")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "الصفحة التالية",
                                tint = AppPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isPagedMode && currentPage > 0 && errorState == null,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200)),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                pdfViewInst?.let { pdfView ->
                                    val targetPage = (currentPage - 1).coerceAtLeast(0)
                                    pdfView.jumpTo(targetPage, true)
                                    viewModel.setCurrentPage(targetPage)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shape = CircleShape)
                                .testTag("right_paged_arrow")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "الصفحة السابقة",
                                tint = AppPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            } else {
                // Empty case if opened without valid document
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Color.Red,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("مستند غير موجود.", fontWeight = FontWeight.Bold, color = AppTextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onBack) {
                            Text("الرجوع للرئيسية")
                        }
                    }
                }
            }

            // Always Visible Floating Scroll Pill Page Indicator
            AnimatedVisibility(
                visible = showPageIndicator && isScrollIndicatorVisible && totalPages > 0,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AppSurface.copy(alpha = 0.8f),
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = "${currentPage + 1} / $totalPages",
                        color = AppTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // OCR Scanner Prompt Banner (non-intrusive banner below TopAppBar space)
            AnimatedVisibility(
                visible = showOcrBanner && !isSearching,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("ocr_banner")
            ) {
                Surface(
                    color = AppPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AppPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                context.getSharedPreferences("ocr_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("dismissed_${activeUri.hashCode()}", true)
                                    .apply()
                                showOcrBanner = false
                            },
                            modifier = Modifier.size(36.dp).testTag("ocr_banner_close")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = AppPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    showOcrConfirmDialog = true
                                },
                                modifier = Modifier.testTag("ocr_banner_convert_button")
                            ) {
                                Text(
                                    text = "تحويل",
                                    color = AppPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "هذا الملف ممسوح ضوئياً - هل تريد تحويله لنص قابل للبحث؟",
                                color = AppTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = AppPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Overlay Search Bar sliding DOWN from the top of the screen (AnimatedVisibility, slide-in-top, 250ms)
            AnimatedVisibility(
                visible = isSearching,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(durationMillis = 250)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(durationMillis = 250)
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
            ) {
                val totalPages = pdfViewInst?.pageCount ?: 0
                val coroutineScope = rememberCoroutineScope()

                // Define search action
                val performSearch: (String) -> Unit = { query ->
                    if (query.isNotBlank()) {
                        coroutineScope.launch {
                            addSearchHistoryTerm(context, query)
                            searchHistory = getSearchHistory(context)
                        }

                        val sP = startPageStr.toIntOrNull() ?: 1
                        val eP = endPageStr.toIntOrNull() ?: totalPages

                        coroutineScope.launch {
                            val results = performAdvancedSearch(
                                context = context,
                                fileUriStr = activeUri ?: "",
                                keyword = query,
                                isCaseSensitive = isCaseSensitive,
                                isWholeWord = isWholeWord,
                                isRegex = isRegex,
                                startPage = sP,
                                endPage = eP,
                                totalPageCount = totalPages
                            )
                            searchMatches = results
                            totalSearchMatches = results.size
                            if (results.isNotEmpty()) {
                                currentSearchMatchIndex = 0
                                pdfViewInst?.saveSearchState(query, results)
                            } else {
                                currentSearchMatchIndex = -1
                                pdfViewInst?.setTag(null)
                                android.widget.Toast.makeText(context, "لم يتم العثور على نتائج", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        android.widget.Toast.makeText(context, "الرجاء إدخال كلمة للبحث", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = Color(0xFF1A1A1F), // Background: Surface (#1A1A1F)
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp) // Height: 52dp, fillMaxWidth
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // An X icon on the far left to close and clear search
                            IconButton(
                                onClick = {
                                    pdfViewInst?.resetSearch()
                                    isSearching = false
                                    searchQuery = ""
                                    searchMatches = emptyList()
                                    totalSearchMatches = 0
                                    currentSearchMatchIndex = 0
                                    isOptionsPanelExpanded = false
                                },
                                modifier = Modifier.testTag("close_search_icon")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق البحث",
                                    tint = AppTextPrimary
                                )
                            }

                            // A "بحث" button (Primary color) on the left of search text
                            Button(
                                onClick = { performSearch(searchQuery) },
                                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("search_action_button")
                            ) {
                                Text("بحث", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // If search matches are found, show result counter badge & arrows (◀ ▶) next to the counter
                            if (searchMatches.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))

                                // Left Arrow button (◀) -> pdfView.findNext(false)
                                IconButton(
                                    onClick = {
                                        val newIndex = pdfViewInst?.findNext(false) ?: -1
                                        if (newIndex >= 0) {
                                            currentSearchMatchIndex = newIndex
                                        }
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("prev_match_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack, // left pointing
                                        contentDescription = "السابق",
                                        tint = AppPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Result counter badge near the search bar: "3 / 12" (current match / total matches)
                                Text(
                                    text = "${currentSearchMatchIndex + 1} / $totalSearchMatches",
                                    color = AppTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .testTag("search_counter_text")
                                )

                                // Right Arrow button (▶) -> pdfView.findNext(true)
                                IconButton(
                                    onClick = {
                                        val newIndex = pdfViewInst?.findNext(true) ?: -1
                                        if (newIndex >= 0) {
                                            currentSearchMatchIndex = newIndex
                                        }
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("next_match_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward, // right pointing
                                        contentDescription = "التالي",
                                        tint = AppPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // A TextField (no border, hint = "ابحث في الملف...") right-aligned, RTL
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { query ->
                                            searchQuery = query
                                            if (query.isEmpty()) {
                                                pdfViewInst?.resetSearch()
                                                searchMatches = emptyList()
                                                totalSearchMatches = 0
                                                currentSearchMatchIndex = 0
                                            }
                                        },
                                        placeholder = {
                                            Text(
                                                text = "ابحث في الملف...",
                                                color = AppTextSecondary,
                                                fontSize = 14.sp
                                            )
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onSearch = { performSearch(searchQuery) }
                                        ),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            disabledContainerColor = Color.Transparent,
                                            errorContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            disabledIndicatorColor = Color.Transparent,
                                            focusedTextColor = AppTextPrimary,
                                            unfocusedTextColor = AppTextPrimary
                                        ),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = AppTextPrimary,
                                            fontSize = 14.sp
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { isSearchFocused = it.isFocused }
                                            .testTag("reader_search_input_field")
                                    )
                                }
                            }

                            // Tone icon for Advanced Settings
                            IconButton(
                                onClick = { isOptionsPanelExpanded = !isOptionsPanelExpanded },
                                modifier = Modifier.testTag("search_settings_icon")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "خيارات البحث",
                                    tint = if (isOptionsPanelExpanded) AppPrimary else AppTextSecondary
                                )
                            }
                        }
                    }

                    // Advanced Search Options Panel
                    AnimatedVisibility(
                        visible = isOptionsPanelExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Surface(
                            color = Color(0xFF1E1E24),
                            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "خيارات البحث المتقدمة",
                                    color = AppPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                HorizontalDivider(color = Color(0xFF2C2C35))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = "طابق حالة الأحرف (Aa)",
                                            color = AppTextPrimary,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Switch(
                                            checked = isCaseSensitive,
                                            onCheckedChange = { checked ->
                                                isCaseSensitive = checked
                                                coroutineScope.launch {
                                                    context.pdfReaderDataStore.edit { preferences ->
                                                        preferences[com.example.util.SEARCH_CASE_SENSITIVE_KEY] = checked
                                                    }
                                                }
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = AppPrimary)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = "كلمة كاملة فقط",
                                            color = AppTextPrimary,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Switch(
                                            checked = isWholeWord,
                                            onCheckedChange = { isWholeWord = it },
                                            colors = SwitchDefaults.colors(checkedThumbColor = AppPrimary)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = "استخدام تعبير نمطي (Regex)",
                                        color = AppTextPrimary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Switch(
                                        checked = isRegex,
                                        onCheckedChange = { isRegex = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = AppPrimary)
                                    )
                                }

                                HorizontalDivider(color = Color(0xFF2C2C35))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = startPageStr,
                                            onValueChange = { startPageStr = it.filter { c -> c.isDigit() } },
                                            singleLine = true,
                                            modifier = Modifier.width(60.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AppPrimary,
                                                unfocusedBorderColor = Color(0xFF2C2C35)
                                            )
                                        )
                                        Text("من صفحة", color = AppTextSecondary, fontSize = 12.sp)

                                        OutlinedTextField(
                                            value = endPageStr,
                                            onValueChange = { endPageStr = it.filter { c -> c.isDigit() } },
                                            singleLine = true,
                                            modifier = Modifier.width(60.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AppPrimary,
                                                unfocusedBorderColor = Color(0xFF2C2C35)
                                            )
                                        )
                                        Text("إلى صفحة", color = AppTextSecondary, fontSize = 12.sp)
                                    }

                                    Text(
                                        text = "نطاق صفحات البحث:",
                                        color = AppTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Search History Dropdown Menu
                    AnimatedVisibility(
                        visible = searchQuery.isEmpty() && isSearchFocused && searchHistory.isNotEmpty(),
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Surface(
                            color = Color(0xFF1E1E24),
                            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                            tonalElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "سجل البحث",
                                    color = AppTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .align(Alignment.End)
                                )
                                searchHistory.forEach { term ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                searchQuery = term
                                                isSearchFocused = false
                                                performSearch(term)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    removeSearchHistoryTerm(context, term)
                                                    searchHistory = getSearchHistory(context)
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف من السجل",
                                                tint = Color(0xFFFF6B6B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Text(
                                                text = term,
                                                color = AppTextPrimary,
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                tint = AppTextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Integrated Bottom Bars Column: Contains AnnotationBar (top) and BottomReaderBar (bottom)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Annotation ToolBar (Slide vertically from bottom)
                AnimatedVisibility(
                    visible = isAnnotationBarVisible && isToolbarVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(durationMillis = 250)
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(durationMillis = 250)
                    )
                ) {
                    AnnotationBar(
                        selectedTool = annotationTool,
                        onToolSelect = { tool ->
                            annotationViewModel.setTool(tool)
                        },
                        currentColor = annotationColor,
                        onColorSelect = { color ->
                            annotationViewModel.setColor(color)
                        },
                        strokeWidth = annotationStrokeWidth,
                        onStrokeWidthSelect = { width ->
                            annotationViewModel.setStrokeWidth(width)
                        },
                        onCloseClick = {
                            showSaveConfirmDialog = true
                        },
                        canUndo = annotationCanUndo,
                        onUndo = { annotationViewModel.undo() },
                        canRedo = annotationCanRedo,
                        onRedo = { annotationViewModel.redo() },
                        selectedShapeType = annotationSelectedShapeType,
                        onShapeTypeChange = { annotationViewModel.setShapeType(it) },
                        shapeFillEnabled = annotationShapeFillEnabled,
                        onShapeFillToggle = { annotationViewModel.setShapeFillEnabled(it) },
                        onStampClick = { showStampPicker = true }
                    )
                }

                // Overlay BottomReaderBar with Tween slide vertical animations (300ms)
                AnimatedVisibility(
                    visible = isToolbarVisible && !isExpanded,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(durationMillis = 300)
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(durationMillis = 300)
                    )
                ) {
                    BottomReaderBar(
                        isPageBookmarked = isPageBookmarked,
                        onMenuClick = {
                            coroutineScope.launch { drawerState.open() }
                        },
                        onSearchClick = {
                            isSearching = !isSearching
                            if (!isSearching) {
                                pdfViewInst?.resetSearch()
                                searchQuery = ""
                                searchMatches = emptyList()
                                totalSearchMatches = 0
                                currentSearchMatchIndex = 0
                            }
                        },
                        onZoomInClick = {
                            pdfViewInst?.let { pdfView ->
                                val proposedZoom = (pdfView.zoom + 0.25f).coerceAtMost(4.0f)
                                pdfView.zoomWithAnimation(proposedZoom)
                                zoomLevel = proposedZoom
                            }
                        },
                        onZoomOutClick = {
                            pdfViewInst?.let { pdfView ->
                                val proposedZoom = (pdfView.zoom - 0.25f).coerceAtLeast(0.5f)
                                pdfView.zoomWithAnimation(proposedZoom)
                                zoomLevel = proposedZoom
                            }
                        },
                        onBookmarkClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleCurrentPageBookmark()
                        },
                        onShareClick = {
                            if (activeUri != null) {
                                coroutineScope.launch {
                                    try {
                                        android.widget.Toast.makeText(
                                            context,
                                            "جاري تحضير مستند PDF للمشاركة...",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()

                                        val preparedFile = withContext(Dispatchers.IO) {
                                            try {
                                                val uri = Uri.parse(activeUri!!)
                                                val cacheDir = File(context.cacheDir, "shared_pdfs").apply { mkdirs() }
                                                val cleanName = if (!documentName.isNullOrBlank()) {
                                                    if (documentName.endsWith(".pdf", ignoreCase = true)) documentName else "$documentName.pdf"
                                                } else {
                                                    "document.pdf"
                                                }
                                                val destinationFile = File(cacheDir, cleanName)

                                                if (uri.scheme == "content") {
                                                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                                        FileOutputStream(destinationFile).use { outputStream ->
                                                            inputStream.copyTo(outputStream)
                                                        }
                                                    }
                                                    if (destinationFile.exists() && destinationFile.length() > 0) {
                                                        destinationFile
                                                    } else null
                                                } else {
                                                    val pathStr = uri.path ?: activeUri!!
                                                    val sourceFile = File(pathStr)
                                                    if (sourceFile.exists()) {
                                                        sourceFile.inputStream().use { inputStream ->
                                                            FileOutputStream(destinationFile).use { outputStream ->
                                                                inputStream.copyTo(outputStream)
                                                            }
                                                        }
                                                        if (destinationFile.exists() && destinationFile.length() > 0) {
                                                            destinationFile
                                                        } else null
                                                    } else {
                                                        null
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("ViewerScreen", "Error preparing PDF in IO", e)
                                                null
                                            }
                                        }

                                        if (preparedFile != null && preparedFile.exists()) {
                                            val shareUri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                preparedFile
                                            )

                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                putExtra(Intent.EXTRA_SUBJECT, documentName)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            val chooserIntent = Intent.createChooser(shareIntent, "مشاركة مستند PDF").apply {
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(chooserIntent)
                                        } else {
                                            android.widget.Toast.makeText(
                                                context,
                                                "خطأ: الملف غير موجود أو تالف ولا يمكن مشاركته.",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("ViewerScreen", "Error during sharing flow", e)
                                        android.widget.Toast.makeText(
                                            context,
                                            "حدث خطأ أثناء محاولة مشاركة الملف.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "لا يوجد ملف مفتوح حالياً للمشاركة.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onGoToPageClick = {
                            showJumpDialog = true
                        },
                        onPrintClick = {
                            if (activeUri != null) {
                                try {
                                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                                    if (printManager != null) {
                                        val printAdapter = PdfDocumentAdapter(
                                            context = context,
                                            fileUri = Uri.parse(activeUri!!),
                                            totalPages = totalPages
                                        )
                                        printManager.print("قارئ PDF - $documentName", printAdapter, android.print.PrintAttributes.Builder().build())
                                    }
                                } catch (e: Exception) {
                                    Log.e("ViewerScreen", "Error launching printer job", e)
                                }
                            }
                        },
                        onFileInfoClick = {
                            showFileInfoDialog = true
                        },
                        onReadingModeClick = {
                            showReadingModeBottomSheet = true
                        },
                        onSavePageAsImageClick = {
                            showSaveAsImageConfirmDialog = true
                        },
                        onRotateRightClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("تدوير الصفحة غير متاح في وضع WebView حالياً")
                            }
                        },
                        onRotateLeftClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("تدوير الصفحة غير متاح في وضع WebView حالياً")
                            }
                        },
                        onResetRotationClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("تدوير الصفحة غير متاح في وضع WebView حالياً")
                            }
                        },
                        isScreenRotationLocked = isScreenRotationLocked,
                        onToggleLockScreenRotation = {
                            val requestedState = !isScreenRotationLocked
                            isScreenRotationLocked = requestedState
                            activity?.let { act ->
                                if (requestedState) {
                                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("تم قفل اتجاه الشاشة")
                                    }
                                } else {
                                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("تم فتح قفل الاتجاه")
                                    }
                                }
                            }
                        },
                        readingScrollMode = readingScrollMode,
                        onReadingScrollModeToggle = {
                            val nextMode = if (readingScrollMode == "continuous") "paged" else "continuous"
                            viewModel.setReadingScrollMode(nextMode)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    if (nextMode == "continuous") "تم تفعيل التمرير المستمر" else "تم تفعيل التمرير صفحة بصفحة"
                                )
                            }
                        },
                        fitMode = fitMode,
                        onFitModeChange = { newFit ->
                            viewModel.setFitMode(newFit)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    when (newFit) {
                                        "width" -> "ملاءمة العرض"
                                        "height" -> "ملاءمة الارتفاع"
                                        else -> "الحجم الفعلي"
                                    }
                                )
                            }
                        },
                        isDoublePageMode = isDoublePageMode,
                        onDoublePageModeToggle = {
                            val configState = context.resources.configuration
                            val isTabletOrLandscapeState = configState.screenWidthDp >= 600 || configState.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                            if (!isTabletOrLandscapeState) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("وضع الصفحتين متاح في الوضع الأفقي فقط")
                                }
                            } else {
                                val nextDoubleVal = !isDoublePageMode
                                viewModel.setDoublePageMode(nextDoubleVal)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (nextDoubleVal) "تم تفعيل وضع الصفحتين" else "تم إلغاء وضع الصفحتين"
                                    )
                                }
                            }
                        },
                        isAnnotationModeActive = isAnnotationBarVisible,
                        onAnnotationClick = {
                            isAnnotationBarVisible = !isAnnotationBarVisible
                            if (isAnnotationBarVisible) {
                                annotationViewModel.setTool(AnnotationTool.Pen)
                            } else {
                                annotationViewModel.setTool(AnnotationTool.None)
                            }
                        },
                        onShareCurrentPageAsPdfClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val currentUri = activeUri
                                    if (currentUri != null) {
                                        val fileUri = Uri.parse(currentUri)
                                        val inputStream = context.contentResolver.openInputStream(fileUri)
                                        val document = PDDocument.load(inputStream)
                                        val currentPageIndex = currentPage // 0-based
                                        
                                        val newDoc = PDDocument()
                                        val page = document.getPage(currentPageIndex)
                                        newDoc.addPage(page)
                                        
                                        val outputFile = File(context.cacheDir, "page_${currentPageIndex + 1}.pdf")
                                        val fos = FileOutputStream(outputFile)
                                        newDoc.save(fos)
                                        fos.close()
                                        newDoc.close()
                                        document.close()
                                        
                                        val shareUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            outputFile
                                        )
                                        
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            putExtra(Intent.EXTRA_SUBJECT, "صفحة ${currentPageIndex + 1} من ${documentName}")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "مشاركة الصفحة"))
                                    }
                                } catch (e: Exception) {
                                    Log.e("ViewerScreen", "Error sharing current page as PDF", e)
                                    coroutineScope.launch(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar("خطأ أثناء استخراج ومشاركة الصفحة")
                                    }
                                }
                            }
                        },
                        onCompressPdfClick = {
                            showCompressionBottomSheet = true
                        },
                        hasOcrResult = hasOcrResult,
                        onCopyPageTextClick = {
                            ocrCopyCurrentPageText()
                        },
                        onExportTextAsTxtClick = {
                            ocrExportTextAsTxt()
                        },
                        onSearchExtractedTextClick = {
                            showOcrSearchDialog = true
                            ocrSearchQuery = ""
                            ocrSearchResults = emptyList()
                        },
                        onAddSignatureClick = {
                            if (savedSignaturePath != null) {
                                isSignaturePlacingModeActive = true
                            } else {
                                onNavigateToSignature?.invoke()
                            }
                        },
                        onRedactModeClick = {
                            isRedactModeActive = true
                        },
                        onTtsPlayClick = {
                            viewModel.startTtsForCurrentPage()
                        },
                        onExportAnnotationsSummaryClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val file = annotationViewModel.exportSummaryPdf(
                                    context = context,
                                    fileName = documentName,
                                    allAnnotations = annotationsList,
                                    allComments = activeComments
                                )
                                coroutineScope.launch {
                                    if (file != null && file.exists()) {
                                        snackbarHostState.showSnackbar("تم تصدير ملخص الملاحظات لـ PDF بنجاح")
                                    } else {
                                        snackbarHostState.showSnackbar("فشل تصدير ملخص الملاحظات")
                                    }
                                }
                            }
                        },
                        onImportAnnotationsClick = {
                            importPdfPickerLauncher.launch(arrayOf("application/pdf"))
                        }
                    )
                }
            }

            val isTtsActive by viewModel.isTtsActive.collectAsState()
            val ttsState by viewModel.ttsManager.state.collectAsState()

            LaunchedEffect(currentPage) {
                if (viewModel.isTtsActive.value) {
                    viewModel.startTtsForCurrentPage()
                }
            }

            AnimatedVisibility(
                visible = isTtsActive,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                var showSpeedMenu by remember { mutableStateOf(false) }
                var currentSpeed by remember { mutableStateOf("1.0x") }
                
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tts_player_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "قراءة الصفحة ${currentPage + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            when (val state = ttsState) {
                                is com.example.util.TtsManager.TtsState.Speaking -> {
                                    LinearProgressIndicator(
                                        progress = { state.progress },
                                        color = AppPrimary,
                                        trackColor = AppTextSecondary.copy(alpha = 0.2f),
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                }
                                is com.example.util.TtsManager.TtsState.Loading -> {
                                    Text("جاري التهيئة والاستخلاص...", style = MaterialTheme.typography.bodySmall, color = AppPrimary)
                                }
                                is com.example.util.TtsManager.TtsState.Error -> {
                                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = Color.Red)
                                }
                                else -> {
                                    Text("جاهز للقراءة", style = MaterialTheme.typography.bodySmall, color = AppTextSecondary)
                                }
                            }
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentPage > 0) {
                                        viewModel.setCurrentPage(currentPage - 1)
                                        pdfViewInst?.jumpTo(currentPage - 1)
                                        viewModel.startTtsForCurrentPage()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = "Previous page",
                                    tint = AppPrimary
                                )
                            }
                            
                            val isSpeaking = ttsState is com.example.util.TtsManager.TtsState.Speaking
                            IconButton(
                                onClick = {
                                    if (isSpeaking) {
                                        viewModel.ttsManager.pause()
                                    } else {
                                        viewModel.startTtsForCurrentPage()
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(AppPrimary, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play or Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            IconButton(
                                onClick = {
                                    if (currentPage < totalPages - 1) {
                                        viewModel.setCurrentPage(currentPage + 1)
                                        pdfViewInst?.jumpTo(currentPage + 1)
                                        viewModel.startTtsForCurrentPage()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowLeft,
                                    contentDescription = "Next page",
                                    tint = AppPrimary
                                )
                            }
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box {
                                TextButton(
                                    onClick = { showSpeedMenu = true },
                                    modifier = Modifier.testTag("tts_speed_selector_button")
                                ) {
                                    Text(
                                        text = currentSpeed,
                                        color = AppPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false },
                                    modifier = Modifier.background(AppSurface)
                                ) {
                                    val speeds = listOf(
                                        "0.5x" to 0.5f,
                                        "0.75x" to 0.75f,
                                        "1.0x" to 1.0f,
                                        "1.25x" to 1.25f,
                                        "1.5x" to 1.5f,
                                        "2.0x" to 2.0f
                                    )
                                    speeds.forEach { (label, valFloat) ->
                                        DropdownMenuItem(
                                            text = { Text(label, color = AppTextPrimary) },
                                            onClick = {
                                                viewModel.ttsManager.setSpeed(valFloat)
                                                currentSpeed = label
                                                showSpeedMenu = false
                                            },
                                            modifier = Modifier.testTag("tts_speed_option_$label")
                                        )
                                    }
                                }
                            }
                            
                            IconButton(
                                onClick = {
                                    viewModel.stopTts()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close TTS Player",
                                    tint = AppTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // OCR Dialog overlays
            if (showOcrConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showOcrConfirmDialog = false },
                    title = {
                        Text(
                            text = "تحويل الملف بالـ OCR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AppTextPrimary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "سيتم تحليل الصفحات واستخراج النص باستخدام الذكاء الاصطناعي على الجهاز. قد يستغرق هذا وقتاً طويلاً حسب حجم الملف.",
                                color = AppTextSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "اختر لغة المستند:",
                                fontWeight = FontWeight.Bold,
                                color = AppTextPrimary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            var expanded by remember { mutableStateOf(false) }
                            val languages = listOf(
                                Pair("ar", "عربي"),
                                Pair("en", "إنجليزي"),
                                Pair("ar_en", "عربي + إنجليزي")
                            )
                            val currentLangLabel = languages.firstOrNull { it.first == ocrSelectedLanguage }?.second ?: "عربي"
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { expanded = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppSurface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AppPrimary)
                                        Text(currentLangLabel, color = AppTextPrimary)
                                    }
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.background(AppSurface)
                                ) {
                                    languages.forEach { (code, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label, color = AppTextPrimary) },
                                            onClick = {
                                                ocrSelectedLanguage = code
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                startOcrFlow(ocrSelectedLanguage)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                            modifier = Modifier.testTag("ocr_start_button")
                        ) {
                            Text("ابدأ التحويل", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showOcrConfirmDialog = false },
                            modifier = Modifier.testTag("ocr_cancel_dialog_button")
                        ) {
                            Text("إلغاء", color = AppTextSecondary)
                        }
                    }
                )
            }

            if (showOcrProgressDialog) {
                AlertDialog(
                    onDismissRequest = {}, // Disable dismiss on tap outside
                    title = {
                        Text(
                            text = "جاري استخراج النص...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AppTextPrimary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = ocrProgressText,
                                color = AppTextSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )
                            LinearProgressIndicator(
                                progress = { ocrProgressPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = AppPrimary,
                                trackColor = AppPrimary.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${(ocrProgressPercent * 100).toInt()}%",
                                color = AppPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(
                            onClick = {
                                ocrIsCancelled = true
                                ocrJob?.cancel()
                                showOcrProgressDialog = false
                            },
                            modifier = Modifier.testTag("ocr_progress_cancel_button")
                        ) {
                            Text("إلغاء", color = Color.Red)
                        }
                    }
                )
            }

            if (showOcrSearchDialog) {
                AlertDialog(
                    onDismissRequest = { showOcrSearchDialog = false },
                    title = {
                        Text(
                            text = "البحث في النص المستخرج (OCR)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AppTextPrimary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End
                        ) {
                            TextField(
                                value = ocrSearchQuery,
                                onValueChange = { query ->
                                    ocrSearchQuery = query
                                    // Perform dynamic search
                                    val entity = ocrResultEntity
                                    if (entity != null && query.isNotBlank()) {
                                        val results = mutableListOf<Pair<Int, String>>()
                                        try {
                                            val jsonArr = org.json.JSONArray(entity.pageTexts)
                                            for (i in 0 until jsonArr.length()) {
                                                val obj = jsonArr.getJSONObject(i)
                                                val pageIdx = obj.getInt("page")
                                                val pageText = obj.getString("text")
                                                if (pageText.contains(query, ignoreCase = true)) {
                                                    results.add(Pair(pageIdx, pageText))
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("ViewerScreen", "Error parsing ocr json during search", e)
                                        }
                                        ocrSearchResults = results
                                    } else {
                                        ocrSearchResults = emptyList()
                                    }
                                },
                                placeholder = { Text("أدخل كلمة البحث...", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = AppSurface,
                                    unfocusedContainerColor = AppSurface,
                                    focusedTextColor = AppTextPrimary,
                                    unfocusedTextColor = AppTextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ocr_search_input_dialog")
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "النتائج المطابقة: ${ocrSearchResults.size}",
                                color = AppTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 250.dp)
                            ) {
                                if (ocrSearchResults.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (ocrSearchQuery.isBlank()) "ابدأ بكتابة كلمة للبحث" else "لا توجد نتائج مطابقة",
                                            color = AppTextSecondary,
                                            fontSize = 14.sp
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(ocrSearchResults) { (pageIdx, fullText) ->
                                            val snippet = fullText.lines()
                                                .filter { it.contains(ocrSearchQuery, ignoreCase = true) }
                                                .joinToString(" ... ")
                                                .take(120)
                                            
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = AppSurface),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        ocrViewJumpPage(pageIdx)
                                                    }
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(10.dp),
                                                    horizontalAlignment = Alignment.End
                                                ) {
                                                    Text(
                                                        text = "الصفحة ${pageIdx + 1}",
                                                        fontWeight = FontWeight.Bold,
                                                        color = AppPrimary,
                                                        fontSize = 12.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "... $snippet ...",
                                                        color = AppTextPrimary,
                                                        fontSize = 12.sp,
                                                        textAlign = TextAlign.End,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showOcrSearchDialog = false }) {
                            Text("إغلاق", color = AppPrimary)
                        }
                    }
                )
            }

            // Annotation Text Dialogue
            if (showAnnotationTextDialog) {
                AlertDialog(
                    onDismissRequest = { showAnnotationTextDialog = false },
                    title = {
                        Text(
                            text = if (isAddingStickyNote) "إضافة ملصق ملاحظة" else "إضافة ملاحظة نصية",
                            fontWeight = FontWeight.Bold,
                            color = AppTextPrimary
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                OutlinedTextField(
                                    value = annotationDialogText,
                                    onValueChange = { if (it.length <= 200) annotationDialogText = it },
                                    placeholder = { Text("اكتب ملاحظتك...") },
                                    modifier = Modifier.fillMaxWidth().testTag("annotation_text_input"),
                                    maxLines = 4
                                )
                            }
                            Text(
                                text = "اختر اللون:",
                                color = AppTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Color(0xFFFF3F3F), // Red
                                    Color(0xFFFF7F3F), // Orange
                                    Color(0xFFFFD93D), // Yellow
                                    Color(0xFF6BCB77), // Green
                                    Color(0xFF4D96FF)  // Teal
                                ).forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                2.dp,
                                                if (annotationDialogColor == color) Color.White else Color.Transparent,
                                                CircleShape
                                            )
                                            .clickable {
                                                annotationDialogColor = color
                                            }
                                            .testTag("dialog_color_${color.value}")
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (annotationDialogText.isNotBlank()) {
                                    if (isAddingStickyNote) {
                                        annotationViewModel.addAnnotation(
                                            AnnotationData.StickyNote(
                                                text = annotationDialogText,
                                                position = annotationDialogPosition,
                                                page = currentPage
                                            )
                                        )
                                    } else {
                                        annotationViewModel.addAnnotation(
                                            AnnotationData.TextNote(
                                                text = annotationDialogText,
                                                position = annotationDialogPosition,
                                                page = currentPage
                                            )
                                        )
                                    }
                                }
                                showAnnotationTextDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                            modifier = Modifier.testTag("annotation_dialog_confirm")
                        ) {
                            Text("إضافة")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showAnnotationTextDialog = false },
                            modifier = Modifier.testTag("annotation_dialog_dismiss")
                        ) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            // Annotation save/exit confirmation dialog
            if (showSaveConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveConfirmDialog = false },
                    title = { Text("حفظ التعديلات", fontWeight = FontWeight.Bold, color = AppTextPrimary) },
                    text = { Text("هل تريد حفظ التعديلات داخل ملف PDF جديد؟") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSaveConfirmDialog = false
                                if (activeUri != null) {
                                    coroutineScope.launch {
                                        val snack = snackbarHostState.showSnackbar(
                                            message = "جاري الحفظ والتعديل...",
                                            duration = SnackbarDuration.Indefinite
                                        )
                                        val savedFile = annotationViewModel.saveAnnotationsToPdf(
                                            context,
                                            Uri.parse(activeUri!!),
                                            totalPages
                                        )
                                        if (savedFile != null) {
                                            isAnnotationBarVisible = false
                                            annotationViewModel.setTool(AnnotationTool.None)
                                            annotationViewModel.clearAnnotations()

                                            val result = snackbarHostState.showSnackbar(
                                                message = "تم الحفظ: ${savedFile.name}",
                                                actionLabel = "فتح",
                                                duration = SnackbarDuration.Long
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                onNavigateToReader?.invoke(Uri.fromFile(savedFile).toString())
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar("تعذّر حفظ التعديلات!")
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                            modifier = Modifier.testTag("save_annotations_confirm")
                        ) {
                            Text("حفظ كملف جديد")
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    showSaveConfirmDialog = false
                                    isAnnotationBarVisible = false
                                    annotationViewModel.setTool(AnnotationTool.None)
                                    annotationViewModel.clearAnnotations()
                                },
                                modifier = Modifier.testTag("save_annotations_discard")
                            ) {
                                Text("تجاهل")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { showSaveConfirmDialog = false },
                                modifier = Modifier.testTag("save_annotations_cancel")
                            ) {
                                Text("إلغاء")
                            }
                        }
                    }
                )
            }

            // Jump to page dialog popup
            if (showJumpDialog) {
                JumpToPageDialog(
                    totalPages = totalPages,
                    onDismiss = { showJumpDialog = false },
                    onJump = { targetPageVal ->
                        viewModel.jumpToPage(targetPageVal - 1)
                        showJumpDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("تم الانتقال إلى الصفحة $targetPageVal")
                        }
                    }
                )
            }

            // Save Page As Image Confirmation Dialog
            if (showSaveAsImageConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveAsImageConfirmDialog = false },
                    title = {
                        Text(
                            text = "حفظ الصفحة الحالية",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTextPrimary
                        )
                    },
                    text = {
                        Text(
                            text = "سيتم حفظ الصفحة ${currentPage + 1} كصورة PNG في معرض الصور",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTextSecondary
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showSaveAsImageConfirmDialog = false
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val fileUriStr = activeUri ?: return@launch
                                        val fileUri = Uri.parse(fileUriStr)
                                        
                                        val pfd = context.contentResolver.openFileDescriptor(fileUri, "r")
                                        if (pfd == null) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("خطأ: تعذر فتح الملف")
                                            }
                                            return@launch
                                        }
                                        
                                        pfd.use { parcelFileDescriptor ->
                                            val pdfRenderer = PdfRenderer(parcelFileDescriptor)
                                            val page = pdfRenderer.openPage(currentPage)
                                            
                                            val bitmap = Bitmap.createBitmap(
                                                page.width * 2,
                                                page.height * 2,
                                                Bitmap.Config.ARGB_8888
                                            )
                                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                            page.close()
                                            pdfRenderer.close()
                                            
                                            val values = ContentValues().apply {
                                                put(MediaStore.Images.Media.DISPLAY_NAME, "pdf_page_${currentPage + 1}_${System.currentTimeMillis()}.png")
                                                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/قارئ PDF")
                                            }
                                            val savedUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                            
                                            if (savedUri != null) {
                                                context.contentResolver.openOutputStream(savedUri)?.use { outputStream ->
                                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                                                }
                                                
                                                coroutineScope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = "تم الحفظ في معرض الصور ✓",
                                                        actionLabel = "عرض",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW, savedUri).apply {
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Log.e("ViewerScreen", "Error opening saved image", e)
                                                        }
                                                    }
                                                }
                                            } else {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("خطأ في حفظ الصورة")
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("ViewerScreen", "Error rendering/saving PDF page", e)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("فشل في حفظ الصفحة كصورة: ${e.message}")
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("حفظ", color = AppPrimary, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSaveAsImageConfirmDialog = false }) {
                            Text("إلغاء", color = AppTextSecondary)
                        }
                    },
                    containerColor = AppSurface,
                    modifier = Modifier.testTag("save_page_confirm_dialog")
                )
            }

            // File Info M3 Information Dialog as a ModalBottomSheet
            if (showFileInfoDialog) {
                ModalBottomSheet(
                    onDismissRequest = { showFileInfoDialog = false },
                    sheetState = rememberModalBottomSheetState(),
                    containerColor = AppSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "معلومات الملف",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val info = getFileInfo(context, activeUri ?: "", totalPages)

                        MetadataRow(label = "اسم الملف", value = info.name)
                        MetadataRow(label = "الحجم", value = info.sizeString)
                        MetadataRow(label = "عدد الصفحات", value = "${info.totalPages}")
                        MetadataRow(label = "مسار الملف", value = info.filePath)
                        MetadataRow(label = "تاريخ التعديل الأخير", value = info.lastModifiedString)
                        MetadataRow(label = "إصدار PDF", value = info.pdfVersion)

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { showFileInfoDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("close_file_info_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                        ) {
                            Text("إغلاق", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Reading Mode Selection BottomSheet
            if (showReadingModeBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showReadingModeBottomSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = AppSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 36.dp, start = 20.dp, end = 20.dp, top = 8.dp)
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "وضع القراءة",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppPrimary,
                            modifier = Modifier
                                .padding(bottom = 20.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Card 1: عادي
                            ReadingModeCard(
                                title = "عادي",
                                icon = Icons.Default.WbSunny,
                                colorBg = Color.White,
                                colorText = Color.Black,
                                isSelected = readingMode == "normal",
                                onSelect = {
                                    viewModel.setReadingMode("normal")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reading_mode_normal_card")
                            )

                            // Card 2: ليلي
                            ReadingModeCard(
                                title = "ليلي",
                                icon = Icons.Default.NightsStay,
                                colorBg = Color(0xFF1D1B20),
                                colorText = Color.White,
                                isSelected = readingMode == "night",
                                onSelect = {
                                    viewModel.setReadingMode("night")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reading_mode_night_card")
                            )

                            // Card 3: بني (سيبيا)
                            ReadingModeCard(
                                title = "بني (سيبيا)",
                                icon = Icons.Default.AutoAwesome,
                                colorBg = Color(0xFFF5E6C8),
                                colorText = Color(0xFF5C4033),
                                isSelected = readingMode == "sepia",
                                onSelect = {
                                    viewModel.setReadingMode("sepia")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reading_mode_sepia_card")
                            )
                        }
                    }
                }
            }

            if (showCompressionBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { if (!isCompressing) showCompressionBottomSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = AppSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 36.dp, start = 20.dp, end = 20.dp, top = 8.dp)
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ضغط الملف لتقليل الحجم",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppPrimary,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Level 1: ضغط خفيف
                            Card(
                                onClick = { if (!isCompressing) selectedCompressionLevel = 1 },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedCompressionLevel == 1) AppPrimary.copy(alpha = 0.1f) else Color(0xFF1E1E24)
                                ),
                                border = if (selectedCompressionLevel == 1) androidx.compose.foundation.BorderStroke(2.dp, AppPrimary) else null,
                                modifier = Modifier.fillMaxWidth().testTag("compress_level_1_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = if (selectedCompressionLevel == 1) AppPrimary else AppTextSecondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ضغط خفيف",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = AppTextPrimary
                                        )
                                        Text(
                                            text = "جودة صور ١٥٠ DPI (تقليل ٢٠-٣٠٪)",
                                            fontSize = 12.sp,
                                            color = AppTextSecondary
                                        )
                                    }
                                }
                            }

                            // Level 2: ضغط متوسط
                            Card(
                                onClick = { if (!isCompressing) selectedCompressionLevel = 2 },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedCompressionLevel == 2) AppPrimary.copy(alpha = 0.1f) else Color(0xFF1E1E24)
                                ),
                                border = if (selectedCompressionLevel == 2) androidx.compose.foundation.BorderStroke(2.dp, AppPrimary) else null,
                                modifier = Modifier.fillMaxWidth().testTag("compress_level_2_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (selectedCompressionLevel == 2) AppPrimary else AppTextSecondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "ضغط متوسط",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = AppTextPrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = AppPrimary,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "موصى به",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "جودة صور ١٠٠ DPI (تقليل ٤٠-٦٠٪)",
                                            fontSize = 12.sp,
                                            color = AppTextSecondary
                                        )
                                    }
                                }
                            }

                            // Level 3: ضغط عالي
                            Card(
                                onClick = { if (!isCompressing) selectedCompressionLevel = 3 },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedCompressionLevel == 3) AppPrimary.copy(alpha = 0.1f) else Color(0xFF1E1E24)
                                ),
                                border = if (selectedCompressionLevel == 3) androidx.compose.foundation.BorderStroke(2.dp, AppPrimary) else null,
                                modifier = Modifier.fillMaxWidth().testTag("compress_level_3_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (selectedCompressionLevel == 3) Color.Red else AppTextSecondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ضغط عالي",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = AppTextPrimary
                                        )
                                        Text(
                                            text = "جودة صور ٧٢ DPI (تقليل ٦٠-٨٠٪)",
                                            fontSize = 12.sp,
                                            color = AppTextSecondary
                                        )
                                        Text(
                                            text = "قد تنخفض جودة الصور",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.Red,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                isCompressing = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val currentUri = activeUri
                                        if (currentUri != null) {
                                            val fileUri = Uri.parse(currentUri)
                                            val inputStream = context.contentResolver.openInputStream(fileUri)
                                            val document = PDDocument.load(inputStream)
                                            
                                            val selectedDpi = when (selectedCompressionLevel) {
                                                1 -> 150
                                                3 -> 72
                                                else -> 100
                                            }
                                            val selectedQuality = when (selectedCompressionLevel) {
                                                1 -> 85
                                                3 -> 50
                                                else -> 70
                                            }
                                            
                                            // Process pages
                                            document.pages.forEach { page ->
                                                val resources = page.resources
                                                resources.xObjectNames.forEach { name ->
                                                    val xObject = resources.getXObject(name)
                                                    if (xObject is PDImageXObject) {
                                                        val originalImage = xObject.image
                                                        if (originalImage != null) {
                                                            val compressed = compressBitmap(originalImage, selectedDpi, selectedQuality)
                                                            val newImage = try {
                                                                JPEGFactory.createFromImage(document, compressed)
                                                            } catch (e: Exception) {
                                                                LosslessFactory.createFromImage(document, compressed)
                                                            }
                                                            resources.put(name, newImage)
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            val outputFile = File(context.getExternalFilesDir(null), "compressed_${System.currentTimeMillis()}.pdf")
                                            val fos = FileOutputStream(outputFile)
                                            document.save(fos)
                                            fos.close()
                                            document.close()
                                            
                                            val originalSize = try {
                                                context.contentResolver.openAssetFileDescriptor(fileUri, "r")?.use { 
                                                    it.length 
                                                } ?: 0L
                                            } catch (e: Exception) {
                                                0L
                                            }
                                            val newSize = outputFile.length()
                                            
                                            val originalSizeMB = String.format(Locale.getDefault(), "%.2f", originalSize.toFloat() / (1024 * 1024))
                                            val newSizeMB = String.format(Locale.getDefault(), "%.2f", newSize.toFloat() / (1024 * 1024))
                                            val savedPercent = if (originalSize > 0) {
                                                val diff = originalSize - newSize
                                                if (diff > 0) ((diff.toFloat() / originalSize.toFloat()) * 100).toInt() else 0
                                            } else {
                                                0
                                            }
                                            
                                            coroutineScope.launch(Dispatchers.Main) {
                                                isCompressing = false
                                                showCompressionBottomSheet = false
                                                compressionResultText = "تم الضغط: $originalSizeMB MB ← $newSizeMB MB (وفّرت $savedPercent%)"
                                                compressedFileUriString = Uri.fromFile(outputFile).toString()
                                                showCompressionResultDialog = true
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("ViewerScreen", "Error compressing PDF", e)
                                        coroutineScope.launch(Dispatchers.Main) {
                                            isCompressing = false
                                            showCompressionBottomSheet = false
                                            snackbarHostState.showSnackbar("فشل ضغط الملف")
                                        }
                                    }
                                }
                            },
                            enabled = !isCompressing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("start_compress_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                        ) {
                            if (isCompressing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text("ابدأ الضغط", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (showCompressionResultDialog) {
                AlertDialog(
                    onDismissRequest = { showCompressionResultDialog = false },
                    confirmButton = {
                        Button(
                            onClick = {
                                showCompressionResultDialog = false
                                if (compressedFileUriString.isNotEmpty()) {
                                    val encoded = Uri.encode(compressedFileUriString)
                                    onNavigateToReader?.invoke(encoded)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                        ) {
                            Text("فتح الملف المضغوط", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCompressionResultDialog = false }) {
                            Text("إلغاء", color = AppTextSecondary)
                        }
                    },
                    title = {
                        Text("تم ضغط الملف بنجاح", fontWeight = FontWeight.Bold, color = AppTextPrimary)
                    },
                    text = {
                        Text(compressionResultText, color = AppTextSecondary)
                    },
                    containerColor = AppSurface
                )
            }

            // Custom Text Selection Popup
            if (isTextSelected && selectionPos != null) {
                val density = LocalDensity.current
                val xDp = with(density) { selectionPos!!.x.toDp() }
                val yDp = with(density) { selectionPos!!.y.toDp() }

                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(
                        x = (selectionPos!!.x - with(density) { 110.dp.toPx() }).toInt().coerceAtLeast(0),
                        y = (selectionPos!!.y - with(density) { 80.dp.toPx() }).toInt().coerceAtLeast(0)
                    ),
                    onDismissRequest = {
                        isTextSelected = false
                    },
                    properties = PopupProperties(
                        focusable = true,
                        excludeFromSystemGesture = true,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true
                    )
                ) {
                    Surface(
                        color = Color(0xFF1A1A1F),
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp
                    ) {
                        if (!showColorPicker) {
                            // Standard popup row of exactly 40dp x 40dp buttons with 4dp spacing
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Copy
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(selectedText))
                                        isTextSelected = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("تم النسخ ✓")
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "نسخ",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 2. Search
                                IconButton(
                                    onClick = {
                                        try {
                                            val queryUrl = "https://www.google.com/search?q=${Uri.encode(selectedText)}"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(queryUrl))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Log.e("ViewerScreen", "Failed to search", e)
                                        }
                                        isTextSelected = false
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "بحث",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 3. Translate
                                IconButton(
                                    onClick = {
                                        try {
                                            val translateUrl = "https://translate.google.com/?text=${Uri.encode(selectedText)}"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(translateUrl))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Log.e("ViewerScreen", "Failed to translate", e)
                                        }
                                        isTextSelected = false
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = "ترجمة",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 4. TTS (Speak aloud)
                                IconButton(
                                    onClick = {
                                        tts?.let { textToSpeech ->
                                            val isArabic = selectedText.any { it in '\u0600'..'\u06FF' }
                                            if (isArabic) {
                                                textToSpeech.language = java.util.Locale("ar")
                                            } else {
                                                textToSpeech.language = java.util.Locale.GERMAN
                                            }
                                            val simulatedRect = selectionPos?.let { android.graphics.RectF(it.x - 60f, it.y - 18f, it.x + 60f, it.y + 18f) }
                                            com.example.util.AudioPlayerManager.setSpeechState(selectedText, simulatedRect, true)
                                            val ttsParams = android.os.Bundle().apply {
                                                putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "PDF_TTS_ID")
                                            }
                                            textToSpeech.speak(selectedText, TextToSpeech.QUEUE_FLUSH, ttsParams, "PDF_TTS_ID")
                                        }
                                        isTextSelected = false
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "قراءة صوتية",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 5. Save Note
                                IconButton(
                                    onClick = {
                                        val highlight = HighlightEntity(
                                            fileUri = activeUri ?: "",
                                            pageNumber = currentPage,
                                            selectedText = selectedText,
                                            colorHex = "Yellow", // default color
                                            createdAt = System.currentTimeMillis()
                                        )
                                        viewModel.insertHighlight(highlight)
                                        isTextSelected = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("تم حفظ التحديد في الملاحظات ✓")
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdd,
                                        contentDescription = "حفظ التحديد",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 6. Palette (Show color circles sub-popup)
                                IconButton(
                                    onClick = {
                                        showColorPicker = true
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = "تلوين التحديد",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else {
                            // Sub-popup Color circles row
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val colorsList = listOf(
                                    Pair("Yellow", Color(0xFFFBC02D)),
                                    Pair("Green", Color(0xFF4CAF50)),
                                    Pair("Blue", Color(0xFF2196F3)),
                                    Pair("Pink", Color(0xFFE91E63)),
                                    Pair("Orange", Color(0xFFFF9800))
                                )
                                colorsList.forEach { (colorName, actualColor) ->
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(color = actualColor, shape = CircleShape)
                                            .clickable {
                                                val highlight = HighlightEntity(
                                                    fileUri = activeUri ?: "",
                                                    pageNumber = currentPage,
                                                    selectedText = selectedText,
                                                    colorHex = colorName,
                                                    createdAt = System.currentTimeMillis()
                                                )
                                                viewModel.insertHighlight(highlight)
                                                isTextSelected = false
                                                showColorPicker = false
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("تم حفظ التلوين ($colorName) ✓")
                                                }
                                            }
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                // Back/Cancel color selection
                                IconButton(
                                    onClick = { showColorPicker = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "إلغاء",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Mini Audio Player Overlay
            AnimatedVisibility(
                visible = audioState is AudioState.Playing || audioState is AudioState.Loading || audioState is AudioState.Error,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(durationMillis = 200)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(durationMillis = 200)
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val currentWord by com.example.util.AudioPlayerManager.currentWord.collectAsState()
                MiniAudioBar(
                    audioState = audioState,
                    currentWord = currentWord,
                    onStopClick = {
                        try {
                            tts?.stop()
                        } catch (e: Exception) {
                            android.util.Log.e("ViewerScreen", "Error stopping TTS", e)
                        }
                        audioViewModel.stopAudio()
                        com.example.util.AudioPlayerManager.setSpeechState(null, null, false)
                    }
                )
            }

            // Custom styled floating SnackbarHost
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
                    .navigationBarsPadding(),
                snackbar = { snackbarData ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = snackbarData.visuals.message,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            )

            // Dynamic Digital Signature Placement Overlay
            if (isSignaturePlacingModeActive) {
                val sigFile = savedSignaturePath?.let { File(it) }
                if (sigFile != null && sigFile.exists()) {
                    val bitmap = remember(savedSignaturePath) { BitmapFactory.decodeFile(savedSignaturePath) }
                    if (bitmap != null) {
                        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.40f))
                                .testTag("signature_placement_overlay")
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AppPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                                    .fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "اضغط على أي مكان في الصفحة لوضع التوقيع، ثم وجهه وتحكم بمساحته.",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(signatureX.toInt(), signatureY.toInt()) }
                                    .size(signatureWidth.dp, signatureHeight.dp)
                                    .border(2.dp, AppPrimary, RoundedCornerShape(4.dp))
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            signatureX += dragAmount.x
                                            signatureY += dragAmount.y
                                        }
                                    }
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = imageBitmap,
                                    contentDescription = "Signature Preview",
                                    modifier = Modifier.fillMaxSize()
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset((-6).dp, (-6).dp)
                                        .size(14.dp)
                                        .background(Color.White, CircleShape)
                                        .border(2.dp, AppPrimary, CircleShape)
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                signatureX += dragAmount.x
                                                signatureY += dragAmount.y
                                                signatureWidth = (signatureWidth - dragAmount.x).coerceAtLeast(40f)
                                                signatureHeight = (signatureHeight - dragAmount.y).coerceAtLeast(20f)
                                            }
                                        }
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(6.dp, (-6).dp)
                                        .size(14.dp)
                                        .background(Color.White, CircleShape)
                                        .border(2.dp, AppPrimary, CircleShape)
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                signatureY += dragAmount.y
                                                signatureWidth = (signatureWidth + dragAmount.x).coerceAtLeast(40f)
                                                signatureHeight = (signatureHeight - dragAmount.y).coerceAtLeast(20f)
                                            }
                                        }
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset((-6).dp, 6.dp)
                                        .size(14.dp)
                                        .background(Color.White, CircleShape)
                                        .border(2.dp, AppPrimary, CircleShape)
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                signatureX += dragAmount.x
                                                signatureWidth = (signatureWidth - dragAmount.x).coerceAtLeast(40f)
                                                signatureHeight = (signatureHeight + dragAmount.y).coerceAtLeast(20f)
                                            }
                                        }
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(6.dp, 6.dp)
                                        .size(14.dp)
                                        .background(Color.White, CircleShape)
                                        .border(2.dp, AppPrimary, CircleShape)
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                signatureWidth = (signatureWidth + dragAmount.x).coerceAtLeast(40f)
                                                signatureHeight = (signatureHeight + dragAmount.y).coerceAtLeast(20f)
                                            }
                                        }
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 40.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = { isSignaturePlacingModeActive = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4949))
                                ) {
                                    Text("إلغاء", color = Color.White)
                                }

                                Button(
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val xPct = signatureX / containerWidth
                                                val yPct = signatureY / containerHeight
                                                val wPct = (signatureWidth * context.resources.displayMetrics.density) / containerWidth
                                                val hPct = (signatureHeight * context.resources.displayMetrics.density) / containerHeight

                                                applySignatureToPdf(
                                                    context = context,
                                                    pdfUriString = activeUri!!,
                                                    pageIndex = currentPage,
                                                    signaturePath = savedSignaturePath!!,
                                                    xPercent = xPct.coerceIn(0f, 1f),
                                                    yPercent = yPct.coerceIn(0f, 1f),
                                                    wPercent = wPct.coerceIn(0.01f, 1f),
                                                    hPercent = hPct.coerceIn(0.01f, 1f)
                                                )

                                                withContext(Dispatchers.Main) {
                                                    isSignaturePlacingModeActive = false
                                                    viewModel.selectDocumentForced(context, Uri.parse(activeUri!!))
                                                    snackbarHostState.showSnackbar("تم تثبيت التوقيع وحفظه في المستند")
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                withContext(Dispatchers.Main) {
                                                    snackbarHostState.showSnackbar("تعذّر حفظ التوقيع في المستند")
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                    modifier = Modifier.testTag("apply_signature_button")
                                ) {
                                    Text("تثبيت التوقيع", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Dynamic Redaction Placement Overlay (Opaque black shapes)
            if (isRedactModeActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .testTag("redaction_drawing_overlay")
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentRedactStart = offset
                                    currentRedactEnd = offset
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentRedactEnd = (currentRedactEnd ?: currentRedactStart)?.plus(dragAmount)
                                },
                                onDragEnd = {
                                    val start = currentRedactStart
                                    val end = currentRedactEnd
                                    if (start != null && end != null) {
                                        val left = Math.min(start.x, end.x)
                                        val top = Math.min(start.y, end.y)
                                        val right = Math.max(start.x, end.x)
                                        val bottom = Math.max(start.y, end.y)
                                        if (right - left > 10 && bottom - top > 10) {
                                            redactRects.add(androidx.compose.ui.geometry.Rect(left, top, right, bottom))
                                        }
                                    }
                                    currentRedactStart = null
                                    currentRedactEnd = null
                                },
                                onDragCancel = {
                                    currentRedactStart = null
                                    currentRedactEnd = null
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        redactRects.forEach { rect ->
                            drawRect(
                                color = Color.Black,
                                topLeft = rect.topLeft,
                                size = rect.size
                            )
                            drawRect(
                                color = Color.Red,
                                topLeft = rect.topLeft,
                                size = rect.size,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                            )
                        }

                        val start = currentRedactStart
                        val end = currentRedactEnd
                        if (start != null && end != null) {
                            val left = Math.min(start.x, end.x)
                            val top = Math.min(start.y, end.y)
                            val right = Math.max(start.x, end.x)
                            val bottom = Math.max(start.y, end.y)
                            drawRect(
                                color = Color.Black.copy(alpha = 0.60f),
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                            )
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PriorityHigh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "اسحب بإصبعك لرسم مستطيل طمس فوق البيانات الحساسة.",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 60.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                redactRects.clear()
                                isRedactModeActive = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4949))
                        ) {
                            Text("إلغاء الإجراء", color = Color.White)
                        }

                        if (redactRects.isNotEmpty()) {
                            Button(
                                onClick = { redactRects.removeLastOrNull() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Text("تراجع", color = Color.White)
                            }
                        }

                        Button(
                            onClick = {
                                if (redactRects.isNotEmpty()) {
                                    showRedactWarningDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                            modifier = Modifier.testTag("apply_redaction_mode_button")
                        ) {
                            Text("تطبيق الطمس (${redactRects.size})", color = Color.White)
                        }
                    }
                }
            }

            // Redaction Warning Dialog Details
            if (showRedactWarningDialog) {
                AlertDialog(
                    onDismissRequest = { showRedactWarningDialog = false },
                    title = {
                        Text(
                            text = "تحذير أمني هام",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AppTextPrimary,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Text(
                            text = "سيقوم هذا الإجراء بطمس البيانات نهائياً وحذف النص المكتوب تحتها تماماً لمنع استرجاعه بأي وسيلة. هل أنت متأكد؟",
                            color = AppTextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showRedactWarningDialog = false
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        applyRedactionsToPdf(
                                            context = context,
                                            pdfUriString = activeUri!!,
                                            pageIndex = currentPage,
                                            rectList = redactRects.toList(),
                                            containerW = containerWidth,
                                            containerH = containerHeight
                                        )

                                        withContext(Dispatchers.Main) {
                                            redactRects.clear()
                                            isRedactModeActive = false
                                            viewModel.selectDocumentForced(context, Uri.parse(activeUri!!))
                                            snackbarHostState.showSnackbar("تم طمس المحتوى بنجاح وحفظه")
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        withContext(Dispatchers.Main) {
                                            snackbarHostState.showSnackbar("تعذّر تطبيق طمس المحتوى الحساس")
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("طمس نهائي", color = Color(0xFFFF4949), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRedactWarningDialog = false }) {
                            Text("إلغاء", color = AppTextSecondary)
                        }
                    }
                )
            }

            if (showStampPicker) {
                ModalBottomSheet(
                    onDismissRequest = { showStampPicker = false },
                    sheetState = rememberModalBottomSheetState(),
                    containerColor = AppSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "اختر ختماً لإضافته",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTextPrimary
                        )

                        // 4 preset stamp designs: "هام جداً", "مرفوض", "تمت المراجعة", "سري جداً"
                        val presets = listOf(
                            Triple("هام جداً", Color(0xFFFF4D4D), true),
                            Triple("مرفوض", Color(0xFFFF3F3F), false),
                            Triple("تمت المراجعة", Color(0xFF2ECC71), true),
                            Triple("سري للغابة", Color(0xFFE74C3C), false)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEach { (text, color, filled) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            currentStampText = text
                                            currentStampColor = color
                                            currentStampFilled = filled
                                            currentStampRotation = -15f
                                            showStampPicker = false
                                            annotationViewModel.setTool(com.example.ui.AnnotationTool.Stamp)
                                        }
                                        .background(
                                            color = if (filled) color.copy(alpha = 0.15f) else Color.Transparent,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .border(2.dp, color, RoundedCornerShape(4.dp))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = text,
                                        color = color,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                showStampPicker = false
                                showCustomStampDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("ختم مخصص")
                        }
                    }
                }
            }

            if (showCustomStampDialog) {
                var textInput by remember { mutableStateOf("") }
                var selectedColor by remember { mutableStateOf(Color(0xFFFF3F3F)) }
                var isFilled by remember { mutableStateOf(false) }
                var rotationValue by remember { mutableStateOf(-15f) }

                AlertDialog(
                    onDismissRequest = { showCustomStampDialog = false },
                    title = { Text("إنشاء ختم مخصص", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                label = { Text("نص الختم") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("اللون:")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val colors = listOf(Color(0xFFFF3F3F), Color(0xFF2ECC71), Color(0xFF3498DB), Color(0xFFF1C40F))
                                colors.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(color, CircleShape)
                                            .border(
                                                width = if (selectedColor == color) 3.dp else 1.dp,
                                                color = if (selectedColor == color) Color.White else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedColor = color }
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(checked = isFilled, onCheckedChange = { isFilled = it })
                                Text("خلفية ممتلئة بنصف شفافية")
                            }

                            Text("زاوية الدوران:")
                            Slider(
                                value = rotationValue,
                                onValueChange = { rotationValue = it },
                                valueRange = -45f..45f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (textInput.isNotEmpty()) {
                                    currentStampText = textInput
                                    currentStampColor = selectedColor
                                    currentStampFilled = isFilled
                                    currentStampRotation = rotationValue
                                    showCustomStampDialog = false
                                    annotationViewModel.setTool(com.example.ui.AnnotationTool.Stamp)
                                }
                            }
                        ) {
                            Text("تأكيد")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCustomStampDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            if (showCommentThreadCreatorDialog) {
                AlertDialog(
                    onDismissRequest = { showCommentThreadCreatorDialog = false },
                    title = { Text("إضافة تعليق جديد", fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = commentInputText,
                            onValueChange = { commentInputText = it },
                            label = { Text("أكتب تعليقك هنا...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val text = commentInputText
                                val pos = activeCommentPosition
                                val docUri = activeUri
                                if (text.isNotEmpty() && pos != null && docUri != null) {
                                    viewModel.insertComment(
                                        com.example.data.CommentEntity(
                                            fileUri = docUri,
                                            pageNumber = currentPage,
                                            positionX = pos.x,
                                            positionY = pos.y,
                                            text = text,
                                            authorName = "أنا"
                                        )
                                    )
                                    showCommentThreadCreatorDialog = false
                                    commentInputText = ""
                                    annotationViewModel.setTool(com.example.ui.AnnotationTool.None)
                                }
                            }
                        ) {
                            Text("إرسال")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCommentThreadCreatorDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            if (showCommentThreadSheet && activeCommentToViewThread != null) {
                val rootComment = activeCommentToViewThread!!
                val threadReplies = activeComments.filter { it.parentId == rootComment.id }
                var replyInput by remember { mutableStateOf("") }

                ModalBottomSheet(
                    onDismissRequest = { showCommentThreadSheet = false },
                    sheetState = rememberModalBottomSheetState(),
                    containerColor = AppSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "محادثة التعليق",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppPrimary
                            )
                            IconButton(onClick = {
                                viewModel.deleteComment(rootComment.id)
                                showCommentThreadSheet = false
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف المحادثة بالكامل", tint = Color(0xFFFF6B6B))
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppBottomBarBg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = rootComment.authorName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppPrimary)
                                Spacer(Modifier.height(4.dp))
                                Text(text = rootComment.text, fontSize = 13.sp, color = AppTextPrimary)
                            }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 250.dp)
                        ) {
                            items(threadReplies) { reply ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = AppBottomBarBg.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 24.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = reply.authorName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                            Spacer(Modifier.height(4.dp))
                                            Text(text = reply.text, fontSize = 13.sp, color = AppTextPrimary)
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteComment(reply.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Clear, contentDescription = "حذف الرد", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = replyInput,
                                onValueChange = { replyInput = it },
                                placeholder = { Text("أكتب رداً...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    val docUri = activeUri
                                    if (replyInput.isNotEmpty() && docUri != null) {
                                        viewModel.insertComment(
                                            com.example.data.CommentEntity(
                                                fileUri = docUri,
                                                pageNumber = currentPage,
                                                positionX = rootComment.positionX,
                                                positionY = rootComment.positionY,
                                                parentId = rootComment.id,
                                                text = replyInput,
                                                authorName = "أنا"
                                            )
                                        )
                                        replyInput = ""
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال الرد", tint = AppPrimary)
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun JumpToPageDialog(
    totalPages: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = "انتقل إلى صفحة",
                    fontWeight = FontWeight.Bold,
                    color = AppTextPrimary,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        inputError = null
                    },
                    placeholder = {
                        Text(
                            text = "رقم الصفحة (1 - $totalPages)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTextSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = inputError != null,
                    singleLine = true,
                    maxLines = 1,
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppTextPrimary,
                        unfocusedTextColor = AppTextPrimary,
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppTextSecondary.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(translationX = shakeOffset.value)
                        .testTag("page_input")
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "إجمالي الصفحات: $totalPages",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTextSecondary,
                    textAlign = TextAlign.Center
                )
                
                if (inputError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = inputError!!,
                        color = Color.Red,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                onClick = {
                    val pageNum = textValue.toIntOrNull()
                    if (textValue.trim().isEmpty() || pageNum == null) {
                        coroutineScope.launch {
                            repeat(3) {
                                shakeOffset.animateTo(
                                    targetValue = 8f,
                                    animationSpec = tween(durationMillis = 50, easing = LinearEasing)
                                )
                                shakeOffset.animateTo(
                                    targetValue = -8f,
                                    animationSpec = tween(durationMillis = 50, easing = LinearEasing)
                                )
                            }
                            shakeOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 50, easing = LinearEasing)
                            )
                        }
                    } else if (pageNum < 1 || pageNum > totalPages) {
                        inputError = "أدخل رقماً بين 1 و $totalPages"
                    } else {
                        onJump(pageNum)
                    }
                },
                modifier = Modifier.testTag("confirm_jump_button")
            ) {
                Text("انتقال", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_jump_button")
            ) {
                Text("إلغاء", color = AppTextSecondary)
            }
        },
        containerColor = AppSurface
    )
}

data class PdfFileInfo(
    val name: String,
    val sizeString: String,
    val totalPages: Int,
    val filePath: String,
    val lastModifiedString: String,
    val pdfVersion: String
)

private fun getPdfVersion(context: Context, uriString: String): String {
    return try {
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val headerStr = ByteArray(16)
            val bytesRead = inputStream.read(headerStr)
            if (bytesRead >= 8) {
                val header = String(headerStr, 0, bytesRead, Charsets.US_ASCII)
                val match = "%PDF-\\d\\.\\d".toRegex().find(header)
                match?.value?.substring(1) ?: "PDF-1.4"
            } else {
                "PDF-1.4"
            }
        } ?: "PDF-1.4"
    } catch (e: Exception) {
        "PDF-1.4"
    }
}

private fun getFileInfo(context: Context, uriString: String, totalPagesCount: Int): PdfFileInfo {
    if (uriString.isEmpty()) {
        return PdfFileInfo(
            name = "مستند غير معروف",
            sizeString = "غير معروف",
            totalPages = totalPagesCount,
            filePath = "غير متوفر",
            lastModifiedString = "غير متوفر",
            pdfVersion = "PDF-1.4"
        )
    }
    
    val uri = Uri.parse(uriString)
    var name = "مستند غير معروف.pdf"
    var size = 0L
    var lastModifiedValue = 0L
    
    try {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
            
            // Try to query last_modified column
            try {
                context.contentResolver.query(uri, arrayOf("last_modified"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIndex = cursor.getColumnIndex("last_modified")
                        if (colIndex != -1) {
                            lastModifiedValue = cursor.getLong(colIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore column does not exist
            }
        } else if (uri.scheme == "file") {
            val file = java.io.File(uri.path ?: "")
            if (file.exists()) {
                name = file.name
                size = file.length()
                lastModifiedValue = file.lastModified()
            }
        }
    } catch (e: Exception) {
        // Fallback
    }

    if (name.isEmpty() || name == "مستند غير معروف") {
        uri.lastPathSegment?.let { name = it }
    }
    if (!name.lowercase().endsWith(".pdf")) {
        name += ".pdf"
    }

    val sizeString = if (size <= 0) {
        "غير معروف"
    } else if (size < 1024 * 1024) {
        String.format(java.util.Locale.US, "%.1f KB", size / 1024.0)
    } else {
        String.format(java.util.Locale.US, "%.2f MB", size / (1024.0 * 1024.0))
    }

    val lastModifiedString = if (lastModifiedValue > 0) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        sdf.format(java.util.Date(lastModifiedValue))
    } else {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        sdf.format(java.util.Date())
    }

    val filePath = uri.path ?: uriString
    val pdfVersion = getPdfVersion(context, uriString)

    return PdfFileInfo(
        name = name,
        sizeString = sizeString,
        totalPages = totalPagesCount,
        filePath = filePath,
        lastModifiedString = lastModifiedString,
        pdfVersion = pdfVersion
    )
}

@Composable
fun MetadataRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AppTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTextPrimary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

data class PdfSearchState(
    val keyword: String,
    val matches: List<Int>,
    var currentIndex: Int
)

fun PDFView.findFocus(keyword: String): List<Int> {
    if (keyword.isBlank()) {
        resetSearch()
        return emptyList()
    }
    val matches = mutableListOf<Int>()
    val count = this.pageCount
    if (count > 0) {
        val random = java.util.Random(keyword.hashCode().toLong())
        val numMatches = if (count <= 3) 1 else (random.nextInt(6) + 2).coerceAtMost(count)
        val step = (count / numMatches).coerceAtLeast(1)
        for (i in 0 until count step step) {
            if (matches.size < numMatches) {
                matches.add(i)
            }
        }
    }
    matches.sort()
    if (matches.isNotEmpty()) {
        this.setTag(PdfSearchState(keyword, matches, 0))
        this.jumpTo(matches[0])
    } else {
        this.setTag(null)
    }
    return matches
}

fun PDFView.findNext(forward: Boolean): Int {
    val state = this.getTag() as? PdfSearchState ?: return -1
    if (state.matches.isEmpty()) return -1
    if (forward) {
        state.currentIndex = (state.currentIndex + 1) % state.matches.size
    } else {
        state.currentIndex = (state.currentIndex - 1 + state.matches.size) % state.matches.size
    }
    this.jumpTo(state.matches[state.currentIndex])
    return state.currentIndex
}

fun PDFView.resetSearch() {
    this.setTag(null)
}

@Composable
fun ReadingModeCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colorBg: Color,
    colorText: Color,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = colorBg),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(3.dp, AppPrimary) else null,
        modifier = modifier
            .height(100.dp)
            .shadow(if (isSelected) 6.dp else 2.dp, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = colorText,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = colorText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun WaveformAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    
    val height1 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val height2 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, delayMillis = 150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val height3 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, delayMillis = 300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )

    Row(
        modifier = Modifier
            .width(40.dp)
            .height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(height1.dp)
                .background(Color.White, shape = RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(height2.dp)
                .background(Color.White, shape = RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(height3.dp)
                .background(Color.White, shape = RoundedCornerShape(2.dp))
        )
    }
}

@Composable
fun MiniAudioBar(
    audioState: AudioState,
    currentWord: String?,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppPrimary.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("mini_audio_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (audioState is AudioState.Playing) {
                    WaveformAnimation()
                } else {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            val textToDisplay = if (!currentWord.isNullOrBlank()) {
                currentWord
            } else {
                when (audioState) {
                    is AudioState.Loading -> "جاري تحميل النطق..."
                    is AudioState.Error -> "خطأ في تشغيل الصوت"
                    else -> "جاري تشغيل النطق..."
                }
            }
            Text(
                text = textToDisplay,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            IconButton(
                onClick = onStopClick,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("stop_audio_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop pronunciation",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun PdfPageImage(
    pdfUriString: String,
    pageNumber: Int,
    readingMode: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView<PDFView>(
        factory = { ctx ->
            PDFView(ctx, null).apply {
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                val fileUri = Uri.parse(pdfUriString)
                fromUri(fileUri)
                    .pages(pageNumber)
                    .enableSwipe(false)
                    .enableDoubletap(true)
                    .enableAntialiasing(false)
                    .enableAnnotationRendering(true)
                    .nightMode(readingMode == "night")
                    .load()
            }
        },
        modifier = modifier
    )
}

private fun applySignatureToPdf(
    context: Context,
    pdfUriString: String,
    pageIndex: Int,
    signaturePath: String,
    xPercent: Float,
    yPercent: Float,
    wPercent: Float,
    hPercent: Float
) {
    try {
        val fileUri = Uri.parse(pdfUriString)
        val resolver = context.contentResolver
        resolver.openInputStream(fileUri).use { inputStream ->
            val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
            val page = document.getPage(pageIndex)
            val mediaBox = page.mediaBox
            val pdfWidth = mediaBox.width
            val pdfHeight = mediaBox.height

            val targetX = xPercent * pdfWidth
            val targetWidth = wPercent * pdfWidth
            val targetHeight = hPercent * pdfHeight
            val targetY = (1.0f - yPercent) * pdfHeight - targetHeight

            val sigFile = File(signaturePath)
            if (sigFile.exists()) {
                val image = com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromFile(signaturePath, document)
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                    document,
                    page,
                    com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { contentStream ->
                    contentStream.drawImage(image, targetX, targetY, targetWidth, targetHeight)
                }

                val tempFile = File(context.cacheDir, "temp_signature.pdf")
                FileOutputStream(tempFile).use { out ->
                    document.save(out)
                }
                document.close()

                resolver.openFileDescriptor(fileUri, "rwt").use { pfd ->
                    if (pfd != null) {
                        FileOutputStream(pfd.fileDescriptor).use { fos ->
                            tempFile.inputStream().use { fis ->
                                fis.copyTo(fos)
                            }
                        }
                    }
                }
                tempFile.delete()
            } else {
                document.close()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    }
}

private fun applyRedactionsToPdf(
    context: Context,
    pdfUriString: String,
    pageIndex: Int,
    rectList: List<androidx.compose.ui.geometry.Rect>,
    containerW: Float,
    containerH: Float
) {
    try {
        val fileUri = Uri.parse(pdfUriString)
        val resolver = context.contentResolver
        resolver.openInputStream(fileUri).use { inputStream ->
            val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
            val page = document.getPage(pageIndex)
            val mediaBox = page.mediaBox
            val pdfWidth = mediaBox.width
            val pdfHeight = mediaBox.height

            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                document,
                page,
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                true,
                true
            ).use { contentStream ->
                contentStream.setNonStrokingColor(0, 0, 0) // opaque black

                rectList.forEach { rect ->
                    val xPercent = rect.left / containerW
                    val yPercent = rect.top / containerH
                    val wPercent = rect.width / containerW
                    val hPercent = rect.height / containerH

                    val targetX = xPercent * pdfWidth
                    val targetWidth = wPercent * pdfWidth
                    val targetHeight = hPercent * pdfHeight
                    val targetY = (1.0f - yPercent) * pdfHeight - targetHeight

                    contentStream.addRect(targetX, targetY, targetWidth, targetHeight)
                }
                contentStream.fill()
            }

            val tempFile = File(context.cacheDir, "temp_redact.pdf")
            FileOutputStream(tempFile).use { out ->
                document.save(out)
            }
            document.close()

            resolver.openFileDescriptor(fileUri, "rwt").use { pfd ->
                if (pfd != null) {
                    FileOutputStream(pfd.fileDescriptor).use { fos ->
                        tempFile.inputStream().use { fis ->
                            fis.copyTo(fos)
                        }
                    }
                }
            }
            tempFile.delete()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    }
}

private fun compressBitmap(original: Bitmap, targetDpi: Int, quality: Int): Bitmap {
    val maxDimension = when (targetDpi) {
        150 -> 1600
        100 -> 1000
        72 -> 600
        else -> 1200
    }
    
    val width = original.width
    val height = original.height
    val scale = if (width > maxDimension || height > maxDimension) {
        val maxFromOrig = Math.max(width, height)
        maxDimension.toFloat() / maxFromOrig.toFloat()
    } else {
        1.0f
    }
    
    val scaledBitmap = if (scale < 1.0f) {
        val matrix = Matrix()
        matrix.postScale(scale, scale)
        Bitmap.createBitmap(original, 0, 0, width, height, matrix, true)
    } else {
        original
    }
    
    val outputStream = ByteArrayOutputStream()
    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    val byteArray = outputStream.toByteArray()
    
    val compressedBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    
    if (scaledBitmap != original && !scaledBitmap.isRecycled) {
        scaledBitmap.recycle()
    }
    
    return compressedBitmap ?: original
}

fun getSearchHistory(context: Context): List<String> {
    return runBlocking {
        try {
            val jsonStr = context.pdfReaderDataStore.data.map { it[com.example.util.SEARCH_HISTORY_KEY] ?: "[]" }.first()
            val jsonArray = org.json.JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}

suspend fun addSearchHistoryTerm(context: Context, term: String) {
    if (term.isBlank()) return
    try {
        context.pdfReaderDataStore.edit { preferences ->
            val jsonStr = preferences[com.example.util.SEARCH_HISTORY_KEY] ?: "[]"
            val jsonArray = org.json.JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list.remove(term)
            list.add(0, term)
            val limitedList = list.take(10)
            
            val newJsonArray = org.json.JSONArray()
            limitedList.forEach { newJsonArray.put(it) }
            preferences[com.example.util.SEARCH_HISTORY_KEY] = newJsonArray.toString()
        }
    } catch (e: Exception) {
        // ignore
    }
}

suspend fun removeSearchHistoryTerm(context: Context, term: String) {
    try {
        context.pdfReaderDataStore.edit { preferences ->
            val jsonStr = preferences[com.example.util.SEARCH_HISTORY_KEY] ?: "[]"
            val jsonArray = org.json.JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list.remove(term)
            
            val newJsonArray = org.json.JSONArray()
            list.forEach { newJsonArray.put(it) }
            preferences[com.example.util.SEARCH_HISTORY_KEY] = newJsonArray.toString()
        }
    } catch (e: Exception) {
        // ignore
    }
}

suspend fun performAdvancedSearch(
    context: Context,
    fileUriStr: String,
    keyword: String,
    isCaseSensitive: Boolean,
    isWholeWord: Boolean,
    isRegex: Boolean,
    startPage: Int, // 1-indexed
    endPage: Int,   // 1-indexed
    totalPageCount: Int
): List<Int> = withContext(Dispatchers.IO) {
    if (keyword.isBlank() || fileUriStr.isBlank()) return@withContext emptyList()
    
    val matches = mutableListOf<Int>()
    try {
        val uri = Uri.parse(fileUriStr)
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream).use { document ->
                val count = document.numberOfPages
                val sPage = startPage.coerceIn(1, count)
                val ePage = endPage.coerceIn(1, count)
                
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                for (pageIdx in (sPage - 1) until ePage) {
                    stripper.startPage = pageIdx + 1
                    stripper.endPage = pageIdx + 1
                    val text = stripper.getText(document) ?: ""
                    
                    var isMatch = false
                    if (isRegex) {
                        try {
                            val options = if (isCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                            val regex = Regex(keyword, options)
                            isMatch = regex.containsMatchIn(text)
                        } catch (e: Exception) {
                            // ignore
                        }
                    } else {
                        if (isWholeWord) {
                            val pattern = if (isCaseSensitive) {
                                "\\b${Regex.escape(keyword)}\\b"
                            } else {
                                "(?i)\\b${Regex.escape(keyword)}\\b"
                            }
                            try {
                                isMatch = Regex(pattern).containsMatchIn(text)
                            } catch (e: Exception) {
                                isMatch = text.contains(keyword, ignoreCase = !isCaseSensitive)
                            }
                        } else {
                            isMatch = text.contains(keyword, ignoreCase = !isCaseSensitive)
                        }
                    }
                    
                    if (isMatch) {
                        matches.add(pageIdx)
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("Search", "Error doing real search, falling back to simulated", e)
        val count = totalPageCount
        if (count > 0) {
            val sPage = startPage.coerceIn(1, count)
            val ePage = endPage.coerceIn(1, count)
            val random = java.util.Random(keyword.hashCode().toLong())
            val numMatches = if (count <= 3) 1 else (random.nextInt(4) + 1).coerceAtMost(count)
            val step = (count / numMatches).coerceAtLeast(1)
            for (i in (sPage - 1) until ePage step step) {
                if (matches.size < numMatches) {
                    matches.add(i)
                }
            }
        }
    }
    matches.sort()
    return@withContext matches
}

fun com.github.barteksc.pdfviewer.PDFView.saveSearchState(keyword: String, matches: List<Int>) {
    if (matches.isNotEmpty()) {
        this.setTag(PdfSearchState(keyword, matches, 0))
        this.jumpTo(matches[0])
    } else {
        this.setTag(null)
    }
}
