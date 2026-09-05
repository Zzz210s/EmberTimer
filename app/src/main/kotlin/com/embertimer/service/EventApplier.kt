package com.embertimer.service

import com.embertimer.di.AppGraph
import com.embertimer.timer.EngineEvent
import com.embertimer.service.EventPolicy

/** v1.3 #6:事件携带的工作段窗口/归属读取(仅结算类事件含字段,其余为空) */
private fun EngineEvent.settleMillis(): Long = when (this) {
    is EngineEvent.PhaseFinished -> settleMillis
    is EngineEvent.PhaseRestarted -> settleMillis
    is EngineEvent.Reset -> settleMillis
    else -> 0
}

private fun EngineEvent.sessionStart(): Long? = when (this) {
    is EngineEvent.PhaseFinished -> sessionStartWall
    is EngineEvent.PhaseRestarted -> sessionStartWall
    is EngineEvent.Reset -> sessionStartWall
    else -> null
}

private fun EngineEvent.sessionEnd(): Long? = when (this) {
    is EngineEvent.PhaseFinished -> sessionEndWall
    is EngineEvent.PhaseRestarted -> sessionEndWall
    is EngineEvent.Reset -> sessionEndWall
    else -> null
}

private fun EngineEvent.profileIdOf(): Long? = when (this) {
    is EngineEvent.PhaseFinished -> profileId
    is EngineEvent.PhaseRestarted -> profileId
    is EngineEvent.Reset -> profileId
    else -> null
}

/**
 * 引擎事件 -> 服务反应(v1.3 拆分):settle 落账 / 段记录 / 闹钟武装 / 检查点 / 提醒。
 * 调用方必须已持有引擎锁(内部不加锁;落库与 EventPolicy 决策需与 ticker 串行)。
 */
internal class EventApplier(
    private val graph: AppGraph,
    private val ledger: TickLedger,
    private val notifier: ServiceNotifier,
) {
    /**
     * 单个事件反应。异常由调用方兜底(F4:记日志保活收集器);本函数只抛不吞。
     * @return 是否 Reset 事件(排空感知:调用方据此完成 STOP 拆除握手)
     */
    suspend fun apply(ev: EngineEvent): Boolean {
        // v1.3 #6:工作段收尾(自动完成/终止/跳过/切换/重启)落一段专注 —— 窗口由引擎在
        // 事件内携带(同次转换赋值,无跨线程竞态);settle>0 才记,防空段
        if (ev.settleMillis() > 0) {
            val ss = ev.sessionStart()
            val se = ev.sessionEnd()
            val pid = ev.profileIdOf()
            if (ss != null && se != null && se > ss && pid != null) {
                graph.totalsRepo.recordWorkSession(pid, ss, se)
            }
        }
        for (fx in EventPolicy.decide(ev, graph.engine.snapshot.value)) {
            when (fx) {
                is EventEffect.Settle -> {
                    if (fx.millis > 0) graph.totalsRepo.addWork(
                        java.time.LocalDate.now().toString(), fx.profileId, fx.millis,
                    )
                }
                is EventEffect.Arm -> graph.alarmScheduler.arm(fx.endElapsed)
                EventEffect.CancelAlarm -> graph.alarmScheduler.cancel()
                EventEffect.ForceCheckpoint -> ledger.flush(graph.engine.snapshot.value, graph.time.elapsedRealtime(), force = true)
                is EventEffect.Remind -> notifier.remind(fx.workFinished)
            }
        }
        return ev is EngineEvent.Reset
    }
}
