package com.example.mobileagent.parental

import android.content.Context
import android.content.SharedPreferences

/**
 * ذخیره‌ی تنظیمات کنترلر والدین.
 *
 * تنظیمات:
 *  - enabled: فعال/غیرفعال بودن سیستم
 *  - limits: پکیج‌هایی که محدودیت دارن + مقدارشون به دقیقه
 *  - pin: رمز برای باز کردن قفل اپ محدود شده
 *  - softBlock: اگه true → فقط هشدار بده، اگه false → بلاک کن
 */
class ParentalStore(ctx: Context) {

    private val prefs: SharedPreferences =
        ctx.getSharedPreferences("parental_store", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    var softBlock: Boolean
        get() = prefs.getBoolean("soft_block", false)
        set(v) = prefs.edit().putBoolean("soft_block", v).apply()

    var pin: String?
        get() = prefs.getString("pin", null)
        set(v) = prefs.edit().putString("pin", v).apply()

    var hasCompletedSetup: Boolean
        get() = prefs.getBoolean("setup_completed", false)
        set(v) = prefs.edit().putBoolean("setup_completed", v).apply()

    /**
     * محدودیت‌ها به دقیقه. کلید = packageName، مقدار = دقیقه در روز.
     * مقدار ۰ یا منفی = بدون محدودیت.
     */
    private fun getLimitsMap(): MutableMap<String, Int> {
        val raw = prefs.getString("limits", "") ?: ""
        val map = mutableMapOf<String, Int>()
        if (raw.isBlank()) return map
        raw.split("|").forEach { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val pkg = parts[0]
                val min = parts[1].toIntOrNull() ?: 0
                if (pkg.isNotEmpty() && min > 0) {
                    map[pkg] = min
                }
            }
        }
        return map
    }

    private fun saveLimits(map: Map<String, Int>) {
        val s = map.entries.joinToString("|") { "${it.key}:${it.value}" }
        prefs.edit().putString("limits", s).apply()
    }

    fun limits(): Map<String, Int> = getLimitsMap()

    fun limitFor(pkg: String): Int = getLimitsMap()[pkg] ?: 0

    fun setLimit(pkg: String, minutes: Int) {
        val map = getLimitsMap()
        if (minutes <= 0) map.remove(pkg) else map[pkg] = minutes
        saveLimits(map)
    }

    fun removeLimit(pkg: String) {
        val map = getLimitsMap()
        map.remove(pkg)
        saveLimits(map)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
