package com.example.ui

import android.content.Context
import com.example.data.PdfRepository
import com.example.data.ReadingSessionEntity
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@ActivityScoped
class ReadingTimerManager @Inject constructor(
    private val repository: PdfRepository
) {
    var startTime: Long = 0
    var totalSeconds: Long = 0  
    var isRunning: Boolean = false
    var currentFileUri: String = ""

    fun startTimer(fileUri: String) {
        currentFileUri = fileUri
        startTime = System.currentTimeMillis()
        isRunning = true
    }

    fun pauseTimer() {
        if (isRunning) {
            totalSeconds += (System.currentTimeMillis() - startTime) / 1000
            isRunning = false
        }
    }

    fun resumeTimer() {
        if (!isRunning) {
            startTime = System.currentTimeMillis()
            isRunning = true
        }
    }

    fun stopAndSave(context: Context, pagesRead: Int) {
        pauseTimer()
        val finalSeconds = totalSeconds
        val finalUri = currentFileUri
        if (finalSeconds > 0 && finalUri.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val session = ReadingSessionEntity(
                    fileUri = finalUri,
                    durationSeconds = finalSeconds,
                    pagesRead = pagesRead,
                    date = System.currentTimeMillis()
                )
                repository.insertReadingSession(session)
            }
        }
        totalSeconds = 0
    }
}
