package com.zoro.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

enum class VoiceState { IDLE, LISTENING, THINKING, SPEAKING }

class VoiceEngine(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onState(state: VoiceState)
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
    }

    var languageTag: String = "en-US"   // "te-IN" Telugu, "hi-IN" Hindi (needs language pack on phone)

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                pickMaleVoice()
                tts?.setPitch(0.8f)       // lower pitch for a deeper voice
                tts?.setSpeechRate(1.0f)
                ttsReady = true
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { main.post { listener.onState(VoiceState.IDLE) } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { main.post { listener.onState(VoiceState.IDLE) } }
        })
    }

    private fun pickMaleVoice() {
        val voices = tts?.voices ?: return
        val male = voices.firstOrNull {
            it.locale.language == "en" && !it.isNetworkConnectionRequired &&
                it.name.contains("male", true) && !it.name.contains("female", true)
        }
        if (male != null) tts?.voice = male
    }

    fun startListening() {
        stopSpeaking()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError("Speech recognition is not available on this phone.")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { listener.onState(VoiceState.LISTENING) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rms: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(t: Int, p: Bundle?) {}
                override fun onPartialResults(p: Bundle?) {
                    p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        ?.let { listener.onPartial(it) }
                }
                override fun onResults(res: Bundle?) {
                    val text = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) {
                        listener.onState(VoiceState.IDLE)
                    } else {
                        listener.onState(VoiceState.THINKING)
                        listener.onFinal(text)
                    }
                }
                override fun onError(code: Int) {
                    listener.onState(VoiceState.IDLE)
                    listener.onError(
                        when (code) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Tap the mic and try again."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Speech recognition needs an internet connection."
                            else -> "Speech error ($code). Please try again."
                        }
                    )
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            r.startListening(intent)
        }
    }

    fun stopListening() { recognizer?.stopListening(); listener.onState(VoiceState.IDLE) }

    fun speak(text: String) {
        if (!ttsReady) { listener.onState(VoiceState.IDLE); return }
        listener.onState(VoiceState.SPEAKING)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zoro")
    }

    fun stopSpeaking() { tts?.stop() }

    fun destroy() { recognizer?.destroy(); tts?.shutdown() }
}
