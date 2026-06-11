package com.example

import android.app.Application
import com.github.barteksc.pdfviewer.util.Constants

class PdfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Set PDFView page cache size to 5 pages to avoid OutOfMemory on large files
        Constants.Cache.CACHE_SIZE = 5
    }
}
