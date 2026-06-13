package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.util.PdfPrefetchManager
import com.github.barteksc.pdfviewer.util.Constants

class PdfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Set PDFView page cache size to 5 pages to avoid OutOfMemory on large files
        Constants.Cache.CACHE_SIZE = 5
        Constants.PART_SIZE = 512f // Render high-resolution tiles for crisp text quality
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // Emergency: clear ALL caches immediately
                PdfPrefetchManager.currentInstance?.cancelAll()
                try {
                    coil.Coil.imageLoader(this).memoryCache?.clear()
                } catch (e: Exception) {
                    // Silently ignore if coil cache is not accessible
                }
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                // Warning: reduce cache to minimum
                PdfPrefetchManager.currentInstance?.setCacheSize(2)
            }
        }
    }
}
