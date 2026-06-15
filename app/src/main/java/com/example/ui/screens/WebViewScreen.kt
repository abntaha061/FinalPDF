package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.AppSurface
import com.example.ui.theme.AppTextPrimary
import com.example.ui.theme.AppTextSecondary
import kotlinx.coroutines.launch

class WebStateHolder(initialUrl: String) {
    private val _isLoading = mutableStateOf(true)
    val isLoading: Boolean get() = _isLoading.value

    private val _url = mutableStateOf(initialUrl)
    val url: String get() = _url.value

    private val _title = mutableStateOf("جاري التحميل...")
    val title: String get() = _title.value

    private val _error = mutableStateOf<String?>(null)
    val error: String? get() = _error.value

    private val _progress = mutableStateOf(0)
    val progress: Int get() = _progress.value

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
    fun setUrl(newUrl: String) {
        _url.value = newUrl
    }
    fun setTitle(newTitle: String) {
        _title.value = newTitle
    }
    fun setError(err: String?) {
        _error.value = err
    }
    fun setProgress(prog: Int) {
        _progress.value = prog
    }
}

@Composable
fun WebViewScreen(
    navController: androidx.navigation.NavController,
    url: String
) {
    WebViewScreen(
        initialUrl = url,
        onBack = {
            navController.popBackStack()
        }
    )
}

@Composable
fun WebViewScreen(
    initialUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val viewModel = remember { WebStateHolder(initialUrl) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    if (android.os.Build.VERSION.SDK_INT >= 34) {
        val activity = context as? androidx.activity.ComponentActivity
        if (activity != null) {
            val dispatcher = activity.onBackInvokedDispatcher
            val callback = remember(webViewInstance) {
                android.window.OnBackInvokedCallback {
                    val webView = webViewInstance
                    if (webView != null && webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        onBack()
                    }
                }
            }
            DisposableEffect(dispatcher, callback) {
                dispatcher.registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    callback
                )
                onDispose {
                    dispatcher.unregisterOnBackInvokedCallback(callback)
                }
            }
        }
    }

    // Handle system back navigation (override default back)
    BackHandler(enabled = true) {
        val webView = webViewInstance
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            onBack()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = Color(0xFF0D0D0F),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFF0D0D0F))
            ) {
                // --- TOP BAR ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color(0xFF0D0D0F))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side: Close Icon
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Center: Title & URL Information
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = viewModel.title,
                            color = AppTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = viewModel.url,
                            color = AppTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Right Side: Action Icons
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("لا يمكن فتح الرابط")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open In Browser",
                                tint = AppTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                webViewInstance?.reload()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = AppTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // --- PROGRESS BAR ---
                AnimatedVisibility(
                    visible = viewModel.isLoading && viewModel.progress < 100,
                    exit = fadeOut()
                ) {
                    LinearProgressIndicator(
                        progress = viewModel.progress / 100f,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF6C63FF),
                        trackColor = AppSurface
                    )
                }

                // --- CONTENT BOX (WEBVIEW + ERROR OVERLAY) ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    mediaPlaybackRequiresUserGesture = false
                                    allowFileAccess = true
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                        viewModel.setLoading(true)
                                        viewModel.setUrl(url)
                                        viewModel.setError(null)
                                    }

                                    override fun onPageFinished(view: WebView, url: String) {
                                        viewModel.setLoading(false)
                                        viewModel.setTitle(view.title ?: url)
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        error: WebResourceError
                                    ) {
                                        if (request.isForMainFrame) {
                                            viewModel.setError("فشل تحميل الصفحة: ${error.description}")
                                        }
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                                        viewModel.setProgress(newProgress)
                                    }
                                }
                                webViewInstance = this
                                loadUrl(initialUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Error overlay
                    if (viewModel.error != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0D0D0F))
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = AppTextSecondary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "لا يمكن تحميل الصفحة",
                                color = AppTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = viewModel.error ?: "",
                                color = AppTextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    viewModel.setError(null)
                                    webViewInstance?.reload()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6C63FF)
                                )
                            ) {
                                Text("إعادة المحاولة", color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("لا يمكن فتح الرابط")
                                        }
                                    }
                                },
                                border = BorderStroke(1.dp, Color(0xFF6C63FF))
                            ) {
                                Text("فتح في المتصفح", color = Color(0xFF6C63FF))
                            }
                        }
                    }
                }

                // --- BOTTOM NAVIGATION BAR ---
                Surface(
                    color = AppSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Web Back
                        IconButton(
                            onClick = {
                                val webView = webViewInstance
                                if (webView != null && webView.canGoBack()) {
                                    webView.goBack()
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("لا يوجد صفحة سابقة")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go Back",
                                tint = Color.White
                            )
                        }

                        // 2. Web Forward
                        IconButton(
                            onClick = {
                                val webView = webViewInstance
                                if (webView != null && webView.canGoForward()) {
                                    webView.goForward()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Go Forward",
                                tint = Color.White
                            )
                        }

                        // 3. Home url
                        IconButton(
                            onClick = {
                                webViewInstance?.loadUrl(initialUrl)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Go Home",
                                tint = Color.White
                            )
                        }

                        // 4. ContentCopy
                        IconButton(
                            onClick = {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("URL", viewModel.url)
                                    clipboard.setPrimaryClip(clip)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("تم نسخ الرابط ✓")
                                    }
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy URL",
                                tint = Color.White
                            )
                        }

                        // 5. Share
                        IconButton(
                            onClick = {
                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, viewModel.url)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة الرابط"))
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share URL",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
