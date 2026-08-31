package com.embertimer.timer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 纯 Kotlin 事件驱动计时状态机。剩余时间恒由 endElapsed 推算,不维护 tick 权威计数。
 * 每次状态迁移后调用 persist(异步,scope 内)。
 */
class TimerEngine(
    private val time: TimeProvider,
    private val scope: CoroutineScope,
    private val persist: suspend (RuntimeSnapshot?) -> Unit,
) {
    private val _snapshot = MutableStateFlow<RuntimeSnapshot?>(null)
    val snapshot: StateFlow<RuntimeSnapshot?> = _snapshot.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(replay = 64, extraBufferCapacity = 64)
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    private val el get() = time.elapsedRealtime()
    private val wall get() = time.now()

    suspend fun awaitReady() { ready.first { it } }

    /** 应用启动时从持久化恢复;之后置 ready */
    suspend fun restore(s: RuntimeSnapshot?) {
        _snapshot.value = s
        _ready.value = true
    }

    /** BOOT 后经 StateRestorer 换算的新快照,直接采纳 */
    fun adoptRestored(s: RuntimeSnapshot) {
        _snapshot.value = s
        save()
    }

    fun start(profileId: Long, workMillis: Long, restMillis: Long) {
        val cur = _snapshot.value
        if (cur != null && cur.status != EngineStatus.IDLE) return
        val e = el; val w = wall
        _snapshot.value = RuntimeSnapshot(
            profileId = profileId, workMillis = workMillis, restMillis = restMillis,
            phase = Phase.WORK, status = EngineStatus.RUNNING, cycleCount = 0,
            startElapsed = e, endElapsed = e + workMillis, endWall = w + workMillis,
            timeSpentPaused = 0, lastPauseTime = 0, timeAtPause = 0,
            savedAtWall = w, savedAtElapsed = e, ckptDate = null, ckptAccum = 0,
        )
        save()
        emit(EngineEvent.PhaseStarted(Phase.WORK, e + workMillis, w + workMillis))
    }

    fun pause() {
        val cur = _snapshot.value ?: return
        if (cur.status != EngineStatus.RUNNING) return
        val e = el
        val atPause = (cur.endElapsed - e).coerceAtLeast(0)
        _snapshot.value = cur.copy(status = EngineStatus.PAUSED, timeAtPause = atPause, lastPauseTime = e)
        save()
        emit(EngineEvent.Paused(atPause))
    }

    fun resume() {
        val cur = _snapshot.value ?: return
        if (cur.status != EngineStatus.PAUSED) return
        val e = el; val w = wall
        val pausedFor = (e - cur.lastPauseTime).coerceAtLeast(0)
        val newEnd = e + cur.timeAtPause
        val newEndWall = w + cur.timeAtPause
        _snapshot.value = cur.copy(
            status = EngineStatus.RUNNING,
            endElapsed = newEnd, endWall = newEndWall,
            timeSpentPaused = cur.timeSpentPaused + pausedFor,
            lastPauseTime = 0, timeAtPause = 0,
        )
        save()
        emit(EngineEvent.Resumed(newEnd, newEndWall))
    }

    fun onCheckpointFlushed(date: String, accum: Long) {
        val cur = _snapshot.value ?: return
        if (cur.phase != Phase.WORK) return
        _snapshot.value = cur.copy(ckptDate = date, ckptAccum = accum)
        save()
    }

    // ---- Task 6 填充 ----
    fun onExpired() { /* Task 6 */ }
    fun skip() { /* Task 6 */ }
    fun reset() { /* Task 6 */ }
    fun restartPhase(profileId: Long, workMillis: Long, restMillis: Long) { /* Task 6 */ }

    private fun save() {
        scope.launch { runCatching { persist(_snapshot.value) } }
    }

    private fun emit(ev: EngineEvent) {
        _events.tryEmit(ev)
    }
}
