package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.util.PdfPrefetchManager

class PdfApplication : Application() {
    companion object {
        lateinit var instance: PdfApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
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
