package com.universalaiagent.automation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object AgentRuntime {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _stopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val stopRequests: SharedFlow<Unit> = _stopRequests.asSharedFlow()

    fun setActive(active: Boolean) {
        _active.value = active
    }

    fun requestStop() {
        _stopRequests.tryEmit(Unit)
    }

    fun isActive(): Boolean = _active.value
}
