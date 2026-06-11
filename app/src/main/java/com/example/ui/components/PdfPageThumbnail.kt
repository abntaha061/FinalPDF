package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun PdfPageThumbnail(
    pdfUriString: String,
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmapState by remember(pdfUriString, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var triedRendering by remember(pdfUriString, pageIndex) { mutableStateOf(false) }

    LaunchedEffect(pdfUriString, pageIndex) {
        if (!triedRendering) {
            triedRendering = true
            withContext(Dispatchers.IO) {
                try {
                    val cacheFile = File(context.cacheDir, "pdf_thumbnail_${pdfUriString.hashCode()}_$pageIndex.png")
                    if (cacheFile.exists()) {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
                        if (bitmap != null) {
                            bitmapState = bitmap
                        }
                    } else {
                        val uri = Uri.parse(pdfUriString)
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            PdfRenderer(pfd).use { renderer ->
                                if (pageIndex < renderer.pageCount) {
                                    renderer.openPage(pageIndex).use { page ->
                                        // Balanced thumbnail dimensions
                                        val width = 120
                                        val height = 160
                                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                        bitmap.eraseColor(android.graphics.Color.WHITE)
                                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        
                                        // Compress and cache file
                                        FileOutputStream(cacheFile).use { fos ->
                                            bitmap.compress(Bitmap.CompressFormat.PNG, 85, fos)
                                        }
                                        bitmapState = bitmap
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PdfPageThumbnail", "Error rendering thumb page $pageIndex", e)
                }
            }
        }
    }

    val bitmap = bitmapState
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "معاينة الصفحة ${pageIndex + 1}",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(AppBottomBarBg.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = AppPrimary.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
