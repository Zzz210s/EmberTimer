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
        // 重锚 elapsed 时钟:先冻结已累计的工作量,再重设 startElapsed。
        // 设备重启后旧 startElapsed/lastPauseTime 锚点已失效(单调钟清零),若只平移 end,
        // accruedWork 会坍缩向 0,导致 ckptAccum 游标倒退、已落库工作量被重复计数。
        val frozenAccrued = (cur.lastPauseTime - cur.startElapsed - cur.timeSpentPaused)
            .coerceIn(0, cur.durationMillis)
        val newEnd = e + cur.timeAtPause
        val newEndWall = w + cur.timeAtPause
        _snapshot.value = cur.copy(
            status = EngineStatus.RUNNING,
            startElapsed = e - frozenAccrued,
            endElapsed = newEnd, endWall = newEndWall,
            timeSpentPaused = 0,
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

    // ---- Task 6: 阶段推进 ----
    fun onExpired() {
        val cur = _snapshot.value ?: return
        if (cur.status != EngineStatus.RUNNING) return
        if (el < cur.endElapsed) return
        finishAndAdvance(cur, settleAtElapsed = cur.endElapsed, auto = true)
    }

    fun skip() {
        val cur = _snapshot.value ?: return
        when (cur.status) {
            EngineStatus.RUNNING -> finishAndAdvance(cur, settleAtElapsed = el.coerceAtMost(cur.endElapsed), auto = false)
            EngineStatus.PAUSED -> finishAndAdvance(cur, settleAtElapsed = cur.lastPauseTime, auto = false)
            EngineStatus.IDLE -> Unit
        }
    }

    fun reset() {
        val cur = _snapshot.value ?: return
        val settleAt = if (cur.status == EngineStatus.RUNNING) el.coerceAtMost(cur.endElapsed) else cur.lastPauseTime
        val settle = cur.settleMillis(settleAt)
        val profileId = cur.profileId
        _snapshot.value = null
        save()
        emit(EngineEvent.Reset(settle, profileId))
    }

    fun restartPhase(profileId: Long, workMillis: Long, restMillis: Long) {
        val cur = _snapshot.value ?: return
        if (cur.status != EngineStatus.PAUSED) return
        val settle = cur.settleMillis(cur.lastPauseTime)
        val dur = if (cur.phase == Phase.WORK) workMillis else restMillis
        val e = el; val w = wall
        _snapshot.value = RuntimeSnapshot(
            profileId = profileId, workMillis = workMillis, restMillis = restMillis,
            phase = cur.phase, status = EngineStatus.RUNNING, cycleCount = cur.cycleCount,
            startElapsed = e, endElapsed = e + dur, endWall = w + dur,
            timeSpentPaused = 0, lastPauseTime = 0, timeAtPause = 0,
            savedAtWall = w, savedAtElapsed = e, ckptDate = null, ckptAccum = 0,
        )
        save()
        emit(EngineEvent.PhaseRestarted(cur.phase, settle, e + dur, w + dur))
    }

    private fun finishAndAdvance(cur: RuntimeSnapshot, settleAtElapsed: Long, auto: Boolean) {
        val settle = cur.settleMillis(settleAtElapsed)
        val next = if (cur.phase == Phase.WORK) Phase.REST else Phase.WORK
        val cycle = if (next == Phase.WORK) cur.cycleCount + 1 else cur.cycleCount
        val dur = if (next == Phase.WORK) cur.workMillis else cur.restMillis
        val e = el; val w = wall
        _snapshot.value = RuntimeSnapshot(
            profileId = cur.profileId, workMillis = cur.workMillis, restMillis = cur.restMillis,
            phase = next, status = EngineStatus.RUNNING, cycleCount = cycle,
            startElapsed = e, endElapsed = e + dur, endWall = w + dur,
            timeSpentPaused = 0, lastPauseTime = 0, timeAtPause = 0,
            savedAtWall = w, savedAtElapsed = e, ckptDate = null, ckptAccum = 0,
        )
        save()
        emit(EngineEvent.PhaseFinished(cur.phase, settle, next, auto))
        emit(EngineEvent.PhaseStarted(next, e + dur, w + dur))
    }

    /** 工作阶段应落库增量 = 已流逝 - 已 flush 游标(仅 WORK) */
    private fun RuntimeSnapshot.settleMillis(settleAtElapsed: Long): Long =
        if (phase == Phase.WORK) (accruedWork(settleAtElapsed) - ckptAccum).coerceAtLeast(0) else 0L

    private fun save() {
        scope.launch { runCatching { persist(_snapshot.value) } }
    }

    private fun emit(ev: EngineEvent) {
        _events.tryEmit(ev)
    }
}
