package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.ui.AudioState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AudioPlayerManager {
    private const val TAG = "AudioPlayerManager"
    private var mediaPlayer: MediaPlayer? = null
    
    private val _audioState = MutableStateFlow<AudioState>(AudioState.Idle)
    val audioState: StateFlow<AudioState> = _audioState

    fun play(context: Context, url: String) {
        Log.d(TAG, "Playing URL: $url")
        _audioState.value = AudioState.Loading
        
        try {
            // Stop and release previous instances
            stop()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                
                try {
                    setDataSource(context, Uri.parse(url))
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting data source", e)
                    _audioState.value = AudioState.Error(e.message ?: "Failed to load audio source")
                    return
                }
                
                setOnPreparedListener { mp ->
                    Log.d(TAG, "Audio prepared, starting playback")
                    mp.start()
                    _audioState.value = AudioState.Playing(url)
                }
                
                setOnCompletionListener {
                    Log.d(TAG, "Playback completed")
                    stop()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Error during playback: what=$what, extra=$extra")
                    _audioState.value = AudioState.Error("Error during playback: what=$what, extra=$extra")
                    stop()
                    true
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaPlayer", e)
            _audioState.value = AudioState.Error(e.message ?: "Error initializing MediaPlayer")
        }
    }

    fun stop() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
            _audioState.value = AudioState.Idle
        }
    }
}
