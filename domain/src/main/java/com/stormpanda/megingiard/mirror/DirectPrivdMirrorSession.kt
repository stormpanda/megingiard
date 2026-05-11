package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "DirectPrivdMirror"

class DirectPrivdMirrorSession(
    private val width: Int,
    private val height: Int,
) {
    enum class State { IDLE, STARTING, RUNNING, STOPPED, FAILED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stopping = false

    suspend fun start(): Boolean {
        if (_state.value != State.IDLE) return _state.value == State.RUNNING
        _state.value = State.STARTING
        AppLog.i(TAG, "start() direct app surface ${width}x${height}")

        val ok = PrivdClient.startDirectMirror(width, height)
        if (!ok) {
            AppLog.w(TAG, "daemon does not support direct surface mirror yet")
            _state.value = State.FAILED
            return false
        }

        _state.value = State.RUNNING
        AppLog.i(TAG, "direct surface session running")
        return true
    }

    fun stop() {
        if (stopping || _state.value == State.STOPPED || _state.value == State.IDLE) return
        val shouldStopRemote = _state.value == State.RUNNING
        stopping = true
        AppLog.i(TAG, "stop()")
        if (shouldStopRemote) {
            scope.launch { runCatching { PrivdClient.stopMirror() } }
        }
        _state.value = State.STOPPED
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
