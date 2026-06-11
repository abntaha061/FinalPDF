package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

object AudioPlayerManager {
    private const val TAG = "AudioPlayerManager"
    private var mediaPlayer: MediaPlayer? = null
    private var currentUrl: String? = null

    fun play(context: Context, url: String) {
        Log.d(TAG, "Playing URL: $url")
        
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
                
                // If the URL is in the app's assets, handle appropriately OR treat as remote/local URI
                setDataSource(context, Uri.parse(url))
                
                setOnPreparedListener { mp ->
                    Log.d(TAG, "Audio prepared, starting playback")
                    mp.start()
                }
                
                setOnCompletionListener {
                    Log.d(TAG, "Playback completed")
                    stop()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Error during playback: what=$what, extra=$extra")
                    stop()
                    true
                }

                prepareAsync()
            }
            currentUrl = url
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaPlayer", e)
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
            currentUrl = null
        }
    }
}
