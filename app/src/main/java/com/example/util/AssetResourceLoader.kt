package com.example.util

import android.content.Context
import java.io.InputStream

object AssetResourceLoader {
    fun openAsset(context: Context, fileName: String): InputStream? {
        return try {
            context.assets.open(fileName)
        } catch (e: Exception) {
            android.util.Log.e("AssetResourceLoader", "Error opening asset $fileName", e)
            null
        }
    }
}
