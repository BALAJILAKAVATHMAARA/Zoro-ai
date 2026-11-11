package com.zoro.ai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoro.ai.voice.VoiceState
import kotlinx.coroutines.delay

val ZoroBlue = Color(0xFF00B8FF)
val ZoroDeep = Color(0xFF0A3A5C)

data class ChatMessage(val fromUser: Boolean, val text: String)

class ZoroUiState {
    var voiceState by mutableStateOf(VoiceState.IDLE)
    var partial by mutableStateOf("")
    var notice by mutableStateOf("")
    val messages = mutableStateListOf<ChatMessage>()
}

@Composable
fun ZoroApp(state: ZoroUiState, onMic: () -> Unit) {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(2000); showSplash = false }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (showSplash) Splash() else Dashboard(state, onMic)
    }
}

@Composable
fun GlowTitle(size: Int) = Text(
    "ZORO",
    style = TextStyle(
        color = ZoroBlue, fontSize = size.sp, fontWeight = FontWeight.Bold, letterSpacing = 10.sp,
        shadow = Shadow(ZoroBlue, Offset.Zero, 30f)
    )
)

@Composable
fun Splash() {
    val a by rememberInfiniteTransition(label = "s").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a"
    )
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Box(Modifier.alpha(a)) { GlowTitle(48) }
        Text("PERSONAL AI ASSISTANT", color = Color(0xFF6FA8C7), fontSize = 12.sp, letterSpacing = 4.sp)
    }
}

@Composable
fun Dashboard(state: ZoroUiState, onMic: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlowTitle(28)
        Spacer(Modifier.height(12.dp))
        Orb(state.voiceState)
        Spacer(Modifier.height(8.dp))
        Text(
            when (state.voiceState) {
                VoiceState.IDLE -> "Tap the mic and speak"
                VoiceState.LISTENING -> if (state.partial.isNotEmpty()) state.partial else "Listening..."
                VoiceState.THINKING -> "Thinking..."
                VoiceState.SPEAKING -> "Speaking... tap the mic to interrupt"
            },
            color = ZoroBlue, fontSize = 14.sp
        )
        if (state.notice.isNotEmpty()) Text(state.notice, color = Color(0xFFFF8A80), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.messages) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                    Text(
                        m.text,
                        color = Color.White,
                        modifier = Modifier
                            .background(if (m.fromUser) ZoroDeep else Color(0xFF151B20), CircleShape.copy(all = androidx.compose.foundation.shape.CornerSize(16.dp)))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.size(72.dp).background(ZoroBlue, CircleShape).clickable { onMic() },
            contentAlignment = Alignment.Center
        ) {
            Text(if (state.voiceState == VoiceState.LISTENING) "■" else "MIC", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Orb(voice: VoiceState) {
    val t = rememberInfiniteTransition(label = "orb")
    val speed = if (voice == VoiceState.IDLE) 9000 else 3000
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(speed, easing = LinearEasing)), label = "r")
    val pulse by t.animateFloat(
        0.92f, 1.08f,
        infiniteRepeatable(tween(if (voice == VoiceState.LISTENING) 500 else 1400), RepeatMode.Reverse), label = "p"
    )
    Canvas(Modifier.size(200.dp)) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(ZoroBlue.copy(alpha = 0.15f), r * 0.55f * pulse, c)
        drawCircle(ZoroBlue.copy(alpha = 0.5f), r * 0.55f * pulse, c, style = Stroke(3f))
        rotate(angle, c) {
            for (i in 0 until 3) {
                rotate(i * 120f, c) {
                    drawArc(ZoroBlue, 0f, 70f, false, Offset(c.x - r * 0.8f, c.y - r * 0.8f),
                        Size(r * 1.6f, r * 1.6f), style = Stroke(5f))
                }
            }
        }
        rotate(-angle * 1.5f, c) {
            drawArc(ZoroBlue.copy(alpha = 0.6f), 0f, 200f, false, Offset(c.x - r * 0.95f, c.y - r * 0.95f),
                Size(r * 1.9f, r * 1.9f), style = Stroke(2f))
        }
    }
}
