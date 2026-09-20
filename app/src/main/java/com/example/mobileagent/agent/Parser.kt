package com.example.mobileagent.agent

object Parser {
    private const val FA = "۰۱۲۳۴۵۶۷۸۹"
    private const val AR = "٠١٢٣٤٥٦٧٨٩"

    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val f = FA.indexOf(ch)
            val a = AR.indexOf(ch)
            when {
                f >= 0 -> sb.append('0' + f)
                a >= 0 -> sb.append('0' + a)
                ch == 'ي' -> sb.append('ی')
                ch == 'ك' -> sb.append('ک')
                ch == '\u200c' -> sb.append(' ')
                ch == '\u200e' || ch == '\u200f' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    fun parse(raw: String): Command {
        val s = normalize(raw)
        if (s.isBlank()) return Command.Unknown(raw)

        if (Regex("^(راهنما|کمک|چیکار میتونی|دستورها|help)$",
                RegexOption.IGNORE_CASE).containsMatchIn(s)) return Command.Help
        if (Regex("^(کنسل|انصراف|بی ?خیال|لغو|نه)$").containsMatchIn(s)) return Command.Cancel

        // ═══════ امنیت
        if (Regex("(ویروس|بدافزار|مالور|malware|اسکن امنیتی|جاسوس|spy|امنیت)",
                RegexOption.IGNORE_CASE).containsMatchIn(s)) {
            return Command.ScanMalware
        }

        // ═══════ فایل تکراری
        if (Regex("(تکراری|دوبار|dup).*(پاک|حذف)").containsMatchIn(s) ||
            Regex("(پاک|حذف).*(تکراری|دوبار)").containsMatchIn(s)) {
            return Command.DeleteDuplicates
        }
        if (Regex("(تکراری|دوبار|dup).*(فایل|عکس|فیلم|پیدا|بگرد|نشون|ببین)")
                .containsMatchIn(s) ||
            Regex("(فایل|عکس|فیلم).*(تکراری|دوبار)").containsMatchIn(s)) {
            return Command.FindDuplicates
        }

        // ═══════ آشغال
        if (Regex("(اضافی|آشغال|junk|بیخود|بیخودی|هدر).*(پاک|حذف|پیدا|بگرد|نشون)")
                .containsMatchIn(s) ||
            Regex("(پاک|حذف).*(اضافی|آشغال|junk)").containsMatchIn(s)) {
            return if (Regex("(پاک|حذف)").containsMatchIn(s)) Command.DeleteJunk
            else Command.FindJunk
        }

        // ═══════ تحلیل حافظه
        if (Regex("(چقدر فضا|چی فضا|فضای خالی|حافظه|چقدر حجم|چی پره|گوشیم چطوره|آنالیز|تحلیل فضا)")
                .containsMatchIn(s)) {
            return Command.AnalyzeStorage
        }

        // ═══════ آلارم
        parseAlarm(s)?.let { return it }

        // ═══════ جستجو
        parseSearch(s)?.let { return it }

        // ═══════ تماس
        Regex("^زنگ بزن(?:\\s+به)?\\s+(.+)$").find(s)?.let {
            return Command.Call(cleanContact(it.groupValues[1]))
        }
        Regex("^به\\s+(.+?)\\s+زنگ بزن$").find(s)?.let {
            return Command.Call(cleanContact(it.groupValues[1]))
        }
        Regex("^(?:با\\s+)?(.+?)\\s+تماس بگیر$").find(s)?.let {
            return Command.Call(cleanContact(it.groupValues[1]))
        }
        Regex("^(?:به\\s+)?(.+?)\\s+زنگ بزن$").find(s)?.let {
            return Command.Call(cleanContact(it.groupValues[1]))
        }

        // ═══════ واتساپ
        if (Regex("(واتساپ|واتس اپ|whatsapp)", RegexOption.IGNORE_CASE).containsMatchIn(s)) {
            val contact = Regex("(?:به|با)\\s+(.+?)\\s+(?:پیام|بگو|بنویس|بزن|چت)")
                .find(s)?.groupValues?.get(1)?.trim()
            val body = Regex("(?:که|بگو|بنویس)\\s+(.+)$")
                .find(s)?.groupValues?.get(1)?.trim()
            if (contact != null) return Command.WhatsApp(cleanContact(contact), body)
            return Command.WhatsApp(s, null)
        }

        // ═══════ پیامک
        Regex("^به\\s+(.+?)\\s+(?:پیام|پیامک|اس ?ام ?اس)\\s+(?:بده|بزن)(?:\\s+که)?\\s*(.*)$")
            .find(s)?.let {
                return Command.Sms(
                    cleanContact(it.groupValues[1]),
                    it.groupValues[2].trim().ifEmpty { "سلام" }
                )
            }
        Regex("^(?:پیام|پیامک)\\s+(?:به\\s+)?(.+?)\\s+(?:بده|بفرست)(?:\\s+که)?\\s*(.*)$")
            .find(s)?.let {
                return Command.Sms(
                    cleanContact(it.groupValues[1]),
                    it.groupValues[2].trim().ifEmpty { "سلام" }
                )
            }

        // ═══════ URL
        Regex("https?://\\S+|[a-z0-9-]+\\.(?:com|ir|org|net|io)\\S*",
                RegexOption.IGNORE_CASE)
            .find(s)?.let { return Command.OpenUrl(it.value) }

        // ═══════ باز کردن اپ
        Regex("^(.+?)\\s*(?:رو|را)?\\s*(?:باز کن|اجرا کن|بازش کن|باز کن برام)$").find(s)?.let {
            val app = it.groupValues[1].trim()
            if (app.isNotEmpty() && !app.contains("آلارم") && !app.contains("زنگ"))
                return Command.OpenApp(app)
        }
        Regex("^(?:باز کن|اجرا کن|بازش کن)\\s+(.+)$").find(s)?.let {
            return Command.OpenApp(it.groupValues[1].trim())
        }

        return Command.Unknown(s)
    }

    private fun parseAlarm(s: String): Command? {
        // کلمه‌ی آلارم؟
        val hasAlarmWord = Regex("(آلارم|زنگ|یادآوری|هشدار|reminder|alarm)",
                RegexOption.IGNORE_CASE).containsMatchIn(s)
        if (!hasAlarmWord) return null

        // ⭐ فرمت ۱: «ساعت 7 و 30 دقیقه»
        Regex("ساعت\\s*(\\d{1,2})\\s*(?:و|و\\s*)\\s*(\\d{1,2})\\s*دقیقه").find(s)?.let {
            val h = it.groupValues[1].toIntOrNull() ?: return null
            val m = it.groupValues[2].toIntOrNull() ?: return null
            if (h in 0..23 && m in 0..59) return Command.SetAlarm(h, m)
        }

        // ⭐ فرمت ۲: «ساعت X:Y» یا «X:Y»
        Regex("(\\d{1,2}):(\\d{1,2})").find(s)?.let {
            val h = it.groupValues[1].toIntOrNull() ?: return null
            val m = it.groupValues[2].toIntOrNull() ?: return null
            if (h in 0..23 && m in 0..59) return Command.SetAlarm(h, m)
        }

        // ⭐ فرمت ۳: «X و نیم» → X:30
        Regex("ساعت\\s*(\\d{1,2})\\s*و\\s*نیم").find(s)?.let {
            val h = it.groupValues[1].toIntOrNull() ?: return null
            if (h in 0..23) return Command.SetAlarm(h, 30)
        }

        // ⭐ فرمت ۴: «X و ربع» → X:15
        Regex("ساعت\\s*(\\d{1,2})\\s*و\\s*ربع").find(s)?.let {
            val h = it.groupValues[1].toIntOrNull() ?: return null
            if (h in 0..23) return Command.SetAlarm(h, 15)
        }

        // ⭐ فرمت ۵: «X صبح/عصر/شب/ظهر»
        Regex("ساعت\\s*(\\d{1,2})\\s*(صبح|عصر|شب|ظهر|بعد از ظهر|بعدازظهر)").find(s)?.let {
            var h = it.groupValues[1].toIntOrNull() ?: return null
            val when_ = it.groupValues[2]
            if (h in 1..12) {
                h = when {
                    when_ == "صبح" -> if (h == 12) 0 else h
                    when_.contains("ظهر") && !when_.contains("بعد") -> if (h < 12) h + 12 else h
                    when_ == "عصر" || when_.contains("بعد") -> if (h < 12) h + 12 else h
                    when_ == "شب" -> if (h < 12) h + 12 else h
                    else -> h
                }
                return Command.SetAlarm(h.coerceIn(0, 23), 0)
            }
        }

        // ⭐ فرمت ۶: «ساعت X» (بدون دقیقه)
        Regex("ساعت\\s*(\\d{1,2})(?:\\s|$)").find(s)?.let {
            val h = it.groupValues[1].toIntOrNull() ?: return null
            if (h in 0..23) return Command.SetAlarm(h, 0)
        }

        return null
    }

    private fun parseSearch(s: String): Command? {
        val patterns = listOf(
            Regex("^(?:جستجو کن|سرچ کن|گوگل کن|بگرد دنبال)\\s+(.+)$"),
            Regex("^(?:جستجو|سرچ|گوگل)\\s+(.+)$"),
            Regex("^(.+?)\\s+(?:رو\\s+)?(?:جستجو کن|سرچ کن|گوگل کن|بگرد)$"),
            Regex("^(.+?)\\s+رو\\s+جستجو کن$")
        )
        for (p in patterns) {
            p.find(s)?.let {
                val q = it.groupValues[1].trim()
                if (q.length >= 2) return Command.WebSearch(q)
            }
        }
        Regex("^(?:هوای|آب و هوای)\\s+(.+)$").find(s)?.let {
            return Command.WebSearch("هوای ${it.groupValues[1].trim()}")
        }
        return null
    }

    private fun cleanContact(s: String): String = s
        .replace(Regex("^(?:به|با|از)\\s+"), "")
        .replace(Regex("\\s+(?:رو|را|به)$"), "")
        .trim()
}
