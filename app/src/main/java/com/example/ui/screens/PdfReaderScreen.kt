package com.example.ui.screens

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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

    LaunchedEffect(uri) {
        val parsedUri = Uri.parse(uri)
        viewModel.selectDocument(context, parsedUri)
    }

    ViewerScreen(
        viewModel = viewModel,
        onBack = {
            navController.popBackStack()
        },
        onNavigateToWebView = { url ->
            val encoded = Uri.encode(url)
            navController.navigate(Screen.WebView.createRoute(encoded))
        }
    )
}
