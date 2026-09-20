package com.example.mobileagent.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.mobileagent.agent.Command
import com.example.mobileagent.agent.ContactResolver
import com.example.mobileagent.agent.Parser
import com.example.mobileagent.security.MalwareScanner
import com.example.mobileagent.security.Threat
import com.example.mobileagent.storage.DuplicateFinder
import com.example.mobileagent.storage.DuplicateGroup
import com.example.mobileagent.storage.JunkCleaner
import com.example.mobileagent.storage.JunkFile

class ActionExecutor(private val context: Context) {

    private val contacts = ContactResolver(context)
    private val malware = MalwareScanner(context)
    private val dupFinder = DuplicateFinder(context)
    private val junk = JunkCleaner(context)

    var lastThreats: List<Threat> = emptyList()
        private set
    var lastDuplicates: List<DuplicateGroup> = emptyList()
        private set
    var lastJunk: List<JunkFile> = emptyList()
        private set

    suspend fun execute(cmd: Command): String = when (cmd) {
        is Command.Call -> call(cmd.contact)
        is Command.Sms -> sms(cmd.contact, cmd.body)
        is Command.WhatsApp -> whatsapp(cmd.contact, cmd.body)
        is Command.OpenApp -> openApp(cmd.name)
        is Command.WebSearch -> webSearch(cmd.query)
        is Command.OpenUrl -> openUrl(cmd.url)
        is Command.SetAlarm -> setAlarm(cmd.hour, cmd.minute)

        Command.ScanMalware -> scanMalware()
        Command.FindDuplicates -> findDuplicates()
        Command.DeleteDuplicates -> deleteDuplicates()
        Command.FindJunk -> findJunk()
        Command.DeleteJunk -> deleteJunk()
        Command.AnalyzeStorage -> analyzeStorage()

        Command.Help -> HELP
        Command.Cancel -> "لغو شد."
        is Command.Unknown -> "متوجه نشدم 🤔\n«راهنما» رو بزن."
    }

    private fun call(contact: String): String {
        val num = contacts.resolve(contact) ?: return "«$contact» توی مخاطبین نبود."
        if (!has(Manifest.permission.CALL_PHONE))
            return "اجازه‌ی تماس نداری."
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
        if (!has(Manifest.permission.SEND_SMS)) return "اجازه‌ی پیامک نداری."
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
        return try {
            context.startActivity(
                Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", q)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "🔎 دارم «$q» رو جستجو می‌کنم."
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=${Uri.encode(q)}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    private fun setAlarm(h: Int, m: Int): String {
        val timeStr = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
        return try {
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, h)
                set(java.util.Calendar.MINUTE, m)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }

            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

            val intent = Intent(context, com.example.mobileagent.alarm.AlarmReceiver::class.java).apply {
                putExtra("hour", h)
                putExtra("minute", m)
            }
            val pi = android.app.PendingIntent.getBroadcast(
                context,
                1000 + h * 60 + m,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val info = android.app.AlarmManager.AlarmClockInfo(target.timeInMillis, pi)
            am.setAlarmClock(info, pi)

            "⏰ آلارم برای $timeStr تنظیم شد."
        } catch (e: Exception) {
            "آلارم نشد: ${e.message}"
        }
    }

    private suspend fun scanMalware(): String {
        lastThreats = malware.scan()
        if (lastThreats.isEmpty()) return "✅ اسکن تموم شد. تهدیدی نبود."
        return buildString {
            append("⚠️ ${lastThreats.size} مورد پیدا شد:\n\n")
            lastThreats.take(5).forEach { append("• ${it.label} — ${it.severity.label}\n") }
            if (lastThreats.size > 5) append("… و ${lastThreats.size - 5} مورد دیگه.\n")
            append("\nصفحه‌ی جزئیات باز شد.")
        }
    }

    private suspend fun findDuplicates(): String {
        lastDuplicates = dupFinder.find()
        if (lastDuplicates.isEmpty()) return "🔍 فایل تکراری نبود."
        val total = lastDuplicates.sumOf { it.wasted }
        return buildString {
            append("🔍 ${lastDuplicates.size} گروه تکراری\n")
            append("💾 قابل آزادسازی: ${fmtB(total)}\n\n")
            lastDuplicates.take(5).forEach {
                append("• «${it.files.first().name}» × ${it.files.size}\n")
            }
            append("\nبرای حذف بگو: «تکراری‌ها رو پاک کن»")
        }
    }

    private suspend fun deleteDuplicates(): String {
        if (lastDuplicates.isEmpty()) return "اول بگو «فایل‌های تکراری رو پیدا کن»."
        val (n, freed) = dupFinder.delete(lastDuplicates)
        lastDuplicates = emptyList()
        return "✅ $n فایل حذف شد.\n💾 ${fmtB(freed)} آزاد شد."
    }

    private suspend fun findJunk(): String {
        lastJunk = junk.findJunk()
        if (lastJunk.isEmpty()) return "🧹 فایل اضافی نبود."
        val total = lastJunk.sumOf { it.size }
        return buildString {
            append("🧹 ${lastJunk.size} فایل اضافی (${fmtB(total)})\n\n")
            lastJunk.take(5).forEach { append("• ${it.file.name} — ${it.reason}\n") }
            append("\nبرای حذف بگو: «اضافی‌ها رو پاک کن»")
        }
    }

    private suspend fun deleteJunk(): String {
        if (lastJunk.isEmpty()) return "اول بگو «فایل‌های اضافی رو پیدا کن»."
        val (n, freed) = junk.deleteJunk(lastJunk)
        lastJunk = emptyList()
        return "✅ $n فایل پاک شد.\n💾 ${fmtB(freed)} آزاد شد."
    }

    private suspend fun analyzeStorage(): String {
        val r = junk.analyze()
        return buildString {
            append("💾 کل: ${fmtB(r.total)}\n")
            append("• آزاد: ${fmtB(r.free)}\n")
            append("• استفاده: ${fmtB(r.used)}\n\n")
            r.byCategory.filter { it.value > 0 }
                .toList().sortedByDescending { it.second }
                .forEach { (k, v) ->
                    val pct = if (r.used > 0) (v * 100.0 / r.used).toInt() else 0
                    append("• $k: ${fmtB(v)} ($pct%)\n")
                }
        }
    }

    private fun has(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private fun fmtB(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
        b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1024.0 / 1024)
        else -> "%.2f GB".format(b / 1024.0 / 1024 / 1024)
    }

    companion object {
        val HELP = """
            می‌تونم این کارها رو بکنم:

            📞 زنگ بزن به علی
            ✉️ به مامان پیام بده که دیر می‌رسم
            📨 واتساپ به بابا بگو رسیدم
            🚀 اینستاگرام رو باز کن
            ⏰ ساعت ۱۰ آلارم بذار
            🔎 گوگل کن هوای تهران

            🛡️ ویروس‌ها رو پیدا کن
            📑 فایل‌های تکراری رو پیدا کن
            🧹 فایل‌های اضافی رو پیدا کن
            📊 چقدر فضا اشغال شده
        """.trimIndent()
    }
}
