package com.example.ui

import androidx.lifecycle.ViewModel
import com.example.util.AudioPlayerManager
import kotlinx.coroutines.flow.StateFlow

sealed class AudioState {
    object Idle : AudioState()
    object Loading : AudioState()
    data class Playing(val url: String) : AudioState()
    data class Error(val msg: String) : AudioState()
}

class AudioViewModel : ViewModel() {
    
    val audioState: StateFlow<AudioState> = AudioPlayerManager.audioState

    fun stopAudio() {
        AudioPlayerManager.stop()
    }
}
