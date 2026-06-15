package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.theme.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToWordScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfName by remember { mutableStateOf("") }
    var totalPagesInPdf by remember { mutableStateOf(0) }

    // Conversion Options
    var includeImages by remember { mutableStateOf(true) }
    var preserveFormatting by remember { mutableStateOf(true) }

    // Engine UI States
    var isConverting by remember { mutableStateOf(false) }
    var conversionStatusMsg by remember { mutableStateOf("") }
    var currentProgressPage by remember { mutableStateOf(0) }
    var convertedDocxFile by remember { mutableStateOf<File?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                selectedPdfUri = uri
                selectedPdfName = getFileNameFromUri(context, uri) ?: "document.pdf"
                convertedDocxFile = null
                
                // Read metadata safely/fast
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val document = PDDocument.load(stream)
                        totalPagesInPdf = document.numberOfPages
                        document.close()
                    }
                } catch (e: Exception) {
                    Log.e("PdfToWordScreen", "Error counting pages", e)
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
                    title = { Text("PDF إلى Word", color = AppTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
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
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Warning Disclaimer Banner
                    Surface(
                        color = Color(0xFFFFD93D).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFFFD93D).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFFFD93D),
                                modifier = Modifier.size(24.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "تنبيه هام وملاحظة:",
                                    color = Color(0xFFFFD93D),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "هذا التحويل يستخرج النص والصور فقط بشكل تقريبي. التنسيقات المعقدة والجداول قد لا تظهر بدقة تطابق المستند الأصلي.",
                                    color = AppTextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // Selected File Card or Selector
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
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = AppTextSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "اختر مستند PDF للبدء بالاستخراج والتحويل",
                                    color = AppTextSecondary,
                                    fontSize = 13.sp
                                )
                                Button(
                                    onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                    modifier = Modifier.testTag("select_pdf_to_word_btn")
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
                                            text = "الصفحات: $totalPagesInPdf صفحة",
                                            color = AppTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            selectedPdfUri = null
                                            convertedDocxFile = null
                                        }
                                    ) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "إلغاء", tint = Color.Red)
                                    }
                                }
                            }
                        }
                    }

                    if (selectedPdfUri != null) {
                        // Options Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppSurface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "خيارات وتفضيلات التحويل:",
                                    color = AppTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { includeImages = !includeImages }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Checkbox(
                                        checked = includeImages,
                                        onCheckedChange = { includeImages = it },
                                        colors = CheckboxDefaults.colors(checkedColor = AppPrimary)
                                    )
                                    Text("تضمين وبث الصور المستخرجة بالـ Docx", color = AppTextPrimary, fontSize = 13.sp)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { preserveFormatting = !preserveFormatting }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Checkbox(
                                        checked = preserveFormatting,
                                        onCheckedChange = { preserveFormatting = it },
                                        colors = CheckboxDefaults.colors(checkedColor = AppPrimary)
                                    )
                                    Text("محاولة الحفاظ على تنسيق وتقارب الأسطر", color = AppTextPrimary, fontSize = 13.sp)
                                }
                            }
                        }

                        // Conversion Result action panel or trigger button
                        if (convertedDocxFile != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AppSurface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AppPrimaryVariant,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text("تم التحويل بنجاح!", color = AppTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("تم حفظ المستند كملف Word DOCX قابل للتعديل بالكامل.", color = AppTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                shareConvertedDocx(context, convertedDocxFile!!)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                            modifier = Modifier.weight(1f).testTag("share_docx_button")
                                        ) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("مشاركة المستند", color = Color.White)
                                        }

                                        Button(
                                            onClick = {
                                                selectedPdfUri = null
                                                convertedDocxFile = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C35)),
                                            modifier = Modifier.weight(0.8f)
                                        ) {
                                            Text("تحويل آخر", color = Color.White)
                                        }
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    isConverting = true
                                    scope.launch {
                                        val result = processPdfToWord(
                                            context = context,
                                            uri = selectedPdfUri!!,
                                            baseName = selectedPdfName.substringBeforeLast("."),
                                            includeImages = includeImages,
                                            preserveFormatting = preserveFormatting,
                                            onProgress = { current, msg ->
                                                currentProgressPage = current
                                                conversionStatusMsg = msg
                                            }
                                        )
                                        isConverting = false
                                        if (result != null) {
                                            convertedDocxFile = result
                                        } else {
                                            Toast.makeText(context, "فشل فك وترجمة ملف الـ PDF إلى Word.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("convert_docx_submit_button")
                            ) {
                                Icon(imageVector = Icons.Default.Transform, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تحويل إلى DOCX الآن", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }

                // Loading Overlay dialog
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
                                Text(
                                    text = "جاري تحويل ومعالجة وتخليص ملف Word...",
                                    color = AppTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                LinearProgressIndicator(
                                    progress = if (totalPagesInPdf > 0) currentProgressPage.toFloat() / totalPagesInPdf.toFloat() else 0f,
                                    color = AppPrimary,
                                    trackColor = Color(0xFF2C2C35),
                                    modifier = Modifier.fillMaxWidth().height(8.dp)
                                )

                                Text(
                                    text = conversionStatusMsg,
                                    color = AppTextSecondary,
                                    fontSize = 12.sp,
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

suspend fun processPdfToWord(
    context: Context,
    uri: Uri,
    baseName: String,
    includeImages: Boolean,
    preserveFormatting: Boolean,
    onProgress: (Int, String) -> Unit
): File? = withContext(Dispatchers.IO) {
    try {
        PDFBoxResourceLoader.init(context.applicationContext)
        val wordDoc = XWPFDocument()

        context.contentResolver.openInputStream(uri)?.use { stream ->
            PDDocument.load(stream).use { pdfDoc ->
                val totalPages = pdfDoc.numberOfPages
                val stripper = PDFTextStripper()

                for (pageIdx in 0 until totalPages) {
                    val pageNumber = pageIdx + 1
                    onProgress(pageNumber, "جاري استخراج وقراءة الصفحة $pageNumber من $totalPages...")

                    // 1. Text extraction via Stripper
                    stripper.startPage = pageNumber
                    stripper.endPage = pageNumber
                    val pageText = stripper.getText(pdfDoc) ?: ""

                    // Add content to Word paragraphs
                    val lines = pageText.split("\n")
                    lines.forEach { line ->
                        if (line.isNotBlank() || !preserveFormatting) {
                            val paragraph = wordDoc.createParagraph()
                            // Arabic layout requires Right-to-Left writing
                            paragraph.alignment = ParagraphAlignment.RIGHT
                            val run = paragraph.createRun()
                            run.setText(line.trim())
                            run.fontSize = 12
                            run.fontFamily = "Arial"
                        }
                    }

                    // Add Page Break inside Word document if not last page
                    if (pageIdx < totalPages - 1) {
                        val breakParagraph = wordDoc.createParagraph()
                        val run = breakParagraph.createRun()
                        run.addBreak(BreakType.PAGE)
                    }

                    // 2. Extra images extraction if enabled
                    if (includeImages) {
                        try {
                            val page = pdfDoc.getPage(pageIdx)
                            val resources = page.resources
                            if (resources != null) {
                                for (name in resources.xObjectNames) {
                                    if (resources.isImageXObject(name)) {
                                        val xObject = resources.getXObject(name)
                                        if (xObject is PDImageXObject) {
                                            val imageBitmap = xObject.image
                                            val bos = ByteArrayOutputStream()
                                            imageBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos)
                                            val imageBytes = bos.toByteArray()

                                            // Draw inside docx
                                            val imageParagraph = wordDoc.createParagraph()
                                            imageParagraph.alignment = ParagraphAlignment.CENTER
                                            val imgRun = imageParagraph.createRun()
                                            imgRun.addPicture(
                                                ByteArrayInputStream(imageBytes),
                                                XWPFDocument.PICTURE_TYPE_PNG,
                                                "embedded_img_${System.currentTimeMillis()}.png",
                                                Units.toEMU(340.0), // ~width 340dp
                                                Units.toEMU(220.0)  // ~height 220dp
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PdfToWordScreen", "Error extracting page image", e)
                        }
                    }
                }
            }
        }

        // Save Word file
        val outputDir = File(context.getExternalFilesDir(null), "ConvertedWordDocuments")
        outputDir.mkdirs()
        
        val cleanName = baseName.replace(Regex("[^a-zA-Z0-9_\\u0600-\\u06FF]"), "_")
        val outputFile = File(outputDir, "${cleanName}.docx")
        
        FileOutputStream(outputFile).use { fos ->
            wordDoc.write(fos)
        }
        wordDoc.close()

        return@withContext outputFile
    } catch (e: Exception) {
        Log.e("PdfToWordScreen", "Conversion docx writing failed", e)
        return@withContext null
    }
}

fun shareConvertedDocx(context: Context, docFile: File) {
    try {
        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            docFile
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "مشاركة الملف المستخرج"))
    } catch (e: Exception) {
        Toast.makeText(context, "فشل مشاركة الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
