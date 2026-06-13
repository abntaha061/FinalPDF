package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.util.PdfPrefetchManager
import com.github.barteksc.pdfviewer.util.Constants

class PdfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Set tile cache size to a robust limit (150 tiles) to prevent high-res tile eviction and fix blurry pages.
        Constants.Cache.CACHE_SIZE = 150
        Constants.PART_SIZE = 256f // Default tile size for crisp and standard rendering
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
