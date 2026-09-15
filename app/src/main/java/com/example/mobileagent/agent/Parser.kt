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

        Regex("^زنگ بزن(?:\\s+به)?\\s+(.+)$").find(s)?.let {
            return Command.Call(cleanContact(it.groupValues[1]))
        }
        Regex("^به\\s+(.+?)\\s+زنگ بزن$").find(s)?.let {
            return Command.Call(cleanContact(it.groupValues[1]))
        }
        Regex("^(?:با\\s+)?(.+?)\\s+تماس بگیر$").find(s)?.let {
            return Command.Call(cleanContact(it.groupValues[1]))
        }

        if (Regex("(واتساپ|واتس اپ|whatsapp)", RegexOption.IGNORE_CASE).containsMatchIn(s)) {
            val contact = Regex("(?:به|با)\\s+(.+?)\\s+(?:پیام|بگو|بنویس|بزن|چت)")
                .find(s)?.groupValues?.get(1)?.trim()
            val body = Regex("(?:که|بگو|بنویس)\\s+(.+)$")
                .find(s)?.groupValues?.get(1)?.trim()
            if (contact != null) return Command.WhatsApp(cleanContact(contact), body)
            return Command.WhatsApp(s, null)
        }

        Regex("^به\\s+(.+?)\\s+(?:پیام|پیامک|اس ?ام ?اس)\\s+(?:بده|بزن)(?:\\s+که)?\\s*(.*)$")
            .find(s)?.let {
                return Command.Sms(
                    cleanContact(it.groupValues[1]),
                    it.groupValues[2].trim().ifEmpty { "سلام" }
                )
            }

        Regex("(?:ساعت|برای ساعت)\\s*(\\d{1,2})(?::(\\d{1,2}))?\\s*(?:آلارم|زنگ|یادآوری)")
            .find(s)?.let {
                val h = it.groupValues[1].toIntOrNull() ?: 7
                val m = it.groupValues[2].toIntOrNull() ?: 0
                return Command.SetAlarm(h.coerceIn(0, 23), m.coerceIn(0, 59))
            }

        Regex("^(?:جستجو کن|سرچ کن|گوگل کن)\\s+(.+)$").find(s)?.let {
            return Command.WebSearch(it.groupValues[1].trim())
        }

        Regex("https?://\\S+|[a-z0-9-]+\\.(?:com|ir|org|net|io)\\S*",
                RegexOption.IGNORE_CASE)
            .find(s)?.let { return Command.OpenUrl(it.value) }

        Regex("^(.+?)\\s*(?:رو|را)?\\s*(?:باز کن|اجرا کن|بازش کن)$").find(s)?.let {
            val app = it.groupValues[1].trim()
            if (app.isNotEmpty()) return Command.OpenApp(app)
        }
        Regex("^(?:باز کن|اجرا کن|بازش کن)\\s+(.+)$").find(s)?.let {
            return Command.OpenApp(it.groupValues[1].trim())
        }

        return Command.Unknown(s)
    }

    private fun cleanContact(s: String): String = s
        .replace(Regex("^(?:به|با|از)\\s+"), "")
        .replace(Regex("\\s+(?:رو|را|به)$"), "")
        .trim()
}
