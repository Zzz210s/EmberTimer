package com.embertimer.service

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.embertimer.EmberApp
import com.embertimer.di.AppGraph
import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase
import com.embertimer.timer.RuntimeSnapshot
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * AlarmReceiver 门控钉(Fix Round 2 F5/F1):
 * - 已到期 RUNNING -> 起前台服务(推进交服务对账)、引擎快照不动;起服务被系统接受时
 *   不再补 retry 闹钟;起服务被拒 -> 60s 后重试闹钟(F1 自愈,不循环);
 * - 未到期 RUNNING -> 按 endElapsed 重武装 + 起服务;
 * - PAUSED/null -> 不动闹钟、不起服务。
 *
 * 直调 onReceive(Robolectric 下 goAsync 直调安全,已 probe 验证;sendBroadcast 不派发
 * 无 intent-filter 的显式清单接收器)。接收器 body 在 Default 真线程异步执行,
 * 有副作用的断言用轮询等待(有界超时),无副作用的断言静置后检查。
 * TestApp 空 onCreate 跳过真实装配(AppGraph/频道),graph 由测试注入;
 * 每用例独立 DataStore 文件名(同进程同文件多实例会抛异常)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AlarmReceiverTest.TestApp::class)
class AlarmReceiverTest {
    class TestApp : EmberApp() { override fun onCreate() { /* 无 super:跳过真实装配 */ } }

    private lateinit var ctx: Context
    private lateinit var app: EmberApp
    private var graph: AppGraph? = null

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        app = ctx as EmberApp
    }

    @After fun tearDown() {
        val g = graph ?: return
        runBlocking { g.appScope.coroutineContext.job.cancelAndJoin(); runCatching { g.db.close() } }
    }

    private fun snap(status: EngineStatus, endElapsed: Long) = RuntimeSnapshot(
        profileId = 1L, workMillis = 100_000L, restMillis = 40_000L,
        phase = Phase.WORK, status = status, cycleCount = 0,
        startElapsed = 0L, endElapsed = endElapsed, endWall = 1_000_000L,
        timeSpentPaused = 0L, lastPauseTime = 0L, timeAtPause = 0L,
        savedAtWall = 1_000_000L, savedAtElapsed = 0L, ckptDate = null, ckptAccum = 0L,
    )

    /** 构造受控 graph 并恢复引擎快照(restore 同时置 ready,接收器 awaitReady 即返) */
    private fun graphFor(storeName: String, snap: (AppGraph) -> RuntimeSnapshot?): AppGraph {
        val g = AppGraph(ctx, useInMemoryDb = true, storeFileName = storeName)
        app.graph = g
        graph = g
        runBlocking { g.engine.restore(snap(g)) }
        return g
    }

    private fun fire(context: Context = ctx) =
        AlarmReceiver().onReceive(context, Intent("com.embertimer.ALARM"))

    /** body 在 Default 真线程执行,轮询等待断言条件成立(有界) */
    private fun awaitCond(timeoutMs: Long = 5_000, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            Thread.sleep(25)
        }
    }

    private fun nextAlarmTrigger(): Long? {
        val am = ctx.getSystemService(AlarmManager::class.java)
        // peek 非破坏性:getNextScheduledAlarm 会从队列中消费闹钟(Robolectric 4.14)
        return shadowOf(am).peekNextScheduledAlarm()?.triggerAtTime
    }

    /** 已到期 + 起服务被系统接受:只起服务,引擎不动,不补 retry 闹钟(不循环重试) */
    @Test fun expiredRunningStartsServiceWithoutTouchingEngineOrAlarm() {
        val g = graphFor("alarm_rx_expired_ok") { g ->
            snap(EngineStatus.RUNNING, g.time.elapsedRealtime() - 1_000)
        }
        val before = g.engine.snapshot.value!!
        fire()
        awaitCond { shadowOf(app).peekNextStartedService() != null }
        assertEquals(ComponentName(ctx, TimerService::class.java), shadowOf(app).nextStartedService.component)
        // 起服务成功 -> 无 retry 闹钟;静置一小段确认 body 无后续武装
        Thread.sleep(300)
        assertNull(nextAlarmTrigger())
        val after = g.engine.snapshot.value!!
        assertEquals(before.status, after.status)
        assertEquals(before.phase, after.phase)
        assertEquals(before.endElapsed, after.endElapsed)
    }

    /** 已到期 + 两种 start 均被拒(F1):60s 后重试闹钟兜底,不 dead-end */
    @Test fun expiredRunningWithDeniedStartRearmsRetryAlarm() {
        val g = graphFor("alarm_rx_expired_denied") { g ->
            snap(EngineStatus.RUNNING, g.time.elapsedRealtime() - 1_000)
        }
        val expected = g.time.elapsedRealtime() + 60_000
        fire(DeniedStartContext(ctx))
        awaitCond { nextAlarmTrigger() != null }
        val trigger = nextAlarmTrigger()!!
        assertTrue("retry alarm should be ~now+60s, got $trigger vs $expected", Math.abs(trigger - expected) < 5_000)
        // 两次 start 均在抛异常前未达系统,不产生记录
        assertNull(shadowOf(app).peekNextStartedService())
    }

    /** 未到期 RUNNING:endElapsed 重武装 + 起服务 */
    @Test fun activeRunningRearmsAtEndElapsedAndStartsService() {
        var end = 0L
        val g = graphFor("alarm_rx_active") { g ->
            end = g.time.elapsedRealtime() + 300_000
            snap(EngineStatus.RUNNING, end)
        }
        fire()
        awaitCond { nextAlarmTrigger() != null }
        assertEquals(end, nextAlarmTrigger())
        awaitCond { shadowOf(app).peekNextStartedService() != null }
        assertNotNull(shadowOf(app).nextStartedService)
    }

    /** PAUSED:不动闹钟、不起服务(UI 负责恢复) */
    @Test fun pausedSnapshotDoesNothing() {
        graphFor("alarm_rx_paused") { g ->
            snap(EngineStatus.PAUSED, g.time.elapsedRealtime() - 1_000) // 到期也轮不到接收器管
        }
        fire()
        Thread.sleep(400) // body 只读快照即返回,静置后断言无副作用
        assertNull(nextAlarmTrigger())
        assertNull(shadowOf(app).peekNextStartedService())
    }

    /** null(空闲):不动闹钟、不起服务 */
    @Test fun nullSnapshotDoesNothing() {
        graphFor("alarm_rx_null") { null }
        fire()
        Thread.sleep(400)
        assertNull(nextAlarmTrigger())
        assertNull(shadowOf(app).peekNextStartedService())
    }

    /** 模拟 Android 12+ 后台 FGS 启动被拒:两种 start 均抛异常(ensureServiceRunning -> false) */
    private class DeniedStartContext(base: Context) : ContextWrapper(base) {
        override fun startForegroundService(service: Intent): ComponentName =
            throw IllegalStateException("background FGS start denied")
        override fun startService(service: Intent): ComponentName? =
            throw IllegalStateException("background start denied")
    }
}
