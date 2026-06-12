package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BookmarkEntity
import com.example.ui.theme.*
import com.github.barteksc.pdfviewer.PDFView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookmarkDrawer(
    pdfUri: String,
    currentPage: Int,
    totalPages: Int,
    pageBookmarks: List<BookmarkEntity>,
    pdfViewInst: PDFView?,
    onJumpToPage: (Int) -> Unit,
    onAddBookmark: (String) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
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
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = "محتويات الملف",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                val showTocTab = tableOfContents.isNotEmpty()

                // Tab selectors: "الإشارات المرجعية" and "الصفحات المصغرة"
                TabRow(
                    selectedTabIndex = selectedDrawerTab,
                    containerColor = Color.Transparent,
                    contentColor = AppPrimary,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .testTag("drawer_tab_row")
                ) {
                    Tab(
                        selected = selectedDrawerTab == 0,
                        onClick = { selectedDrawerTab = 0 },
                        text = {
                            Text(
                                "الإشارات المرجعية",
                                fontSize = 13.sp,
                                fontWeight = if (selectedDrawerTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("bookmarks_tab")
                    )
                    Tab(
                        selected = selectedDrawerTab == 1,
                        onClick = { selectedDrawerTab = 1 },
                        text = {
                            Text(
                                "الصفحات المصغرة",
                                fontSize = 13.sp,
                                fontWeight = if (selectedDrawerTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("thumbnails_tab")
                    )
                    if (showTocTab) {
                        Tab(
                            selected = selectedDrawerTab == 2,
                            onClick = { selectedDrawerTab = 2 },
                            text = {
                                Text(
                                    "الفهرس",
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedDrawerTab == 2) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("toc_tab")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (selectedDrawerTab == 0) {
                        // TAB 1: BOOKMARKS
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (pageBookmarks.isEmpty()) {
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
                                                            // Close drawer + jump to (pageNumber - 1)
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
                                                    // Right side: Custom Page Icon (Arabic RTL means first in row)
                                                    Icon(
                                                        imageVector = Icons.Default.Bookmark,
                                                        contentDescription = null,
                                                        tint = AppPrimary,
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .testTag("bookmark_icon_primary")
                                                    )

                                                    Spacer(modifier = Modifier.width(16.dp))

                                                    // Center: Page number and Custom label details
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

                                                    // Left side: Small formatted timestamp
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
                                CircularProgressIndicator(color = AppPrimary)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                state = gridState,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                                    .testTag("thumbnails_lazy_grid")
                            ) {
                                items(totalPages) { pageIndex ->
                                    val isVisible = remember { derivedStateOf { visibleIndices.contains(pageIndex) } }

                                    // Render thread on visibility bound checking (lazy load)
                                    LaunchedEffect(pageIndex, isVisible.value) {
                                        if (isVisible.value && !thumbnailCache.containsKey(pageIndex) && pdfUri.isNotEmpty()) {
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    val uri = Uri.parse(pdfUri)
                                                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                                        PdfRenderer(pfd).use { renderer ->
                                                            if (pageIndex < renderer.pageCount) {
                                                                renderer.openPage(pageIndex).use { page ->
                                                                    // High resolution thumbnail
                                                                    val bitmap = Bitmap.createBitmap(140, 180, Bitmap.Config.ARGB_8888)
                                                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                                    thumbnailCache[pageIndex] = bitmap
                                                                }
                                                            }
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("BookmarkDrawer", "Error lazy rendering page $pageIndex", e)
                                                }
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.75f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AppBottomBarBg.copy(alpha = 0.4f))
                                            .clickable {
                                                onCloseDrawer()
                                                pdfViewInst?.jumpTo(pageIndex)
                                                onJumpToPage(pageIndex)
                                            }
                                            .testTag("thumbnail_card_item_$pageIndex"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val cachedBitmap = thumbnailCache[pageIndex]
                                        if (cachedBitmap != null) {
                                            Image(
                                                bitmap = cachedBitmap.asImageBitmap(),
                                                contentDescription = "الصفحة ${pageIndex + 1}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            CircularProgressIndicator(
                                                color = AppPrimary,
                                                modifier = Modifier.size(24.dp).testTag("rendering_progress_$pageIndex")
                                            )
                                        }

                                        // Page number badge at the bottom center of each thumbnail
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 6.dp)
                                                .background(
                                                    color = Color.Black.copy(alpha = 0.7f),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${pageIndex + 1}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.testTag("thumbnail_badge_text_$pageIndex")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (selectedDrawerTab == 2 && showTocTab) {
                        // TAB 3: TOC (Table of Contents)
                        val expandedMap = remember { mutableStateMapOf<String, Boolean>() }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top controls: "توسيع الكل" and "طي الكل"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                TextButton(
                                    onClick = {
                                        val parentKeys = getAllParentKeys(tableOfContents)
                                        expandedMap.clear()
                                        parentKeys.forEach { expandedMap[it] = true }
                                    },
                                    modifier = Modifier.testTag("toc_expand_all")
                                ) {
                                    Text(
                                        text = "توسيع الكل",
                                        color = AppPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .height(16.dp)
                                        .width(1.dp)
                                        .background(AppTextSecondary.copy(alpha = 0.3f))
                                )

                                TextButton(
                                    onClick = {
                                        expandedMap.clear()
                                    },
                                    modifier = Modifier.testTag("toc_collapse_all")
                                ) {
                                    Text(
                                        text = "طي الكل",
                                        color = AppTextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            HorizontalDivider(
                                thickness = 1.dp,
                                color = AppBottomBarBg.copy(alpha = 0.5f)
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("toc_lazy_column")
                            ) {
                                items(tableOfContents) { rootItem ->
                                    TocItemRow(
                                        item = rootItem,
                                        level = 0,
                                        expandedMap = expandedMap,
                                        onToggleExpand = { key ->
                                            expandedMap[key] = !(expandedMap[key] ?: false)
                                        },
                                        onJumpTo = { item ->
                                            if (onTocItemClicked != null) {
                                                onTocItemClicked(item)
                                            } else {
                                                onCloseDrawer()
                                                pdfViewInst?.jumpTo(item.pageIdx.toInt())
                                                onJumpToPage(item.pageIdx.toInt())
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dialog: Add Bookmark label
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("إضافة إشارة مرجعية", fontWeight = FontWeight.Bold, color = AppPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("أدخل تسمية توضيحية لصفحة الحالية (${currentPage + 1}):", color = AppTextPrimary)
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
                },
                confirmButton = {
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

        // Dialog: Delete confirmation
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
