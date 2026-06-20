package com.example.ui.screens

import android.net.Uri
import com.example.util.findActivity
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RecentFileEntity
import com.example.ui.PdfViewModel
import com.example.ui.navigation.Screen
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "Shimmer")
    val fullWidth = 1000f
    val animatedOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = fullWidth * 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerOffset"
    )
    return Brush.linearGradient(
        colors = listOf(AppSurface, Color(0xFF2A2A35), AppSurface),
        start = Offset(animatedOffset - fullWidth, 0f),
        end   = Offset(animatedOffset, 0f)
    )
}

@Composable
fun ShimmerEffect(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(0.dp)) {
    val brush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun SkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        items(4) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ShimmerEffect(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                ShimmerEffect(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(12.dp),
                    shape = RoundedCornerShape(4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerEffect(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(8.dp),
                    shape = RoundedCornerShape(4.dp)
                )
            }
        }
    }
}

@Composable
fun HomeScreenEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val pulseTransition = rememberInfiniteTransition(label = "FolderPulse")
            val folderScale by pulseTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "FolderScale"
            )

            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = AppPrimary.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(96.dp)
                    .scale(folderScale)
                    .testTag("empty_folder_icon")
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "لا توجد ملفات بعد",
                color = AppTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("empty_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "اضغط على زر + بالأسفل لفتح أول ملف PDF",
                color = AppTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .testTag("empty_subtitle")
            )

            Spacer(modifier = Modifier.height(40.dp))

            val arrowTransition = rememberInfiniteTransition(label = "ArrowBounce")
            val arrowOffsetY by arrowTransition.animateFloat(
                initialValue = 0f,
                targetValue = 12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ArrowOffset"
            )

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier
                    .size(32.dp)
                    .offset(y = arrowOffsetY.dp)
                    .testTag("empty_arrow_down")
            )
        }
    }
}

