package com.rameshai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rameshai.config.RuntimeConfig
import com.rameshai.core.AssistantOrchestrator
import com.rameshai.ui.components.AiOrb
import com.rameshai.ui.theme.BackgroundDark
import com.rameshai.voice.AndroidSpeechRecognizerEngine
import com.rameshai.voice.AndroidTextToSpeechEngine
import com.rameshai.voice.AssistantState
import com.rameshai.voice.VoiceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The entire "normal" app experience: a big orb, minimal chrome, tap-or-say to
 * talk. All actual work (AI calls, tool execution) happens in [AssistantOrchestrator];
 * this screen only wires voice I/O to it and reflects [AssistantState] visually.
 */
@Composable
fun HomeScreen(
    orchestrator: AssistantOrchestrator,
    speechEngine: AndroidSpeechRecognizerEngine,
    ttsEngine: AndroidTextToSpeechEngine,
    config: StateFlow<RuntimeConfig>,
    onOpenSettings: () -> Unit,
    hasMicPermission: () -> Boolean,
    onRequestMicPermission: () -> Unit
) {
    val state by orchestrator.state.collectAsState()
    val lastText by orchestrator.lastSpokenText.collectAsState()
    val currentConfig by config.collectAsState()
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("Tap ya bolo \"${currentConfig.wakePhrase}\"") }

    fun startListening() {
        if (!hasMicPermission()) {
            onRequestMicPermission()
            return
        }
        orchestrator.onListeningStarted()
        statusText = "Sun raha hoon..."
        speechEngine.startListening(currentConfig.language) { event ->
            when (event) {
                is VoiceEvent.FinalResult -> {
                    statusText = "Soch raha hoon..."
                    scope.launch {
                        val reply = orchestrator.handleUserUtterance(event.text)
                        statusText = "Bol raha hoon..."
                        ttsEngine.speak(reply, currentConfig.language) {
                            orchestrator.onSpeakingFinished()
                            statusText = "Tap ya bolo \"${currentConfig.wakePhrase}\""
                        }
                    }
                }
                is VoiceEvent.Error -> {
                    orchestrator.onError(event.message)
                    statusText = event.message
                }
                is VoiceEvent.TimedOut -> {
                    orchestrator.resetToIdle()
                    statusText = "Tap ya bolo \"${currentConfig.wakePhrase}\""
                }
                else -> Unit
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.clickable { if (state != AssistantState.LISTENING) startListening() },
                contentAlignment = Alignment.Center
            ) {
                AiOrb(state = state)
                if (state == AssistantState.LISTENING) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Listening",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = statusText.ifBlank { lastText },
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
