package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.graphics.RectF
import com.example.ui.AudioState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AudioPlayerManager {
    private const val TAG = "AudioPlayerManager"
    private var mediaPlayer: MediaPlayer? = null

    // ====================================================================================
    // FIX (الشريط البنفسجي العالق / لا يختفي):
    // A background watchdog coroutine. Any time we enter "Loading" or "Playing" state we
    // (re)start a timeout. If playback doesn't finish/transition naturally within these
    // limits (e.g. broken network URL, MediaPlayer never calls onError/onCompletion for
    // certain stream types), we force-call stop() so the purple bar is guaranteed to
    // disappear instead of staying on screen forever.
    // ====================================================================================
    private val watchdogScope = CoroutineScope(Dispatchers.Main)
    private var watchdogJob: Job? = null

    private const val LOADING_TIMEOUT_MS = 8_000L   // max time allowed stuck on "Loading"
    private const val PLAYING_TIMEOUT_MS = 30_000L  // max time allowed stuck on "Playing" (safety net)

    private fun startWatchdog(timeoutMs: Long) {
        watchdogJob?.cancel()
        watchdogJob = watchdogScope.launch {
            delay(timeoutMs)
            Log.w(TAG, "Watchdog timeout reached - forcing stop() to hide the audio bar")
            stop()
        }
    }

    private fun cancelWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    private val _audioState = MutableStateFlow<AudioState>(AudioState.Idle)
    val audioState: StateFlow<AudioState> = _audioState

    private val _isSpeakingOrPlaying = MutableStateFlow(false)
    val isSpeakingOrPlaying: StateFlow<Boolean> = _isSpeakingOrPlaying

    private val _currentWord = MutableStateFlow<String?>(null)
    val currentWord: StateFlow<String?> = _currentWord

    private val _highlightedRect = MutableStateFlow<RectF?>(null)
    val highlightedRect: StateFlow<RectF?> = _highlightedRect

    fun setSpeechState(word: String?, rect: RectF?, active: Boolean) {
        _isSpeakingOrPlaying.value = active
        _currentWord.value = if (active) word else null
        _highlightedRect.value = if (active) rect else null
        if (active) {
            _audioState.value = AudioState.Playing("")
            // Even text-to-speech (no MediaPlayer involved) gets a watchdog: if onDone/onError
            // from the TTS engine never fires for any reason, force-hide after PLAYING_TIMEOUT_MS.
            startWatchdog(PLAYING_TIMEOUT_MS)
        } else {
            _audioState.value = AudioState.Idle
            cancelWatchdog()
        }
    }

    fun play(context: Context, url: String, volume: Float = 1.0f, wordText: String? = null, rect: RectF? = null) {
        Log.d(TAG, "Playing URL: $url")
        _audioState.value = AudioState.Loading
        _isSpeakingOrPlaying.value = true
        _currentWord.value = wordText
        _highlightedRect.value = rect

        // Start watchdog for the "Loading" phase: if MediaPlayer never reaches
        // onPrepared/onError within LOADING_TIMEOUT_MS, force stop().
        startWatchdog(LOADING_TIMEOUT_MS)

        try {
            // Stop and release previous instances (does NOT cancel our new watchdog,
            // because stop() cancels the watchdog and we restart it again right after).
            internalStopPlayerOnly()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                setVolume(volume, volume)

                try {
                    setDataSource(context, Uri.parse(url))
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting data source", e)
                    _audioState.value = AudioState.Error(e.message ?: "Failed to load audio source")
                    _isSpeakingOrPlaying.value = false
                    _currentWord.value = null
                    _highlightedRect.value = null
                    cancelWatchdog()
                    return
                }

                setOnPreparedListener { mp ->
                    Log.d(TAG, "Audio prepared, starting playback")
                    mp.start()
                    _audioState.value = AudioState.Playing(url)
                    // Switch the watchdog to the "Playing" phase timeout now that
                    // we know loading succeeded.
                    startWatchdog(PLAYING_TIMEOUT_MS)
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
            _isSpeakingOrPlaying.value = false
            _currentWord.value = null
            _highlightedRect.value = null
            cancelWatchdog()
        }
    }

    // Releases the MediaPlayer instance only, without touching state flows or the watchdog.
    // Used internally by play() to clean up any previous instance before starting a new one.
    private fun internalStopPlayerOnly() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing previous MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }
    }

    fun stop() {
        cancelWatchdog()
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
            _isSpeakingOrPlaying.value = false
            _currentWord.value = null
            _highlightedRect.value = null
        }
    }
}
