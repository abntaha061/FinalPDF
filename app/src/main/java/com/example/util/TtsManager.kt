package com.example.util

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(@ApplicationContext val context: Context) {

    private var tts: TextToSpeech? = null
    var isInitialized = false
        private set
    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    val state: StateFlow<TtsState> = _state

    var volume: Float = 1.0f
    private var currentText: String = ""
    private var currentLanguage: Locale = Locale("ar")
    private var currentSpeechRate: Float = 1.0f
    private var currentPitch: Float = 1.0f

    sealed class TtsState {
        object Idle : TtsState()
        object Loading : TtsState()
        data class Speaking(val text: String, val progress: Float) : TtsState()
        data class Error(val message: String) : TtsState()
        object Finished : TtsState()
    }

    fun init(onReady: () -> Unit) {
        if (isInitialized) {
            onReady()
            return
        }
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                onReady()
            } else {
                _state.value = TtsState.Error("فشل تهيئة محرك النطق")
            }
        }
    }

    fun speak(text: String, language: Locale = Locale("ar")) {
        currentText = text
        currentLanguage = language
        if (!isInitialized) {
            init {
                speakInternal(text, language)
            }
        } else {
            speakInternal(text, language)
        }
    }

    private fun speakInternal(text: String, language: Locale) {
        tts?.language = language
        tts?.setSpeechRate(currentSpeechRate)
        tts?.setPitch(currentPitch)
        _state.value = TtsState.Speaking(text, 0f)

        // Split text into sentences for progress tracking
        val sentences = text.split(Regex("[.!?؟،\n]")).filter { it.isNotBlank() }
        if (sentences.isEmpty()) {
            _state.value = TtsState.Finished
            return
        }
        var currentSentence = 0

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                currentSentence++
                _state.value = TtsState.Speaking(text,
                    (currentSentence.toFloat() / sentences.size).coerceAtMost(1.0f))
            }
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "last") {
                    _state.value = TtsState.Finished
                }
            }
            override fun onError(utteranceId: String?) {
                _state.value = TtsState.Error("خطأ في النطق")
            }
        })

        sentences.forEachIndexed { index, sentence ->
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, 0f)
            }
            tts?.speak(sentence.trim(), 
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                params,
                if (index == sentences.lastIndex) "last" else "sentence_$index")
        }
    }

    fun pause() { tts?.stop(); _state.value = TtsState.Idle }
    fun resume() {
        if (currentText.isNotEmpty()) {
            speak(currentText, currentLanguage)
        }
    }
    fun stop() { tts?.stop(); _state.value = TtsState.Idle }
    fun setSpeed(speed: Float) {
        currentSpeechRate = speed
        tts?.setSpeechRate(speed)
    } // 0.5f - 2.0f
    fun setPitch(pitch: Float) {
        currentPitch = pitch
        tts?.setPitch(pitch)
    }      // 0.5f - 2.0f
    fun shutdown() { tts?.shutdown() }
}
