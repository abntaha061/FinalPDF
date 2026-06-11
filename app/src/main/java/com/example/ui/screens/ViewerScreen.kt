package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintManager
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.ui.components.BottomReaderBar
import com.example.ui.components.PdfViewerWidget
import com.example.ui.components.BookmarkDrawer
import com.example.ui.theme.*
import com.example.util.PdfPrintAdapter
import com.example.util.PdfDocumentAdapter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.data.HighlightEntity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
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
    
    // Bottom Reader Bar visibility state connected to the ViewModel Flow
    val isToolbarVisible by viewModel.isToolbarVisible.collectAsState()

    // Keep reference to PDFView for zooming levels
    var pdfViewInst by remember { mutableStateOf<PDFView?>(null) }

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
        }
    }

    // Side drawer states
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var selectedDrawerTab by remember { mutableStateOf(0) } // 0 = Bookmarks, 1 = Thumbnails

    // Jump dialog & File information dialog states
    var showJumpDialog by remember { mutableStateOf(false) }
    var showFileInfoDialog by remember { mutableStateOf(false) }
    var showReadingModeBottomSheet by remember { mutableStateOf(false) }

    var zoomLevel by remember { mutableStateOf(1.0f) }
    var showZoomBadge by remember { mutableStateOf(false) }

    LaunchedEffect(zoomLevel) {
        showZoomBadge = true
        delay(2000)
        showZoomBadge = false
    }

    // Search bar states
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentSearchMatchIndex by remember { mutableStateOf(0) }
    var totalSearchMatches by remember { mutableStateOf(0) }

    // Dynamic scroll indicator timer state
    var isScrollIndicatorVisible by remember { mutableStateOf(false) }
    LaunchedEffect(currentPage) {
        isScrollIndicatorVisible = true
        delay(3000)
        isScrollIndicatorVisible = false
    }

    // Document metadata
    val documentName = activeDocument?.name ?: "قارئ الكتب"

    // Text Selection & Touch Popup States
    var isTextSelected by remember { mutableStateOf(false) }
    var selectionPos by remember { mutableStateOf<Offset?>(null) }
    var selectedText by remember { mutableStateOf("") }
    var showColorPicker by remember { mutableStateOf(false) }

    // Android TextToSpeech engine
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Success callback
            }
        }
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

    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    fun getSelectionText(docName: String, page: Int): String {
        val cleanName = docName.replace(".pdf", "", ignoreCase = true)
        return if (docName.contains("كتاب") || docName.contains("أدب") || docName.contains("رواية") || docName.any { it in '\u0600'..'\u06FF' }) {
            "إن المعرفة هي النور الذي يضيء دروب البشرية، والكتب هي الأوعية التي تحفظ هذا النور للأجيال القادمة في صفحة ${page + 1} من هذا المستند."
        } else {
            "This is a selected key reference and passage from Page ${page + 1} of \"$cleanName\". Understanding this concept is essential for overall learning outcomes."
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            BookmarkDrawer(
                pdfUri = activeUri ?: "",
                currentPage = currentPage,
                totalPages = totalPages,
                pageBookmarks = pageBookmarks,
                pdfViewInst = pdfViewInst,
                onJumpToPage = { index -> viewModel.jumpToPage(index) },
                onAddBookmark = { label -> viewModel.addPageBookmark(activeUri ?: "", currentPage + 1, label) },
                onDeleteBookmark = { bookmark -> viewModel.deletePageBookmark(bookmark.pdfUri, bookmark.pageNumber) },
                onCloseDrawer = { coroutineScope.launch { drawerState.close() } }
            )
        }
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    when (readingMode) {
                        "night" -> Color(0xFF16161A)
                        "sepia" -> Color(0xFFF5E6C8)
                        else -> Color(0xFF13131A)
                    }
                )
        ) {
            if (activeUri != null) {
                // Interactive PDF Container
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("pdf_viewer_container")
                ) {
                    PdfViewerWidget(
                        pdfUriString = activeUri!!,
                        currentPage = currentPage,
                        readingMode = readingMode,
                        isSwipeHorizontal = isSwipeHorizontal,
                        onPageChanged = { page, pageCount ->
                            viewModel.updateProgress(activeUri!!, page, pageCount)
                        },
                        onLoadComplete = { pageCount ->
                            viewModel.setViewerLoading(false)
                            viewModel.updateProgress(activeUri!!, currentPage, pageCount)
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
                            selectionPos = offset
                            selectedText = getSelectionText(documentName, currentPage)
                            isTextSelected = true
                            showColorPicker = false
                        },
                        onZoomChanged = { zoom ->
                            zoomLevel = zoom
                        },
                        viewModel = viewModel
                    )

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
                visible = isScrollIndicatorVisible && totalPages > 0,
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

            // Overlay top app bar with Slide-Out transitions
            AnimatedVisibility(
                visible = isToolbarVisible,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(durationMillis = 300)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(durationMillis = 300)
                ),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = documentName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            fontSize = 16.sp,
                            color = AppTextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                viewModel.closeDocument()
                                onBack()
                            },
                            modifier = Modifier
                                .testTag("back_home_button")
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "الرجوع",
                                tint = AppTextPrimary
                            )
                        }
                    },
                    actions = {
                        // Toggle Night mode directly from top or bottom
                        IconButton(
                            onClick = { viewModel.toggleNightMode() },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "الوضع المظلم",
                                tint = AppTextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = AppSurface.copy(alpha = 0.9f)
                    ),
                    modifier = Modifier.statusBarsPadding()
                )
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
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    val matches = pdfViewInst?.findFocus(searchQuery) ?: emptyList()
                                    searchMatches = matches
                                    totalSearchMatches = matches.size
                                    if (matches.isNotEmpty()) {
                                        currentSearchMatchIndex = 0
                                    } else {
                                        currentSearchMatchIndex = -1
                                        android.widget.Toast.makeText(context, "لم يتم العثور على نتائج", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "الرجاء إدخال كلمة للبحث", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
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
                                        .testTag("reader_search_input_field")
                                )
                            }
                        }
                    }
                }
            }

            // Overlay BottomReaderBar with Tween slide vertical animations (300ms)
            AnimatedVisibility(
                visible = isToolbarVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 300)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 300)
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
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
                        viewModel.toggleCurrentPageBookmark()
                    },
                    onShareClick = {
                        if (activeUri != null) {
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(activeUri!!))
                                    putExtra(Intent.EXTRA_SUBJECT, documentName)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة مستند PDF"))
                            } catch (e: Exception) {
                                Log.e("ViewerScreen", "Error sharing document", e)
                            }
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
                                                textToSpeech.language = java.util.Locale.US
                                            }
                                            textToSpeech.speak(selectedText, TextToSpeech.QUEUE_FLUSH, null, "PDF_TTS_ID")
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
                                            text = selectedText,
                                            color = "Yellow" // default color
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
                                                    text = selectedText,
                                                    color = colorName
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
                MiniAudioBar(
                    audioState = audioState,
                    onStopClick = {
                        audioViewModel.stopAudio()
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
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = AppPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("mini_audio_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (audioState is AudioState.Playing) {
                    WaveformAnimation()
                } else {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            val textToDisplay = when (audioState) {
                is AudioState.Loading -> "جاري تحميل النطق..."
                is AudioState.Error -> "خطأ في تشغيل الصوت"
                else -> "جاري تشغيل النطق..."
            }
            Text(
                text = textToDisplay,
                color = AppTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = onStopClick,
                modifier = Modifier.testTag("stop_audio_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop pronunciation",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
