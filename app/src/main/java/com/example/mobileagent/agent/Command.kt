package com.example.mobileagent.agent

sealed interface Command {
    data class Call(val contact: String) : Command
    data class Sms(val contact: String, val body: String) : Command
    data class WhatsApp(val contact: String, val body: String?) : Command
    data class OpenApp(val name: String) : Command
    data class WebSearch(val query: String) : Command
    data class OpenUrl(val url: String) : Command
    data class SetAlarm(val hour: Int, val minute: Int) : Command

    data object ScanMalware : Command
    data object FindDuplicates : Command
    data object DeleteDuplicates : Command
    data object FindJunk : Command
    data object DeleteJunk : Command
    data object AnalyzeStorage : Command

    data object Help : Command
    data object Cancel : Command
    data class Unknown(val original: String) : Command
}
