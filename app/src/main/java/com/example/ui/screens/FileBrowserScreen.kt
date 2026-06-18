package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.data.RecentFileEntity
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Thumbnail and Metadata Cache
object BrowserCache {
    val bitmapCache = LruCache<String, Bitmap>(100)
    val pageCountCache = mutableMapOf<String, Int>()
}

@Composable
fun PdfBrowserThumbnail(file: File, modifier: Modifier = Modifier) {
    var bitmap by remember(file.absolutePath) { mutableStateOf<Bitmap?>(BrowserCache.bitmapCache.get(file.absolutePath)) }

    if (bitmap == null) {
        LaunchedEffect(file.absolutePath) {
            withContext(Dispatchers.IO) {
                try {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val ratio = page.width.toFloat() / page.height.toFloat()
                        val targetHeight = 160
                        val targetWidth = (targetHeight * ratio).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        BrowserCache.bitmapCache.put(file.absolutePath, bmp)
                        bitmap = bmp
                    }
                    renderer.close()
                    pfd.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(AppSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = AppPrimary.copy(alpha = 0.5f),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
fun rememberPageCount(file: File): Int {
    var count by remember(file.absolutePath) { mutableStateOf(BrowserCache.pageCountCache[file.absolutePath] ?: 0) }
    if (count == 0) {
        LaunchedEffect(file.absolutePath) {
            withContext(Dispatchers.IO) {
                try {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    val pages = renderer.pageCount
                    BrowserCache.pageCountCache[file.absolutePath] = pages
                    count = pages
                    renderer.close()
                    pfd.close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
    return count
}

fun formatBrowserFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileBrowserScreen(
    viewModel: PdfViewModel,
    onPdfOpened: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. Storage roots setup
    val roots = remember(context) {
        val list = mutableListOf<File>()
        try {
            Environment.getExternalStorageDirectory()?.let { list.add(it) }
        } catch (e: Exception) {}

        try {
            val extDirs = context.getExternalFilesDirs(null)
            if (extDirs.size > 1 && extDirs[1] != null) {
                var file = extDirs[1]
                while (file != null && !file.absolutePath.contains("/Android")) {
                    file = file.parentFile
                }
                if (file != null && file.parentFile != null) {
                    list.add(file.parentFile!!.parentFile)
                }
            }
        } catch (e: Exception) {}
        list
    }

    // Dynamic states
    var currentDirectory by remember { mutableStateOf<File?>(if (roots.size == 1) roots.first() else null) }
    var fileItems by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoadingFiles by remember { mutableStateOf(false) }

    // Settings
    var isGridView by remember { mutableStateOf(true) }
    var browserPdfOnly by remember { mutableStateOf(true) }
    var sortOption by remember { mutableStateOf("type") } // "name", "newest", "size", "type"

    // Search
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Selection
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf<Set<File>>(emptySet()) }

    // Dialog & bottom sheet states
    var activeBottomSheetItem by remember { mutableStateOf<File?>(null) }
    var showRenameDialogItem by remember { mutableStateOf<File?>(null) }
    var showDeleteDialogItem by remember { mutableStateOf<File?>(null) }
    var showInfoDialogItem by remember { mutableStateOf<File?>(null) }
    var showFolderPickerDialogItem by remember { mutableStateOf<Pair<File, Boolean>?>(null) } // File, isCopy
    var activeProgressDialog by remember { mutableStateOf<ProgressState?>(null) }

    // Favorites track
    val recentDocs by viewModel.recentDocuments.collectAsState()
    val favoritedUris = remember(recentDocs) {
        recentDocs.filter { it.isFavorite }.map { it.uri }.toSet()
    }

    // File listing effect
    LaunchedEffect(currentDirectory, searchQuery, browserPdfOnly, sortOption) {
        val dir = currentDirectory
        if (dir == null) {
            fileItems = emptyList()
            return@LaunchedEffect
        }
        isLoadingFiles = true
        withContext(Dispatchers.IO) {
            val files = dir.listFiles()?.toList() ?: emptyList()
            val filtered = files.filter { file ->
                // Filter pdf only
                val matchesType = !browserPdfOnly || file.isDirectory || file.name.lowercase(Locale.ROOT).endsWith(".pdf")
                // Search term
                val matchesSearch = searchQuery.isBlank() || file.name.lowercase(Locale.ROOT).contains(searchQuery.lowercase(Locale.ROOT))
                matchesType && matchesSearch
            }

            // Sort
            val (dirs, nonDirs) = filtered.partition { it.isDirectory }
            val sortedDirs = when (sortOption) {
                "name" -> dirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                "newest" -> dirs.sortedByDescending { it.lastModified() }
                "size" -> dirs.sortedByDescending { it.length() }
                else -> dirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
            val sortedFiles = when (sortOption) {
                "name" -> nonDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                "newest" -> nonDirs.sortedByDescending { it.lastModified() }
                "size" -> nonDirs.sortedByDescending { it.length() }
                else -> nonDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }

            val finalItems = if (sortOption == "type") {
                sortedDirs + sortedFiles
            } else {
                (sortedDirs + sortedFiles).let { all ->
                    when (sortOption) {
                        "name" -> all.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                        "newest" -> all.sortedByDescending { it.lastModified() }
                        "size" -> all.sortedByDescending { it.length() }
                        else -> all
                    }
                }
            }

            fileItems = finalItems
        }
        isLoadingFiles = false
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            if (isMultiSelectMode) {
                // Multi-select Top bar
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedItems.size} محدد",
                            color = AppTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isMultiSelectMode = false
                                selectedItems = emptySet()
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = AppTextPrimary)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val pdfs = selectedItems.filter { !it.isDirectory && it.name.lowercase(Locale.ROOT).endsWith(".pdf") }
                                if (pdfs.isEmpty()) {
                                    Toast.makeText(context, "لم يتم تحديد أي ملفات PDF للمشاركة", Toast.LENGTH_SHORT).show()
                                } else {
                                    shareMultipleFiles(context, pdfs)
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = AppTextPrimary)
                        }
                        IconButton(
                            onClick = {
                                // Bulk Delete Dialog
                                showDeleteDialogItem = File("") // DUMMY indicates multi select
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D0D0F))
                )
            } else {
                // Standard Top bar
                TopAppBar(
                    title = {
                        if (isSearching) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("بحث عن ملف...", color = AppTextSecondary) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = AppTextSecondary)
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            isSearching = false
                                            searchQuery = ""
                                        }
                                    ) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = AppTextSecondary)
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("browser_search_input")
                            )
                        } else {
                            Text(
                                text = "تصفح المجلدات",
                                color = AppTextPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                        }
                    },
                    actions = {
                        if (!isSearching) {
                            IconButton(onClick = { isSearching = true }) {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = AppTextPrimary)
                            }
                        }
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                                contentDescription = "Toggle Grid/List",
                                tint = AppTextPrimary
                            )
                        }

                        var showOverflow by remember { mutableStateOf(false) }
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More", tint = AppTextPrimary)
                        }

                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                            modifier = Modifier.background(AppSurface)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("إظهار ملفات PDF فقط", color = AppTextPrimary)
                                        Switch(
                                            checked = browserPdfOnly,
                                            onCheckedChange = { browserPdfOnly = it },
                                            modifier = Modifier.scale(0.8f)
                                        )
                                    }
                                },
                                onClick = { /* Do handled by Switch */ }
                            )

                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = AppTextSecondary) },
                                text = { Text("الترتيب: الاسم أ→ي", color = if (sortOption == "name") AppPrimary else AppTextPrimary) },
                                onClick = {
                                    sortOption = "name"
                                    showOverflow = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null, tint = AppTextSecondary) },
                                text = { Text("الترتيب: الأحدث أولاً", color = if (sortOption == "newest") AppPrimary else AppTextPrimary) },
                                onClick = {
                                    sortOption = "newest"
                                    showOverflow = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null, tint = AppTextSecondary) },
                                text = { Text("الترتيب: الحجم الأكبر", color = if (sortOption == "size") AppPrimary else AppTextPrimary) },
                                onClick = {
                                    sortOption = "size"
                                    showOverflow = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Category, contentDescription = null, tint = AppTextSecondary) },
                                text = { Text("الترتيب: النوع أولاً", color = if (sortOption == "type") AppPrimary else AppTextPrimary) },
                                onClick = {
                                    sortOption = "type"
                                    showOverflow = false
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D0D0F))
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 2. Breadcrumb Navigation
            if (currentDirectory != null) {
                val segments = remember(currentDirectory, roots) {
                    val root = roots.find { currentDirectory!!.absolutePath.startsWith(it.absolutePath) } ?: roots.first()
                    val pathList = mutableListOf<BreadcrumbSegment>()
                    val rootName = if (root.absolutePath == Environment.getExternalStorageDirectory().absolutePath) {
                        "التخزين الداخلي"
                    } else {
                        "بطاقة SD"
                    }
                    pathList.add(BreadcrumbSegment(rootName, root))

                    if (currentDirectory!!.absolutePath != root.absolutePath) {
                        val relPath = currentDirectory!!.absolutePath.substring(root.absolutePath.length).trim('/')
                        if (relPath.isNotEmpty()) {
                            val parts = relPath.split('/')
                            var tempFile = root
                            for (part in parts) {
                                tempFile = File(tempFile, part)
                                pathList.add(BreadcrumbSegment(part, tempFile))
                            }
                        }
                    }
                    pathList
                }

                val breadcrumbScrollState = rememberScrollState()
                LaunchedEffect(segments.size) {
                    breadcrumbScrollState.animateScrollTo(breadcrumbScrollState.maxValue)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D0D0F))
                        .horizontalScroll(breadcrumbScrollState)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    segments.forEachIndexed { index, segment ->
                        val isLast = index == segments.size - 1
                        TextButton(
                            onClick = {
                                if (!isLast) {
                                    currentDirectory = segment.file
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = segment.name,
                                color = if (isLast) AppTextPrimary else AppTextSecondary,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }

                        if (!isLast) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = null,
                                tint = AppTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 3. Body contents
            if (currentDirectory == null) {
                // Root Storage Selection Cards (If multiple available)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "اختر وحدة التخزين",
                            color = AppTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        roots.forEach { root ->
                            val isInternal = root.absolutePath == Environment.getExternalStorageDirectory().absolutePath
                            val title = if (isInternal) "التخزين الداخلي" else "بطاقة SD"
                            val icon = if (isInternal) Icons.Default.Storage else Icons.Default.SdCard

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .clickable { currentDirectory = root },
                                colors = CardDefaults.cardColors(containerColor = AppSurface),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = AppPrimary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(title, color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(root.absolutePath, color = AppTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Browsing files/folders list
                if (isLoadingFiles) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppPrimary)
                    }
                } else if (fileItems.isEmpty()) {
                    // Empty folder state
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = AppTextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "لا توجد نتائج لـ «$searchQuery»" else "هذا المجلد فارغ",
                                color = AppTextSecondary,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    if (isGridView) {
                        // Grid Mode (2 columns)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(fileItems, key = { it.absolutePath }) { file ->
                                GridBrowserItem(
                                    file = file,
                                    isFavorited = favoritedUris.contains(Uri.fromFile(file).toString()),
                                    isSelected = selectedItems.contains(file),
                                    isMultiSelectActive = isMultiSelectMode,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            selectedItems = if (selectedItems.contains(file)) {
                                                selectedItems - file
                                            } else {
                                                selectedItems + file
                                            }
                                            if (selectedItems.isEmpty()) {
                                                isMultiSelectMode = false
                                            }
                                        } else {
                                            if (file.isDirectory) {
                                                currentDirectory = file
                                            } else if (file.name.lowercase(Locale.ROOT).endsWith(".pdf")) {
                                                viewModel.selectDocument(context, Uri.fromFile(file))
                                                onPdfOpened(Uri.fromFile(file))
                                            } else {
                                                Toast.makeText(context, "هذا الملف ليس PDF", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!isMultiSelectMode) {
                                            isMultiSelectMode = true
                                            selectedItems = setOf(file)
                                        }
                                    },
                                    onMoreClick = {
                                        activeBottomSheetItem = file
                                    },
                                    onFavoriteToggle = {
                                        val isFavStr = Uri.fromFile(file).toString()
                                        val isFav = favoritedUris.contains(isFavStr)
                                        viewModel.toggleFavoriteForFile(file, !isFav)
                                    }
                                )
                            }
                        }
                    } else {
                        // List Mode
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(fileItems, key = { it.absolutePath }) { file ->
                                ListBrowserItem(
                                    file = file,
                                    isFavorited = favoritedUris.contains(Uri.fromFile(file).toString()),
                                    isSelected = selectedItems.contains(file),
                                    isMultiSelectActive = isMultiSelectMode,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            selectedItems = if (selectedItems.contains(file)) {
                                                selectedItems - file
                                            } else {
                                                selectedItems + file
                                            }
                                            if (selectedItems.isEmpty()) {
                                                isMultiSelectMode = false
                                            }
                                        } else {
                                            if (file.isDirectory) {
                                                currentDirectory = file
                                            } else if (file.name.lowercase(Locale.ROOT).endsWith(".pdf")) {
                                                viewModel.selectDocument(context, Uri.fromFile(file))
                                                onPdfOpened(Uri.fromFile(file))
                                            } else {
                                                Toast.makeText(context, "هذا الملف ليس PDF", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!isMultiSelectMode) {
                                            isMultiSelectMode = true
                                            selectedItems = setOf(file)
                                        }
                                    },
                                    onMoreClick = {
                                        activeBottomSheetItem = file
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // A. Bottom Sheet Context Menu
    activeBottomSheetItem?.let { file ->
        val isFolder = file.isDirectory
        ModalBottomSheet(
            onDismissRequest = { activeBottomSheetItem = null },
            containerColor = AppSurface,
            contentColor = AppTextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isFolder) Icons.Default.Folder else Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = if (isFolder) Color(0xFFFFD93D) else AppPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.name, fontWeight = FontWeight.Bold, color = AppTextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = if (isFolder) "مجلد" else formatBrowserFileSize(file.length()),
                            color = AppTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                if (isFolder) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = AppTextSecondary) },
                        text = { Text("فتح", color = AppTextPrimary) },
                        onClick = {
                            activeBottomSheetItem = null
                            currentDirectory = file
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = AppTextSecondary) },
                        text = { Text("إعادة التسمية", color = AppTextPrimary) },
                        onClick = {
                            activeBottomSheetItem = null
                            showRenameDialogItem = file
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                        text = { Text("حذف", color = Color.Red) },
                        onClick = {
                            activeBottomSheetItem = null
                            showDeleteDialogItem = file
                        }
                    )
                } else {
                    val isPdf = file.name.lowercase(Locale.ROOT).endsWith(".pdf")
                    if (isPdf) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AppTextSecondary) },
                            text = { Text("فتح", color = AppTextPrimary) },
                            onClick = {
                                activeBottomSheetItem = null
                                viewModel.selectDocument(context, Uri.fromFile(file))
                                onPdfOpened(Uri.fromFile(file))
                            }
                        )
                        val uriStr = Uri.fromFile(file).toString()
                        val isFav = favoritedUris.contains(uriStr)
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Star else Icons.Outlined.Star,
                                    contentDescription = null,
                                    tint = if (isFav) HighlightYellow else AppTextSecondary
                                )
                            },
                            text = { Text(if (isFav) "إزالة من المفضلة" else "إضافة للمفضلة", color = AppTextPrimary) },
                            onClick = {
                                activeBottomSheetItem = null
                                viewModel.toggleFavoriteForFile(file, !isFav)
                                Toast.makeText(context, if (isFav) "تم الإزالة من المفضلة" else "تمت الإضافة إلى المفضلة", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = AppTextSecondary) },
                            text = { Text("إعادة التسمية", color = AppTextPrimary) },
                            onClick = {
                                activeBottomSheetItem = null
                                showRenameDialogItem = file
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.FileCopy, contentDescription = null, tint = AppTextSecondary) },
                            text = { Text("نسخ إلى...", color = AppTextPrimary) },
                            onClick = {
                                activeBottomSheetItem = null
                                showFolderPickerDialogItem = Pair(file, true)
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = AppTextSecondary) },
                            text = { Text("نقل إلى...", color = AppTextPrimary) },
                            onClick = {
                                activeBottomSheetItem = null
                                showFolderPickerDialogItem = Pair(file, false)
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = AppTextSecondary) },
                            text = { Text("مشاركة", color = AppTextPrimary) },
                            onClick = {
                                activeBottomSheetItem = null
                                shareSingleFile(context, file)
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = AppTextSecondary) },
                            text = { Text("معلومات الملف", color = AppTextPrimary) },
                            onClick = {
                                activeBottomSheetItem = null
                                showInfoDialogItem = file
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            text = { Text("حذف", color = Color.Red) },
                            onClick = {
                                activeBottomSheetItem = null
                                showDeleteDialogItem = file
                            }
                        )
                    } else {
                        // Generic non pdf options
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            text = { Text("حذف", color = Color.Red) },
                            onClick = {
                                activeBottomSheetItem = null
                                showDeleteDialogItem = file
                            }
                        )
                    }
                }
            }
        }
    }

    // B. Rename Dialog
    showRenameDialogItem?.let { file ->
        val originalNameWithoutExtension = if (file.isDirectory) file.name else file.nameWithoutExtension
        var newNameInput by remember { mutableStateOf(originalNameWithoutExtension) }

        AlertDialog(
            onDismissRequest = { showRenameDialogItem = null },
            title = { Text("إعادة التسمية", color = AppTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newNameInput,
                    onValueChange = { newNameInput = it },
                    label = { Text("الاسم الجديد") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppTextPrimary,
                        unfocusedTextColor = AppTextPrimary,
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppTextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = newNameInput.trim()
                        if (input.isNotEmpty()) {
                            val destName = if (file.isDirectory) input else "$input.${file.extension}"
                            val destination = File(file.parentFile, destName)
                            val success = file.renameTo(destination)
                            if (success) {
                                // Trigger refreshed folder listing
                                val oldPath = currentDirectory
                                currentDirectory = null
                                currentDirectory = oldPath
                                Toast.makeText(context, "تمت إعادة التسمية ✓", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "تعديل الاسم فشل", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showRenameDialogItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                ) {
                    Text("تأكيد", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogItem = null }) {
                    Text("إلغاء", color = AppTextSecondary)
                }
            },
            containerColor = AppSurface
        )
    }

    // C. Delete Confirmation Dialog
    showDeleteDialogItem?.let { file ->
        val isMulti = file.name.isEmpty() && file.path.isEmpty()
        val titleText = if (isMulti) "حذف الملفات المحددة" else "حذف الملف"
        val bodyText = if (isMulti) {
            "سيتم حذف «${selectedItems.size} ملفات» نهائياً. لا يمكن التراجع عن هذا الإجراء."
        } else {
            "سيتم حذف «${file.name}» نهائياً. لا يمكن التراجع عن هذا الإجراء."
        }

        AlertDialog(
            onDismissRequest = { showDeleteDialogItem = null },
            title = { Text(titleText, color = AppTextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(bodyText, color = AppTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        if (isMulti) {
                            var deletedCount = 0
                            selectedItems.forEach { selected ->
                                if (selected.deleteRecursively()) deletedCount++
                            }
                            isMultiSelectMode = false
                            selectedItems = emptySet()
                            Toast.makeText(context, "تم حذف $deletedCount ملفات", Toast.LENGTH_SHORT).show()
                        } else {
                            val success = file.deleteRecursively()
                            if (success) {
                                Toast.makeText(context, "تم الحذف", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "لم يتم الحذف", Toast.LENGTH_SHORT).show()
                            }
                        }
                        // Refresh
                        val oldPath = currentDirectory
                        currentDirectory = null
                        currentDirectory = oldPath

                        showDeleteDialogItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("حذف", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogItem = null }) {
                    Text("إلغاء", color = AppTextSecondary)
                }
            },
            containerColor = AppSurface
        )
    }

    // D. Copy/Move FolderPickerDialog
        showFolderPickerDialogItem?.let { pair ->
        val (sourceFile, isCopy) = pair
        FolderPickerDialog(
            context = context,
            roots = roots,
            onDismiss = { showFolderPickerDialogItem = null },
            onFolderSelected = { targetFolder ->
                showFolderPickerDialogItem = null
                // Run background task
                val destFile = File(targetFolder, sourceFile.name)

                // Start progress state
                val progressState = ProgressState(isCopy = isCopy, sourceName = sourceFile.name)
                activeProgressDialog = progressState

                scope.launch(Dispatchers.IO) {
                    try {
                        if (isCopy) {
                            copyFileWithProgress(sourceFile, destFile, { progress ->
                                progressState.progress = progress
                            }, { success ->
                                scope.launch(Dispatchers.Main) {
                                    activeProgressDialog = null
                                    if (success) {
                                        Toast.makeText(context, "تم النسخ بنجاح ✓", Toast.LENGTH_SHORT).show()
                                        // Refresh
                                        val oldPath = currentDirectory
                                        currentDirectory = null
                                        currentDirectory = oldPath
                                    } else {
                                        Toast.makeText(context, "خطأ بالنسخ", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            })
                        } else {
                            // Move with progress
                            val totalSize = sourceFile.length()
                            val buffer = ByteArray(1024 * 64)
                            var bytesCopied = 0L

                            FileInputStream(sourceFile).use { input ->
                                FileOutputStream(destFile).use { output ->
                                    var bytes = input.read(buffer)
                                    while (bytes >= 0) {
                                        output.write(buffer, 0, bytes)
                                        bytesCopied += bytes
                                        progressState.progress = if (totalSize > 0) bytesCopied.toFloat() / totalSize.toFloat() else 0.5f
                                        bytes = input.read(buffer)
                                    }
                                }
                            }
                            sourceFile.delete()
                            scope.launch(Dispatchers.Main) {
                                activeProgressDialog = null
                                Toast.makeText(context, "تم النقل بنجاح ✓", Toast.LENGTH_SHORT).show()
                                // Refresh
                                val old = currentDirectory
                                currentDirectory = null
                                currentDirectory = old
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        scope.launch(Dispatchers.Main) {
                            activeProgressDialog = null
                            Toast.makeText(context, "فشلت العملية: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }

    // F. File Detail Info Dialog
    showInfoDialogItem?.let { file ->
        FileDetailDialog(file = file, onDismiss = { showInfoDialogItem = null })
    }

    // E. Real-time Copy/Move dialog progress
    activeProgressDialog?.let { state ->
        Dialog(onDismissRequest = {}) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = AppSurface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (state.isCopy) "جاري النسخ..." else "جاري النقل...",
                        color = AppTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.sourceName,
                        color = AppTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = AppPrimary,
                        trackColor = AppSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        color = AppTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

class ProgressState(val isCopy: Boolean, val sourceName: String) {
    var progress by mutableStateOf(0f)
}

data class BreadcrumbSegment(val name: String, val file: File)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridBrowserItem(
    file: File,
    isFavorited: Boolean,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val isFolder = file.isDirectory
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag(if (isFolder) "folder_grid_${file.name}" else "file_grid_${file.name}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, AppPrimary) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isFolder) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder Icon",
                        tint = Color(0xFFFFD93D),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = file.name,
                        color = AppTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // count total children
                    val contentsCount = remember(file.absolutePath) {
                        file.listFiles()?.size ?: 0
                    }
                    Text(
                        text = "$contentsCount عنصر",
                        color = AppTextSecondary,
                        fontSize = 11.sp
                    )
                } else {
                    val isPdf = file.name.lowercase(Locale.ROOT).endsWith(".pdf")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPdf) {
                            PdfBrowserThumbnail(file = file, modifier = Modifier.fillMaxSize())
                            // Small purple page badge
                            val pages = rememberPageCount(file = file)
                            if (pages > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(AppPrimary, shape = RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$pages ص",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .scale(scale.value)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        onFavoriteToggle()
                                        scope.launch {
                                            scale.animateTo(1.3f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 400f))
                                            scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 400f))
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "المفضلة",
                                    tint = if (isFavorited) Color(0xFFFF6B6B) else AppTextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = file.name,
                        color = if (isPdf) AppTextPrimary else AppTextSecondary.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatBrowserFileSize(file.length()),
                        color = AppTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // MoreVert option or select checkbox
            if (isMultiSelectActive) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = AppPrimary),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                )
            } else {
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                ) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menu", tint = AppTextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListBrowserItem(
    file: File,
    isFavorited: Boolean,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val isFolder = file.isDirectory
    val isPdf = !isFolder && file.name.lowercase(Locale.ROOT).endsWith(".pdf")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (isSelected) AppPrimary.copy(alpha = 0.1f) else Color.Transparent)
            .testTag(if (isFolder) "folder_list_${file.name}" else "file_list_${file.name}")
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectActive) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = AppPrimary),
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // Thumbnail context
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(AppSurface),
            contentAlignment = Alignment.Center
        ) {
            if (isFolder) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFFFFD93D),
                    modifier = Modifier.size(32.dp)
                )
            } else if (isPdf) {
                PdfBrowserThumbnail(file = file, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Name and file details
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.name,
                    color = if (isFolder || isPdf) AppTextPrimary else AppTextSecondary.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPdf && isFavorited) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorited",
                        tint = HighlightYellow,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            val date = remember(file.lastModified()) {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
            }
            val details = if (isFolder) {
                val contents = file.listFiles()?.size ?: 0
                "$contents عنصر  •  $date"
            } else {
                "${formatBrowserFileSize(file.length())}  •  $date"
            }
            Text(details, color = AppTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        IconButton(onClick = onMoreClick) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menu", tint = AppTextSecondary)
        }
    }
}

// Dialog for Folder Picker
@Composable
fun FolderPickerDialog(
    context: Context,
    roots: List<File>,
    onDismiss: () -> Unit,
    onFolderSelected: (File) -> Unit
) {
    var activeFolder by remember { mutableStateOf<File?>(roots.firstOrNull()) }
    var listFolders by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(activeFolder) {
        val current = activeFolder
        if (current != null) {
            listFolders = withContext(Dispatchers.IO) {
                current.listFiles()?.filter { it.isDirectory }?.toList() ?: emptyList()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = AppSurface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("اختر مجلد الوجهة", color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = AppTextSecondary)
                    }
                }

                // Path breadcrumb
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.2f))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    roots.forEach { root ->
                        val isCurrentAncestor = activeFolder?.absolutePath?.startsWith(root.absolutePath) == true
                        if (isCurrentAncestor) {
                            TextButton(onClick = { activeFolder = root }) {
                                Text(
                                    text = if (root.absolutePath == Environment.getExternalStorageDirectory().absolutePath) "التخزين الداخلي" else "بطاقة SD",
                                    color = if (activeFolder?.absolutePath == root.absolutePath) AppPrimary else AppTextPrimary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    if (activeFolder != null && roots.none { it.absolutePath == activeFolder?.absolutePath }) {
                        Text("/", color = AppTextSecondary)
                        Text(activeFolder!!.name, color = AppPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    item {
                        if (activeFolder?.parentFile != null && roots.any { activeFolder!!.absolutePath.startsWith(it.absolutePath) && activeFolder!!.absolutePath != it.absolutePath }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeFolder = activeFolder?.parentFile }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Up", tint = AppPrimary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("المجلد السابق (...)", color = AppTextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    items(listFolders) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeFolder = folder }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFFD93D), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(folder.name, color = AppTextPrimary, fontSize = 14.sp)
                        }
                    }
                }

                // Selection panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.1f))
                        .padding(16.dp)
                ) {
                    activeFolder?.let { current ->
                        Text("محدد حالياً: ${current.name}", color = AppTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onFolderSelected(current) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                        ) {
                            Text("تحديد هذا المجلد", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// Background buffered copy with flow / update progress callback
suspend fun copyFileWithProgress(
    source: File,
    dest: File,
    onProgress: (Float) -> Unit,
    onComplete: (Boolean) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val totalSize = source.length()
            val buffer = ByteArray(64 * 1024)
            var bytesCopied = 0L

            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output ->
                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        output.write(buffer, 0, bytes)
                        bytesCopied += bytes
                        onProgress(if (totalSize > 0) bytesCopied.toFloat() / totalSize.toFloat() else 0.5f)
                        bytes = input.read(buffer)
                    }
                }
            }
            onComplete(true)
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(false)
        }
    }
}

// Multiple pdf files sharing using System Share sheets
fun shareMultipleFiles(context: Context, files: List<File>) {
    try {
        val uris = ArrayList<Uri>()
        files.forEach { file ->
            val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            uris.add(u)
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الملفات PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "فشلت مشاركة الملفات", Toast.LENGTH_SHORT).show()
    }
}

fun shareSingleFile(context: Context, file: File) {
    try {
        val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, u)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الملف"))
    } catch (e: Exception) {
        Toast.makeText(context, "خطأ بمشاركة الملف", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun FileDetailDialog(file: File, onDismiss: () -> Unit) {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
    val sizeText = formatBrowserFileSize(file.length())
    val details = """
        اسم الملف: ${file.name}
        الموقع: ${file.absolutePath}
        الحجم: $sizeText
        آخر تعديل: $date
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تفاصيل الملف", color = AppTextPrimary, fontWeight = FontWeight.Bold) },
        text = { Text(details, color = AppTextSecondary, fontSize = 13.sp) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
            ) {
                Text("موافق", color = Color.White)
            }
        },
        containerColor = AppSurface
    )
}
