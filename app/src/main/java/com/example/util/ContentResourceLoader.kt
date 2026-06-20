package com.example.util

import android.content.Context
import android.net.Uri
import java.io.InputStream

object ContentResourceLoader {
    fun openContentUri(context: Context, uriString: String): InputStream? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            android.util.Log.e("ContentResourceLoader", "Error opening content URI $uriString", e)
            null
        }
    }
}
