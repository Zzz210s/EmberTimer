package com.embertimer.service

import com.embertimer.di.AppGraph
import com.embertimer.timer.Checkpointer
import com.embertimer.timer.RuntimeSnapshot
import java.time.LocalDate

/**
 * 检查点落账(v1.3 拆分):60 秒增量 + 阶段切换/启动强落。持 lastFlush 游标状态,
 * 由调用方(引擎锁内)驱动 —— 语义与拆分前 TimerService.flushCheckpoint 完全一致。
 */
internal class TickLedger(private val graph: AppGraph) {
    private var lastFlushDate = ""
    private var lastFlushElapsed = 0L

    /**
     * 60 秒增量落账;force 用于阶段切换/启动时。
     * 调用方必须已持有引擎锁(内部不再加锁,嵌套加锁会死锁)。
     */
    suspend fun flush(snap: RuntimeSnapshot?, nowElapsed: Long, force: Boolean) {
        val s = snap ?: return
        val today = LocalDate.now().toString()
        val f = Checkpointer.compute(s, nowElapsed, today)
        if (f.deltaMillis > 0 || (force && s.status == com.embertimer.timer.EngineStatus.RUNNING && s.phase == com.embertimer.timer.Phase.WORK)) {
            graph.totalsRepo.addWork(f.date, s.profileId, f.deltaMillis)
            graph.engine.onCheckpointFlushed(f.date, f.newAccum)
        }
        lastFlushElapsed = nowElapsed
        lastFlushDate = today
    }
}
