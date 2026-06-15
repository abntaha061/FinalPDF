package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.compose.material3.TextButton
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.view.View
import androidx.core.animation.doOnEnd
import com.example.data.AppDatabase
import com.example.data.PdfRepository
import com.example.ui.PdfViewModel
import com.example.ui.PdfViewModelFactory
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ViewerScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.LanguageScreen
import com.example.ui.screens.WebViewScreen
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.util.Locale

import com.example.util.pdfReaderDataStore

private val APP_LANGUAGE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("app_language")

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: PdfViewModel
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { PdfRepository(database.recentFileDao(), database.bookmarkDao(), database.highlightDao(), database.readingSessionDao(), database.ocrResultDao(), database.audioBookmarkDao()) }

    private fun hasRequiredPermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= 33 -> {
                // On Android 13+, standard storage permission is deprecated. Since we use Storage Access Framework
                // dynamically, we can return true directly to avoid requesting unnecessary media permissions here.
                true
            }
            else -> {
                // For API <= 32 (including Android 11 & 12 / API 30-32), standard READ_EXTERNAL_STORAGE is sufficient
                // and avoids launching the highly restrictive MANAGE_EXTERNAL_STORAGE settings page.
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        var savedLang = "ar"
        try {
            savedLang = runBlocking { pdfReaderDataStore.data.map { it[APP_LANGUAGE_KEY] ?: "ar" }.first() }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error reading language during cold start", e)
        }

        try {
            val locale = Locale(savedLang)
            Locale.setDefault(locale)
            val config = resources.configuration
            config.setLocale(locale)
            resources.updateConfiguration(config, resources.displayMetrics)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting locale", e)
        }

        val splashScreen = installSplashScreen()

        try {
            super.onCreate(savedInstanceState)
            
            // Let the system handle notch insets smoothly
            enableEdgeToEdge()

            // Core modern ViewModel instantiation passing context
            viewModel = ViewModelProvider(
                this,
                PdfViewModelFactory(repository, this.applicationContext)
            )[PdfViewModel::class.java]

            splashScreen.setKeepOnScreenCondition {
                // Keep splash visible until ViewModel finishes loading recent files
                !viewModel.isReady.value
            }

            splashScreen.setOnExitAnimationListener { splashScreenView ->
                val iconView = splashScreenView.iconView
                if (iconView != null) {
                    // Animate the logo scaling down and fading out
                    val scaleX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 0.8f)
                    val scaleY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 0.8f)
                    val alpha  = ObjectAnimator.ofFloat(iconView, View.ALPHA, 1f, 0f)
                    AnimatorSet().apply {
                        playTogether(scaleX, scaleY, alpha)
                        duration = 400
                        doOnEnd { splashScreenView.remove() }
                        start()
                    }
                } else {
                    splashScreenView.remove()
                }
            }

            setContent {
                val isNightModeBySettings by viewModel.isNightMode.collectAsState()
                val isOnboardingCompleted by viewModel.isOnboardingDone.collectAsState()
                val primaryColorHex by viewModel.primaryColorHex.collectAsState()
                val uiFontSize by viewModel.uiFontSize.collectAsState()
                val dynamicColorState by viewModel.dynamicColor.collectAsState()
                
                MyApplicationTheme(
                    darkTheme = isNightModeBySettings,
                    dynamicColor = dynamicColorState,
                    primaryColorHex = primaryColorHex
                ) {
                    CompositionLocalProvider(
                        LocalTextStyle provides LocalTextStyle.current.copy(fontSize = uiFontSize.sp)
                    ) {
                        val context = LocalContext.current
                        var hasPermission by remember { mutableStateOf(true) }
                        var showRationaleDialog by remember { mutableStateOf(false) }
                        var showDeniedDialog by remember { mutableStateOf(false) }

                        val standardPermissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { isGranted ->
                            // Bypassed: always proceed as true via SAF persistable permissions
                            hasPermission = true
                        }

                        val manageStorageLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult()
                        ) { _ ->
                            hasPermission = true
                        }

                        val lifecycleOwner = LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    hasPermission = true
                                    showDeniedDialog = false
                                    showRationaleDialog = false
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                if (showRationaleDialog) {
                    AlertDialog(
                        onDismissRequest = { showRationaleDialog = false },
                        title = { Text("طلب صلاحية الوصول", fontWeight = FontWeight.Bold) },
                        text = { Text("يحتاج التطبيق إلى صلاحية الوصول إلى الملفات لقراءة كتب ومستندات الـ PDF المخزنة على جهازك وعرضها لك.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showRationaleDialog = false
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        standardPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                                    } else {
                                        standardPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                }
                            ) {
                                Text("موافق")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showRationaleDialog = false }
                            ) {
                                Text("إلغاء")
                            }
                        }
                    )
                }

                if (showDeniedDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeniedDialog = false },
                        title = { Text("الصلاحية مطلوبة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                        text = { Text("تم رفض تفعيل صلاحية الوصول إلى الملفات. يمكنك تفعيلها يدوياً من إعدادات النظام، أو مواصلة استخدام التطبيق بميزات جزئية.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            ) {
                                Text("فتح الإعدادات")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showDeniedDialog = false }
                            ) {
                                Text("إلغاء")
                            }
                        }
                    )
                }

                if (isOnboardingCompleted != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()

                        val securityExceptionUri by viewModel.securityExceptionUri.collectAsState()
                        val largeFileUriPending by viewModel.largeFileUriPending.collectAsState()

                        if (securityExceptionUri != null) {
                            AlertDialog(
                                onDismissRequest = { viewModel.clearSecurityException() },
                                title = { Text("لا يوجد إذن للوصول", fontWeight = FontWeight.Bold) },
                                text = { Text("لا يملك التطبيق صلاحية قراءة هذا الملف. اذهب إلى الإعدادات وامنح الإذن يدوياً.") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.clearSecurityException()
                                            try {
                                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.fromParts("package", packageName, null)
                                                }
                                                startActivity(intent)
                                            } catch (e: Exception) {
                                                // ignore
                                            }
                                        }
                                    ) {
                                        Text("فتح الإعدادات")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { viewModel.clearSecurityException() }
                                    ) {
                                        Text("إلغاء")
                                    }
                                },
                                modifier = Modifier.testTag("permission_denied_dialog")
                            )
                        }

                        if (largeFileUriPending != null) {
                            val pendingUri = largeFileUriPending!!.first
                            val sizeMB = largeFileUriPending!!.second
                            AlertDialog(
                                onDismissRequest = { viewModel.clearLargeFilePending() },
                                title = { Text("الملف كبير جداً", fontWeight = FontWeight.Bold) },
                                text = { Text("حجم الملف $sizeMB MB. قد يستغرق التحميل وقتاً أطول أو يسبب بطءاً. هل تريد المتابعة؟") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.selectDocumentForced(context, Uri.parse(pendingUri))
                                            val currentRoute = navController.currentBackStackEntry?.destination?.route ?: ""
                                            if (!currentRoute.startsWith("pdf_reader")) {
                                                val encodedUri = Uri.encode(pendingUri)
                                                navController.navigate(com.example.ui.navigation.Screen.PdfReader.createRoute(encodedUri))
                                            }
                                        }
                                    ) {
                                        Text("متابعة")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            viewModel.clearLargeFilePending()
                                            val currentRoute = navController.currentBackStackEntry?.destination?.route ?: ""
                                            if (currentRoute.startsWith("pdf_reader")) {
                                                navController.popBackStack(com.example.ui.navigation.Screen.Home.route, inclusive = false)
                                            }
                                        }
                                    ) {
                                        Text("إلغاء")
                                    }
                                },
                                modifier = Modifier.testTag("file_too_large_dialog")
                            )
                        }

                        val isDragging by viewModel.isDragging.collectAsState()
                        val pendingDragDropUri by viewModel.pendingDragDropUri.collectAsState()

                        LaunchedEffect(pendingDragDropUri) {
                            pendingDragDropUri?.let { uriStr ->
                                viewModel.setPendingDragDropUri(null)
                                val currentRoute = navController.currentBackStackEntry?.destination?.route ?: ""
                                if (!currentRoute.startsWith("pdf_reader")) {
                                    val encodedUri = Uri.encode(uriStr)
                                    navController.navigate(com.example.ui.navigation.Screen.PdfReader.createRoute(encodedUri))
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            com.example.ui.navigation.AppNavGraph(navController = navController)

                            if (isDragging) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(40.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                                            .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.PictureAsPdf,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "أفلت ملف PDF هنا",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 20.sp,
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
        }
    }
} catch (e: Exception) {
    android.util.Log.e("MainActivity", "Error in onCreate body execution", e)
}

    try {
        setupDragAndDropListener()
        handleIntent(intent)
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error in onCreate post-setContent execution", e)
    }
}

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND) {
            val uri = if (action == Intent.ACTION_SEND) {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
            } else {
                intent.data
            }

            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    // ignore
                }
                viewModel.selectDocument(this, uri)
                viewModel.setPendingDragDropUri(uri.toString())
            }
        }
    }

    private fun setupDragAndDropListener() {
        val rootView = window.decorView.rootView
        rootView.setOnDragListener { _, event ->
            when (event.action) {
                android.view.DragEvent.ACTION_DRAG_STARTED -> {
                    true
                }
                android.view.DragEvent.ACTION_DRAG_ENTERED -> {
                    viewModel.setIsDragging(true)
                    true
                }
                android.view.DragEvent.ACTION_DRAG_EXITED -> {
                    viewModel.setIsDragging(false)
                    true
                }
                android.view.DragEvent.ACTION_DRAG_ENDED -> {
                    viewModel.setIsDragging(false)
                    true
                }
                android.view.DragEvent.ACTION_DROP -> {
                    viewModel.setIsDragging(false)
                    val clipData = event.clipData
                    if (clipData != null && clipData.itemCount > 0) {
                        val uri = clipData.getItemAt(0).uri
                        if (uri != null) {
                            try {
                                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                contentResolver.takePersistableUriPermission(uri, takeFlags)
                            } catch (e: Exception) {
                                // ignore
                            }
                            viewModel.selectDocument(this, uri)
                            viewModel.setPendingDragDropUri(uri.toString())
                            true
                        } else false
                    } else false
                }
                else -> true
            }
        }
    }
}
