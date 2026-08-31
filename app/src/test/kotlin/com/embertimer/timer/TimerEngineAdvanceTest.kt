package com.embertimer.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineAdvanceTest {
    private class FT(var nowMs: Long = 1_000_000L, var el: Long = 10_000L) : TimeProvider {
        override fun now() = nowMs
        override fun elapsedRealtime() = el
    }

    private fun engine(t: FT, saved: MutableList<RuntimeSnapshot?>) =
        // UnconfinedTestDispatcher: persist 在 save() 内同步执行,避免独立 scheduler 永不推进
        TimerEngine(t, TestScope(UnconfinedTestDispatcher()), persist = { saved += it })

    @Test fun workExpirySwitchesToRest() = runTest {
        val t = FT()
        val e = engine(t, mutableListOf())
        e.restore(null)
        e.start(1, 100_000L, 40_000L)
        t.el += 100_000
        e.onExpired()
        val s = e.snapshot.value!!
        assertEquals(Phase.REST, s.phase)
        assertEquals(0, s.cycleCount)
        assertEquals(t.el + 40_000L, s.endElapsed)
        val fin = e.events.replayCache.filterIsInstance<EngineEvent.PhaseFinished>().single()
        assertEquals(Phase.WORK, fin.finished)
        assertEquals(Phase.REST, fin.next)
        assertEquals(100_000L, fin.settleMillis)
        assertTrue(fin.auto)
    }

    @Test fun restExpirySwitchesToWorkAndIncrementsCycle() = runTest {
        val t = FT()
        val e = engine(t, mutableListOf())
        e.restore(null)
        e.start(1, 100_000L, 40_000L)
        t.el += 100_000; e.onExpired() // -> REST
        t.el += 40_000; e.onExpired()  // -> WORK, cycle 1
        val s = e.snapshot.value!!
        assertEquals(Phase.WORK, s.phase)
        assertEquals(1, s.cycleCount)
        assertEquals(t.el + 100_000L, s.endElapsed)
        assertTrue(e.events.replayCache.last() is EngineEvent.PhaseStarted)
    }

    @Test fun onExpiredBeforeDeadlineIsNoop() = runTest {
        val t = FT()
        val e = engine(t, mutableListOf())
        e.restore(null)
        e.start(1, 100_000L, 40_000L)
        t.el += 99_999
        e.onExpired()
        assertEquals(Phase.WORK, e.snapshot.value!!.phase)
    }

    @Test fun settleMillisDeductsCheckpointCursor() = runTest {
        val t = FT()
        val e = engine(t, mutableListOf())
        e.restore(null)
        e.start(1, 100_000L, 40_000L)
        t.el += 60_000
        e.onCheckpointFlushed("2026-08-31", 60_000L)
        t.el += 40_000
        e.onExpired()
        val fin = e.events.replayCache.filterIsInstance<EngineEvent.PhaseFinished>().single()
        assertEquals(40_000L, fin.settleMillis)
    }

    @Test fun skipFromRunningRestAdvancesCycleImmediately() = runTest {
        val t = FT()
        val e = engine(t, mutableListOf())
        e.restore(null)
        e.start(1, 100_000L, 40_000L)
        t.el += 100_000; e.onExpired() // REST
        t.el += 5_000
        e.skip() // 跳过休息
        val s = e.snapshot.value!!
        assertEquals(Phase.WORK, s.phase)
        assertEquals(1, s.cycleCount)
        // 此前 onExpired 已产生一条 PhaseFinished(WORK),skip 的这条用 last() 取
        val fin = e.events.replayCache.filterIsInstance<EngineEvent.PhaseFinished>().last()
        assertEquals(Phase.REST, fin.finished)
        assertEquals(0L, fin.settleMillis)
    }

    @Test fun skipFromRunningWorkSettlesAccrued() = runTest {
        val t = FT()
        val e = engine(t, mutableListOf())
        e.restore(null)
        e.start(1, 100_000L, 40_000L)
        t.el += 30_000
        e.skip()
        val fin = e.events.replayCache.filterIsInstance<EngineEvent.PhaseFinished>().single()
        assertEquals(30_000L, fin.settleMillis)
        assertEquals(Phase.REST, e.snapshot.value!!.phase)
    }

    @Test fun resetClearsSnapshotAndSettles() = runTest {
        val t = FT()
        val saved = mutableListOf<RuntimeSnapshot?>()
        val e = engine(t, saved)
        e.restore(null)
        e.start(1, 100_000L, 40_000L)
        t.el += 70_000
        e.reset()
        advanceUntilIdle()
        assertNull(e.snapshot.value)
        val ev = e.events.replayCache.filterIsInstance<EngineEvent.Reset>().single()
        assertEquals(70_000L, ev.settleMillis)
        assertEquals(1L, ev.profileId)
        assertNull(saved.last())
    }

    @Test fun restartPhaseOnlyWhenPausedReopensFullDuration() = runTest {
        val t = FT()
        val e = engine(t, mutableListOf())
        e.restore(null)
        e.start(1, 100_000L, 40_000L)
        t.el += 50_000
        e.pause()
        e.restartPhase(2, 200_000L, 80_000L)
        val s = e.snapshot.value!!
        assertEquals(EngineStatus.RUNNING, s.status)
        assertEquals(Phase.WORK, s.phase)
        assertEquals(2L, s.profileId)
        assertEquals(200_000L, s.workMillis)
        assertEquals(t.el + 200_000L, s.endElapsed)
        assertEquals(0L, s.ckptAccum)
        val ev = e.events.replayCache.filterIsInstance<EngineEvent.PhaseRestarted>().single()
        assertEquals(50_000L, ev.settleMillis)
    }

    @Test fun restartPhaseIgnoredWhileRunning() = runTest {
        val t = FT()
        val e = engine(t, mutableListOf())
        e.restore(null)
        e.start(1, 100_000L, 40_000L)
        e.restartPhase(2, 200_000L, 80_000L) // RUNNING,忽略
        assertEquals(1L, e.snapshot.value!!.profileId)
        assertEquals(100_000L, e.snapshot.value!!.workMillis)
    }

    @Test fun expiredWhileDeadSettlesFullWork() = runTest {
        // 进程死亡期间工作阶段已到期:恢复后 onExpired 用 endElapsed 结算
        val t = FT()
        val saved = mutableListOf<RuntimeSnapshot?>()
        val e1 = TimerEngine(t, TestScope(UnconfinedTestDispatcher()), persist = { saved += it })
        e1.restore(null)
        e1.start(1, 100_000L, 40_000L)
        t.el += 60_000
        e1.onCheckpointFlushed("2026-08-31", 60_000L)
        val persisted = saved.last { it != null }!!
        // 进程死后很久,elapsed 继续走
        t.el += 500_000
        val e2 = TimerEngine(t, TestScope(UnconfinedTestDispatcher()), persist = { saved += it })
        e2.restore(persisted)
        e2.onExpired()
        val fin = e2.events.replayCache.filterIsInstance<EngineEvent.PhaseFinished>().single()
        assertEquals(40_000L, fin.settleMillis) // clamp 到 workMillis,扣游标 60k -> 40k
    }
}
