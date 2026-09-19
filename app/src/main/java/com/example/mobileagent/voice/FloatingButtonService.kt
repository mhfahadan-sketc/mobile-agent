package com.example.mobileagent.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.mobileagent.MainActivity
import com.example.mobileagent.R

/**
 * سرویس دکمه‌ی شناور.
 *
 * یه دکمه‌ی گرد با آیکون میکروفون که روی همه‌ی اپ‌ها شناوره.
 */
class FloatingButtonService : Service() {

    companion object {
        private const val TAG = "FloatingBtn"
        private const val CHANNEL_ID = "floating_button"
        private const val NOTIF_ID = 8301

        const val ACTION_START = "com.example.mobileagent.FLOAT_START"
        const val ACTION_STOP = "com.example.mobileagent.FLOAT_STOP"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    startForegroundNotification()
                    addFloatingButton()
                }
            }
            ACTION_STOP -> {
                removeFloatingButton()
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ═══════════════════════════════════════
    //  دکمه‌ی شناور
    // ═══════════════════════════════════════

    private fun addFloatingButton() {
        if (floatingView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        // ⭐ تبدیل 64dp به پیکسل — اندازه‌ی ثابت
        val sizePx = (64 * resources.displayMetrics.density).toInt()

        layoutParams = WindowManager.LayoutParams(
            sizePx,     // ← عرض ثابت
            sizePx,     // ← ارتفاع ثابت
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  // ← FLAG_LAYOUT_NO_LIMITS حذف شد
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        // ساخت View
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)

        // تنظیم کلیک و درگ
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        floatingView?.setOnTouchListener { _, event ->
            val lp = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 100) isDragging = true
                    lp.x = initialX + dx.toInt()
                    lp.y = initialY + dy.toInt()
                    windowManager?.updateViewLayout(floatingView, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        openVoiceRecognition()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(floatingView, layoutParams)
        isRunning = true
    }

    private fun removeFloatingButton() {
        try {
            floatingView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) { }
        floatingView = null
        layoutParams = null
        isRunning = false
    }

    private fun openVoiceRecognition() {
        try {
            val i = Intent(this, VoiceRecognitionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(i)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "openVoiceRecognition failed", e)
        }
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
                        "دکمه‌ی شناور دستیار",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "دکمه‌ی میکروفون روی صفحه"
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
            Intent(this, FloatingButtonService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("🎤 دستیار فعاله")
            .setContentText("دکمه‌ی میکروفون روی صفحه‌ست")
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
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onDestroy() {
        removeFloatingButton()
        super.onDestroy()
    }

    // ═══════════════════════════════════════
    //  کمکی استاتیک
    // ═══════════════════════════════════════

    object Starter {
        fun start(ctx: Context) {
            val i = Intent(ctx, FloatingButtonService::class.java).apply {
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
                Intent(ctx, FloatingButtonService::class.java).apply {
                    action = ACTION_STOP
                }
            )
        }
    }
}
