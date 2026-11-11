package com.zoro.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.zoro.ai.commands.CommandHandler
import com.zoro.ai.ui.ChatMessage
import com.zoro.ai.ui.ZoroApp
import com.zoro.ai.ui.ZoroUiState
import com.zoro.ai.voice.VoiceEngine
import com.zoro.ai.voice.VoiceState

class MainActivity : ComponentActivity(), VoiceEngine.Listener {

    private val ui = ZoroUiState()
    private lateinit var voice: VoiceEngine
    private lateinit var commands: CommandHandler

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.startListening() else ui.notice = "Microphone permission is required."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = VoiceEngine(this, this)
        commands = CommandHandler(this)
        setContent { ZoroApp(ui, onMic = ::onMicTap) }
    }

    private fun onMicTap() {
        ui.notice = ""
        when (ui.voiceState) {
            VoiceState.SPEAKING -> { voice.stopSpeaking(); ui.voiceState = VoiceState.IDLE }
            VoiceState.LISTENING -> voice.stopListening()
            else -> {
                val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (ok) voice.startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    override fun onState(state: VoiceState) { ui.voiceState = state; if (state != VoiceState.LISTENING) ui.partial = "" }
    override fun onPartial(text: String) { ui.partial = text }
    override fun onError(message: String) { ui.notice = message }

    override fun onFinal(text: String) {
        ui.messages.add(ChatMessage(true, text))
        val reply = commands.handle(text)
        ui.messages.add(ChatMessage(false, reply))
        voice.speak(reply)
    }

    override fun onDestroy() { voice.destroy(); super.onDestroy() }
}
