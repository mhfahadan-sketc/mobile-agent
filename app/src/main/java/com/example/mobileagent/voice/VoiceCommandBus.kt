package com.example.mobileagent.voice

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * پل ارتباطی بین WakeWordService (که همیشه گوش می‌ده)
 * و ChatViewModel (که دستور رو اجرا می‌کنه).
 *
 * جریان:
 *  سرویس یه دستور صوتی ضبط می‌کنه → اینجا می‌فرسته
 *  ViewModel از اینجا می‌خونه → اجرا می‌کنه
 */
object VoiceCommandBus {

    /**
     * رویدادهای صوتی
     */
    sealed interface Event {
        /** کلمه‌ی کلیدی شنیده شد */
        data object WakeWordDetected : Event

        /** یه دستور کامل ضبط شد */
        data class CommandRecognized(val text: String) : Event

        /** ضبط شروع شد */
        data object Listening : Event

        /** ضبط تموم شد */
        data object Idle : Event

        /** خطا */
        data class Error(val message: String) : Event
    }

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    fun emit(event: Event) {
        _events.tryEmit(event)
    }
}
