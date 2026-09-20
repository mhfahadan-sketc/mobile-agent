package com.example.mobileagent.parental

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.mobileagent.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * سرویس پایش استفاده از اپ‌ها.
 *
 * هر ۳ ثانیه چک می‌کنه:
 *  ۱. کاربر توی چه اپی هست (از UsageStatsManager)
 *  ۲. چقدر استفاده کرده امروز
 *  ۳. اگه از حد گذشته → صفحه‌ی مسدود باز کن
 */
class AppLimitService : Service() {

    companion object {
        private const val TAG = "AppLimitService"
        private const val CHANNEL_ID = "app_limit_monitor"
        private const val NOTIF_ID = 8501
        private const val CHECK_INTERVAL_MS = 3000L

        const val ACTION_START = "com.example.mobileagent.LIMIT_START"
        const val ACTION_STOP = "com.example.mobileagent.LIMIT_STOP"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private lateinit var store: ParentalStore
    private lateinit var usageHelper: UsageStatsHelper

    private var lastBlockedPkg: String? = null
    private var lastBlockTime: Long = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        store = ParentalStore(this)
        usageHelper = UsageStatsHelper(this)

        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    startForegroundNotification()
                    startMonitoring()
                }
            }
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ═══════════════════════════════════════
    //  پایش
    // ═══════════════════════════════════════

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            isRunning = true
            Log.i(TAG, "Monitoring started")

            while (isActive) {
                try {
                    if (store.enabled && usageHelper.hasPermission()) {
                        checkCurrentApp()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "check loop error", e)
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        isRunning = false
        Log.i(TAG, "Monitoring stopped")
    }

    /**
     * چک کن کاربر الان توی چه اپی هست و آیا محدودیتش تموم شده.
     */
    private fun checkCurrentApp() {
        val currentPkg = getForegroundPackage() ?: return
        if (currentPkg == packageName) return  // خودمون
        if (currentPkg == "android") return      // صفحه‌ی home
        if (currentPkg.startsWith("com.android.systemui")) return
                // اگه والدین اجازه‌ی موقت داده، بلاک نکن
        if (AppLimitServiceGuard.isAllowed(currentPkg)) return

        val limit = store.limitFor(currentPkg)
        if (limit <= 0) return  // محدودیتی نداره

        val used = usageHelper.getUsageFor(currentPkg)
        if (used < limit) {
            // هنوز زیر حد
            lastBlockedPkg = null
            return
        }

        // از حد گذشته — بلاک کن
        // نذار بیشتر از یه بار در دقیقه بلاک شه
        val now = System.currentTimeMillis()
        if (currentPkg == lastBlockedPkg && now - lastBlockTime < 60_000L) {
            return
        }

        lastBlockedPkg = currentPkg
        lastBlockTime = now
        launchBlockScreen(currentPkg, used, limit)
    }

    /**
     * پیدا کردن اپ فعال (foreground).
     */
    private fun getForegroundPackage(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 60_000L,
                now
            ) ?: return null

            // آخرین اپ با lastTimeUsed بزرگ‌ترین
            stats.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "getForegroundPackage failed", e)
            null
        }
    }

    private fun launchBlockScreen(pkg: String, usedMinutes: Int, limitMinutes: Int) {
        try {
            Log.i(TAG, "Blocking $pkg — $usedMinutes / $limitMinutes min")

            val i = Intent(this, LimitOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(LimitOverlayActivity.EXTRA_PKG, pkg)
                putExtra(LimitOverlayActivity.EXTRA_USED, usedMinutes)
                putExtra(LimitOverlayActivity.EXTRA_LIMIT, limitMinutes)
            }
            startActivity(i)
        } catch (e: Exception) {
            Log.e(TAG, "launchBlockScreen failed", e)
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
                        "کنترلر والدین",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "پایش استفاده از اپ‌ها"
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
            Intent(this, AppLimitService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("🛡️ کنترلر والدین فعاله")
            .setContentText("استفاده از اپ‌ها پایش می‌شه")
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
        stopMonitoring()
        scope.cancel()
        super.onDestroy()
    }

    // ═══════════════════════════════════════
    //  کمکی استاتیک
    // ═══════════════════════════════════════

    object Starter {
        fun start(ctx: Context) {
            val i = Intent(ctx, AppLimitService::class.java).apply {
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
                Intent(ctx, AppLimitService::class.java).apply {
                    action = ACTION_STOP
                }
            )
        }
    }
}
