package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat

data class SelectedPdfFile(
    val id: String,
    val uri: Uri,
    val name: String,
    val sizeString: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val selectedFiles = remember { mutableStateListOf<SelectedPdfFile>() }
    var isMerging by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val (name, size) = getFileNameAndSize(context, uri)
                selectedFiles.add(
                    SelectedPdfFile(
                        id = uri.toString() + "_" + System.nanoTime(),
                        uri = uri,
                        name = name,
                        sizeString = size
                    )
                )
            }
        }
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "دمج ملفات PDF",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("merge_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "عودة",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // List / State section
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallMerge,
                                contentDescription = null,
                                tint = AppTextSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(80.dp)
                            )
                            Text(
                                text = "لم تقم باختيار أي ملفات للدمج بعد.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "اضغط على الزر أدناه لاختيار ملفات PDF التي ترغب في دمجها وترتيبها.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTextSecondary.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "استخدم أزرار الأسهم (▲/▼) لتغيير الترتيب حسب رغبتك قبل الدمج:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("selected_pdfs_list")
                    ) {
                        itemsIndexed(selectedFiles, key = { _, item -> item.id }) { index, item ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = AppSurface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Reordering Arrows (Up / Down controls on left)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    // Swap with previous
                                                    val temp = selectedFiles[index]
                                                    selectedFiles[index] = selectedFiles[index - 1]
                                                    selectedFiles[index - 1] = temp
                                                }
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .testTag("reorder_up_$index")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "ترتيب للأعلى",
                                                tint = if (index > 0) AppPrimary else AppTextSecondary.copy(alpha = 0.3f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        
                                        IconButton(
                                            onClick = {
                                                if (index < selectedFiles.size - 1) {
                                                    // Swap with next
                                                    val temp = selectedFiles[index]
                                                    selectedFiles[index] = selectedFiles[index + 1]
                                                    selectedFiles[index + 1] = temp
                                                }
                                            },
                                            enabled = index < selectedFiles.size - 1,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .testTag("reorder_down_$index")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "ترتيب للأسفل",
                                                tint = if (index < selectedFiles.size - 1) AppPrimary else AppTextSecondary.copy(alpha = 0.3f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    // PDF Icon
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = AppPrimary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    // File Info
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = AppTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "الحجم: ${item.sizeString}",
                                            fontSize = 12.sp,
                                            color = AppTextSecondary
                                        )
                                    }
                                    
                                    // Delete Action
                                    IconButton(
                                        onClick = { selectedFiles.remove(item) },
                                        modifier = Modifier.testTag("delete_pdf_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف",
                                            tint = Color.Red.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Buttons and Progress
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Add Files Button
                Button(
                    onClick = { filePickerLauncher.launch("application/pdf") },
                    enabled = !isMerging,
                    colors = ButtonDefaults.buttonColors(containerColor = AppSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_pdf_files_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إضافة ملفات PDF", color = Color.White, fontWeight = FontWeight.Bold)
                }
                
                // Merge Execute Button
                Button(
                    onClick = {
                        isMerging = true
                        errorMessage = null
                        coroutineScope.launch {
                            try {
                                val destinationFile = withContext(Dispatchers.IO) {
                                    val mergedDocument = PDDocument()
                                    val openedDocs = mutableListOf<PDDocument>()
                                    
                                    try {
                                        selectedFiles.forEach { file ->
                                            val inputStream = context.contentResolver.openInputStream(file.uri)
                                            if (inputStream != null) {
                                                val sourceDoc = PDDocument.load(inputStream)
                                                openedDocs.add(sourceDoc)
                                                sourceDoc.pages.forEach { page ->
                                                    mergedDocument.addPage(page)
                                                }
                                            }
                                        }
                                        
                                        val output = File(context.getExternalFilesDir(null), "merged_${System.currentTimeMillis()}.pdf")
                                        val fos = FileOutputStream(output)
                                        mergedDocument.save(fos)
                                        fos.close()
                                        
                                        output
                                    } finally {
                                        try {
                                            mergedDocument.close()
                                        } catch (e: Exception) {
                                            Log.e("MergePdfScreen", "Error closing merged doc", e)
                                        }
                                        openedDocs.forEach { doc ->
                                            try {
                                                doc.close()
                                            } catch (e: Exception) {
                                                Log.e("MergePdfScreen", "Error closing source doc", e)
                                            }
                                        }
                                    }
                                }
                                
                                isMerging = false
                                val encoded = Uri.encode(Uri.fromFile(destinationFile).toString())
                                onNavigateToReader(encoded)
                            } catch (e: Exception) {
                                Log.e("MergePdfScreen", "Error during merger", e)
                                isMerging = false
                                errorMessage = "حدث خطأ أثناء دمج الملفات. يرجى التأكد من صلاحية الملفات."
                            }
                        }
                    },
                    enabled = selectedFiles.size >= 2 && !isMerging,
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_merge_button")
                ) {
                    if (isMerging) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CallMerge,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "دمج وحفظ الملف (${selectedFiles.size} ملفات)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, String> {
    var name = "ملف PDF"
    var sizeString = "غير معروف"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex) ?: "ملف PDF"
                }
                if (sizeIndex != -1) {
                    val sizeBytes = cursor.getLong(sizeIndex)
                    sizeString = formatFileSize(sizeBytes)
                }
            }
        }
    } catch (e: Exception) {
        name = uri.lastPathSegment ?: "ملف PDF"
    }
    return Pair(name, sizeString)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}