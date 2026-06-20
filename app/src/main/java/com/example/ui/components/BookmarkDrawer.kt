package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.BookmarkEntity
import com.example.data.AudioBookmarkEntity
import com.example.ui.theme.*
import com.github.barteksc.pdfviewer.PDFView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookmarkDrawer(
    pdfUri: String,
    currentPage: Int,
    totalPages: Int,
    pageBookmarks: List<BookmarkEntity>,
    audioBookmarks: List<AudioBookmarkEntity> = emptyList(),
    pdfViewInst: PDFView?,
    onJumpToPage: (Int) -> Unit,
    onAddBookmark: (String) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
    onAddAudioBookmark: (AudioBookmarkEntity) -> Unit = {},
    onDeleteAudioBookmark: (AudioBookmarkEntity) -> Unit = {},
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    tableOfContents: List<com.shockwave.pdfium.PdfDocument.Bookmark> = emptyList(),
    onTocItemClicked: ((com.shockwave.pdfium.PdfDocument.Bookmark) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var selectedDrawerTab by remember { mutableStateOf(0) } // 0 = Bookmarks, 1 = Thumbnails
    
    // Add Bookmark Dialog custom states
    var showAddDialog by remember { mutableStateOf(false) }
    var bookmarkLabelInput by remember { mutableStateOf("") }

    // Delete Bookmark state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var bookmarkToDelete by remember { mutableStateOf<BookmarkEntity?>(null) }

    // Delete Audio Bookmark state
    var showAudioDeleteDialog by remember { mutableStateOf(false) }
    var audioBookmarkToDelete by remember { mutableStateOf<AudioBookmarkEntity?>(null) }

    // Audio Playback in sidebar state
    var currentlyPlayingPath by remember { mutableStateOf<String?>(null) }
    val sidebarPlayer = remember { MediaPlayer() }

    LaunchedEffect(currentlyPlayingPath) {
        if (currentlyPlayingPath == null) {
            try {
                if (sidebarPlayer.isPlaying) {
                    sidebarPlayer.stop()
                }
                sidebarPlayer.reset()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                sidebarPlayer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Render caching map for Thumbnails
    val thumbnailCache = remember { mutableStateMapOf<Int, Bitmap>() }
    val gridState = rememberLazyGridState()

    // Derived lazy rendering bounds based on visible items
    val visibleIndices by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) {
                emptyList()
            } else {
                visibleItemsInfo.map { it.index }
            }
        }
    }

    // Force RTL layout direction for right sidebar and Arabic styling
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalDrawerSheet(
            drawerContainerColor = AppSurface,
            modifier = modifier
                .fillMaxHeight()
                .width(320.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {} // explicitly consume taps inside the panel content area to prevent click-through dismissal
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
            ) {
                // Header details
                Text(
                    text = "محتويات المستند",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTextPrimary,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .testTag("drawer_header_title")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Modern visual Switch tabs
                TabRow(
                    selectedTabIndex = selectedDrawerTab,
                    containerColor = AppBottomBarBg,
                    contentColor = AppPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedDrawerTab]),
                            color = AppPrimary
                        )
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedDrawerTab == 0,
                        onClick = { selectedDrawerTab = 0 },
                        text = { Text("الإشارات", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("drawer_tab_bookmarks")
                    )
                    Tab(
                        selected = selectedDrawerTab == 1,
                        onClick = { selectedDrawerTab = 1 },
                        text = { Text("الصفحات", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("drawer_tab_thumbnails")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable container contents
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (selectedDrawerTab == 0) {
                        // TAB 1: BOOKMARKS
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (pageBookmarks.isEmpty() && audioBookmarks.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "لا توجد إشارات مرجعية محفوظة.",
                                            color = AppTextSecondary,
                                            fontSize = 14.sp
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("bookmarks_lazy_column"),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (pageBookmarks.isNotEmpty()) {
                                            item {
                                                Text(
                                                    text = "إشارات مرجعية نصية",
                                                    color = AppPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                                )
                                            }
                                            items(pageBookmarks) { bookmark ->
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = Color.Transparent
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .combinedClickable(
                                                            onLongClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                bookmarkToDelete = bookmark
                                                                showDeleteDialog = true
                                                            },
                                                            onClick = {
                                                                onCloseDrawer()
                                                                val targetIndex = (bookmark.pageNumber - 1).coerceAtLeast(0)
                                                                pdfViewInst?.jumpTo(targetIndex)
                                                                onJumpToPage(targetIndex)
                                                            }
                                                        )
                                                        .testTag("bookmark_item_card_${bookmark.pageNumber}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 20.dp, vertical = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Bookmark,
                                                            contentDescription = null,
                                                            tint = AppPrimary,
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .testTag("bookmark_icon_primary")
                                                        )

                                                        Spacer(modifier = Modifier.width(16.dp))

                                                        Column(
                                                            modifier = Modifier.weight(1f),
                                                            horizontalAlignment = Alignment.Start
                                                        ) {
                                                            Text(
                                                                text = "صفحة ${bookmark.pageNumber}",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = AppTextPrimary,
                                                                modifier = Modifier.testTag("bookmark_page_title_${bookmark.pageNumber}")
                                                            )
                                                            Text(
                                                                text = bookmark.label,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = AppTextSecondary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.testTag("bookmark_page_label_${bookmark.pageNumber}")
                                                            )
                                                        }

                                                        val timeStr = remember(bookmark.createdAt) {
                                                            val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                                            sdf.format(java.util.Date(bookmark.createdAt))
                                                        }
                                                        Text(
                                                            text = timeStr,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AppTextSecondary,
                                                            fontSize = 10.sp,
                                                            modifier = Modifier.testTag("bookmark_timestamp_${bookmark.pageNumber}")
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (audioBookmarks.isNotEmpty()) {
                                            item {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text(
                                                    text = "إشارات مرجعية صوتية",
                                                    color = Color(0xFFFF4949),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                                )
                                            }
                                            items(audioBookmarks) { audioBookmark ->
                                                val isPlaying = currentlyPlayingPath == audioBookmark.audioPath
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = Color.Transparent
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .combinedClickable(
                                                            onLongClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                audioBookmarkToDelete = audioBookmark
                                                                showAudioDeleteDialog = true
                                                            },
                                                            onClick = {
                                                                onCloseDrawer()
                                                                val targetIndex = (audioBookmark.pageNumber - 1).coerceAtLeast(0)
                                                                pdfViewInst?.jumpTo(targetIndex)
                                                                onJumpToPage(targetIndex)
                                                            }
                                                        )
                                                        .testTag("audio_bookmark_card_${audioBookmark.id}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Mic,
                                                            contentDescription = null,
                                                            tint = AppPrimary,
                                                            modifier = Modifier.size(20.dp)
                                                        )

                                                        Spacer(modifier = Modifier.width(16.dp))

                                                        Column(
                                                            modifier = Modifier.weight(1f),
                                                            horizontalAlignment = Alignment.Start
                                                        ) {
                                                            Text(
                                                                text = "صفحة ${audioBookmark.pageNumber}",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = AppTextPrimary
                                                            )
                                                            val secs = audioBookmark.durationMs / 1000
                                                            val durationText = String.format("%02d:%02d", secs / 60, secs % 60)
                                                            Text(
                                                                text = "مدة التسجيل: $durationText",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = AppTextSecondary
                                                            )
                                                        }

                                                        IconButton(
                                                            onClick = {
                                                                if (isPlaying) {
                                                                    currentlyPlayingPath = null
                                                                } else {
                                                                    try {
                                                                        sidebarPlayer.reset()
                                                                        sidebarPlayer.setDataSource(audioBookmark.audioPath)
                                                                        sidebarPlayer.prepare()
                                                                        sidebarPlayer.start()
                                                                        currentlyPlayingPath = audioBookmark.audioPath
                                                                        sidebarPlayer.setOnCompletionListener {
                                                                            currentlyPlayingPath = null
                                                                        }
                                                                    } catch (e: Exception) {
                                                                        e.printStackTrace()
                                                                        currentlyPlayingPath = null
                                                                    }
                                                                }
                                                            },
                                                            modifier = Modifier.size(36.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                                contentDescription = "Play/Stop bookmark",
                                                                tint = AppPrimary,
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // At the bottom of the tab, a button "+ إضافة إشارة للصفحة الحالية"
                            Button(
                                onClick = {
                                    bookmarkLabelInput = "صفحة ${currentPage + 1}"
                                    showAddDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .height(48.dp)
                                    .testTag("add_current_page_bookmark_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "+ إضافة إشارة للصفحة الحالية",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else if (selectedDrawerTab == 1) {
                        // TAB 2: THUMBNAILS
                        if (totalPages == 0) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "جاري قراءة صفحات الملف...",
                                    color = AppTextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                state = gridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp)
                                    .testTag("thumbnails_grid"),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(totalPages) { index ->
                                    val isCurrent = index == currentPage
                                    
                                    val isVisible = index in visibleIndices
                                    
                                    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
                                    val scope = rememberCoroutineScope()
                                    
                                    LaunchedEffect(isVisible) {
                                        if (isVisible && renderedBitmap == null) {
                                            val cached = thumbnailCache[index]
                                            if (cached != null) {
                                                renderedBitmap = cached
                                            } else {
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        val fileUriObj = Uri.parse(pdfUri)
                                                        context.contentResolver.openFileDescriptor(fileUriObj, "r")?.use { pfd ->
                                                            val pdfRenderer = PdfRenderer(pfd)
                                                            if (index < pdfRenderer.pageCount) {
                                                                val page = pdfRenderer.openPage(index)
                                                                
                                                                val ratio = page.height.toFloat() / page.width.toFloat()
                                                                val targetWidth = 140
                                                                val targetHeight = (targetWidth * ratio).toInt().coerceIn(120, 240)
                                                                
                                                                val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                                                bmp.eraseColor(android.graphics.Color.WHITE)
                                                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                                
                                                                withContext(Dispatchers.Main) {
                                                                    thumbnailCache[index] = bmp
                                                                    renderedBitmap = bmp
                                                                }
                                                                page.close()
                                                            }
                                                            pdfRenderer.close()
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("BookmarkDrawer", "Error lazy rendering page $index", e)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCurrent) AppPrimary.copy(alpha = 0.12f) else Color.Transparent
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        border = if (isCurrent) androidx.compose.foundation.BorderStroke(2.dp, AppPrimary) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onCloseDrawer()
                                                pdfViewInst?.jumpTo(index)
                                                onJumpToPage(index)
                                            }
                                            .testTag("thumbnail_card_$index")
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .aspectRatio(0.75f)
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.White),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val bmp = renderedBitmap
                                                if (bmp != null) {
                                                    Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = "صفحة ${index + 1}",
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 2.dp,
                                                        color = AppPrimary.copy(alpha = 0.5f)
                                                    )
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCurrent) AppPrimary else AppTextSecondary
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

        // Dialog: Enhanced Add Bookmark
        if (showAddDialog) {
            var activeTab by remember { mutableStateOf(0) } // 0 = Text, 1 = Audio
            val micPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (!isGranted) {
                    // Show message
                }
            }

            fun checkAndRequestMicPermission(onGranted: () -> Unit) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    onGranted()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { 
                    Text("إضافة إشارة مرجعية في صفحة ${currentPage + 1}", fontWeight = FontWeight.Bold, color = AppPrimary, fontSize = 16.sp) 
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TabRow(
                            selectedTabIndex = activeTab,
                            containerColor = AppBottomBarBg,
                            contentColor = AppPrimary,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        ) {
                            Tab(
                                selected = activeTab == 0,
                                onClick = { activeTab = 0 },
                                text = { Text("إشارة نصية", fontSize = 12.sp) }
                            )
                            Tab(
                                selected = activeTab == 1,
                                onClick = { 
                                    checkAndRequestMicPermission {
                                        activeTab = 1
                                    }
                                },
                                text = { Text("إشارة صوتية", fontSize = 12.sp) }
                            )
                        }

                        if (activeTab == 0) {
                            // TEXT BOOKMARK TAB
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("أدخل تسمية توضيحية للإشارة:", color = AppTextSecondary, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = bookmarkLabelInput,
                                    onValueChange = { bookmarkLabelInput = it },
                                    placeholder = { Text("مثال: بداية الفصل الأول...") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = AppTextPrimary,
                                        unfocusedTextColor = AppTextPrimary,
                                        focusedBorderColor = AppPrimary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bookmark_label_input_field")
                                )
                            }
                        } else {
                            // AUDIO BOOKMARK TAB
                            var isRecording by remember { mutableStateOf(false) }
                            var hasRecorded by remember { mutableStateOf(false) }
                            var recorderInstance by remember { mutableStateOf<MediaRecorder?>(null) }
                            var audioFile by remember { mutableStateOf<File?>(null) }
                            var startTime by remember { mutableStateOf(0L) }
                            var timerSeconds by remember { mutableStateOf(0) }
                            var isPreviewPlaying by remember { mutableStateOf(false) }
                            val previewPlayer = remember { MediaPlayer() }

                            DisposableEffect(Unit) {
                                onDispose {
                                    try {
                                        recorderInstance?.release()
                                    } catch (e: Exception) {}
                                    try {
                                        previewPlayer.release()
                                    } catch (e: Exception) {}
                                }
                            }

                            LaunchedEffect(isRecording) {
                                if (isRecording) {
                                    timerSeconds = 0
                                    while (isRecording) {
                                        delay(1000)
                                        timerSeconds++
                                    }
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                WaveformVisualizer(isRecording = isRecording)
                                
                                val durationText = String.format("%02d:%02d", timerSeconds / 60, timerSeconds % 60)
                                Text(
                                    text = if (isRecording) "جاري التسجيل... $durationText" else if (hasRecorded) "تم الانتهاء من التسجيل" else "اضغط على الزر الأحمر لبدء تسجيل صوتك",
                                    color = if (isRecording) Color.Red else AppTextPrimary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Big Record Button
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(if (isRecording) Color.Red.copy(alpha = 0.15f) else Color.Transparent)
                                            .border(2.dp, if (isRecording) Color.Red else AppPrimary, CircleShape)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (isRecording) {
                                                    // Stop recording
                                                    try {
                                                        recorderInstance?.apply {
                                                            stop()
                                                            release()
                                                        }
                                                        hasRecorded = true
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    } finally {
                                                        isRecording = false
                                                        recorderInstance = null
                                                    }
                                                } else {
                                                    // Start recording
                                                    try {
                                                        if (previewPlayer.isPlaying) {
                                                            previewPlayer.stop()
                                                            isPreviewPlaying = false
                                                        }
                                                        previewPlayer.reset()

                                                        val file = File(context.filesDir, "bookmark_audio_${System.currentTimeMillis()}.aac")
                                                        audioFile = file
                                                        
                                                        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                            MediaRecorder(context)
                                                        } else {
                                                            MediaRecorder()
                                                        }
                                                        recorder.apply {
                                                            setAudioSource(MediaRecorder.AudioSource.MIC)
                                                            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                                                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                                            setAudioSamplingRate(44100)
                                                            setAudioEncodingBitRate(128000)
                                                            setOutputFile(file.absolutePath)
                                                            prepare()
                                                            start()
                                                        }
                                                        recorderInstance = recorder
                                                        isRecording = true
                                                        startTime = System.currentTimeMillis()
                                                        hasRecorded = false
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            }
                                            .testTag("dialog_mic_record_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                            contentDescription = "Mic Record button",
                                            tint = if (isRecording) Color.Red else AppPrimary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                if (hasRecorded && audioFile != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = {
                                            if (isPreviewPlaying) {
                                                try {
                                                    if (previewPlayer.isPlaying) {
                                                        previewPlayer.stop()
                                                    }
                                                    previewPlayer.reset()
                                                } catch (e: Exception) {}
                                                isPreviewPlaying = false
                                            } else {
                                                try {
                                                    previewPlayer.reset()
                                                    previewPlayer.setDataSource(audioFile!!.absolutePath)
                                                    previewPlayer.prepare()
                                                    previewPlayer.start()
                                                    isPreviewPlaying = true
                                                    previewPlayer.setOnCompletionListener {
                                                        isPreviewPlaying = false
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AppBottomBarBg),
                                        modifier = Modifier.testTag("preview_audio_bookmark_button")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = AppPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isPreviewPlaying) "إيقاف المعاينة" else "تشغيل المعاينة الصوتية",
                                                color = AppPrimary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                        onClick = {
                                            val fileObj = audioFile
                                            if (fileObj != null && fileObj.exists()) {
                                                var durationMs = 0L
                                                try {
                                                    val tempPlayer = MediaPlayer()
                                                    tempPlayer.setDataSource(fileObj.absolutePath)
                                                    tempPlayer.prepare()
                                                    durationMs = tempPlayer.duration.toLong()
                                                    tempPlayer.release()
                                                } catch (e: Exception) {
                                                    durationMs = (timerSeconds * 1000).toLong()
                                                }

                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onAddAudioBookmark(
                                                    AudioBookmarkEntity(
                                                        fileUri = pdfUri,
                                                        pageNumber = currentPage + 1,
                                                        audioPath = fileObj.absolutePath,
                                                        durationMs = durationMs,
                                                        createdAt = System.currentTimeMillis()
                                                    )
                                                )
                                                showAddDialog = false
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("save_audio_bookmark_button")
                                    ) {
                                        Text("حفظ الإشارة الصوتية")
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (activeTab == 0) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                            onClick = {
                                if (bookmarkLabelInput.isNotBlank()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAddBookmark(bookmarkLabelInput)
                                    showAddDialog = false
                                }
                            },
                            modifier = Modifier.testTag("confirm_add_bookmark_btn")
                        ) {
                            Text("إضافة")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAddDialog = false },
                        modifier = Modifier.testTag("cancel_add_bookmark_btn")
                    ) {
                        Text("إلغاء", color = AppPrimary)
                    }
                },
                containerColor = AppSurface
            )
        }

        // Dialog: Text Bookmark Delete confirmation
        if (showDeleteDialog && bookmarkToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("حذف إشارة مرجعية", fontWeight = FontWeight.Bold, color = Color.Red) },
                text = {
                    Text(
                        "هل أنت متأكد من رغبتك في حذف الإشارة المرجعية لـ \"${bookmarkToDelete?.label}\" في صفحة ${bookmarkToDelete?.pageNumber}؟",
                        color = AppTextPrimary
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        onClick = {
                            bookmarkToDelete?.let { onDeleteBookmark(it) }
                            showDeleteDialog = false
                            bookmarkToDelete = null
                        },
                        modifier = Modifier.testTag("confirm_delete_bookmark_btn")
                    ) {
                        Text("حذف", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            bookmarkToDelete = null
                        },
                        modifier = Modifier.testTag("cancel_delete_bookmark_btn")
                    ) {
                        Text("إلغاء", color = AppPrimary)
                    }
                },
                containerColor = AppSurface
            )
        }

        // Dialog: Audio Bookmark Delete confirmation
        if (showAudioDeleteDialog && audioBookmarkToDelete != null) {
            AlertDialog(
                onDismissRequest = { showAudioDeleteDialog = false },
                title = { Text("حذف إشارة مرجعية صوتية", fontWeight = FontWeight.Bold, color = Color.Red) },
                text = {
                    Text(
                        "هل أنت متأكد من رغبتك في حذف الإشارة المرجعية الصوتية في صفحة ${audioBookmarkToDelete?.pageNumber}؟",
                        color = AppTextPrimary
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        onClick = {
                            audioBookmarkToDelete?.let {
                                try {
                                    val f = File(it.audioPath)
                                    if (f.exists()) {
                                        f.delete()
                                    }
                                } catch (e: Exception) {}
                                onDeleteAudioBookmark(it)
                            }
                            showAudioDeleteDialog = false
                            audioBookmarkToDelete = null
                        }
                    ) {
                        Text("حذف وصوت الإشارة", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAudioDeleteDialog = false
                            audioBookmarkToDelete = null
                        }
                    ) {
                        Text("إلغاء", color = AppPrimary)
                    }
                },
                containerColor = AppSurface
            )
        }
    }
}

// Waveform visualizer animates based on active recording state or idle heights
@Composable
fun WaveformVisualizer(isRecording: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val heights = (0 until 20).map { index ->
        val delay = index * 100
        val anim = infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 400 + (index % 3) * 100, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
        if (isRecording) anim.value else 0.15f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { heightFactor ->
            val barHeight = 35.dp * heightFactor
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(4.dp)
                    .height(barHeight)
                    .background(AppPrimary, RoundedCornerShape(2.dp))
            )
        }
    }
}

private fun getAllParentKeys(items: List<com.shockwave.pdfium.PdfDocument.Bookmark>): List<String> {
    val keys = mutableListOf<String>()
    fun traverse(node: com.shockwave.pdfium.PdfDocument.Bookmark) {
        val hasChildren = node.children != null && node.children.isNotEmpty()
        if (hasChildren) {
            keys.add("${node.title}_${node.pageIdx}")
            node.children.forEach { child ->
                traverse(child)
            }
        }
    }
    items.forEach { traverse(it) }
    return keys
}

@Composable
private fun TocItemRow(
    item: com.shockwave.pdfium.PdfDocument.Bookmark,
    level: Int,
    expandedMap: Map<String, Boolean>,
    onToggleExpand: (String) -> Unit,
    onJumpTo: (com.shockwave.pdfium.PdfDocument.Bookmark) -> Unit
) {
    if (level >= 3) return

    val key = "${item.title}_${item.pageIdx}"
    val hasChildren = item.children != null && item.children.isNotEmpty()
    val isExpanded = expandedMap[key] == true

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (hasChildren) {
                        onToggleExpand(key)
                    } else {
                        onJumpTo(item)
                    }
                }
                .padding(vertical = 12.dp, horizontal = 16.dp)
                .padding(start = (level * 24).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasChildren) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowLeft,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = AppPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Spacer(modifier = Modifier.width(26.dp))
            }

            Text(
                text = item.title ?: "",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (level == 0) 15.sp else 13.sp,
                    color = if (level == 0) AppTextPrimary else AppTextSecondary,
                    fontWeight = if (level == 0) FontWeight.Bold else FontWeight.Normal
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "${item.pageIdx + 1}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = if (level == 0) 14.sp else 12.sp,
                    color = AppTextSecondary
                )
            )
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = AppBottomBarBg.copy(alpha = 0.5f)
        )

        if (hasChildren) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isExpanded,
                enter = androidx.compose.animation.expandVertically(animationSpec = tween(200)),
                exit = androidx.compose.animation.shrinkVertically(animationSpec = tween(200))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    item.children.forEach { child ->
                        TocItemRow(
                            item = child,
                            level = level + 1,
                            expandedMap = expandedMap,
                            onToggleExpand = onToggleExpand,
                            onJumpTo = onJumpTo
                        )
                    }
                }
            }
        }
    }
}
