package com.example.mobileagent.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.mobileagent.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/**
 * سرویس همیشه‌گوش.
 *
 * در پس‌زمینه کار می‌کنه و به کلمه‌ی کلیدی («دستیار») گوش می‌ده.
 * وقتی شنید، یه نوتیف نشون می‌ده و منتظر دستور می‌مونه.
 *
 * مدل رو از filesDir/vosk-model می‌خونه (جایی که VoiceSettingsScreen دانلود می‌کنه).
 */
class WakeWordService : Service(), RecognitionListener {

    companion object {
        private const val TAG = "WakeWordSvc"
        private const val CHANNEL_ID = "wake_word"
        private const val NOTIF_ID = 8101

        const val ACTION_START = "com.example.mobileagent.WAKE_START"
        const val ACTION_STOP = "com.example.mobileagent.WAKE_STOP"

        private const val MODEL_DIR = "vosk-model"

        private val WAKE_WORDS = listOf(
            "دستیار",
            "دستیارم",
            "هی دستیار",
            "ایجنت",
            "assistant"
        )

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var model: Model? = null
    private var speechService: SpeechService? = null

    private var isInitializing = false
    private var lastWakeTime: Long = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning && !isInitializing) {
                    startForegroundNotification()
                    initVosk()
                }
            }
            ACTION_STOP -> {
                stopEverything()
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ═══════════════════════════════════════
    //  راه‌اندازی Vosk
    // ═══════════════════════════════════════

    private fun initVosk() {
        isInitializing = true

        // مدل توی filesDir/vosk-model هست (دونلود شده از تنظیمات)
        val modelDir = File(filesDir, MODEL_DIR)

        if (!modelDir.exists() || modelDir.listFiles()?.isEmpty() != false) {
            Log.w(TAG, "Model not found at ${modelDir.absolutePath}")
            isInitializing = false
            VoiceCommandBus.emit(
                VoiceCommandBus.Event.Error(
                    "مدل نصب نیست. از تنظیمات دانلود کن."
                )
            )
            stopSelf()
            return
        }

        try {
            Log.i(TAG, "Loading model from ${modelDir.absolutePath}")
            val m = Model(modelDir.absolutePath)
            model = m
            isInitializing = false
            startRecognition()
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed", e)
            isInitializing = false
            VoiceCommandBus.emit(
                VoiceCommandBus.Event.Error("مدل خرابه، دوباره دانلود کن")
            )
            stopSelf()
        }
    }

    private fun startRecognition() {
        val m = model ?: return

        try {
            val recognizer = Recognizer(m, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this)
            isRunning = true
            VoiceCommandBus.emit(VoiceCommandBus.Event.Listening)
            Log.i(TAG, "Listening started")
        } catch (e: Exception) {
            Log.e(TAG, "startRecognition failed", e)
            isRunning = false
            stopSelf()
        }
    }

    // ═══════════════════════════════════════
    //  RecognitionListener
    // ═══════════════════════════════════════

    override fun onPartialResult(hypothesis: String?) {
        // نتیجه‌ی نهایی از onResult میاد
    }

    override fun onResult(hypothesis: String?) {
        if (hypothesis.isNullOrBlank()) return

        try {
            val json = JSONObject(hypothesis)
            val text = json.optString("text", "").trim()

            if (text.isEmpty()) return

            Log.d(TAG, "Recognized: $text")

            val hasWake = WAKE_WORDS.any { text.contains(it, ignoreCase = true) }

            if (hasWake) {
                val now = System.currentTimeMillis()
                if (now - lastWakeTime < 3000) return
                lastWakeTime = now

                VoiceCommandBus.emit(VoiceCommandBus.Event.WakeWordDetected)

                // از متن کلمه‌ی کلیدی رو حذف کن
                var command = text
                WAKE_WORDS.forEach { w ->
                    command = command.replace(w, "", ignoreCase = true)
                }
                command = command.trim()

                if (command.isNotEmpty()) {
                    VoiceCommandBus.emit(
                        VoiceCommandBus.Event.CommandRecognized(command)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onResult parse failed", e)
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        onResult(hypothesis)
    }

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Recognition error", exception)
        VoiceCommandBus.emit(
            VoiceCommandBus.Event.Error(exception?.message ?: "خطای صدا")
        )
    }

    override fun onTimeout() {
        Log.d(TAG, "Recognition timeout")
    }

    // ═══════════════════════════════════════
    //  نوتیف دائمی
    // ═══════════════════════════════════════

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "دستیار صوتی",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "وقتی فعاله، به کلمه‌ی «دستیار» گوش می‌ده"
                        setShowBadge(false)
                        enableVibration(false)
                        setSound(null, null)
                    }
                )
            }
        }
    }

    private fun startForegroundNotification() {
        ensureChannel()

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, WakeWordService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("🎤 دستیار گوش می‌ده")
            .setContentText("بگو «دستیار» تا فعال شه")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "خاموش", stop)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID, notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun stopEverything() {
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (_: Exception) { }
        speechService = null
        model = null
        isRunning = false
        VoiceCommandBus.emit(VoiceCommandBus.Event.Idle)
    }

    override fun onDestroy() {
        stopEverything()
        scope.cancel()
        super.onDestroy()
    }

    // ═══════════════════════════════════════
    //  کمکی استاتیک
    // ═══════════════════════════════════════

    object Starter {
        fun start(ctx: Context) {
            val i = Intent(ctx, WakeWordService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            ctx.startService(
                Intent(ctx, WakeWordService::class.java).apply {
                    action = ACTION_STOP
                }
            )
        }
    }
}
