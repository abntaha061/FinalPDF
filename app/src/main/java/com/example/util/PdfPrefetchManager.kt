package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.*
import javax.inject.Inject

@ActivityScoped
class PdfPrefetchManager @Inject constructor() {
    private val prefetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var bitmapCache = LruCache<Int, Bitmap>(10) // cache 10 pages max

    init {
        currentInstance = this
    }

    fun prefetchAround(currentPage: Int, totalPages: Int, fileUri: Uri, context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val availableMB = memInfo.availMem / 1024 / 1024

        val cacheSize = when {
            availableMB > 512 -> 10  // plenty of RAM: cache 10 pages
            availableMB > 256 -> 6   // medium RAM: cache 6 pages
            availableMB > 128 -> 4   // low RAM: cache 4 pages
            else              -> 2   // very low: cache 2 only
        }

        if (bitmapCache.maxSize() != cacheSize) {
            val oldCache = bitmapCache
            bitmapCache = object : LruCache<Int, Bitmap>(cacheSize) {
                override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
                    // garbage collection handles RGB_565 cleanly
                }
            }
            val oldMap = oldCache.snapshot()
            var count = 0
            for ((key, value) in oldMap) {
                if (count < cacheSize) {
                    bitmapCache.put(key, value)
                    count++
                } else {
                    break
                }
            }
            oldCache.evictAll()
        }

        val pagesToFetch = listOf(
            currentPage + 1,
            currentPage + 2,
            currentPage + 3,
            currentPage - 1   // one page back too
        ).filter { it in 0 until totalPages }
          .filter { bitmapCache.get(it) == null } // skip already cached

        pagesToFetch.forEach { pageIndex ->
            prefetchScope.launch {
                try {
                    val pfd = context.contentResolver.openFileDescriptor(fileUri, "r") ?: return@launch
                    val renderer = PdfRenderer(pfd)
                    val page = renderer.openPage(pageIndex)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.RGB_565)
                    // RGB_565 uses HALF the memory of ARGB_8888 — critical for large files
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    renderer.close()
                    bitmapCache.put(pageIndex, bitmap)
                } catch (e: Exception) {
                    // Silently ignore prefetch errors — non-critical
                }
            }
        }
    }

    fun getPage(pageIndex: Int): Bitmap? = bitmapCache.get(pageIndex)

    fun cancelAll() {
        prefetchScope.coroutineContext.cancelChildren()
        bitmapCache.evictAll()
    }

    fun setCacheSize(size: Int) {
        val oldCache = bitmapCache
        bitmapCache = LruCache(size)
        oldCache.evictAll()
    }

    companion object {
        @Volatile
        var currentInstance: PdfPrefetchManager? = null
            private set
    }
}
