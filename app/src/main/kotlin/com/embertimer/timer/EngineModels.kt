package com.embertimer.timer

enum class Phase { WORK, REST }

enum class EngineStatus { IDLE, RUNNING, PAUSED }

/**
 * 计时引擎的完整运行时状态。时间语义:
 * - *Elapsed 字段基于 SystemClock.elapsedRealtime()(单调,重启清零)
 * - *Wall 字段基于 System.currentTimeMillis()(重启换算用)
 * - status == IDLE 时引擎快照为 null,本枚举值仅用于 UI 映射
 */
data class RuntimeSnapshot(
    val profileId: Long,
    val workMillis: Long,
    val restMillis: Long,
    val phase: Phase,
    val status: EngineStatus,
    val cycleCount: Int,
    val startElapsed: Long,
    val endElapsed: Long,
    val endWall: Long,
    val timeSpentPaused: Long,
    val lastPauseTime: Long,
    val timeAtPause: Long,
    val savedAtWall: Long,
    val savedAtElapsed: Long,
    val ckptDate: String?,
    val ckptAccum: Long,
) {
    val durationMillis: Long get() = if (phase == Phase.WORK) workMillis else restMillis

    fun remaining(nowElapsed: Long): Long = when (status) {
        EngineStatus.RUNNING -> (endElapsed - nowElapsed).coerceAtLeast(0)
        EngineStatus.PAUSED -> timeAtPause
        EngineStatus.IDLE -> 0L
    }

    /** 本阶段(仅 WORK)已流逝的工作毫秒,扣除暂停 */
    fun accruedWork(nowElapsed: Long): Long = when {
        phase == Phase.WORK && status == EngineStatus.RUNNING ->
            (nowElapsed - startElapsed - timeSpentPaused).coerceIn(0, workMillis)
        phase == Phase.WORK && status == EngineStatus.PAUSED ->
            (lastPauseTime - startElapsed - timeSpentPaused).coerceIn(0, workMillis)
        else -> 0L
    }
}

sealed interface EngineEvent {
    data class PhaseStarted(val phase: Phase, val endElapsed: Long, val endWall: Long) : EngineEvent
    /** settleMillis = 待落库的工作增量(已扣除 checkpoint 游标) */
    data class PhaseFinished(val finished: Phase, val settleMillis: Long, val next: Phase, val auto: Boolean) : EngineEvent
    /**
     * settleMillis = 待落库的工作增量(已扣除 checkpoint 游标);
     * profileId = 结算归属的 profile —— 重启前快照的 profileId。restartPhase 可换 profile,
     * 已累计的工作量仍归属旧 profile,携带方式镜像 Reset(事件发出时快照已指向新 profile)。
     */
    data class PhaseRestarted(val phase: Phase, val settleMillis: Long, val profileId: Long, val endElapsed: Long, val endWall: Long) : EngineEvent
    data class Paused(val timeAtPause: Long) : EngineEvent
    data class Resumed(val endElapsed: Long, val endWall: Long) : EngineEvent
    /** reset 后快照已清空,事件必须自带 profileId 供 settle 落库 */
    data class Reset(val settleMillis: Long, val profileId: Long) : EngineEvent
}
