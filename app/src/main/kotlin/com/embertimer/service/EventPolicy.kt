package com.embertimer.service

import com.embertimer.timer.EngineEvent
import com.embertimer.timer.Phase
import com.embertimer.timer.RuntimeSnapshot

/** 服务侧对单个引擎事件的反应(纯决策层,无 Android 依赖,可 JVM 直测) */
sealed interface EventEffect {
    /** settle 落库:millis = 工作增量;profileId = 归属 profile,一律取事件携带值 */
    data class Settle(val millis: Long, val profileId: Long) : EventEffect
    data class Arm(val endElapsed: Long) : EventEffect
    data object CancelAlarm : EventEffect
    data object ForceCheckpoint : EventEffect
    /** auto 结束提醒;fresh = 发射时的后继快照仍然在位 */
    data class Remind(val workFinished: Boolean) : EventEffect
}

/**
 * 引擎事件 -> 服务反应 的纯决策层(风格对应 Reconciler/Checkpointer)。
 *
 * settle 一律归属事件携带的 profileId(Fix Round 1 Concern 3 / Fix Round 2 F2):
 * 事件发出与收集器处理之间,排队中的 RESET/restartPhase 可能已清空或替换快照,
 * 事后读快照会错归属或整个丢 settle。
 *
 * Remind 门控(F2):engineMutex FIFO 保证事件收集器先于引擎进一步推进处理
 * PhaseFinished,正常流下 successorSnapshot.phase == ev.next 恒成立;若 RESET
 * 先赢得锁,快照已为 null(或阶段不符),门控抑制这次过期提醒(幻影提醒)。
 *
 * settleMillis <= 0 不产生 Settle 效果(决策层直接省略;flushSettle 的同款守卫
 * 保留作纵深防御,pin 见 EventPolicyTest)。
 */
object EventPolicy {
    fun decide(ev: EngineEvent, successorSnapshot: RuntimeSnapshot?): List<EventEffect> = when (ev) {
        is EngineEvent.PhaseStarted -> listOf(
            EventEffect.Arm(ev.endElapsed),
            EventEffect.ForceCheckpoint,
        )
        is EngineEvent.Resumed -> listOf(EventEffect.Arm(ev.endElapsed))
        is EngineEvent.Paused -> listOf(EventEffect.CancelAlarm)
        is EngineEvent.PhaseFinished -> buildList {
            add(EventEffect.CancelAlarm)
            settle(ev.settleMillis, ev.profileId)?.let(::add)
            if (ev.auto && successorSnapshot?.phase == ev.next) {
                add(EventEffect.Remind(ev.finished == Phase.WORK))
            }
        }
        is EngineEvent.PhaseRestarted -> buildList {
            add(EventEffect.CancelAlarm)
            settle(ev.settleMillis, ev.profileId)?.let(::add)
            add(EventEffect.Arm(ev.endElapsed))
        }
        is EngineEvent.Reset -> buildList {
            add(EventEffect.CancelAlarm)
            settle(ev.settleMillis, ev.profileId)?.let(::add)
        }
    }

    /** settle<=0 无落库意义,不产生效果(幂等性:空效果即零副作用) */
    private fun settle(millis: Long, profileId: Long): EventEffect.Settle? =
        if (millis > 0) EventEffect.Settle(millis, profileId) else null
}
