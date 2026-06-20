package com.example.ui.screens

import android.content.Context
import android.net.Uri
import com.example.util.findActivity
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchMatch(
    val fileUriStr: String,
    val fileName: String,
    val pageIndex: Int, // 0-indexed
    val lineContext: String
)

data class FileSearchSummary(
    val fileUriStr: String,
    val fileName: String,
    val matchCount: Int,
    val matches: List<SearchMatch>
)

data class SearchableFile(
    val uri: Uri,
    val name: String,
    val isSelected: Boolean = true,
    val isCustom: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiFileSearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val viewModel = remember(context) {
        val activity = context.findActivity()
            ?: throw IllegalStateException("Context must be ComponentActivity")
        androidx.lifecycle.ViewModelProvider(activity)[PdfViewModel::class.java]
    }

    val recentDocs by viewModel.recentDocuments.collectAsState()

    // List of files to search
    var searchableFiles by remember { mutableStateOf<List<SearchableFile>>(emptyList()) }

    // On start, populate files from recent documents
    LaunchedEffect(recentDocs) {
        if (searchableFiles.isEmpty() && recentDocs.isNotEmpty()) {
            searchableFiles = recentDocs.map {
                SearchableFile(
                    uri = Uri.parse(it.uri),
                    name = it.name,
                    isSelected = true,
                    isCustom = false
                )
            }
        }
    }

    // SAF Picker for raw files selection
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                val newFiles = uris.map { uri ->
                    // Get display name
                    val displayName = getFileNameFromUri(context, uri) ?: uri.lastPathSegment ?: "ملف غير معروف"
                    try {
                        val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    SearchableFile(
                        uri = uri,
                        name = displayName,
                        isSelected = true,
                        isCustom = true
                    )
                }
                searchableFiles = searchableFiles + newFiles
            }
        }
    )

    // States
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FileSearchSummary>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }

    // Advanced search options
    var isCaseSensitive by remember { mutableStateOf(false) }
    var isWholeWord by remember { mutableStateOf(false) }
    var isRegex by remember { mutableStateOf(false) }

    // RTL Provider
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "البحث متعدد الملفات",
                            color = AppTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "رجوع",
                                tint = AppTextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0D0D0F)
                    )
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Search Bar Input
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("أدخل كلمة البحث...", color = AppTextSecondary, fontSize = 14.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AppPrimary,
                                        unfocusedBorderColor = Color(0xFF2C2C35),
                                        focusedTextColor = AppTextPrimary,
                                        unfocusedTextColor = AppTextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("multi_search_input_field")
                                )

                                Button(
                                    onClick = {
                                        val activeFiles = searchableFiles.filter { it.isSelected }.map { it.uri to it.name }
                                        if (searchQuery.isBlank()) {
                                            android.widget.Toast.makeText(context, "الرجاء إدخال كلمة للبحث", android.widget.Toast.LENGTH_SHORT).show()
                                        } else if (activeFiles.isEmpty()) {
                                            android.widget.Toast.makeText(context, "الرجاء تحديد ملف واحد على الأقل للبحث فيه", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            isSearching = true
                                            hasSearched = true
                                            searchResults = emptyList()
                                            coroutineScope.launch {
                                                val results = searchMultiplePdfs(
                                                    context = context,
                                                    pdfFiles = activeFiles,
                                                    query = searchQuery,
                                                    isCaseSensitive = isCaseSensitive,
                                                    isWholeWord = isWholeWord,
                                                    isRegex = isRegex,
                                                    onProgress = { msg -> progressMessage = msg }
                                                )
                                                searchResults = results
                                                isSearching = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                    modifier = Modifier
                                        .height(56.dp)
                                        .testTag("multi_search_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("بحث", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            // Horizontal Divider
                            HorizontalDivider(color = Color(0xFF2C2C35), modifier = Modifier.padding(vertical = 4.dp))

                            // Advanced Options
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Checkbox(
                                        checked = isCaseSensitive,
                                        onCheckedChange = { isCaseSensitive = it },
                                        colors = CheckboxDefaults.colors(checkedColor = AppPrimary)
                                    )
                                    Text("مطابقة الأحرف", color = AppTextPrimary, fontSize = 11.sp)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Checkbox(
                                        checked = isWholeWord,
                                        onCheckedChange = { isWholeWord = it },
                                        colors = CheckboxDefaults.colors(checkedColor = AppPrimary)
                                    )
                                    Text("كلمة كاملة", color = AppTextPrimary, fontSize = 11.sp)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Checkbox(
                                        checked = isRegex,
                                        onCheckedChange = { isRegex = it },
                                        colors = CheckboxDefaults.colors(checkedColor = AppPrimary)
                                    )
                                    Text("تعبير نمطي (Regex)", color = AppTextPrimary, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // 2. File Selector & Target Checklist
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "الملفات المستهدفة بالبحث (${searchableFiles.size}):",
                                    color = AppTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Button(
                                    onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C35)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("إضافة ملفات", color = Color.White, fontSize = 11.sp)
                                }
                            }

                            if (searchableFiles.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("لا توجد ملفات محددة. أضف ملفات للبحث فيها.", color = AppTextSecondary, fontSize = 12.sp)
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(searchableFiles) { file ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    searchableFiles = searchableFiles.map {
                                                        if (it.uri == file.uri) it.copy(isSelected = !it.isSelected) else it
                                                    }
                                                }
                                                .padding(vertical = 4.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = file.isSelected,
                                                onCheckedChange = { checked ->
                                                    searchableFiles = searchableFiles.map {
                                                        if (it.uri == file.uri) it.copy(isSelected = checked) else it
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = AppPrimary),
                                                modifier = Modifier.size(20.dp)
                                            )

                                            Icon(
                                                imageVector = Icons.Default.PictureAsPdf,
                                                contentDescription = null,
                                                tint = AppPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )

                                            Text(
                                                text = file.name,
                                                color = if (file.isSelected) AppTextPrimary else AppTextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )

                                            if (file.isCustom) {
                                                IconButton(
                                                    onClick = {
                                                        searchableFiles = searchableFiles.filter { it.uri != file.uri }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Results Section Title
                    Text(
                        text = "نتائج البحث الأخير:",
                        color = AppTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // 4. Search Progress / Results List
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (isSearching) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = AppPrimary)
                                Text(
                                    text = progressMessage,
                                    color = AppTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (!hasSearched) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "الرجاء إدخال كلمة البحث والبدء",
                                    color = AppTextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        } else if (searchResults.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "لم يتم العثور على أي مطابقات في الملفات المحددة",
                                    color = AppTextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(searchResults) { summary ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = AppSurface),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = AppPrimary, modifier = Modifier.size(18.dp))
                                                    Text(
                                                        text = summary.fileName,
                                                        color = AppTextPrimary,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Badge(containerColor = AppPrimary, contentColor = Color.White) {
                                                    Text("${summary.matchCount} تطابق")
                                                }
                                            }

                                            HorizontalDivider(color = Color(0xFF2C2C35))

                                            summary.matches.forEach { match ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            // Open Document at the matching page!
                                                            val uri = Uri.parse(match.fileUriStr)
                                                            viewModel.selectDocument(context, uri)
                                                            
                                                            // Setup ViewModel last read page to matching page directly so it opens there!
                                                            viewModel.saveLastPage(match.fileUriStr, match.pageIndex)

                                                            val encodedUri = Uri.encode(match.fileUriStr)
                                                            onNavigateToReader(encodedUri)
                                                        }
                                                        .padding(6.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Badge(containerColor = Color(0xFF2C2C35), contentColor = AppTextPrimary) {
                                                        Text("صفحة ${match.pageIndex + 1}")
                                                    }

                                                    Text(
                                                        text = match.lineContext,
                                                        color = AppTextSecondary,
                                                        fontSize = 12.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )

                                                    Icon(
                                                        imageVector = Icons.Default.OpenInNew,
                                                        contentDescription = "فتح الملف والذهاب للصفحة",
                                                        tint = AppPrimary,
                                                        modifier = Modifier.size(16.dp)
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
            }
        }
    }
}

fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

suspend fun searchMultiplePdfs(
    context: Context,
    pdfFiles: List<Pair<Uri, String>>,
    query: String,
    isCaseSensitive: Boolean,
    isWholeWord: Boolean,
    isRegex: Boolean,
    onProgress: (String) -> Unit
): List<FileSearchSummary> = withContext(Dispatchers.IO) {
    if (query.isBlank() || pdfFiles.isEmpty()) return@withContext emptyList()
    
    val summaries = mutableListOf<FileSearchSummary>()
    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
    
    for ((uri, name) in pdfFiles) {
        onProgress("جرى البحث في: $name")
        val fileMatches = mutableListOf<SearchMatch>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream).use { document ->
                    val totalPages = document.numberOfPages
                    val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                    
                    for (pageIdx in 0 until totalPages) {
                        stripper.startPage = pageIdx + 1
                        stripper.endPage = pageIdx + 1
                        val pageText = stripper.getText(document) ?: ""
                        
                        val lines = pageText.split('\n')
                        for (line in lines) {
                            val trimmedLine = line.trim()
                            if (trimmedLine.isBlank()) continue
                            
                            var isMatch = false
                            if (isRegex) {
                                try {
                                    val options = if (isCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                                    val regex = Regex(query, options)
                                    isMatch = regex.containsMatchIn(trimmedLine)
                                } catch (e: Exception) {
                                    // ignore
                                }
                            } else {
                                if (isWholeWord) {
                                    val pattern = if (isCaseSensitive) {
                                        "\\b${Regex.escape(query)}\\b"
                                    } else {
                                        "(?i)\\b${Regex.escape(query)}\\b"
                                    }
                                    try {
                                        isMatch = Regex(pattern).containsMatchIn(trimmedLine)
                                    } catch (e: Exception) {
                                        isMatch = trimmedLine.contains(query, ignoreCase = !isCaseSensitive)
                                    }
                                } else {
                                    isMatch = trimmedLine.contains(query, ignoreCase = !isCaseSensitive)
                                }
                            }
                            
                            if (isMatch) {
                                fileMatches.add(
                                    SearchMatch(
                                        fileUriStr = uri.toString(),
                                        fileName = name,
                                        pageIndex = pageIdx,
                                        lineContext = trimmedLine
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MultiFileSearch", "Error searching file: $name", e)
        }
        
        if (fileMatches.isNotEmpty()) {
            summaries.add(
                FileSearchSummary(
                    fileUriStr = uri.toString(),
                    fileName = name,
                    matchCount = fileMatches.size,
                    matches = fileMatches
                )
            )
        }
    }
    
    return@withContext summaries
}
