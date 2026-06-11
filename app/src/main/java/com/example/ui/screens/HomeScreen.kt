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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PdfDocumentEntity
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PdfViewModel,
    onPdfOpened: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recentPdfs by viewModel.recentDocuments.collectAsState()

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
                    onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
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

                // 2. Row of 3 Stat Cards
                val totalPdfs = recentPdfs.size
                val lastOpenedDoc = recentPdfs.firstOrNull()
                val lastOpenedName = lastOpenedDoc?.name ?: "—"
                val lastOpenedPages = lastOpenedDoc?.totalPages?.let { if (it > 0) "$it" else "—" } ?: "—"

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

                // 3. Grid (LazyVerticalGrid, 2 columns) of recently opened files
                if (recentPdfs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = AppPrimary.copy(alpha = 0.3f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "لا توجد ملفات مفتوحة حالياً",
                                color = AppTextSecondary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "انقر على زر الزائد لفتح ملف PDF من جهازك",
                                color = AppTextSecondary.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
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
    pdf: PdfDocumentEntity,
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
                        text = formatArabicFileSize(pdf.size),
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
