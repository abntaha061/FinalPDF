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
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { PdfRepository(database.recentFileDao(), database.bookmarkDao(), database.highlightDao()) }

    private fun hasRequiredPermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= 33 -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= 30 -> {
                Environment.isExternalStorageManager()
            }
            else -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        
        // Let the system handle notch insets smoothly
        enableEdgeToEdge()

        // Core modern ViewModel instantiation passing context
        val viewModel = ViewModelProvider(
            this,
            PdfViewModelFactory(repository, this.applicationContext)
        )[PdfViewModel::class.java]

        splashScreen.setKeepOnScreenCondition {
            // Keep splash visible until ViewModel finishes loading recent files
            !viewModel.isReady.value
        }

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            // Animate the logo scaling down and fading out
            val scaleX = ObjectAnimator.ofFloat(splashScreenView.iconView, View.SCALE_X, 1f, 0.8f)
            val scaleY = ObjectAnimator.ofFloat(splashScreenView.iconView, View.SCALE_Y, 1f, 0.8f)
            val alpha  = ObjectAnimator.ofFloat(splashScreenView.iconView, View.ALPHA, 1f, 0f)
            AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                duration = 400
                doOnEnd { splashScreenView.remove() }
                start()
            }
        }

        setContent {
            val isNightModeBySettings by viewModel.isNightMode.collectAsState()
            val isOnboardingCompleted by viewModel.isOnboardingDone.collectAsState()
            val primaryColorHex by viewModel.primaryColorHex.collectAsState()
            val uiFontSize by viewModel.uiFontSize.collectAsState()
            
            MyApplicationTheme(
                darkTheme = isNightModeBySettings,
                primaryColorHex = primaryColorHex
            ) {
                CompositionLocalProvider(
                    LocalTextStyle provides LocalTextStyle.current.copy(fontSize = uiFontSize.sp)
                ) {
                    val context = LocalContext.current
                    var hasPermission by remember { mutableStateOf(hasRequiredPermission(context)) }
                var showRationaleDialog by remember { mutableStateOf(!hasPermission) }
                var showDeniedDialog by remember { mutableStateOf(false) }

                val standardPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasPermission = isGranted
                    if (!isGranted) {
                        showDeniedDialog = true
                    }
                }

                val manageStorageLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { _ ->
                    val granted = hasRequiredPermission(context)
                    hasPermission = granted
                    if (!granted) {
                        showDeniedDialog = true
                    }
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            val granted = hasRequiredPermission(context)
                            hasPermission = granted
                            if (granted) {
                                showDeniedDialog = false
                                showRationaleDialog = false
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                if (showRationaleDialog) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("طلب صلاحية الوصول", fontWeight = FontWeight.Bold) },
                        text = { Text("يحتاج التطبيق إلى صلاحية الوصول إلى الملفات لقراءة كتب ومستندات الـ PDF المخزنة على جهازك وعرضها لك.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showRationaleDialog = false
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        standardPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                                    } else if (Build.VERSION.SDK_INT >= 30) {
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            manageStorageLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            try {
                                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                                manageStorageLauncher.launch(intent)
                                            } catch (ex: Exception) {
                                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(intent)
                                            }
                                        }
                                    } else {
                                        standardPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                }
                            ) {
                                Text("موافق")
                            }
                        },
                        dismissButton = null
                    )
                }

                if (showDeniedDialog) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("الصلاحية مطلوبة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                        text = { Text("تم رفض تفعيل صلاحية الوصول إلى الملفات. يرجى تفعيلها يدوياً من إعدادات النظام لمواصلة استخدام التطبيق بميزات كاملة.") },
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
                        dismissButton = null
                    )
                }

                if (isOnboardingCompleted != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()
                        val startDest = if (isOnboardingCompleted == true) "home" else "onboarding"

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
                                            if (navController.currentBackStackEntry?.destination?.route != "viewer") {
                                                navController.navigate("viewer")
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
                                            if (navController.currentBackStackEntry?.destination?.route == "viewer") {
                                                navController.popBackStack("home", inclusive = false)
                                            }
                                        }
                                    ) {
                                        Text("إلغاء")
                                    }
                                },
                                modifier = Modifier.testTag("file_too_large_dialog")
                            )
                        }

                        NavHost(
                            navController = navController,
                            startDestination = startDest
                        ) {
                            composable("onboarding") {
                                OnboardingScreen(
                                    onFinished = {
                                        viewModel.completeOnboarding()
                                        navController.navigate("home") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(
                                route = "home",
                                enterTransition = {
                                    fadeIn(animationSpec = tween(450)) + slideInHorizontally(animationSpec = tween(450))
                                }
                            ) {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onPdfOpened = {
                                        navController.navigate("viewer")
                                    },
                                    onNavigateToSettings = {
                                        navController.navigate("settings")
                                    }
                                )
                            }
                            
                            composable("viewer") {
                                ViewerScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable(
                                route = "settings",
                                enterTransition = {
                                    slideInHorizontally(
                                        initialOffsetX = { it },
                                        animationSpec = tween(300)
                                    ) + fadeIn(animationSpec = tween(300))
                                },
                                exitTransition = {
                                    slideOutHorizontally(
                                        targetOffsetX = { it },
                                        animationSpec = tween(300)
                                    ) + fadeOut(animationSpec = tween(300))
                                }
                            ) {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        navController.popBackStack()
                                    }
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
