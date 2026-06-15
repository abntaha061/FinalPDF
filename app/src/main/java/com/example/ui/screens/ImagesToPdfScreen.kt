package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ImageFileItem(
    val uri: Uri,
    val name: String,
    val sizeStr: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesToPdfScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedImages by remember { mutableStateOf<List<ImageFileItem>>(emptyList()) }

    // Page Settings
    var selectedPageSize by remember { mutableStateOf("A4") } // "A4", "Letter", "Original"
    var orientation by remember { mutableStateOf("عمودي") } // "عمودي", "أفقي"
    var marginValue by remember { mutableStateOf(10f) } // 0dp to 40dp
    var imageFitMode by remember { mutableStateOf("ملاءمة") } // "ملاءمة", "تعبئة", "تمدد"

    var isConverting by remember { mutableStateOf(false) }

    // Multi-Select Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                val newItems = uris.map { uri ->
                    // Get Name & Size
                    val details = getImageDetailsFromUri(context, uri)
                    try {
                        val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    ImageFileItem(
                        uri = uri,
                        name = details.first,
                        sizeStr = details.second
                    )
                }
                selectedImages = selectedImages + newItems
            }
        }
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("صور إلى PDF", color = AppTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Images List Box (Scrollable area)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.2f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "الصور المختارة (${selectedImages.size}):",
                                    color = AppTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Button(
                                    onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(36.dp).testTag("select_images_btn")
                                ) {
                                    Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("اختر صور", color = Color.White, fontSize = 13.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (selectedImages.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, tint = AppTextSecondary, modifier = Modifier.size(56.dp))
                                        Text("أضف بعض الصور للبدء ببناء كتاب الـ PDF الخاص بك", color = AppTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(selectedImages) { index, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(80.dp)
                                                .background(Color(0xFF25252D), RoundedCornerShape(8.dp))
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Handle or Reorder buttons
                                            Column(
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.width(36.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        if (index > 0) {
                                                            selectedImages = selectedImages.toMutableList().apply {
                                                                val temp = this[index]
                                                                this[index] = this[index - 1]
                                                                this[index - 1] = temp
                                                            }
                                                        }
                                                    },
                                                    enabled = index > 0,
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "أعلى", tint = if (index > 0) AppTextPrimary else Color.DarkGray)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        if (index < selectedImages.size - 1) {
                                                            selectedImages = selectedImages.toMutableList().apply {
                                                                val temp = this[index]
                                                                this[index] = this[index + 1]
                                                                this[index + 1] = temp
                                                            }
                                                        }
                                                    },
                                                    enabled = index < selectedImages.size - 1,
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "أسفل", tint = if (index < selectedImages.size - 1) AppTextPrimary else Color.DarkGray)
                                                }
                                            }

                                            // Thumbnail
                                            AsyncImage(
                                                model = item.uri,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                            )

                                            // Text info
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.name,
                                                    color = AppTextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = item.sizeStr,
                                                    color = AppTextSecondary,
                                                    fontSize = 10.sp
                                                )
                                            }

                                            // Delete icon
                                            IconButton(
                                                onClick = {
                                                    selectedImages = selectedImages.filterIndexed { idx, _ -> idx != index }
                                                }
                                            ) {
                                                Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف ومسح", tint = Color.Red, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Page Settings Box (Scrollable)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("إعدادات صفحات الـ PDF:", color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            // PAGE SIZE
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("قياس الصفحة وتصميمها:", color = AppTextSecondary, fontSize = 11.sp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    listOf("A4", "Letter", "حجم الصورة").forEach { size ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            RadioButton(
                                                selected = selectedPageSize == size,
                                                onClick = { selectedPageSize = size },
                                                colors = RadioButtonDefaults.colors(selectedColor = AppPrimary),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(size, color = AppTextPrimary, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // ORIENTATION
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("اتجاه اتساق الورق:", color = AppTextSecondary, fontSize = 11.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    listOf("عمودي", "أفقي").forEach { dir ->
                                        FilterChip(
                                            selected = orientation == dir,
                                            onClick = { orientation = dir },
                                            label = { Text(dir, fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = AppPrimary,
                                                selectedLabelColor = Color.White
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            // MARGIN
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("هوامش وحدود برواز الورقة (بيضاء):", color = AppTextSecondary, fontSize = 11.sp)
                                    Text("${marginValue.toInt()}dp", color = AppPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = marginValue,
                                    onValueChange = { marginValue = it },
                                    valueRange = 0f..40f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AppPrimary,
                                        activeTrackColor = AppPrimary
                                    )
                                )
                            }

                            // IMAGE FIT MODE
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("ملاءمة تمدد صور المستند:", color = AppTextSecondary, fontSize = 11.sp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    listOf("ملاءمة", "تعبئة", "تمدد").forEach { mode ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            RadioButton(
                                                selected = imageFitMode == mode,
                                                onClick = { imageFitMode = mode },
                                                colors = RadioButtonDefaults.colors(selectedColor = AppPrimary),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = when (mode) {
                                                    "ملاءمة" -> "ملاءمة (حدود بيضاء)"
                                                    "تعبئة" -> "تعبئة (قص الزائد)"
                                                    else -> "تمدد (قد يشوه)"
                                                },
                                                color = AppTextPrimary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // CONVERT EXECUTION BUTTON
                    Button(
                        onClick = {
                            if (selectedImages.isEmpty()) {
                                Toast.makeText(context, "الرجاء اختيار صورة واحدة على الأقل لبناء الـ PDF", Toast.LENGTH_SHORT).show()
                            } else {
                                isConverting = true
                                scope.launch {
                                    val resultFile = buildPdfFromImages(
                                        context = context,
                                        imageItems = selectedImages,
                                        pageSizeOption = selectedPageSize,
                                        orientationOption = orientation,
                                        marginOption = marginValue,
                                        fitModeOption = imageFitMode
                                    )
                                    isConverting = false
                                    if (resultFile != null) {
                                        val totalPagesCount = selectedImages.size
                                        // Show Snackbar
                                        scope.launch {
                                            val snackAction = snackbarHostState.showSnackbar(
                                                message = "تم إنشاء PDF بـ $totalPagesCount صفحة بنجاح ✓",
                                                actionLabel = "فتح",
                                                duration = SnackbarDuration.Long
                                            )
                                            if (snackAction == SnackbarResult.ActionPerformed) {
                                                val encodedUri = Uri.encode(Uri.fromFile(resultFile).toString())
                                                onNavigateToReader(encodedUri)
                                            }
                                        }
                                        selectedImages = emptyList()
                                    } else {
                                        Toast.makeText(context, "فشل إنشاء تصدير ملف الـ PDF.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("submit_images_to_pdf_btn")
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إنشاء ملف الـ PDF الآن", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                // Loading Overlay
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
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(color = AppPrimary, strokeWidth = 5.dp)
                                Text(
                                    text = "جاري تجميع وتشفير ملفات الصور في مستند PDF واحد...",
                                    color = AppTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getImageDetailsFromUri(context: Context, uri: Uri): Pair<String, String> {
    var name = "image.png"
    var sizeStr = "حجم غير معروف"
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    try {
        if (cursor != null && cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                name = cursor.getString(nameIndex) ?: name
            }
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex != -1) {
                val sizeBytes = cursor.getLong(sizeIndex)
                sizeStr = String.format("%.2f MB", sizeBytes / (1024f * 1024f))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        cursor?.close()
    }
    return Pair(name, sizeStr)
}

suspend fun buildPdfFromImages(
    context: Context,
    imageItems: List<ImageFileItem>,
    pageSizeOption: String,
    orientationOption: String,
    marginOption: Float,
    fitModeOption: String
): File? = withContext(Dispatchers.IO) {
    try {
        PDFBoxResourceLoader.init(context.applicationContext)
        val document = PDDocument()

        val predefinedSize = when (pageSizeOption) {
            "A4" -> PDRectangle.A4
            "Letter" -> PDRectangle.LETTER
            else -> null
        }

        imageItems.forEach { item ->
            // Load Bitmap safely
            val bitmap = try {
                if (Build.VERSION.SDK_INT >= 28) {
                    val source = ImageDecoder.createSource(context.contentResolver, item.uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    context.contentResolver.openInputStream(item.uri).use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                }
            } catch (e: Exception) {
                Log.e("ImagesToPdf", "Error decoding bitmap ${item.name}", e)
                null
            } ?: return@forEach

            val finalPageSize = predefinedSize ?: PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat())
            
            // Handle Orientation
            val pageRectangle = if (orientationOption == "أفقي") {
                PDRectangle(finalPageSize.height, finalPageSize.width)
            } else {
                finalPageSize
            }

            val page = PDPage(pageRectangle)
            document.addPage(page)

            // Lossless representation of image
            val pdImage = LosslessFactory.createFromImage(document, bitmap)
            val contentStream = PDPageContentStream(document, page)

            // Calculate Placement with Margins & Fit Mode
            val pw = page.mediaBox.width
            val ph = page.mediaBox.height
            val marg = marginOption

            val (drawW, drawH, posX, posY) = calculateImagePlacement(
                bitmapWidth = bitmap.width.toFloat(),
                bitmapHeight = bitmap.height.toFloat(),
                pageWidth = pw,
                pageHeight = ph,
                margin = marg,
                fitMode = fitModeOption
            )

            contentStream.drawImage(pdImage, posX, posY, drawW, drawH)
            contentStream.close()
            
            bitmap.recycle()
        }

        val outputDir = File(context.getExternalFilesDir(null), "GeneratedPDFs")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "images_to_pdf_${System.currentTimeMillis()}.pdf")
        
        document.save(outputFile)
        document.close()

        return@withContext outputFile
    } catch (e: Exception) {
        Log.e("ImagesToPdfScreen", "Error during buildPdfFromImages", e)
        return@withContext null
    }
}

fun calculateImagePlacement(
    bitmapWidth: Float,
    bitmapHeight: Float,
    pageWidth: Float,
    pageHeight: Float,
    margin: Float,
    fitMode: String
): FloatArray {
    val availW = pageWidth - 2 * margin
    val availH = pageHeight - 2 * margin

    var drawWidth = availW
    var drawHeight = availH
    var x = margin
    var y = margin

    when (fitMode) {
        "ملاءمة" -> {
            val scale = Math.min(availW / bitmapWidth, availH / bitmapHeight)
            drawWidth = bitmapWidth * scale
            drawHeight = bitmapHeight * scale
            x = margin + (availW - drawWidth) / 2f
            y = margin + (availH - drawHeight) / 2f
        }
        "تعبئة" -> {
            val scale = Math.max(availW / bitmapWidth, availH / bitmapHeight)
            drawWidth = bitmapWidth * scale
            drawHeight = bitmapHeight * scale
            x = margin + (availW - drawWidth) / 2f
            y = margin + (availH - drawHeight) / 2f
        }
        "تمدد" -> {
            drawWidth = availW
            drawHeight = availH
            x = margin
            y = margin
        }
    }

    return floatArrayOf(drawWidth, drawHeight, x, y)
}
