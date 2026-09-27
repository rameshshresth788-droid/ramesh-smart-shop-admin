package com.rameshai.core

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rameshai.MainActivity
import com.rameshai.R
import com.rameshai.RameshAIApplication
import com.rameshai.news.NewsRepository
import com.rameshai.reminders.ReminderScheduler
import com.rameshai.routine.RoutineRepository
import com.rameshai.voice.AndroidSpeechRecognizerEngine
import com.rameshai.voice.AndroidTextToSpeechEngine
import com.rameshai.voice.WakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service backing "background/continuous listening" (see spec
 * section 11). Only ever started when the user has explicitly enabled
 * background listening in Settings (RuntimeConfig.featureBackgroundListening) —
 * see [start]. Always shows the required ongoing notification so the user knows
 * the mic may be listening for the wake phrase, and stops immediately via
 * [stop]/STOP_ACTION when the user disables the feature.
 */
class AssistantForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var wakeWordDetector: WakeWordDetector? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        val app = application as RameshAIApplication
        val config = app.configRepository.current()
        if (!config.featureBackgroundListening) {
            stopSelf()
            return
        }

        val speechEngine = AndroidSpeechRecognizerEngine(this)
        val ttsEngine = AndroidTextToSpeechEngine(this)
        val orchestrator = AssistantOrchestrator(
            this,
            app.configRepository,
            com.rameshai.memory.MemoryRepository(app.memoryDatabase.memoryDao()),
            ReminderScheduler(this, app.reminderDatabase.reminderDao()),
            RoutineRepository(this),
            NewsRepository(this, config)
        )

        wakeWordDetector = WakeWordDetector(speechEngine, config.wakePhrase).also { detector ->
            detector.start(
                languageTag = config.language,
                onWakeWordDetected = {
                    // Hand off to a full command listening pass, then speak the reply.
                    speechEngine.startListening(config.language) { event ->
                        if (event is com.rameshai.voice.VoiceEvent.FinalResult) {
                            scope.launch {
                                val reply = orchestrator.handleUserUtterance(event.text)
                                ttsEngine.speak(reply, config.language) {
                                    // Resume passive wake-word listening after speaking.
                                    detector.start(config.language, {}, {})
                                }
                            }
                        }
                    }
                },
                onListeningError = { /* logged via debugMode-gated logger, never crashes the service */ }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        wakeWordDetector?.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openIntent = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, RameshAIApplication.CHANNEL_ASSISTANT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("RAMESH AI")
            .setContentText(getString(R.string.assistant_notification_text))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.rameshai.action.STOP_ASSISTANT"

        fun start(context: Context) {
            val intent = Intent(context, AssistantForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AssistantForegroundService::class.java).apply { action = ACTION_STOP })
        }
    }
}
