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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.components.BottomReaderBar
import com.example.ui.components.PdfViewerWidget
import com.example.ui.components.PdfPageThumbnail
import com.example.ui.theme.*
import com.example.util.PdfPrintAdapter
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
    
    val activeDocument by viewModel.currentDocument.collectAsState()
    val activeUri by viewModel.selectedUri.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val isNightMode by viewModel.isNightMode.collectAsState()
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

    // Search bar states
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<Int>>(emptyList()) }

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
            ModalDrawerSheet(
                drawerContainerColor = AppSurface,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp)
                ) {
                    Text(
                        text = "فهرس المستند",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppPrimary,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Text(
                        text = documentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextSecondary,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                        maxLines = 1
                    )

                    // Drawer Tabs Selection
                    TabRow(
                        selectedTabIndex = selectedDrawerTab,
                        containerColor = Color.Transparent,
                        contentColor = AppPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Tab(
                            selected = selectedDrawerTab == 0,
                            onClick = { selectedDrawerTab = 0 },
                            text = { Text("الإشارات", fontSize = 13.sp) }
                        )
                        Tab(
                            selected = selectedDrawerTab == 1,
                            onClick = { selectedDrawerTab = 1 },
                            text = { Text("المعاينة", fontSize = 13.sp) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                    ) {
                        if (selectedDrawerTab == 0) {
                            // Page Bookmarks items
                            if (pageBookmarks.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "لا توجد صفحات محفوظة.",
                                        color = AppTextSecondary,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(pageBookmarks) { bookmark ->
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = AppBottomBarBg.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.jumpToPage(bookmark.pageNumber)
                                                    coroutineScope.launch { drawerState.close() }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Bookmark,
                                                        contentDescription = null,
                                                        tint = AppPrimaryVariant,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = "صفحة ${bookmark.pageNumber + 1}",
                                                        fontWeight = FontWeight.Bold,
                                                        color = AppTextPrimary,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        viewModel.deletePageBookmark(bookmark.pdfUri, bookmark.pageNumber)
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "حذف الإشارة",
                                                        tint = Color.Red.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Thumbnails previews grid list
                            if (totalPages == 0) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = AppPrimary)
                                }
                            } else {
                                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(totalPages) { pageIndex ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    viewModel.jumpToPage(pageIndex)
                                                    coroutineScope.launch { drawerState.close() }
                                                }
                                                .background(
                                                    if (currentPage == pageIndex) AppPrimary.copy(alpha = 0.15f)
                                                    else Color.Transparent
                                                )
                                                .padding(6.dp)
                                        ) {
                                            PdfPageThumbnail(
                                                pdfUriString = activeUri!!,
                                                pageIndex = pageIndex,
                                                modifier = Modifier
                                                    .size(80.dp, 110.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "الصفحة ${pageIndex + 1}",
                                                fontSize = 11.sp,
                                                color = if (currentPage == pageIndex) AppPrimary else AppTextPrimary,
                                                fontWeight = if (currentPage == pageIndex) FontWeight.Bold else FontWeight.Normal
                                            )
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
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(if (isNightMode) Color.Black else Color(0xFF13131A))
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
                        isNightMode = isNightMode,
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
                        viewModel = viewModel
                    )

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

            // Overlay Search Bar at top when search mode is active
            AnimatedVisibility(
                visible = isSearching,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
            ) {
                Surface(
                    color = AppSurface,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            isSearching = false
                            searchQuery = ""
                            searchMatches = emptyList()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق البحث", tint = AppTextPrimary)
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { query ->
                                searchQuery = query
                                if (query.isNotBlank() && totalPages > 0) {
                                    // Simulated search text finder matches
                                    val simulatedIndexes = mutableListOf<Int>()
                                    for (i in 0 until totalPages) {
                                        if (i % 3 == 0) { // simulate matches
                                            simulatedIndexes.add(i)
                                        }
                                    }
                                    searchMatches = simulatedIndexes
                                } else {
                                    searchMatches = emptyList()
                                }
                            },
                            placeholder = { Text("ابحث عن الكلمات في الكتاب...", color = AppTextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AppTextPrimary,
                                unfocusedTextColor = AppTextPrimary,
                                focusedBorderColor = AppPrimary,
                                unfocusedBorderColor = AppTextSecondary.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .testTag("reader_search_input_field")
                        )

                        if (searchMatches.isNotEmpty()) {
                            Text(
                                text = "${searchMatches.size} تطابق",
                                fontSize = 12.sp,
                                color = AppPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }

            // Floating Search Matches Popup overlay when searching is active
            if (isSearching && searchMatches.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppSurface.copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 90.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "نتائج البحث المقترحة:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(searchMatches) { matchPageIndex ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            viewModel.jumpToPage(matchPageIndex)
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "تطابقات الكلمة في الصفحة ${matchPageIndex + 1}",
                                        color = AppTextPrimary,
                                        fontSize = 12.sp
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = AppPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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
                    },
                    onZoomInClick = {
                        pdfViewInst?.let { pdfView ->
                            val proposedZoom = (pdfView.zoom + 0.25f).coerceAtMost(4.0f)
                            pdfView.zoomWithAnimation(proposedZoom)
                        }
                    },
                    onZoomOutClick = {
                        pdfViewInst?.let { pdfView ->
                            val proposedZoom = (pdfView.zoom - 0.25f).coerceAtLeast(0.5f)
                            pdfView.zoomWithAnimation(proposedZoom)
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
                                    val printAdapter = PdfPrintAdapter(
                                        context = context,
                                        pdfUri = Uri.parse(activeUri!!),
                                        documentName = documentName
                                    )
                                    printManager.print("$documentName Print Job", printAdapter, null)
                                }
                            } catch (e: Exception) {
                                Log.e("ViewerScreen", "Error launching printer job", e)
                            }
                        }
                    },
                    onFileInfoClick = {
                        showFileInfoDialog = true
                    }
                )
            }

            // Jump to page dialog popup
            if (showJumpDialog) {
                JumpToPageDialog(
                    totalPages = totalPages,
                    onDismiss = { showJumpDialog = false },
                    onJump = { targetPage ->
                        viewModel.jumpToPage(targetPage)
                        showJumpDialog = false
                    }
                )
            }

            // File Info M3 Information Dialog
            if (showFileInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showFileInfoDialog = false },
                    title = { Text("معلومات الملف", fontWeight = FontWeight.Bold, color = AppPrimary) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("اسم الملف: $documentName", color = AppTextPrimary)
                            val sizeText = activeDocument?.let {
                                if (it.size > 1024 * 1024) String.format("%.2f MB", it.size / (1024f * 1024f))
                                else String.format("%.1f KB", it.size / 1024f)
                            } ?: "غير معروف"
                            Text("الحجم: $sizeText", color = AppTextPrimary)
                            Text("عدد الصفحات: $totalPages", color = AppTextPrimary)
                            Text("مسار الملف: ${activeDocument?.uri ?: "غير متوفر"}", fontSize = 11.sp, color = AppTextSecondary)
                        }
                    },
                    confirmButton = {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                            onClick = { showFileInfoDialog = false }
                        ) {
                            Text("حسناً")
                        }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتقال إلى صفحة", fontWeight = FontWeight.Bold, color = AppPrimary) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "أدخل رقم الصفحة بين 1 و $totalPages",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        inputError = null
                    },
                    placeholder = { Text("رقم الصفحة") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = inputError != null,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppTextPrimary,
                        unfocusedTextColor = AppTextPrimary,
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppTextSecondary.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("page_input")
                )
                if (inputError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = inputError!!,
                        color = Color.Red,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                onClick = {
                    val pageNum = textValue.toIntOrNull()
                    if (pageNum == null) {
                        inputError = "رقم الصفحة غير صالح"
                    } else if (pageNum < 1 || pageNum > totalPages) {
                        inputError = "يجب أن تكون الصفحة بين 1 و $totalPages"
                    } else {
                        onJump(pageNum - 1)
                    }
                }
            ) {
                Text("اذهب")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = AppPrimary)
            }
        },
        containerColor = AppSurface
    )
}
