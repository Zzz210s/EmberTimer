package com.embertimer.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.3 #6:引擎在结算事件内携带工作段墙钟窗口(段起点~收尾时刻)。
 * 起点在 WORK RUNNING 起置;暂停保留起点(墙钟含间隙);自动完成/终止/跳过/
 * 正计时停止均随事件给出;休息段收尾不携带。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineSessionWindowTest {
    private class FT(var nowMs: Long = 1_000_000L, var el: Long = 10_000L) : TimeProvider {
        override fun now() = nowMs
        override fun elapsedRealtime() = el
    }

    private fun engine(t: FT) = TimerEngine(t, TestScope(UnconfinedTestDispatcher()), persist = { })

    private fun recordEvents(e: TimerEngine): MutableList<EngineEvent> {
        val seen = mutableListOf<EngineEvent>()
        TestScope(UnconfinedTestDispatcher()).launch { e.events.collect { seen += it } }
        return seen
    }

    @Test fun autoWorkFinishCarriesSessionWindow() = runTest {
        val t = FT()
        val e = engine(t)
        e.restore(null)
        val seen = recordEvents(e)
        e.start(1, 60_000L, 30_000L)
        t.el += 60_000; t.nowMs += 60_000
        e.onExpired()
        val pf = seen.filterIsInstance<EngineEvent.PhaseFinished>().single()
        assertEquals(Phase.WORK, pf.finished)
        assertEquals(1_000_000L, pf.sessionStartWall) // 起点 = start 时刻墙钟
        assertEquals(1_060_000L, pf.sessionEndWall)
    }

    @Test fun pauseKeepsOriginalStartResumeDoesNotReset() = runTest {
        val t = FT()
        val e = engine(t)
        e.restore(null)
        val seen = recordEvents(e)
        e.start(1, 100_000L, 30_000L) // wall 1_000_000
        t.el += 20_000; t.nowMs += 20_000
        e.pause()
        t.el += 30_000; t.nowMs += 30_000 // 暂停 30s(间隙含于墙钟窗口)
        e.resume()
        t.el += 80_000; t.nowMs += 80_000 // 续跑至到期
        e.onExpired()
        val pf = seen.filterIsInstance<EngineEvent.PhaseFinished>().last()
        assertEquals(1_000_000L, pf.sessionStartWall) // 仍是首次开始时刻
        assertEquals(1_130_000L, pf.sessionEndWall) // 收尾墙钟(含暂停间隙)
    }

    @Test fun stopDuringWorkCarriesResetWindow() = runTest {
        val t = FT()
        val e = engine(t)
        e.restore(null)
        val seen = recordEvents(e)
        e.start(1, 60_000L, 30_000L)
        t.el += 40_000; t.nowMs += 40_000
        e.reset()
        val r = seen.filterIsInstance<EngineEvent.Reset>().single()
        assertNotNull(r.sessionStartWall)
        assertEquals(1_000_000L, r.sessionStartWall)
        assertEquals(1_040_000L, r.sessionEndWall)
    }

    @Test fun restFinishCarriesNoWindow() = runTest {
        val t = FT()
        val e = engine(t)
        e.restore(null)
        val seen = recordEvents(e)
        e.start(1, 10_000L, 8_000L)
        t.el += 10_000; t.nowMs += 10_000
        e.onExpired() // work->rest
        t.el += 8_000; t.nowMs += 8_000
        e.onExpired() // rest->work(auto 起新工作段,窗口应重置为 1_018_000)
        val rest = seen.filterIsInstance<EngineEvent.PhaseFinished>().first { it.finished == Phase.REST }
        assertNull("休息段收尾不携带窗口", rest.sessionStartWall)
        t.el += 10_000; t.nowMs += 10_000
        e.onExpired() // 第二轮工作到期 -> 携带新窗口
        val work2 = seen.filterIsInstance<EngineEvent.PhaseFinished>().filter { it.finished == Phase.WORK }.last()
        assertEquals(1_018_000L, work2.sessionStartWall) // 第二轮工作起点 = rest 结束时刻
        assertEquals(1_028_000L, work2.sessionEndWall)
    }

    @Test fun countUpStopDuringWorkCarriesWindow() = runTest {
        val t = FT()
        val e = engine(t)
        e.restore(null)
        val seen = recordEvents(e)
        e.start(1, 500_000L, 0L, countUp = true)
        t.el += 75_000; t.nowMs += 75_000
        e.reset() // 正计时手动停止
        val r = seen.filterIsInstance<EngineEvent.Reset>().single()
        assertEquals(1_000_000L, r.sessionStartWall)
        assertEquals(1_075_000L, r.sessionEndWall)
        assertEquals(75_000L, r.settleMillis) // 全额结算
    }
}
