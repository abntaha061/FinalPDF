package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToImagesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFileBrowser: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfName by remember { mutableStateOf("") }
    var totalPagesInPdf by remember { mutableStateOf(0) }

    // Options States
    var format by remember { mutableStateOf("PNG") } // "PNG" or "JPG"
    var jpgQuality by remember { mutableStateOf(0.9f) } // 50% - 100% (represented as 0.5f to 1.0f)
    var resolutionScale by remember { mutableStateOf(2) } // 1, 2, 3
    var isAllPages by remember { mutableStateOf(true) }
    var customPageFrom by remember { mutableStateOf("1") }
    var customPageTo by remember { mutableStateOf("") }

    // Conversion Engine State
    var isConverting by remember { mutableStateOf(false) }
    var currentProgressPage by remember { mutableStateOf(0) }
    var conversionSuccessDir by remember { mutableStateOf<File?>(null) }
    var convertedImagesList by remember { mutableStateOf<List<File>>(emptyList()) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                selectedPdfUri = uri
                selectedPdfName = getFileNameFromUri(context, uri) ?: "document.pdf"
                conversionSuccessDir = null
                convertedImagesList = emptyList()
                
                // Read page count
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        val renderer = PdfRenderer(pfd)
                        totalPagesInPdf = renderer.pageCount
                        customPageTo = totalPagesInPdf.toString()
                        renderer.close()
                    }
                } catch (e: Exception) {
                    Log.e("PdfToImagesScreen", "Error counting pages", e)
                    Toast.makeText(context, "فشل قراءة ملف الـ PDF. قد يكون محمياً أو تالفاً.", Toast.LENGTH_LONG).show()
                    selectedPdfUri = null
                }
            }
        }
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("PDF إلى صور", color = AppTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "رجوع", tint = AppTextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D0D0F))
                )
            },
            containerColor = AppBackground
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (conversionSuccessDir != null) {
                    // --- SUCCESS RESULT SCREEN ---
                    val totalMB = (convertedImagesList.sumOf { it.length() } / (1024f * 1024f))
                    val formattedSize = String.format("%.2f", totalMB)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header Box
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppSurface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "اكتمل التحويل بنجاح!",
                                        color = AppPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "$formattedSize ميغابايت • ${convertedImagesList.size} صورة",
                                        color = AppTextSecondary,
                                        fontSize = 13.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                shareImagesAsZip(context, selectedPdfName, convertedImagesList)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                        modifier = Modifier.testTag("share_zip_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("مشاركة ZIP", color = Color.White, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        // Images Grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(convertedImagesList) { file ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(180.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = file,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(Color.Black.copy(alpha = 0.65f))
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = file.name,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Reset Button
                        Button(
                            onClick = {
                                selectedPdfUri = null
                                selectedPdfName = ""
                                conversionSuccessDir = null
                                convertedImagesList = emptyList()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C35)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تحويل ملف آخر", color = Color.White)
                        }
                    }
                } else {
                    // --- SETTINGS AND CONFIGURATION PANEL ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // File Selection Selector Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppSurface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (selectedPdfUri == null) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = AppTextSecondary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "اختر ملف PDF للبدء بالتحويل",
                                        color = AppTextSecondary,
                                        fontSize = 14.sp
                                    )
                                    Button(
                                        onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                        modifier = Modifier.testTag("select_pdf_btn")
                                    ) {
                                        Text("اختر ملف PDF", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = AppPrimary,
                                            modifier = Modifier.size(36.dp)
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = selectedPdfName,
                                                color = AppTextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "عدد الصفحات الإجمالي: $totalPagesInPdf صفحة",
                                                color = AppTextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }

                                        IconButton(
                                            onClick = { selectedPdfUri = null }
                                        ) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "إلغاء", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }

                        if (selectedPdfUri != null) {
                            // FORMAT SELECTOR
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AppSurface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("صيغة الصور الناتجة:", color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        listOf("PNG", "JPG").forEach { ext ->
                                            FilterChip(
                                                selected = format == ext,
                                                onClick = { format = ext },
                                                label = { Text(if (ext == "PNG") "PNG (جودة أعلى)" else "JPG (حجم أصغر)", fontSize = 13.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = AppPrimary,
                                                    selectedLabelColor = Color.White
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    // QUALITY SLIDER for JPG only
                                    AnimatedVisibility(visible = format == "JPG") {
                                        Column {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("جودة الـ JPG والضغط:", color = AppTextSecondary, fontSize = 12.sp)
                                                Text("${(jpgQuality * 100).toInt()}%", color = AppPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Slider(
                                                value = jpgQuality,
                                                onValueChange = { jpgQuality = it },
                                                valueRange = 0.5f..1.0f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = AppPrimary,
                                                    activeTrackColor = AppPrimary
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // RESOLUTION SELECTOR (DPI multiplier)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AppSurface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("دقة ووضوح الصور:", color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    
                                    val options = listOf(
                                        1 to "عادية (1x) — دقة الشاشة",
                                        2 to "عالية (2x) — مستحسن (توازن مذهل)",
                                        3 to "فائقة (3x) — جودة طباعة (ملف ضخم)"
                                    )

                                    options.forEach { (scale, text) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { resolutionScale = scale }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            RadioButton(
                                                selected = resolutionScale == scale,
                                                onClick = { resolutionScale = scale },
                                                colors = RadioButtonDefaults.colors(selectedColor = AppPrimary)
                                            )
                                            Text(text, color = AppTextPrimary, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            // PAGE RANGE FILTER
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AppSurface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("نطاق الصفحات المراد تحويلها:", color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isAllPages = true }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        RadioButton(
                                            selected = isAllPages,
                                            onClick = { isAllPages = true },
                                            colors = RadioButtonDefaults.colors(selectedColor = AppPrimary)
                                        )
                                        Text("كل الصفحات ($totalPagesInPdf صفحة)", color = AppTextPrimary, fontSize = 13.sp)
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isAllPages = false }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        RadioButton(
                                            selected = !isAllPages,
                                            onClick = { isAllPages = false },
                                            colors = RadioButtonDefaults.colors(selectedColor = AppPrimary)
                                        )
                                        Text("نطاق مخصص للتحويل", color = AppTextPrimary, fontSize = 13.sp)
                                    }

                                    AnimatedVisibility(visible = !isAllPages) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, top = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = customPageFrom,
                                                onValueChange = { customPageFrom = it },
                                                label = { Text("من صفحة") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = AppPrimary,
                                                    focusedLabelColor = AppPrimary
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )

                                            OutlinedTextField(
                                                value = customPageTo,
                                                onValueChange = { customPageTo = it },
                                                label = { Text("إلى صفحة") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = AppPrimary,
                                                    focusedLabelColor = AppPrimary
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            // LAUNCH BUTTON
                            Button(
                                onClick = {
                                    val start = if (isAllPages) 1 else customPageFrom.toIntOrNull() ?: 1
                                    val end = if (isAllPages) totalPagesInPdf else customPageTo.toIntOrNull() ?: totalPagesInPdf

                                    // Validate
                                    if (start < 1 || end > totalPagesInPdf || start > end) {
                                        Toast.makeText(context, "الرجاء التحقق من نطاق الصفحات المدخل", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isConverting = true
                                        scope.launch {
                                            try {
                                                val result = renderPdfToImages(
                                                    context = context,
                                                    uri = selectedPdfUri!!,
                                                    baseName = selectedPdfName.substringBeforeLast("."),
                                                    startPageZeroBased = start - 1,
                                                    endPageZeroBased = end - 1,
                                                    scale = resolutionScale,
                                                    format = format,
                                                    quality = (jpgQuality * 100).toInt(),
                                                    onProgress = { current, total ->
                                                        currentProgressPage = current
                                                    }
                                                )
                                                if (result != null) {
                                                    conversionSuccessDir = result
                                                    convertedImagesList = result.listFiles()?.toList()?.sortedBy { it.name } ?: emptyList()
                                                } else {
                                                    Toast.makeText(context, "حدث خطأ أثناء فك ومعالجة الصفحات", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Log.e("PdfToImagesScreen", "Exception during conversion", e)
                                                Toast.makeText(context, "خطأ في التحويل: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isConverting = false
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("convert_pdf_to_images_submit_button")
                            ) {
                                Icon(imageVector = Icons.Default.Transform, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تحويل الآن", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }

                // --- PROGRESS DIALOG OVERLAY ---
                if (isConverting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppSurface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(color = AppPrimary, strokeWidth = 5.dp)

                                val start = if (isAllPages) 1 else customPageFrom.toIntOrNull() ?: 1
                                val end = if (isAllPages) totalPagesInPdf else customPageTo.toIntOrNull() ?: totalPagesInPdf
                                val totalToConvert = end - start + 1
                                val currentRelative = currentProgressPage - start + 1

                                Text(
                                    text = "جاري تحويل صفحات المستند لقوالب صور...",
                                    color = AppTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                LinearProgressIndicator(
                                    progress = if (totalToConvert > 0) currentProgressPage.toFloat() / totalToConvert.toFloat() else 0f,
                                    color = AppPrimary,
                                    trackColor = Color(0xFF2C2C35),
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                )

                                Text(
                                    text = "الصفحة ${currentProgressPage} من ${totalToConvert}",
                                    color = AppTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

suspend fun renderPdfToImages(
    context: Context,
    uri: Uri,
    baseName: String,
    startPageZeroBased: Int,
    endPageZeroBased: Int,
    scale: Int,
    format: String,
    quality: Int,
    onProgress: (Int, Int) -> Unit
): File? = withContext(Dispatchers.IO) {
    var outputDir: File? = null
    try {
        val countToConvert = endPageZeroBased - startPageZeroBased + 1
        val cleanBaseName = baseName.replace(Regex("[^a-zA-Z0-9_\\u0600-\\u06FF]"), "_")
        outputDir = File(context.getExternalFilesDir(null), "PDF_Images_${cleanBaseName}_${System.currentTimeMillis()}")
        outputDir.mkdirs()

        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val pdfRenderer = PdfRenderer(pfd)
            
            for (i in startPageZeroBased..endPageZeroBased) {
                if (i >= pdfRenderer.pageCount) break
                
                val page = pdfRenderer.openPage(i)
                
                // Scale Bitmap
                val width = page.width * scale
                val height = page.height * scale
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                
                // Background must be white for PDFs to correct transparency issues in JPEG/PNG
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val ext = if (format == "PNG") "png" else "jpg"
                val outFile = File(outputDir, "page_${i + 1}.$ext")
                
                FileOutputStream(outFile).use { stream ->
                    if (format == "PNG") {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    } else {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                    }
                }
                bitmap.recycle()

                val currentStep = i - startPageZeroBased + 1
                onProgress(currentStep, countToConvert)
            }
            
            pdfRenderer.close()
        }
        return@withContext outputDir
    } catch (e: Exception) {
        Log.e("PdfToImagesScreen", "Rendering error", e)
        outputDir?.deleteRecursively()
        return@withContext null
    }
}

suspend fun shareImagesAsZip(context: Context, pdfName: String, files: List<File>) = withContext(Dispatchers.IO) {
    try {
        val cleanName = pdfName.substringBeforeLast(".").replace(" ", "_")
        val zipFile = File(context.cacheDir, "${cleanName}_images.zip")
        if (zipFile.exists()) {
            zipFile.delete()
        }

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { input ->
                    input.copyTo(zip)
                }
                zip.closeEntry()
            }
        }

        // Share Dialog
        val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", zipFile)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "مشاركة الملفات المجمعة كمجلد مضغوط ZIP"))
    } catch (e: Exception) {
        Log.e("PdfToImagesScreen", "Zipping error", e)
    }
}
