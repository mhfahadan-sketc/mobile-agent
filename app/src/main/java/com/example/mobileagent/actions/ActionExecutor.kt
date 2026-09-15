package com.example.mobileagent.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.mobileagent.agent.Command
import com.example.mobileagent.agent.ContactResolver
import com.example.mobileagent.agent.Parser

class ActionExecutor(private val context: Context) {

    private val contacts = ContactResolver(context)

    fun execute(cmd: Command): String = when (cmd) {
        is Command.Call -> call(cmd.contact)
        is Command.Sms -> sms(cmd.contact, cmd.body)
        is Command.WhatsApp -> whatsapp(cmd.contact, cmd.body)
        is Command.OpenApp -> openApp(cmd.name)
        is Command.WebSearch -> webSearch(cmd.query)
        is Command.OpenUrl -> openUrl(cmd.url)
        is Command.SetAlarm -> setAlarm(cmd.hour, cmd.minute)
        Command.Help -> HELP
        Command.Cancel -> "لغو شد."
        is Command.Unknown -> "متوجه نشدم 🤔\n«راهنما» رو بزن."
    }

    private fun call(contact: String): String {
        val num = contacts.resolve(contact) ?: return "«$contact» توی مخاطبین نبود."
        if (!has(Manifest.permission.CALL_PHONE))
            return "اجازه‌ی تماس نداری. از تنظیمات → برنامه‌ها → دستیار موبایل → مجوزها بده."
        return try {
            context.startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(num)}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "📞 تماس با «$contact»"
        } catch (e: Exception) { "تماس نشد: ${e.message}" }
    }

    private fun sms(contact: String, body: String): String {
        val num = contacts.resolve(contact) ?: return "«$contact» پیدا نشد."
        if (!has(Manifest.permission.SEND_SMS))
            return "اجازه‌ی پیامک نداری."
        return try {
            val sm = if (Build.VERSION.SDK_INT >= 31)
                context.getSystemService(SmsManager::class.java)!!
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = sm.divideMessage(body)
            if (parts.size == 1) sm.sendTextMessage(num, null, body, null, null)
            else sm.sendMultipartTextMessage(num, null, parts, null, null)
            "✉️ پیامک به «$contact» فرستاده شد."
        } catch (e: Exception) { "پیامک نشد: ${e.message}" }
    }

    private fun whatsapp(contact: String, body: String?): String {
        val num = contacts.resolve(contact) ?: return "«$contact» پیدا نشد."
        val cleaned = num.filter { it.isDigit() }
            .removePrefix("0").let { if (it.startsWith("98")) it else "98$it" }
        val url = buildString {
            append("https://wa.me/").append(cleaned)
            if (!body.isNullOrBlank()) append("?text=").append(Uri.encode(body))
        }
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "📨 واتساپ برای «$contact» باز شد."
        } catch (e: Exception) { "واتساپ نصب نیست." }
    }

    private val appAliases = mapOf(
        "اینستاگرام" to "com.instagram.android",
        "تلگرام" to "org.telegram.messenger",
        "واتساپ" to "com.whatsapp",
        "یوتیوب" to "com.google.android.youtube",
        "کروم" to "com.android.chrome",
        "نقشه" to "com.google.android.apps.maps",
        "تنظیمات" to "com.android.settings"
    )

    private fun openApp(name: String): String {
        val q = Parser.normalize(name).lowercase()
        val pm = context.packageManager

        val direct = appAliases[q] ?: appAliases.entries.firstOrNull {
            q.contains(it.key) || it.key.contains(q)
        }?.value
        if (direct != null) {
            pm.getLaunchIntentForPackage(direct)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
                return "🚀 «$name» باز شد."
            }
        }

        val apps = if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getInstalledApplications(0)
        for (a in apps) {
            val lbl = Parser.normalize(pm.getApplicationLabel(a).toString())
            if (lbl.contains(q, true) || q.contains(lbl, true)) {
                pm.getLaunchIntentForPackage(a.packageName)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                    return "🚀 «$lbl» باز شد."
                }
            }
        }
        return "اپی با نام «$name» نبود."
    }

    private fun webSearch(q: String): String {
        // اول تلاش کن با اپ پیش‌فرض مرورگر
        return try {
            context.startActivity(
                Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", q)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "🔎 دارم «$q» رو جستجو می‌کنم."
        } catch (_: Exception) {
            // فالبک: باز کردن گوگل با URL
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=${Uri.encode(q)}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "🔎 دارم «$q» رو جستجو می‌کنم."
            } catch (_: Exception) { "مرورگر نبود." }
        }
    }

    private fun openUrl(url: String): String {
        val full = if (url.startsWith("http")) url else "https://$url"
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(full))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "باز شد: $full"
        } catch (_: Exception) { "باز نشد." }
    }

    /**
     * آلارم — با چند فالبک برای سازگاری با گوشی‌های مختلف
     */
    private fun setAlarm(h: Int, m: Int): String {
        val timeStr = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"

        // روش ۱: Intent استاندارد SetAlarm
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, h)
                putExtra(AlarmClock.EXTRA_MINUTES, m)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return "⏰ آلارم $timeStr تنظیم شد."
        } catch (_: Exception) { }

        // روش ۲: باز کردن مستقیم اپ ساعت
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, h)
                putExtra(AlarmClock.EXTRA_MINUTES, m)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return "⏰ آلارم $timeStr تنظیم شد."
        } catch (_: Exception) { }

        // روش ۳: باز کردن خود اپ ساعت با دستی تنظیم کن
        try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return "⏰ اپ ساعت باز شد. ساعت $timeStr رو دستی تنظیم کن."
        } catch (_: Exception) { }

        // روش ۴: صفحه‌ی تاریخ/زمان تنظیمات
        return try {
            context.startActivity(
                Intent(Settings.ACTION_DATE_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "⏰ اپ ساعت روی گوشیت پیدا نشد. برای آلارم ساعت $timeStr از اپ ساعت خودت استفاده کن."
        } catch (_: Exception) {
            "نتونستم آلارم بذارم. احتمالاً اپ ساعت روی گوشیت پیدا نشد."
        }
    }

    private fun has(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    companion object {
        val HELP = """
            می‌تونم این کارها رو بکنم:

            📞 زنگ بزن به علی
            ✉️ به مامان پیام بده که دیر می‌رسم
            📨 واتساپ به بابا بگو رسیدم
            🚀 اینستاگرام رو باز کن
            ⏰ ساعت ۱۰ آلارم بذار
            ⏰ ۷ صبح یادآوری بذار
            🔎 گوگل کن هوای تهران
            🔎 هوای مشهد
        """.trimIndent()
    }
}
