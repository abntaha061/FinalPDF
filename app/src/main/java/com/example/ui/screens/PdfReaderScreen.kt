package com.example.ui.screens

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.example.ui.PdfViewModel
import com.example.ui.navigation.Screen

@Composable
fun PdfReaderScreen(
    navController: NavController,
    uri: String
) {
    val context = LocalContext.current
    val viewModel = remember(context) {
        val activity = context as? androidx.activity.ComponentActivity
            ?: throw IllegalStateException("Context must be ComponentActivity")
        androidx.lifecycle.ViewModelProvider(activity)[PdfViewModel::class.java]
    }

    if (android.os.Build.VERSION.SDK_INT >= 34) {
        val activity = context as? androidx.activity.ComponentActivity
        if (activity != null) {
            val dispatcher = activity.onBackInvokedDispatcher
            val callback = remember {
                android.window.OnBackInvokedCallback {
                    navController.popBackStack()
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

    LaunchedEffect(uri) {
        val parsedUri = Uri.parse(uri)
        viewModel.selectDocument(context, parsedUri)
        viewModel.timerManager.startTimer(uri)
    }

    DisposableEffect(Unit) {
        onDispose {
            // Log pagesRead at disposal
            viewModel.timerManager.stopAndSave(context, viewModel.currentPage.value + 1)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.timerManager.pauseTimer()
                Lifecycle.Event.ON_RESUME -> viewModel.timerManager.resumeTimer()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ViewerScreen(
        viewModel = viewModel,
        onBack = {
            navController.popBackStack()
        },
        onNavigateToWebView = { url ->
            val encoded = Uri.encode(url)
            navController.navigate(Screen.WebView.createRoute(encoded))
        },
        onNavigateToReader = { newUri ->
            val encoded = Uri.encode(newUri)
            navController.navigate(Screen.PdfReader.createRoute(encoded))
        },
        onNavigateToSignature = {
            navController.navigate(Screen.Signature.route)
        }
    )
}
