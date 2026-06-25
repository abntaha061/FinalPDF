package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppPrimaryVariant
import com.example.ui.theme.AppSurface
import com.example.ui.theme.AppTextPrimary
import com.example.ui.theme.AppTextSecondary

@Composable
fun AboutScreen(navController: androidx.navigation.NavController) {
    AboutScreen(
        onBack = {
            navController.popBackStack()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    // Force RTL for consistency
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = Color(0xFF0D0D0F),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    title = {},
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
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFF0D0D0F))
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- SECTION 1: App Identity ---
                Spacer(modifier = Modifier.height(16.dp))
                
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_round),
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "قارئ PDF الشامل",
                    color = AppTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "الإصدار $versionName",
                    color = AppTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Pil badges row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge 1: مجاني 100%
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = AppSurface,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = "مجاني 100%",
                            color = AppPrimaryVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    // Badge 2: بدون إعلانات
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = AppSurface,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = "بدون إعلانات",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    // Badge 3: مفتوح المصدر
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = AppSurface,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = "مفتوح المصدر",
                            color = Color(0xFF6BCB77),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // --- SECTION 2: Features Summary ---
                Spacer(modifier = Modifier.height(32.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1A1A1F),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val features = listOf(
                            Icons.Default.PictureAsPdf to "يدعم ملفات PDF حتى 200 صفحة",
                            Icons.Default.Link to "فتح الروابط الصوتية لتعلم اللغات",
                            Icons.Default.Search to "البحث داخل الملف",
                            Icons.Outlined.DarkMode to "وضع القراءة الليلي والبني",
                            Icons.Default.Bookmark to "إشارات مرجعية وتظليل النصوص",
                            Icons.Default.Speed to "تحميل سريع مع إدارة ذكية للذاكرة"
                        )

                        features.forEach { (icon, text) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = text,
                                    color = AppTextPrimary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // --- SECTION 3: Developer Info ---
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "تم التطوير بـ ❤️ بواسطة",
                    color = AppTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "محمد",
                    color = AppTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "مبني بـ Kotlin + Jetpack Compose",
                    color = AppTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                // --- SECTION 4: Libraries Used ---
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "المكتبات المستخدمة",
                    color = AppTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val libraries = listOf(
                        Triple("PDF.js (Mozilla)", "https://github.com/mozilla/pdf.js", Icons.Default.Attachment),
                        Triple("Android PdfRenderer", "https://developer.android.com/reference/android/graphics/pdf/PdfRenderer", Icons.Default.Code),
                        Triple("Jetpack Compose", "https://developer.android.com/compose", Icons.Default.Book),
                        Triple("Room Database", "https://developer.android.com/room", Icons.Default.Storage),
                        Triple("Hilt", "https://dagger.dev/hilt", Icons.Default.Extension)
                    )

                    libraries.forEach { (name, url, icon) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AppSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = name,
                                        color = AppTextPrimary,
                                        fontSize = 14.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "Open Link",
                                    tint = AppTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
