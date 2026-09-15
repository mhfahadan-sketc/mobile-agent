package com.example.mobileagent.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Msg(val text: String, val me: Boolean)

class ChatViewModel : ViewModel() {

    private val _msgs = MutableStateFlow(
        listOf(
            Msg("سلام 👋 من دستیار موبایلم.\nبرای دیدن دستورها بگو «راهنما».", me = false)
        )
    )
    val msgs: StateFlow<List<Msg>> = _msgs.asStateFlow()

    fun send(input: String) {
        if (input.isBlank()) return
        _msgs.value = _msgs.value + Msg(input, true)
        val reply = respond(input)
        _msgs.value = _msgs.value + Msg(reply, false)
    }

    private fun respond(input: String): String {
        val s = input.trim()
        return when {
            s.contains("سلام") -> "سلام! 👋 چطوری؟"
            s.contains("خوبی") -> "من خوبم، ممنون! تو چطوری؟"
            s.contains("راهنما") -> HELP
            s.contains("ممنون") || s.contains("مرسی") -> "خواهش می‌کنم 🙏"
            else -> "فعلاً فقط می‌تونم چت کنم 🤔\n«راهنما» رو بزن."
        }
    }

    companion object {
        val HELP = """
            فعلاً فقط می‌تونم چت کنم. ولی به‌زودی می‌تونم:
            📞 زنگ بزنم
            ✉️ پیام بفرستم
            🛡️ ویروس پیدا کنم
            📑 فایل تکراری پاک کنم
            🧹 پاکسازی کنم
            🌐 VPN وصل کنم
        """.trimIndent()
    }
}
