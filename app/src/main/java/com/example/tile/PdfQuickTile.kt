package com.example.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.R
import com.example.util.pdfReaderDataStore
import com.example.util.LAST_FILE_NAME_KEY
import com.example.util.LAST_FILE_URI_KEY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class PdfQuickTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        // Update tile UI
        val currTile = qsTile ?: return
        currTile.apply {
            state = Tile.STATE_ACTIVE
            icon = Icon.createWithResource(this@PdfQuickTile, R.drawable.ic_pdf_tile)
            
            // Read last file name from DataStore
            val lastName = runBlocking {
                try {
                    pdfReaderDataStore.data.map { it[LAST_FILE_NAME_KEY] ?: "مستند PDF" }.first()
                } catch (e: Exception) {
                    "مستند PDF"
                }
            }
            label = "قارئ PDF"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                subtitle = lastName.take(20)
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // Open app and last PDF
        val lastUri = runBlocking {
            try {
                pdfReaderDataStore.data.map { it[LAST_FILE_URI_KEY] ?: "" }.first()
            } catch (e: Exception) {
                ""
            }
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (lastUri.isNotEmpty()) putExtra("open_uri", lastUri)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
