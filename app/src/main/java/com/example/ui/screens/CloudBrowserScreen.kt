package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// High fidelity Cloud entity matches cloud-integration goals
data class CloudFileItem(
    val id: String,
    val name: String,
    val size: String,
    val provider: String, // "google_drive" or "dropbox"
    val modifiedTime: String,
    var isDownloaded: Boolean = false,
    var downloadProgress: Float = 0f,
    var isDownloading: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBrowserScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Status connections
    var isGoogleConnected by remember { mutableStateOf(false) }
    var isDropboxConnected by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Sync Animation Logic
    var isSyncing by remember { mutableStateOf(false) }
    var lastSyncTime by remember { mutableStateOf("منذ ساعة") }
    val syncRotation = remember { Animatable(0f) }
    
    // Mock cloud files lists
    var cloudFiles by remember {
        mutableStateOf(
            listOf(
                CloudFileItem("1", "كتيب_تطوير_الواجهات.pdf", "4.2 م.ب", "google_drive", "منذ ساعتين", isDownloaded = true),
                CloudFileItem("2", "الميزانية_المقترحة_2026.pdf", "8.9 م.ب", "google_drive", "منذ يومين"),
                CloudFileItem("3", "دليل_المستخدم_السريع.pdf", "1.5 م.ب", "dropbox", "منذ 3 أيام"),
                CloudFileItem("4", "رواية_عالم_جديد.pdf", "12.1 م.ب", "dropbox", "منذ أسبوع"),
                CloudFileItem("5", "شهادة_التخرج_النهائية.pdf", "2.7 م.ب", "google_drive", "شهر مضى")
            )
        )
    }

    val filteredFiles = remember(cloudFiles, searchQuery) {
        cloudFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "التخزين السحابي",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = AppTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "العودة",
                            tint = AppTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF13131A),
                    titleContentColor = AppTextPrimary
                )
            )
        },
        containerColor = Color(0xFF13131A),
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Providers & Connect State Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Google Drive Connection Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("google_drive_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF4285F4).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "Google Drive",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AppTextPrimary
                        )
                        
                        Text(
                            if (isGoogleConnected) "متصل (7.2 ج.ب حر)" else "غير متصل",
                            fontSize = 11.sp,
                            color = if (isGoogleConnected) Color(0xFF4CAF50) else AppTextSecondary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Button(
                            onClick = {
                                isGoogleConnected = !isGoogleConnected
                                val message = if (isGoogleConnected) "تم ربط حساب Google Drive بنجاح" else "تم إلغاء ربط حساب Google Drive"
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isGoogleConnected) Color.Gray.copy(alpha = 0.3f) else AppPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp).testTag("google_drive_btn")
                        ) {
                            Text(
                                if (isGoogleConnected) "إلغاء الربط" else "ربط الحساب",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Dropbox Connection Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dropbox_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF0061FE).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = Color(0xFF0061FE),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "Dropbox",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AppTextPrimary
                        )
                        
                        Text(
                            if (isDropboxConnected) "متصل (2.1 ج.ب حر)" else "غير متصل",
                            fontSize = 11.sp,
                            color = if (isDropboxConnected) Color(0xFF4CAF50) else AppTextSecondary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Button(
                            onClick = {
                                isDropboxConnected = !isDropboxConnected
                                val message = if (isDropboxConnected) "تم ربط حساب Dropbox بنجاح" else "تم إلغاء ربط حساب Dropbox"
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDropboxConnected) Color.Gray.copy(alpha = 0.3f) else AppPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp).testTag("dropbox_btn")
                        ) {
                            Text(
                                if (isDropboxConnected) "إلغاء الربط" else "ربط الحساب",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sync Stats and Trigger Bar
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = AppPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "المزامنة السحابية التقدمية",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AppTextPrimary
                            )
                            Text(
                                "آخر تحديث: $lastSyncTime",
                                fontSize = 11.sp,
                                color = AppTextSecondary
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = {
                            if (!isGoogleConnected && !isDropboxConnected) {
                                Toast.makeText(context, "الرجاء ربط حساب تخزين واحد على الأقل أولاً", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            if (!isSyncing) {
                                isSyncing = true
                                coroutineScope.launch {
                                    // Rotate animation
                                    syncRotation.animateTo(
                                        targetValue = syncRotation.value + 360f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1200, easing = LinearEasing),
                                            repeatMode = RepeatMode.Restart
                                        )
                                    )
                                }
                                coroutineScope.launch {
                                    delay(2000)
                                    isSyncing = false
                                    syncRotation.snapTo(0f)
                                    lastSyncTime = "الآن"
                                    Toast.makeText(context, "اكتملت المزامنة السحابية بنجاح!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("sync_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "مزامنة الآن",
                            tint = AppPrimary
                        )
                    }
                }
            }

            // Search input field
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث في الملفات السحابية...", color = AppTextSecondary, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = AppTextPrimary,
                    unfocusedTextColor = AppTextPrimary,
                    focusedContainerColor = AppSurface,
                    unfocusedContainerColor = AppSurface,
                    disabledContainerColor = AppSurface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("cloud_search_bar")
            )

            // Content status check
            if (!isGoogleConnected && !isDropboxConnected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.CloudOff,
                            contentDescription = null,
                            tint = AppTextSecondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "لم يتم ربط أي وحدة تخزين سحابي",
                            color = AppTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "اربط حساب Google Drive أو Dropbox الخاص بك لمزامنة، استيراد وتصفح ملفاتك بأمان.",
                            color = AppTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = AppTextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "لا توجد نتائج مطابقة لـ \"$searchQuery\"",
                            color = AppTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Text(
                    "المستندات السحابية المتاحة:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = AppTextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredFiles, key = { it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = AppSurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6B6B),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = AppTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                item.size,
                                                fontSize = 11.sp,
                                                color = AppTextSecondary
                                            )
                                            Text(
                                                "•",
                                                fontSize = 11.sp,
                                                color = AppTextSecondary
                                            )
                                            Text(
                                                if (item.provider == "google_drive") "Google Drive" else "Dropbox",
                                                fontSize = 11.sp,
                                                color = if (item.provider == "google_drive") Color(0xFF4285F4) else Color(0xFF0061FE)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (item.isDownloading) {
                                        CircularProgressIndicator(
                                            progress = item.downloadProgress,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(24.dp),
                                            color = AppPrimary
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                if (item.isDownloaded) {
                                                    Toast.makeText(context, "الملف جاهز ومحفوظ محلياً!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val index = cloudFiles.indexOfFirst { it.id == item.id }
                                                    if (index != -1) {
                                                        val newList = cloudFiles.toMutableList()
                                                        newList[index] = newList[index].copy(isDownloading = true, downloadProgress = 0f)
                                                        cloudFiles = newList
                                                        
                                                        // Simulate background download
                                                        coroutineScope.launch {
                                                            for (progress in 1..10) {
                                                                delay(150)
                                                                val currentIdx = cloudFiles.indexOfFirst { it.id == item.id }
                                                                if (currentIdx != -1) {
                                                                    val listForUpdate = cloudFiles.toMutableList()
                                                                    listForUpdate[currentIdx] = listForUpdate[currentIdx].copy(downloadProgress = progress / 10f)
                                                                    cloudFiles = listForUpdate
                                                                }
                                                            }
                                                            val finalIdx = cloudFiles.indexOfFirst { it.id == item.id }
                                                            if (finalIdx != -1) {
                                                                val listForCompletion = cloudFiles.toMutableList()
                                                                listForCompletion[finalIdx] = listForCompletion[finalIdx].copy(isDownloading = false, isDownloaded = true)
                                                                cloudFiles = listForCompletion
                                                            }
                                                            Toast.makeText(context, "اكتمل تحميل الملف: ${item.name}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (item.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                                contentDescription = if (item.isDownloaded) "محمل" else "تحميل",
                                                tint = if (item.isDownloaded) Color(0xFF4CAF50) else AppPrimary,
                                                modifier = Modifier.size(24.dp)
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
