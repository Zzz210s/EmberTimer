package com.embertimer.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
private class FakeTime(var nowMs: Long = 1_000_000L, var el: Long = 10_000L) : TimeProvider {
    override fun now() = nowMs
    override fun elapsedRealtime() = el
}

@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineBasicTest {
    private fun engine(t: FakeTime, saved: MutableList<RuntimeSnapshot?>) =
        // UnconfinedTestDispatcher: persist 在 save() 内同步执行,避免独立 scheduler 永不推进
        TimerEngine(t, TestScope(UnconfinedTestDispatcher()), persist = { saved += it })

    @Test fun startBeginsWorkPhaseCountdown() = runTest {
        val t = FakeTime()
        val saved = mutableListOf<RuntimeSnapshot?>()
        val e = engine(t, saved)
        e.restore(null)
        e.start(3, 25 * 60_000L, 5 * 60_000L)
        advanceUntilIdle()
        val s = e.snapshot.value!!
        assertEquals(Phase.WORK, s.phase)
        assertEquals(EngineStatus.RUNNING, s.status)
        assertEquals(0, s.cycleCount)
        assertEquals(t.el + 25 * 60_000L, s.endElapsed)
        assertEquals(t.nowMs + 25 * 60_000L, s.endWall)
        assertEquals(EngineEvent.PhaseStarted(Phase.WORK, t.el + 25 * 60_000L, t.nowMs + 25 * 60_000L), e.events.replayCache.last())
        assertEquals(s, saved.last())
    }

    @Test fun startIgnoredWhileActive() = runTest {
        val t = FakeTime()
        val e = engine(t, mutableListOf())
        e.restore(null)
        e.start(1, 100_000L, 50_000L)
        e.start(2, 999_000L, 999_000L) // 忽略
        assertEquals(1L, e.snapshot.value!!.profileId)
        assertEquals(100_000L, e.snapshot.value!!.workMillis)
    }

    @Test fun pauseFreezesRemainingAndResumeShiftsEnd() = runTest {
        val t = FakeTime()
        val saved = mutableListOf<RuntimeSnapshot?>()
        val e = engine(t, saved)
        e.restore(null)
        e.start(1, 100_000L, 50_000L)
        t.el += 40_000
        e.pause()
        val p = e.snapshot.value!!
        assertEquals(EngineStatus.PAUSED, p.status)
        assertEquals(60_000L, p.timeAtPause)
        assertEquals(EngineEvent.Paused(60_000L), e.events.replayCache.last())
        t.el += 3_000_000 // 暂停很久
        e.resume()
        val r = e.snapshot.value!!
        assertEquals(EngineStatus.RUNNING, r.status)
        assertEquals(t.el + 60_000L, r.endElapsed)
        assertEquals(3_000_000L, r.timeSpentPaused)
        assertEquals(60_000L, r.remaining(t.el))
        assertTrue(e.events.replayCache.last() is EngineEvent.Resumed)
    }

    @Test fun pauseIgnoredWhenNotRunning() = runTest {
        val t = FakeTime()
        val e = engine(t, mutableListOf())
        e.restore(null)
        e.pause() // 无快照,忽略
        assertNull(e.snapshot.value)
    }

    @Test fun accruedWorkAccountsForPause() = runTest {
        val t = FakeTime()
        val e = engine(t, mutableListOf())
        e.restore(null)
        e.start(1, 100_000L, 50_000L)
        t.el += 30_000
        e.pause()
        t.el += 500_000
        e.resume()
        t.el += 20_000
        val s = e.snapshot.value!!
        assertEquals(50_000L, s.accruedWork(t.el))
    }

    @Test fun readyGate() = runTest {
        val t = FakeTime()
        val e = engine(t, mutableListOf())
        assertFalse(e.ready.value)
        e.restore(null)
        assertTrue(e.ready.value)
    }

    @Test fun onCheckpointFlushedUpdatesCursor() = runTest {
        val t = FakeTime()
        val saved = mutableListOf<RuntimeSnapshot?>()
        val e = engine(t, saved)
        e.restore(null)
        e.start(1, 100_000L, 50_000L)
        e.onCheckpointFlushed("2026-08-31", 42_000L)
        advanceUntilIdle()
        val s = e.snapshot.value!!
        assertEquals("2026-08-31", s.ckptDate)
        assertEquals(42_000L, s.ckptAccum)
    }
}
