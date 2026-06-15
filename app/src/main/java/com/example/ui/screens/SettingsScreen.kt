package com.example.ui.screens

import android.app.AlertDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

import androidx.compose.ui.draw.alpha

@Composable
fun SettingsScreen(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val viewModel = remember(context) {
        val activity = context as? androidx.activity.ComponentActivity
            ?: throw IllegalStateException("Context must be ComponentActivity")
        androidx.lifecycle.ViewModelProvider(activity)[PdfViewModel::class.java]
    }
    SettingsScreen(
        viewModel = viewModel,
        onBack = {
            navController.popBackStack()
        },
        onNavigateToLanguage = {
            navController.navigate(com.example.ui.navigation.Screen.Language.route)
        },
        onNavigateToAbout = {
            navController.navigate(com.example.ui.navigation.Screen.About.route)
        },
        onNavigateToGestureSettings = {
            navController.navigate(com.example.ui.navigation.Screen.GestureSettings.route)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToGestureSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect settings states
    val appLanguage by viewModel.appLanguage.collectAsState()
    val displayLanguage = if (appLanguage == "en") "English" else "العربية"

    val defaultReadingMode by viewModel.defaultReadingMode.collectAsState()
    val primaryColorHex by viewModel.primaryColorHex.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val uiFontSize by viewModel.uiFontSize.collectAsState()
    val autoSavePosition by viewModel.autoSavePosition.collectAsState()
    val showPageIndicator by viewModel.showPageIndicator.collectAsState()
    val pageSpacing by viewModel.pageSpacing.collectAsState()
    val scrollSpeed by viewModel.scrollSpeed.collectAsState()
    val linkOpenMode by viewModel.linkOpenMode.collectAsState()
    val autoPlayAudio by viewModel.autoPlayAudio.collectAsState()
    val audioVolume by viewModel.audioVolume.collectAsState()
    val recentPdfs by viewModel.recentDocuments.collectAsState()

    // Dialog state for color chooser
    var showColorDialog by remember { mutableStateOf(false) }

    // Dynamic cache size state
    var cacheSize by remember { mutableStateOf("0 KB") }
    LaunchedEffect(Unit) {
        cacheSize = getCacheSize(context)
    }

    // Force Arabic layout direction for RTL requirement
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = Color(0xFF0D0D0F),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0D0D0F)
                    ),
                    title = {
                        Text(
                            text = "الإعدادات",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTextPrimary,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFF0D0D0F))
            ) {
                // SECTION 1: المظهر
                item {
                    SectionHeader("المظهر")
                }

                // Setting 0: لغة التطبيق
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 16.dp)
                            .clickable { onNavigateToLanguage() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("لغة التطبيق", color = AppTextPrimary, fontSize = 15.sp)
                                Text(displayLanguage, color = AppTextSecondary, fontSize = 12.sp)
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Navigate To Language",
                            tint = AppTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Setting 1: وضع القراءة الافتراضي
                item {
                    var dropdownExpanded by remember { mutableStateOf(false) }
                    val displayMode = when (defaultReadingMode) {
                        "night" -> "ليلي"
                        "sepia" -> "بني (سيبيا)"
                        else -> "عادي"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("وضع القراءة الافتراضي", color = AppTextPrimary, fontSize = 15.sp)
                                Text("عادي / ليلي / بني", color = AppTextSecondary, fontSize = 12.sp)
                            }
                        }

                        Box {
                            Button(
                                onClick = { dropdownExpanded = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(displayMode, color = AppTextPrimary, fontSize = 14.sp)
                            }
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.background(AppSurface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("عادي", color = AppTextPrimary) },
                                    onClick = {
                                        viewModel.setDefaultReadingMode("normal")
                                        dropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("ليلي", color = AppTextPrimary) },
                                    onClick = {
                                        viewModel.setDefaultReadingMode("night")
                                        dropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("بني (سيبيا)", color = AppTextPrimary) },
                                    onClick = {
                                        viewModel.setDefaultReadingMode("sepia")
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Setting 2: لون واجهة التطبيق
                item {
                    val parsedColor = try {
                        Color(android.graphics.Color.parseColor(primaryColorHex))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 16.dp)
                            .clickable { showColorDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("لون واجهة التطبيق", color = AppTextPrimary, fontSize = 15.sp)
                                Text("اختر لون التمييز الرئيسي", color = AppTextSecondary, fontSize = 12.sp)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(parsedColor, CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                    }
                }

                // Setting: الألوان الديناميكية (Material You)
                item {
                    val isAndroid12Plus = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                    val subtitle = if (isAndroid12Plus) "يستخدم ألوان خلفية شاشتك" else "يتطلب Android 12 أو أحدث"
                    val itemAlpha = if (isAndroid12Plus) 1.0f else 0.5f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 16.dp)
                            .alpha(itemAlpha)
                            .clickable(enabled = isAndroid12Plus) {
                                viewModel.setDynamicColor(!dynamicColor)
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = if (isAndroid12Plus) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("الألوان الديناميكية (Material You)", color = AppTextPrimary, fontSize = 15.sp)
                                Text(subtitle, color = AppTextSecondary, fontSize = 12.sp)
                            }
                        }

                        Switch(
                            checked = if (isAndroid12Plus) dynamicColor else false,
                            onCheckedChange = { viewModel.setDynamicColor(it) },
                            enabled = isAndroid12Plus,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(0.3f),
                            )
                        )
                    }
                }

                // Setting 3: حجم خط الواجهة
                item {
                    val sizes = listOf(12f, 15f, 18f, 20f)
                    val sizeLabels = listOf("صغير (١٢)", "متوسط (١٥)", "كبير (١٨)", "كبير جداً (٢٠)")
                    val sliderIndex = sizes.indexOfFirst { it >= uiFontSize }.let { if (it == -1) 1 else it }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("حجم خط الواجهة", color = AppTextPrimary, fontSize = 15.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Badge showing current value above slider standard
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                text = sizeLabels[sliderIndex],
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = sliderIndex.toFloat(),
                            onValueChange = {
                                val idx = it.roundToInt()
                                viewModel.setUiFontSize(sizes[idx])
                            },
                            valueRange = 0f..3f,
                            steps = 2,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        )
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = AppSurface)
                }

                // SECTION 2: القراءة
                item {
                    SectionHeader("القراءة")
                }

                // Setting 4: حفظ موضع القراءة تلقائياً
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdded,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("حفظ موضع القراءة تلقائياً", color = AppTextPrimary, fontSize = 15.sp)
                        }

                        Switch(
                            checked = autoSavePosition,
                            onCheckedChange = { viewModel.setAutoSavePosition(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(0.3f),
                                uncheckedThumbColor = AppTextSecondary,
                                uncheckedTrackColor = AppSurface
                            )
                        )
                    }
                }

                // Setting 5: عرض مؤشر الصفحة
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("إظهار مؤشر رقم الصفحة أثناء التمرير", color = AppTextPrimary, fontSize = 15.sp)
                        }

                        Switch(
                            checked = showPageIndicator,
                            onCheckedChange = { viewModel.setShowPageIndicator(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(0.3f),
                                uncheckedThumbColor = AppTextSecondary,
                                uncheckedTrackColor = AppSurface
                            )
                        )
                    }
                }

                // Setting 6: المسافة بين الصفحات
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("المسافة بين الصفحات", color = AppTextPrimary, fontSize = 15.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                text = "${pageSpacing.toInt()} dp",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = pageSpacing,
                            onValueChange = {
                                viewModel.setPageSpacing(it.roundToInt().toFloat())
                            },
                            valueRange = 0f..24f,
                            steps = 5, // Snaps to multiples of 4: 0, 4, 8, 12, 16, 20, 24
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        )
                    }
                }

                // Setting 7: سرعة التمرير
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("حساسية التمرير", color = AppTextPrimary, fontSize = 15.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                text = "%.1f".format(scrollSpeed),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = scrollSpeed,
                            onValueChange = {
                                // Snap to 0.5, 1.0, 1.5, 2.0
                                val rounded = (it * 2).roundToInt() / 2f
                                viewModel.setScrollSpeed(rounded)
                            },
                            valueRange = 0.5f..2.0f,
                            steps = 2, // snaps to 0.5, 1.0, 1.5, 2.0
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("بطيء", color = AppTextSecondary, fontSize = 11.sp)
                            Text("سريع", color = AppTextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = AppSurface)
                }

                // SECTION 3: الروابط والصوت
                item {
                    SectionHeader("الروابط والصوت")
                }

                // Setting 8: فتح الروابط الخارجية
                item {
                    var linkMenuExpanded by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("فتح الروابط الخارجية", color = AppTextPrimary, fontSize = 15.sp)
                        }

                        Box {
                            Button(
                                onClick = { linkMenuExpanded = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(linkOpenMode, color = AppTextPrimary, fontSize = 14.sp)
                            }
                            DropdownMenu(
                                expanded = linkMenuExpanded,
                                onDismissRequest = { linkMenuExpanded = false },
                                modifier = Modifier.background(AppSurface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("المتصفح الافتراضي", color = AppTextPrimary) },
                                    onClick = {
                                        viewModel.setLinkOpenMode("المتصفح الافتراضي")
                                        linkMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("داخل التطبيق (WebView)", color = AppTextPrimary) },
                                    onClick = {
                                        viewModel.setLinkOpenMode("داخل التطبيق (WebView)")
                                        linkMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("اسأل في كل مرة", color = AppTextPrimary) },
                                    onClick = {
                                        viewModel.setLinkOpenMode("اسأل في كل مرة")
                                        linkMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Setting 9: تشغيل الصوت تلقائياً
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("تشغيل روابط الصوت تلقائياً عند الضغط", color = AppTextPrimary, fontSize = 15.sp)
                        }

                        Switch(
                            checked = autoPlayAudio,
                            onCheckedChange = { viewModel.setAutoPlayAudio(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(0.3f),
                                uncheckedThumbColor = AppTextSecondary,
                                uncheckedTrackColor = AppSurface
                            )
                        )
                    }
                }

                // Setting 10: مستوى الصوت الافتراضي (معروض فقط في حالة تشغيل الصوت تلقائياً)
                if (autoPlayAudio) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Hearing,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("مستوى الصوت الافتراضي", color = AppTextPrimary, fontSize = 15.sp)
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .align(Alignment.CenterHorizontally)
                            ) {
                                Text(
                                    text = "${(audioVolume * 100).roundToInt()}%",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Slider(
                                value = audioVolume,
                                onValueChange = { viewModel.setAudioVolume(it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                )
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = AppSurface)
                }

                // SECTION 4: التخزين والبيانات
                item {
                    SectionHeader("التخزين والبيانات")
                }

                // Setting 11: مسح ملفات الكاش
                item {
                    var showCacheDialog by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable { showCacheDialog = true }
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("مسح ذاكرة التخزين المؤقت", color = AppTextPrimary, fontSize = 15.sp)
                                    Text(cacheSize, color = AppTextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (showCacheDialog) {
                        AlertDialog(
                            containerColor = AppSurface,
                            onDismissRequest = { showCacheDialog = false },
                            title = { Text("مسح الكاش؟", color = AppTextPrimary, fontWeight = FontWeight.Bold) },
                            text = { Text("سيتم حذف الصور المصغرة المحفوظة. سيتم إعادة إنشاؤها عند الحاجة.", color = AppTextSecondary) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        try {
                                            File(context.cacheDir, "pdf_thumbs").deleteRecursively()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        cacheSize = "0 KB"
                                        showCacheDialog = false
                                        scope.launch {
                                            snackbarHostState.showSnackbar("تم مسح الكاش ✓")
                                        }
                                    }
                                ) {
                                    Text("مسح", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCacheDialog = false }) {
                                    Text("إلغاء", color = AppTextPrimary)
                                }
                            }
                        )
                    }
                }

                // Setting 12: مسح سجل الملفات الأخيرة
                item {
                    var showRecentDialog by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable { showRecentDialog = true }
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("مسح سجل الملفات الأخيرة", color = AppTextPrimary, fontSize = 15.sp)
                                    Text("عدد الملفات المحفوظة: ${recentPdfs.size}", color = AppTextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (showRecentDialog) {
                        AlertDialog(
                            containerColor = AppSurface,
                            onDismissRequest = { showRecentDialog = false },
                            title = { Text("تأكيد مسح السجل", color = AppTextPrimary, fontWeight = FontWeight.Bold) },
                            text = { Text("هل تريد مسح جميع الملفات من قائمة الأخيرة؟", color = AppTextSecondary) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.clearAllRecentFiles()
                                        showRecentDialog = false
                                        scope.launch {
                                            snackbarHostState.showSnackbar("تم المسح ✓")
                                        }
                                    }
                                ) {
                                    Text("مسح", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRecentDialog = false }) {
                                    Text("إلغاء", color = AppTextPrimary)
                                }
                            }
                        )
                    }
                }

                // Setting 13: مسح كل الإشارات والتظليلات
                item {
                    var showBookmarksAndHighlightsDialog by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable { showBookmarksAndHighlightsDialog = true }
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("مسح جميع الإشارات والتظليلات", color = Color(0xFFFF6B6B), fontSize = 15.sp)
                            }
                        }
                    }

                    if (showBookmarksAndHighlightsDialog) {
                        AlertDialog(
                            containerColor = AppSurface,
                            onDismissRequest = { showBookmarksAndHighlightsDialog = false },
                            title = { Text("تحذير", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold) },
                            text = { Text("سيتم حذف جميع الإشارات المرجعية والنصوص المظللة بشكل نهائي.", color = AppTextSecondary) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.clearAllBookmarks()
                                        viewModel.clearAllHighlights()
                                        showBookmarksAndHighlightsDialog = false
                                        scope.launch {
                                            snackbarHostState.showSnackbar("تم الحذف النهائي ✓")
                                        }
                                    }
                                ) {
                                    Text("حذف نهائي", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showBookmarksAndHighlightsDialog = false }) {
                                    Text("إلغاء", color = AppTextPrimary)
                                }
                            }
                        )
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = AppSurface)
                }

                // SECTION: الإيماءات (Gestures Customization Theme)
                item {
                    SectionHeader("الإيماءات")
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable { onNavigateToGestureSettings() }
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("تخصيص الإيماءات", color = AppTextPrimary, fontSize = 15.sp)
                                Text("تعيين وظائف السحب والنقر المتعدد", color = AppTextSecondary, fontSize = 12.sp)
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Navigate to gestures",
                            tint = AppTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = AppSurface)
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable { onNavigateToAbout() }
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("عن التطبيق", color = AppTextPrimary, fontSize = 15.sp)
                                Text("معلومات المطور والمكتبات المستخدمة", color = AppTextSecondary, fontSize = 12.sp)
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Navigate To About",
                            tint = AppTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Color Picker Dialog
        if (showColorDialog) {
            val colors = listOf(
                "#6C63FF", "#4ECDC4", "#FF6B6B", "#FFD93D", "#6BCB77",
                "#4D96FF", "#FF922B", "#CC5DE8", "#F06595", "#74C0FC"
            )
            AlertDialog(
                containerColor = AppSurface,
                onDismissRequest = { showColorDialog = false },
                title = { Text("اختر اللون الرئيسي", color = AppTextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        ) {
                            items(colors) { colorString ->
                                val rgb = Color(android.graphics.Color.parseColor(colorString))
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(rgb, CircleShape)
                                        .border(
                                            width = if (primaryColorHex.equals(colorString, ignoreCase = true)) 2.dp else 0.dp,
                                            color = Color.White,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            viewModel.setPrimaryColorHex(colorString)
                                            showColorDialog = false
                                            scope.launch {
                                                snackbarHostState.showSnackbar("تم تغيير اللون بنجاح")
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (primaryColorHex.equals(colorString, ignoreCase = true)) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showColorDialog = false }) {
                        Text("إلغاء", color = AppTextPrimary)
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        color = AppTextSecondary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)
    )
}

private fun getCacheSize(context: Context): String {
    val dir = File(context.cacheDir, "pdf_thumbs")
    if (!dir.exists()) {
        try {
            dir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "0 KB"
    }
    val bytes = dir.walkTopDown().sumOf { it.length() }
    if (bytes <= 0) return "0 KB"
    if (bytes < 1024 * 1024) {
        return "${bytes / 1024} KB"
    }
    val mb = bytes.toDouble() / (1024 * 1024)
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}