@Composable
fun HomeScreenRealFiles(
    recentPdfs: List<RecentFileEntity>,
    viewModel: PdfViewModel,
    onPdfOpened: (Uri) -> Unit,
    onNavigateToFavorites: () -> Unit
) {
    val context = LocalContext.current
    
    val scrollState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    LaunchedEffect(scrollState) {
        var previousIndex = 0
        var previousScrollOffset = 0
        snapshotFlow { scrollState.firstVisibleItemIndex to scrollState.firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                val isScrollingDown = when {
                    currentIndex > previousIndex -> true
                    currentIndex < previousIndex -> false
                    else -> currentOffset > previousScrollOffset
                }
                
                val atTop = currentIndex == 0 && currentOffset < 50
                if (atTop) {
                    viewModel.setBottomBarVisible(true)
                } else if (currentIndex != previousIndex || java.lang.Math.abs(currentOffset - previousScrollOffset) > 15) {
                    viewModel.setBottomBarVisible(!isScrollingDown)
                }
                
                previousIndex = currentIndex
                previousScrollOffset = currentOffset
            }
    }

    LaunchedEffect(Unit) {
        viewModel.scrollToTopEvent.collect {
            try {
                scrollState.animateScrollToItem(0)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    val totalPdfs = recentPdfs.size
    val lastOpenedDoc = recentPdfs.firstOrNull()
    val lastOpenedName = lastOpenedDoc?.name ?: "—"
    val lastOpenedPages = lastOpenedDoc?.totalPages?.let { if (it > 0) "$it" else "—" } ?: "—"

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isMedium = screenWidth in 600..839
    val isExpanded = screenWidth >= 840

    if (isExpanded) {
        var selectedPdfUri by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(recentPdfs) {
            if (selectedPdfUri == null || recentPdfs.none { it.uri == selectedPdfUri }) {
                selectedPdfUri = recentPdfs.firstOrNull()?.uri
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left list of files
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
            ) {
                Text(
                    text = "الملفات الأخيرة",
                    color = AppTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentPdfs, key = { it.uri }) { pdf ->
                        val isSelected = selectedPdfUri == pdf.uri
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else AppSurface,
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedPdfUri = pdf.uri
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PdfThumbnail(
                                pdfUriString = pdf.uri,
                                modifier = Modifier
                                    .size(44.dp, 56.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pdf.name,
                                    color = AppTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "الصفحات: ${pdf.totalPages}",
                                    color = AppTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Right PDF preview
            val selectedPdf = recentPdfs.find { it.uri == selectedPdfUri } ?: recentPdfs.firstOrNull()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(AppSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF202025), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (selectedPdf != null) {
                    PdfThumbnail(
                        pdfUriString = selectedPdf.uri,
                        modifier = Modifier
                            .width(180.dp)
                            .height(240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = selectedPdf.name,
                        color = AppTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "الموقع: ${selectedPdf.uri}",
                        color = AppTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("الصفحات", color = AppTextSecondary, fontSize = 12.sp)
                            Text("${selectedPdf.totalPages}", color = AppTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("تاريخ إضافة", color = AppTextSecondary, fontSize = 12.sp)
                            Text(
                                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(selectedPdf.lastOpenedAt)),
                                color = AppTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val uri = Uri.parse(selectedPdf.uri)
                            viewModel.selectDocument(context, uri)
                            onPdfOpened(uri)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("قراءة وفتح الملف", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = AppTextSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("لم يتم تحديد مستند بعد", color = AppTextSecondary, fontSize = 16.sp)
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            val isHeaderVisible by viewModel.isBottomBarVisible.collectAsState()

            // Row of 3 Stat Cards
            AnimatedVisibility(
                visible = isHeaderVisible,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            title = "الملفات المفتوحة",
                            value = "$totalPdfs",
                            icon = Icons.Default.FolderOpen,
                            iconColor = AppPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "آخر فتح",
                            value = lastOpenedName,
                            icon = Icons.Default.RemoveRedEye,
                            iconColor = AppPrimaryVariant,
                            modifier = Modifier.weight(1.2f)
                        )
                        StatCard(
                            title = "الصفحات",
                            value = lastOpenedPages,
                            icon = Icons.Default.MenuBook,
                            iconColor = AppPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            val favoritePdfs = remember(recentPdfs) { recentPdfs.filter { it.isFavorite } }

            if (favoritePdfs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⭐ المفضلة",
                        color = AppTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (favoritePdfs.size > 5) {
                        TextButton(
                            onClick = onNavigateToFavorites,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("عرض الكل", color = AppPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    items(favoritePdfs, key = { it.uri + "_fav" }) { pdf ->
                        SmallerFavoriteCard(
                            pdf = pdf,
                            onClick = {
                                val uri = Uri.parse(pdf.uri)
                                viewModel.selectDocument(context, uri)
                                onPdfOpened(uri)
                            },
                            onFavoriteClick = {
                                viewModel.toggleFavorite(pdf.uri, !pdf.isFavorite)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Recent files title
            Text(
                text = "الملفات الأخيرة",
                color = AppTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyVerticalGrid(
                state = scrollState,
                columns = GridCells.Fixed(if (isMedium) 3 else 2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(recentPdfs, key = { it.uri }) { pdf ->
                    PdfGridCard(
                        pdf = pdf,
                        onClick = {
                            val uri = Uri.parse(pdf.uri)
                            viewModel.selectDocument(context, uri)
                            onPdfOpened(uri)
                        },
                        onLongClick = {
                            viewModel.deleteDocument(pdf.uri)
                        },
                        onFavoriteClick = {
                            viewModel.toggleFavorite(pdf.uri, !pdf.isFavorite)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val viewModel = remember(context) {
        val activity = context.findActivity()
            ?: throw IllegalStateException("Context must be ComponentActivity")
        androidx.lifecycle.ViewModelProvider(activity)[PdfViewModel::class.java]
    }
    HomeScreen(
        viewModel = viewModel,
        onPdfOpened = { uri ->
            val encodedUri = Uri.encode(uri.toString())
            navController.navigate(com.example.ui.navigation.Screen.PdfReader.createRoute(encodedUri))
        },
        onNavigateToSettings = {
            navController.navigate(com.example.ui.navigation.Screen.Settings.route)
        },
        onNavigateToStatistics = {
            navController.navigate("statistics")
        },
        onNavigateToMerge = {
            navController.navigate(com.example.ui.navigation.Screen.MergePdfs.route)
        },
        onNavigateToMultiSearch = {
            navController.navigate(com.example.ui.navigation.Screen.MultiFileSearch.route)
        },
        onNavigateToPdfToImages = {
            navController.navigate(com.example.ui.navigation.Screen.PdfToImages.route)
        },
        onNavigateToImagesToPdf = {
            navController.navigate(com.example.ui.navigation.Screen.ImagesToPdf.route)
        },
        onNavigateToPdfToWord = {
            navController.navigate(com.example.ui.navigation.Screen.PdfToWord.route)
        },
        onNavigateToSignature = {
            navController.navigate(com.example.ui.navigation.Screen.Signature.route)
        },
        onNavigateToFavorites = {
            navController.navigate(com.example.ui.navigation.Screen.Favorites.route)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PdfViewModel,
    onPdfOpened: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    onNavigateToMerge: () -> Unit = {},
    onNavigateToMultiSearch: () -> Unit = {},
    onNavigateToPdfToImages: () -> Unit = {},
    onNavigateToImagesToPdf: () -> Unit = {},
    onNavigateToPdfToWord: () -> Unit = {},
    onNavigateToSignature: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showOnlyFavorites by rememberSaveable { mutableStateOf(false) }
    val recentPdfs by viewModel.recentDocuments.collectAsState()
    val displayedPdfs = remember(recentPdfs, showOnlyFavorites) {
        if (showOnlyFavorites) recentPdfs.filter { it.isFavorite } else recentPdfs
    }
    val isReady by viewModel.isReady.collectAsState()
    var isLoading by remember { mutableStateOf(true) }
    val haptic = LocalHapticFeedback.current

    val activeFilterCount by viewModel.activeFilterCount.collectAsState()
    var showSortSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val sortMode by viewModel.sortMode.collectAsState()
    
    val filterMinSize by viewModel.filterMinSize.collectAsState()
    val filterMaxSize by viewModel.filterMaxSize.collectAsState()
    val filterMinPages by viewModel.filterMinPages.collectAsState()
    val filterMaxPages by viewModel.filterMaxPages.collectAsState()
    val filterDateRange by viewModel.filterDateRange.collectAsState()

    var tempMinSize by remember { mutableStateOf(0f) }
    var tempMaxSize by remember { mutableStateOf(100f) }
    var tempMinPages by remember { mutableStateOf(1) }
    var tempMaxPages by remember { mutableStateOf(500) }
    var tempDateRange by remember { mutableStateOf("الكل") }

    val scope = rememberCoroutineScope()

    LaunchedEffect(showFilterSheet) {
        if (showFilterSheet) {
            tempMinSize = filterMinSize
            tempMaxSize = filterMaxSize
            tempMinPages = filterMinPages
            tempMaxPages = filterMaxPages
            tempDateRange = filterDateRange
        }
    }

    LaunchedEffect(isReady) {
        if (isReady) {
            kotlinx.coroutines.delay(500)
            isLoading = false
        } else {
            kotlinx.coroutines.delay(800)
            isLoading = false
        }
    }

    // SAF PDF Picker Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                // Grant persistent read permission to avoid Uri permission loss across app restarts
                try {
                    val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                viewModel.selectDocument(context, uri)
                onPdfOpened(uri)
            }
        }
    )

    // RTL translation for standard Arabic styling
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = AppBackground,
            bottomBar = {},
            floatingActionButton = {
                val isBottomBarVisible by viewModel.isBottomBarVisible.collectAsState()
                AnimatedVisibility(
                    visible = isBottomBarVisible,
                    enter = slideInVertically(
                        animationSpec = tween(250, delayMillis = 50)
                    ) { it },
                    exit = slideOutVertically(
                        animationSpec = tween(250)
                    ) { it }
                ) {
                    var isFabExpanded by remember { mutableStateOf(false) }
                    
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 16.dp, start = 16.dp)
                    ) {
                        if (isFabExpanded) {
                            // Mini FAB 1: "فتح ملف PDF"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = AppSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    shadowElevation = 4.dp
                                ) {
                                    Text(
                                        text = "فتح ملف PDF",
                                        color = AppTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                FloatingActionButton(
                                    onClick = {
                                        isFabExpanded = false
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                    },
                                    containerColor = AppSurface,
                                    contentColor = AppPrimary,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("open_pdf_mini_fab")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "فتح ملف",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Mini FAB 2: "دمج ملفات PDF"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = AppSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    shadowElevation = 4.dp
                                ) {
                                    Text(
                                        text = "دمج ملفات PDF",
                                        color = AppTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                FloatingActionButton(
                                    onClick = {
                                        isFabExpanded = false
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onNavigateToMerge()
                                    },
                                    containerColor = AppSurface,
                                    contentColor = AppPrimary,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("merge_pdf_mini_fab")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CallMerge,
                                        contentDescription = "دمج ملفات PDF",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Mini FAB 3: "البحث متعدد الملفات"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = AppSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    shadowElevation = 4.dp
                                ) {
                                    Text(
                                        text = "البحث متعدد الملفات",
                                        color = AppTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                FloatingActionButton(
                                    onClick = {
                                        isFabExpanded = false
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onNavigateToMultiSearch()
                                    },
                                    containerColor = AppSurface,
                                    contentColor = AppPrimary,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("multi_search_mini_fab")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "البحث متعدد الملفات",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // Main FAB
                        FloatingActionButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isFabExpanded = !isFabExpanded
                            },
                            containerColor = AppPrimary,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .testTag("open_pdf_fab")
                        ) {
                            val rotationAngle by animateFloatAsState(
                                targetValue = if (isFabExpanded) 45f else 0f,
                                animationSpec = tween(durationMillis = 200),
                                label = "FabRotation"
                            )
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "قائمة الإجراءات",
                                modifier = Modifier
                                    .size(28.dp)
                                    .rotate(rotationAngle)
                            )
                        }
                    }
                }
            },
            modifier = modifier
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                val isHeaderVisible by viewModel.isBottomBarVisible.collectAsState()

                AnimatedVisibility(
                    visible = isHeaderVisible,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
                ) {
                    Column {
                        // 1. App Title and Settings Icon Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "قارئ PDF",
                                color = AppTextPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 28.sp,
                                textAlign = TextAlign.Start
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Button 0: Favorites Filter
                                IconButton(
                                    onClick = { showOnlyFavorites = !showOnlyFavorites },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("favorites_filter_button")
                                ) {
                                    Icon(
                                        imageVector = if (showOnlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "تصفية المفضلة",
                                        tint = if (showOnlyFavorites) Color(0xFFFF6B6B) else AppTextPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Button 1: Filter List with badge
                                IconButton(
                                    onClick = { showFilterSheet = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("filter_button")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (activeFilterCount > 0) {
                                                Badge(
                                                    containerColor = Color.Red,
                                                    contentColor = Color.White
                                                ) {
                                                    Text(text = "$activeFilterCount")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = "تصفية",
                                            tint = AppTextPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                // Button 2: Sort
                                IconButton(
                                    onClick = { showSortSheet = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("sort_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "ترتيب",
                                        tint = AppTextPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Button 3: Statistics
                                IconButton(
                                    onClick = onNavigateToStatistics,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("statistics_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BarChart,
                                        contentDescription = "إحصائيات",
                                        tint = AppTextPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                IconButton(
                                    onClick = onNavigateToSettings,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = AppTextPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "افتح ملفاتك بجودة عالية",
                            color = AppTextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isHeaderVisible) 24.dp else 4.dp))

                AnimatedContent(
                    targetState = isLoading,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)).togetherWith(fadeOut(animationSpec = tween(300)))
                    },
                    label = "HomeScreenLoading",
                    modifier = Modifier.weight(1f)
                ) { loading ->
                    if (loading) {
                        SkeletonGrid()
                    } else {
                        if (displayedPdfs.isEmpty()) {
                            HomeScreenEmptyState()
                        } else {
                            HomeScreenRealFiles(
                                recentPdfs = displayedPdfs,
                                viewModel = viewModel,
                                onPdfOpened = onPdfOpened,
                                onNavigateToFavorites = onNavigateToFavorites
                            )
                        }
                    }
                }
            }
        }

        if (showSortSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSortSheet = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 36.dp, start = 20.dp, end = 20.dp)
                ) {
                    // Title Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showSortSheet = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = AppTextPrimary
                            )
                        }
                        Text(
                            text = "ترتيب الملفات",
                            color = AppTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                    
                    val options = listOf(
                        "الأحدث أولاً" to Icons.Default.AccessTime,
                        "الأقدم أولاً" to Icons.Default.History,
                        "الاسم (أ → ي)" to Icons.Default.SortByAlpha,
                        "الحجم (الأكبر أولاً)" to Icons.Default.Storage
                    )
                    
                    options.forEach { (optionName, icon) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable {
                                    viewModel.setSortMode(optionName)
                                    scope.launch {
                                        kotlinx.coroutines.delay(300)
                                        showSortSheet = false
                                    }
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = AppTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = optionName,
                                    color = AppTextPrimary,
                                    fontSize = 15.sp
                                )
                            }
                            RadioButton(
                                selected = (sortMode == optionName),
                                onClick = {
                                    viewModel.setSortMode(optionName)
                                    scope.launch {
                                        kotlinx.coroutines.delay(300)
                                        showSortSheet = false
                                    }
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        }

        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 36.dp, start = 20.dp, end = 20.dp)
                ) {
                    // Title Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showFilterSheet = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = AppTextPrimary
                            )
                        }
                        Text(
                            text = "تصفية الملفات",
                            color = AppTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // FILTER 1: حجم الملف
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "حجم الملف",
                                color = AppTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${tempMinSize.toInt()} م.ب - ${tempMaxSize.toInt()} م.ب",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        RangeSlider(
                            value = tempMinSize..tempMaxSize,
                            onValueChange = { range ->
                                tempMinSize = range.start
                                tempMaxSize = range.endInclusive
                            },
                            valueRange = 0f..100f,
                            steps = 100,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                thumbColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // FILTER 2: عدد الصفحات
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "عدد الصفحات",
                                color = AppTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${tempMinPages} - ${tempMaxPages} صفحة",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        RangeSlider(
                            value = tempMinPages.toFloat()..tempMaxPages.toFloat(),
                            onValueChange = { range ->
                                tempMinPages = range.start.toInt()
                                tempMaxPages = range.endInclusive.toInt()
                            },
                            valueRange = 1f..500f,
                            steps = 500,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                thumbColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // FILTER 3: تاريخ الفتح
                    Text(
                        text = "فُتح في آخر:",
                        color = AppTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    val dateOptions = listOf("الكل", "24 ساعة", "أسبوع", "شهر")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dateOptions.forEach { option ->
                            val isSelected = (tempDateRange == option)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { tempDateRange = option }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Control Buttons Row: Reset and Apply
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Outlined Reset Button styled with Modifier.border
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .background(Color.Transparent, RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                .clickable {
                                    tempMinSize = 0f
                                    tempMaxSize = 100f
                                    tempMinPages = 1
                                    tempMaxPages = 500
                                    tempDateRange = "الكل"
                                    viewModel.resetFilters()
                                    showFilterSheet = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "إعادة ضبط",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // Filled Apply Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setFilterMinSize(tempMinSize)
                                    viewModel.setFilterMaxSize(tempMaxSize)
                                    viewModel.setFilterMinPages(tempMinPages)
                                    viewModel.setFilterMaxPages(tempMaxPages)
                                    viewModel.setFilterDateRange(tempDateRange)
                                    showFilterSheet = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "تطبيق",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.height(105.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = value,
                    color = AppTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    color = AppTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PdfGridCard(
    pdf: RecentFileEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("pdf_item_card")
    ) {
        Column {
            // Thumbnail container with rounded top borders
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                PdfThumbnail(
                    pdfUriString = pdf.uri,
                    modifier = Modifier.fillMaxSize()
                )

                // Favorite heart overlay icon top-start corner (top-right under RTL layout)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .scale(scale.value)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            onFavoriteClick()
                            scope.launch {
                                scale.animateTo(1.3f, spring(dampingRatio = 0.4f, stiffness = 400f))
                                scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f))
                            }
                        }
                ) {
                    Icon(
                        imageVector = if (pdf.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "المفضلة",
                        tint = if (pdf.isFavorite) Color(0xFFFF6B6B) else AppTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Page count purple badge at top-end corner
                if (pdf.totalPages > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(AppPrimary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${pdf.totalPages} ص",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Info under the thumbnail
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = pdf.name,
                    color = AppTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatArabicFileSize(pdf.sizeBytes),
                        color = AppTextSecondary,
                        fontSize = 11.sp
                    )

                    // Simple delete icon button for rapid library customization
                    IconButton(
                        onClick = onLongClick,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete from history",
                            tint = AppTextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PdfThumbnail(
    pdfUriString: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var fileState by remember(pdfUriString) { mutableStateOf<File?>(null) }
    var triedRendering by remember(pdfUriString) { mutableStateOf(false) }

    LaunchedEffect(pdfUriString) {
        if (!triedRendering) {
            triedRendering = true
            withContext(Dispatchers.IO) {
                try {
                    val cacheFile = File(context.cacheDir, "pdf_thumb_${pdfUriString.hashCode()}.png")
                    if (cacheFile.exists()) {
                        fileState = cacheFile
                    } else {
                        val uri = Uri.parse(pdfUriString)
                        // Verify we can access the file
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                                if (renderer.pageCount > 0) {
                                    renderer.openPage(0).use { page ->
                                        // High quality standard crop dimensions
                                        val width = 300
                                        val height = 380
                                        val bitmap = android.graphics.Bitmap.createBitmap(
                                            width, height,
                                            android.graphics.Bitmap.Config.ARGB_8888
                                        )
                                        bitmap.eraseColor(android.graphics.Color.WHITE)
                                        page.render(
                                            bitmap,
                                            null,
                                            null,
                                            android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                        )
                                        FileOutputStream(cacheFile).use { fos ->
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, fos)
                                        }
                                        bitmap.recycle()
                                        fileState = cacheFile
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    if (fileState != null) {
        coil.compose.AsyncImage(
            model = fileState,
            contentDescription = "PDF Thumbnail Image",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        // Fallback beautiful template placeholder
        Box(
            modifier = modifier.background(AppSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

fun formatArabicFileSize(bytes: Long): String {
    if (bytes <= 0) return "٠ ك.ب"
    val df = DecimalFormat("#.#")
    return if (bytes < 1024 * 1024) {
        "${df.format(bytes / 1024.0)} ك.ب"
    } else {
        "${df.format(bytes / (1024.0 * 1024.0))} م.ب"
    }
}

@Composable
fun SmallerFavoriteCard(
    pdf: RecentFileEntity,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                PdfThumbnail(
                    pdfUriString = pdf.uri,
                    modifier = Modifier.fillMaxSize()
                )
                // Favorite heart overlay button with spring animation
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .scale(scale.value)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            onFavoriteClick()
                            scope.launch {
                                scale.animateTo(1.3f, spring(dampingRatio = 0.4f, stiffness = 400f))
                                scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f))
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "إزالة من المفضلة",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = pdf.name,
                color = AppTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                textAlign = TextAlign.Start
            )
        }
    }
}
