package com.example.mobileagent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobileagent.actions.ActionExecutor
import com.example.mobileagent.agent.Command
import com.example.mobileagent.agent.LlmAgent
import com.example.mobileagent.agent.Parser
import com.example.mobileagent.security.Threat
import com.example.mobileagent.voice.VoiceCommandBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Msg(val text: String, val me: Boolean)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    val executor = ActionExecutor(app)
    private val llm = LlmAgent()

    private val _msgs = MutableStateFlow(
        listOf(
            Msg(
                "سلام 👋 من دستیار موبایلم.\n" +
                "می‌تونی هر جوری که راحتی حرف بزنی — می‌فهمم.\n" +
                "یا از دکمه‌های پایین استفاده کن.",
                me = false
            )
        )
    )
    val msgs: StateFlow<List<Msg>> = _msgs.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _showThreats = MutableStateFlow(false)
    val showThreats: StateFlow<Boolean> = _showThreats.asStateFlow()

    private val _threats = MutableStateFlow<List<Threat>>(emptyList())
    val threats: StateFlow<List<Threat>> = _threats.asStateFlow()

    // برای صفحه‌ی تنظیمات صدا
    private val _showVoiceSettings = MutableStateFlow(false)
    val showVoiceSettings: StateFlow<Boolean> = _showVoiceSettings.asStateFlow()

    // آخرین هشدار صوتی که نشون داده شد
    private val _voiceStatus = MutableStateFlow<String?>(null)
    val voiceStatus: StateFlow<String?> = _voiceStatus.asStateFlow()

    init {
        // گوش دادن به رویدادهای سرویس صدا
        viewModelScope.launch {
            VoiceCommandBus.events.collect { event ->
                when (event) {
                    is VoiceCommandBus.Event.WakeWordDetected -> {
                        _voiceStatus.value = "🎤 بله؟"
                    }
                    is VoiceCommandBus.Event.CommandRecognized -> {
                        _voiceStatus.value = null
                        // دستور ضبط‌شده رو خودکار بفرست
                        send(event.text, fromVoice = true)
                    }
                    is VoiceCommandBus.Event.Listening -> {
                        _voiceStatus.value = null
                    }
                    is VoiceCommandBus.Event.Idle -> {
                        _voiceStatus.value = null
                    }
                    is VoiceCommandBus.Event.Error -> {
                        _voiceStatus.value = "خطای صدا: ${event.message}"
                    }
                }
            }
        }
    }

    fun send(input: String, fromVoice: Boolean = false) {
        if (input.isBlank() || _busy.value) return

        // اگه از صوت اومده، یه علامت کوچیک بذار
        val displayText = if (fromVoice) "🎤 $input" else input
        _msgs.value = _msgs.value + Msg(displayText, true)
        _busy.value = true

        viewModelScope.launch {
            try {
                // ۱) اول LLM
                val history = _msgs.value
                    .dropLast(1)
                    .takeLast(6)
                    .map { it.text to it.me }

                val result = llm.parse(input, history)

                when (result) {
                    is LlmAgent.Result.Cmd -> {
                        val reply = try {
                            executor.execute(result.command)
                        } catch (e: Exception) {
                            "خطا در اجرا: ${e.message}"
                        }
                        _msgs.value = _msgs.value + Msg(reply, false)

                        if (result.command == Command.ScanMalware &&
                            executor.lastThreats.isNotEmpty()
                        ) {
                            _threats.value = executor.lastThreats
                            _showThreats.value = true
                        }
                    }

                    is LlmAgent.Result.Chat -> {
                        _msgs.value = _msgs.value + Msg(result.text, false)
                    }

                    is LlmAgent.Result.Error -> {
                        val fallback = localFallback(input)
                        _msgs.value = _msgs.value + Msg(fallback, false)
                    }
                }
            } catch (e: Exception) {
                _msgs.value = _msgs.value + Msg("خطا: ${e.message}", false)
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun localFallback(input: String): String {
        val cmd = Parser.parse(input)
        val reply = try {
            executor.execute(cmd)
        } catch (e: Exception) {
            "خطا: ${e.message}"
        }

        if (cmd == Command.ScanMalware && executor.lastThreats.isNotEmpty()) {
            _threats.value = executor.lastThreats
            _showThreats.value = true
        }

        return "$reply\n\n(AI در دسترس نبود، از حالت ساده استفاده شد)"
    }

    fun openThreats() { _showThreats.value = true }
    fun closeThreats() { _showThreats.value = false }

    fun openVoiceSettings() { _showVoiceSettings.value = true }
    fun closeVoiceSettings() { _showVoiceSettings.value = false }
}
