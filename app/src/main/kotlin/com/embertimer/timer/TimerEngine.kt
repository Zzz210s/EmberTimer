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
 * 不变量:每次引擎状态迁移(含 pause/resume/onCheckpointFlushed)都刷新
 * savedAtElapsed/savedAtWall —— StateRestorer 的重启判据依赖它。
 */
class TimerEngine(
    private val time: TimeProvider,
    private val scope: CoroutineScope,
    private val persist: suspend (RuntimeSnapshot?) -> Unit,
    /** 可选的事件丢弃回调(AppGraph 注入 Log.w):引擎包保持无 Android 依赖、
     *  可在纯 JVM 测试中运行,故不直接用 android.util.Log(测试默认传 null 不回调) */
    private val onEventDropped: ((EngineEvent) -> Unit)? = null,
) {
    private val _snapshot = MutableStateFlow<RuntimeSnapshot?>(null)
    val snapshot: StateFlow<RuntimeSnapshot?> = _snapshot.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(replay = 0, extraBufferCapacity = 64)
    /**
     * 引擎事件流。replay=0:事件仅投递给已订阅的收集器 —— 订阅之前发出的变更事件
     * (含上一会话、跨服务实例的旧事件)不会重放,订阅后也不会补发;因此服务侧
     * (TimerService)必须先完成订阅握手(事件收集器挂上后才开始驱动引擎),
     * 否则事件会被静默丢弃。extraBufferCapacity=64 保证订阅者在处理前一条事件期间
     * 新到的入队不丢。原计划(Task 5)为 replay=64,Fix Round 1 改为 0:
     * 重放缓存会把旧会话的 settle/提醒事件重投给新服务实例,导致工作量重复落库。
     */
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

    fun start(profileId: Long, workMillis: Long, restMillis: Long, countUp: Boolean = false) {
        val cur = _snapshot.value
        if (cur != null && cur.status != EngineStatus.IDLE) return
        val e = el; val w = wall
        _snapshot.value = RuntimeSnapshot(
            profileId = profileId, workMillis = workMillis, restMillis = restMillis,
            phase = Phase.WORK, status = EngineStatus.RUNNING, cycleCount = 0,
            startElapsed = e, endElapsed = e + workMillis, endWall = w + workMillis,
            timeSpentPaused = 0, lastPauseTime = 0, timeAtPause = 0,
            savedAtWall = w, savedAtElapsed = e, ckptDate = null, ckptAccum = 0, countUp = countUp,
        )
        save()
        emit(EngineEvent.PhaseStarted(Phase.WORK, e + workMillis, w + workMillis))
    }

    fun pause() {
        val cur = _snapshot.value ?: return
        if (cur.status != EngineStatus.RUNNING) return
        val e = el; val w = wall
        // 暂停存“剩余”(倒计时)或“暂停时已走时长”(正计时 accrued,可超 workMillis)
        val atPause = (if (cur.countUp) (e - cur.startElapsed - cur.timeSpentPaused) else (cur.endElapsed - e)).coerceAtLeast(0)
        _snapshot.value = cur.copy(
            status = EngineStatus.PAUSED, timeAtPause = atPause, lastPauseTime = e,
            savedAtWall = w, savedAtElapsed = e,
        )
        save()
        emit(EngineEvent.Paused(atPause))
    }

    fun resume() {
        val cur = _snapshot.value ?: return
        if (cur.status != EngineStatus.PAUSED) return
        val e = el; val w = wall
        // 重锚 elapsed:冻结已走量后重设 startElapsed(重启单调钟清零时只平移 end 会坍缩/游标倒退);
        // countUp 不封顶,end=start+名义跨度(倒计时 timeAtPause=剩余,end=当前+剩余)
        val frozenAccrued = (cur.lastPauseTime - cur.startElapsed - cur.timeSpentPaused).let {
            if (cur.countUp) it.coerceAtLeast(0) else it.coerceIn(0, cur.durationMillis)
        }
        val newEnd = if (cur.countUp) e - frozenAccrued + cur.durationMillis else e + cur.timeAtPause
        val newEndWall = if (cur.countUp) w - frozenAccrued + cur.durationMillis else w + cur.timeAtPause
        _snapshot.value = cur.copy(
            status = EngineStatus.RUNNING,
            startElapsed = e - frozenAccrued,
            endElapsed = newEnd, endWall = newEndWall,
            timeSpentPaused = 0,
            lastPauseTime = 0, timeAtPause = 0,
            savedAtWall = w, savedAtElapsed = e,
        )
        save()
        emit(EngineEvent.Resumed(newEnd, newEndWall))
    }

    fun onCheckpointFlushed(date: String, accum: Long) {
        val cur = _snapshot.value ?: return
        if (cur.phase != Phase.WORK) return
        val e = el; val w = wall
        _snapshot.value = cur.copy(ckptDate = date, ckptAccum = accum, savedAtWall = w, savedAtElapsed = e)
        save()
    }

    // ---- Task 6: 阶段推进 ----
    fun onExpired() {
        val cur = _snapshot.value ?: return
        if (cur.status != EngineStatus.RUNNING) return
        if (cur.countUp) return // 正计时永不到期:不推进、不结算(Task 6 硬契约)
        if (el < cur.endElapsed) return
        finishAndAdvance(cur, settleAtElapsed = cur.endElapsed, auto = true)
    }

    fun skip() {
        val cur = _snapshot.value ?: return
        if (cur.countUp) return // 正计时 skip 为 no-op:不结算、不推进、不发事件(Task 6 硬契约)
        when (cur.status) {
            EngineStatus.RUNNING -> finishAndAdvance(cur, settleAtElapsed = el.coerceAtMost(cur.endElapsed), auto = false)
            EngineStatus.PAUSED -> finishAndAdvance(cur, settleAtElapsed = cur.lastPauseTime, auto = false)
            EngineStatus.IDLE -> Unit
        }
    }

    fun reset() {
        val cur = _snapshot.value ?: return
        val settleAt = if (cur.status != EngineStatus.RUNNING) cur.lastPauseTime
            else if (cur.countUp) el else el.coerceAtMost(cur.endElapsed) // 正计时全额结算(可超 workMillis)
        val settle = cur.settleMillis(settleAt)
        val profileId = cur.profileId
        _snapshot.value = null
        save()
        emit(EngineEvent.Reset(settle, profileId))
    }

    fun restartPhase(profileId: Long, workMillis: Long, restMillis: Long, countUp: Boolean = false) {
        val cur = _snapshot.value ?: return
        if (cur.status != EngineStatus.PAUSED) return
        val dur = if (countUp || cur.phase == Phase.WORK) workMillis else restMillis
        val e = el; val w = wall
        _snapshot.value = RuntimeSnapshot(
            profileId = profileId, workMillis = workMillis, restMillis = restMillis,
            phase = if (countUp) Phase.WORK else cur.phase, status = EngineStatus.RUNNING,
            cycleCount = cur.cycleCount, startElapsed = e, endElapsed = e + dur, endWall = w + dur,
            timeSpentPaused = 0, lastPauseTime = 0, timeAtPause = 0,
            savedAtWall = w, savedAtElapsed = e, ckptDate = null, ckptAccum = 0, countUp = countUp,
        )
        save()
        // 结算归属旧 profile:换 profile 重开时已累计工作量不跟新 profile 走
        emit(EngineEvent.PhaseRestarted(cur.phase, cur.settleMillis(cur.lastPauseTime), cur.profileId, e + dur, w + dur))
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
            savedAtWall = w, savedAtElapsed = e, ckptDate = null, ckptAccum = 0, countUp = cur.countUp,
        )
        save()
        emit(EngineEvent.PhaseFinished(cur.phase, settle, cur.profileId, next, auto))
        emit(EngineEvent.PhaseStarted(next, e + dur, w + dur))
    }

    /** 工作阶段应落库增量 = 已流逝 - 已 flush 游标(仅 WORK) */
    private fun RuntimeSnapshot.settleMillis(settleAtElapsed: Long): Long =
        if (phase == Phase.WORK) (accruedWork(settleAtElapsed) - ckptAccum).coerceAtLeast(0) else 0L

    private fun save() {
        scope.launch { runCatching { persist(_snapshot.value) } }
    }

    private fun emit(ev: EngineEvent) {
        // 缓冲溢出(64)丢弃事件:驱动路径已串行化后几乎不可达,但丢弃正是本模块要消除的
        // 静默失败,必须可观测(F7:回调注入,引擎不引入 Android 依赖)
        if (!_events.tryEmit(ev)) onEventDropped?.invoke(ev)
    }
}
