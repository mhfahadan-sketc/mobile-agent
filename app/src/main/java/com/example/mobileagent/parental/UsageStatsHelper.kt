package com.example.mobileagent.parental

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

data class AppUsageInfo(
    val packageName: String,
    val label: String,
    val usedMinutes: Int,
    val limitMinutes: Int,
    val isSystem: Boolean
) {
    val remainingMinutes: Int
        get() = if (limitMinutes > 0) (limitMinutes - usedMinutes).coerceAtLeast(0) else -1
    val isOverLimit: Boolean
        get() = limitMinutes > 0 && usedMinutes >= limitMinutes
    val progress: Float
        get() = if (limitMinutes > 0) (usedMinutes.toFloat() / limitMinutes).coerceIn(0f, 1f) else 0f
}

/**
 * خواندن آمار استفاده‌ی اپ‌ها.
 *
 * از UsageStatsManager استفاده می‌کنه که به پرمیشن
 * PACKAGE_USAGE_STATS نیاز داره (دستی از تنظیمات باید داده بشه).
 */
class UsageStatsHelper(private val ctx: Context) {

    companion object {
        private const val TAG = "UsageStatsHelper"
    }

    private val usm: UsageStatsManager? =
        ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * چک کن پرمیشن آمار استفاده داده شده یا نه.
     */
    fun hasPermission(): Boolean {
        if (usm == null) return false
        val end = System.currentTimeMillis()
        val start = end - 60_000L
        return try {
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, start, end
            )
            !stats.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * آمار استفاده از ابتدای امروز تا حالا.
     * @return Map با کلید packageName و مقدار دقیقه
     */
    fun getTodayUsage(): Map<String, Int> {
        val manager = usm ?: return emptyMap()
        val now = System.currentTimeMillis()

        // ابتدای امروز (۰۰:۰۰)
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        return try {
            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startOfDay, now
            ) ?: return emptyMap()

            // ادغام ورودی‌های تکراری (گاهی سیستم چند رکورد می‌ده)
            val merged = mutableMapOf<String, Long>()
            for (s in stats) {
                val existing = merged[s.packageName] ?: 0L
                merged[s.packageName] = existing + s.totalTimeInForeground
            }

            merged.mapValues { (_, ms) ->
                (ms / 60_000L).toInt()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTodayUsage failed", e)
            emptyMap()
        }
    }

    /**
     * لیست اپ‌های کاربری (غیرسیستمی) با آمار استفاده.
     */
    fun getUserApps(): List<AppUsageInfo> {
        val pm = ctx.packageManager
        val usage = getTodayUsage()

        val apps = if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        else
            @Suppress("DEPRECATION") pm.getInstalledApplications(0)

        return apps
            .filter { app ->
                pm.getLaunchIntentForPackage(app.packageName) != null
            }
            .map { app ->
                val isSys = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
                AppUsageInfo(
                    packageName = app.packageName,
                    label = pm.getApplicationLabel(app).toString(),
                    usedMinutes = usage[app.packageName] ?: 0,
                    limitMinutes = 0, // بعداً پر می‌شه
                    isSystem = isSys
                )
            }
            .filter { !it.isSystem }
            .sortedByDescending { it.usedMinutes }
    }

    /**
     * آمار استفاده‌ی یه اپ خاص.
     */
    fun getUsageFor(pkg: String): Int {
        return getTodayUsage()[pkg] ?: 0
    }
}
