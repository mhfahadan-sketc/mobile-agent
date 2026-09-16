package com.example.mobileagent.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface ScanProgress {
    data class Scanning(
        val current: Int,
        val total: Int,
        val label: String,
        val phase: String = ""
    ) : ScanProgress

    data class Done(
        val summary: String,
        val itemCount: Int
    ) : ScanProgress
}

object ProgressChannel {
    private val _progress = MutableSharedFlow<ScanProgress>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val progress = _progress.asSharedFlow()

    suspend fun emit(p: ScanProgress) {
        _progress.emit(p)
    }
}
