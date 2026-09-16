package com.example.mobileagent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileagent.actions.ActionExecutor
import com.example.mobileagent.agent.Command
import com.example.mobileagent.agent.Parser
import com.example.mobileagent.security.Threat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Msg(val text: String, val me: Boolean)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    val executor = ActionExecutor(app)

    private val _msgs = MutableStateFlow(
        listOf(
            Msg(
                "سلام 👋 من دستیار موبایلم.\n" +
                "«راهنما» رو بزن یا از دکمه‌های پایین استفاده کن.",
                me = false
            )
        )
    )
    val msgs: StateFlow<List<Msg>> = _msgs.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // صفحه‌ی تهدیدها
    private val _showThreats = MutableStateFlow(false)
    val showThreats: StateFlow<Boolean> = _showThreats.asStateFlow()

    private val _threats = MutableStateFlow<List<Threat>>(emptyList())
    val threats: StateFlow<List<Threat>> = _threats.asStateFlow()

    fun send(input: String) {
        if (input.isBlank() || _busy.value) return
        _msgs.value = _msgs.value + Msg(input, true)
        _busy.value = true

        viewModelScope.launch {
            val cmd = Parser.parse(input)
            val reply = try {
                executor.execute(cmd)
            } catch (e: Exception) {
                "خطا: ${e.message}"
            }
            _msgs.value = _msgs.value + Msg(reply, false)
            _busy.value = false

            // اگه اسکن ویروس بود و تهدید پیدا شد، صفحه رو باز کن
            if (cmd == Command.ScanMalware && executor.lastThreats.isNotEmpty()) {
                _threats.value = executor.lastThreats
                _showThreats.value = true
            }
        }
    }

    fun openThreats() { _showThreats.value = true }
    fun closeThreats() { _showThreats.value = false }
}
