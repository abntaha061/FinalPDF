package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
    onPdfOpened: () -> Unit
) {
    val context = LocalContext.current
    val totalPdfs = recentPdfs.size
    val lastOpenedDoc = recentPdfs.firstOrNull()
    val lastOpenedName = lastOpenedDoc?.name ?: "—"
    val lastOpenedPages = lastOpenedDoc?.totalPages?.let { if (it > 0) "$it" else "—" } ?: "—"

    Column(modifier = Modifier.fillMaxSize()) {
        // 2. Row of 3 Stat Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
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
            columns = GridCells.Fixed(2),
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
                        viewModel.selectDocument(context, Uri.parse(pdf.uri))
                        onPdfOpened()
                    },
                    onLongClick = {
                        // Optional submenu or delete
                        viewModel.deleteDocument(pdf.uri)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PdfViewModel,
    onPdfOpened: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recentPdfs by viewModel.recentDocuments.collectAsState()
    val isReady by viewModel.isReady.collectAsState()
    var isLoading by remember { mutableStateOf(true) }
    val haptic = LocalHapticFeedback.current

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
                onPdfOpened()
            }
        }
    )

    // RTL translation for standard Arabic styling
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = AppBackground,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                    },
                    containerColor = AppPrimary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .testTag("open_pdf_fab")
                        .padding(bottom = 16.dp, start = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "open file in Arabic",
                        modifier = Modifier.size(28.dp)
                    )
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
                // 1. App Title and Subtitle area
                Text(
                    text = "قارئ PDF",
                    color = AppTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "افتح ملفاتك بجودة عالية",
                    color = AppTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(24.dp))

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
                        if (recentPdfs.isEmpty()) {
                            HomeScreenEmptyState()
                        } else {
                            HomeScreenRealFiles(recentPdfs = recentPdfs, viewModel = viewModel, onPdfOpened = onPdfOpened)
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
    modifier: Modifier = Modifier
) {
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
