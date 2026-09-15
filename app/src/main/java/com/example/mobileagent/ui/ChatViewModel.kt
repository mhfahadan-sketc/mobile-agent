package com.example.mobileagent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.mobileagent.actions.ActionExecutor
import com.example.mobileagent.agent.Parser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Msg(val text: String, val me: Boolean)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val executor = ActionExecutor(app)

    private val _msgs = MutableStateFlow(
        listOf(
            Msg("سلام 👋 من دستیار موبایلم.\nبرای دیدن دستورها بگو «راهنما».", me = false)
        )
    )
    val msgs: StateFlow<List<Msg>> = _msgs.asStateFlow()

    fun send(input: String) {
        if (input.isBlank()) return
        _msgs.value = _msgs.value + Msg(input, true)
        val reply = try {
            val cmd = Parser.parse(input)
            executor.execute(cmd)
        } catch (e: Exception) {
            "خطا: ${e.message}"
        }
        _msgs.value = _msgs.value + Msg(reply, false)
    }
}
