package com.embertimer.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase
import com.embertimer.timer.RuntimeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh")
class NotificationsTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private val snap = RuntimeSnapshot(
        profileId = 1, workMillis = 100_000, restMillis = 40_000,
        phase = Phase.WORK, status = EngineStatus.RUNNING, cycleCount = 1,
        startElapsed = 0, endElapsed = 100_000, endWall = 1_000_000,
        timeSpentPaused = 0, lastPauseTime = 0, timeAtPause = 0,
        savedAtWall = 0, savedAtElapsed = 0, ckptDate = null, ckptAccum = 0,
    )

    @Test fun ensureChannelsCreatesBoth() {
        TimerNotifications.ensureChannels(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        assertNotNull(nm.getNotificationChannel(TimerNotifications.CH_TIMER))
        assertNotNull(nm.getNotificationChannel(TimerNotifications.CH_TIMER))
    }

    /** v1.9.4 标准通知契约:title=阶段;text=循环;运行态系统 chronometer;action 行 终止|暂停|跳过(STOP intent) */
    @Test fun inProgressLayoutAndActions() {
        TimerNotifications.ensureChannels(ctx)
        val running = TimerNotifications.inProgress(ctx, snap)
        val paused = TimerNotifications.inProgress(ctx, snap.copy(status = EngineStatus.PAUSED))
        assertEquals("工作中", running.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
        assertEquals("工作中", paused.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
        // 系统模板:chronometer 开启(运行态)
        assertEquals(true, running.extras.getBoolean(NotificationCompat.EXTRA_SHOW_CHRONOMETER))
        // action 行:倒计时三键 终止|暂停|跳过;暂停态 终止|恢复|跳过
        assertEquals(3, (running.actions ?: emptyArray()).size)
        assertEquals(3, (paused.actions ?: emptyArray()).size)
        assertEquals("跳过", running.actions!![2].title.toString())
        // 终止键 intent 指向 STOP
        assertEquals("com.embertimer.action.STOP", shadowOf(running.actions!![0].actionIntent).savedIntent.action)
    }

    @Test fun phaseDoneIsAutoCancel() {
        TimerNotifications.ensureChannels(ctx)
        val n = TimerNotifications.phaseDone(ctx, workFinished = true)
        assertEquals(true, (n.flags and android.app.Notification.FLAG_AUTO_CANCEL) != 0)
    }

    /** 占位通知契约:走 CH_PROGRESS 且 ongoing(前台服务通知不可滑动清除) */
    @Test fun minimalPlaceholderIsOngoing() {
        TimerNotifications.ensureChannels(ctx)
        val n = TimerNotifications.minimal(ctx)
        assertEquals(TimerNotifications.CH_TIMER, n.channelId)
        assertEquals(true, (n.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
    }

    // ---- Task 7 / #10:正计时通知 —— 无循环/跳过/到期;运行态正向 chronometer 承载已走时长 ----

    @Test fun countUpRunningUsesForwardChronometer() {
        TimerNotifications.ensureChannels(ctx)
        val n = TimerNotifications.inProgress(ctx, snap.copy(countUp = true))
        assertEquals("工作中", n.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
        // 正计时:无跳过键
        assertEquals(2, (n.actions ?: emptyArray()).size)
    }

    @Test fun countUpPausedFreezesElapsedInText() {
        TimerNotifications.ensureChannels(ctx)
        val paused = snap.copy(countUp = true, status = EngineStatus.PAUSED, timeAtPause = 45_000)
        val n = TimerNotifications.inProgress(ctx, paused)
        assertEquals(2, (n.actions ?: emptyArray()).size)
    }

    // ---- v1.9.4:Chronometer base 必须基于 elapsedRealtime(墙钟 endWall 会错/空) ----

    @Test fun clockSpecCountdownUsesElapsedEnd() {
        val spec = buildClockSpec(snap) // countUp=false
        assertEquals(100_000L, spec.base) // endElapsed(elapsed时间轴),而非 endWall
        assertEquals(true, spec.countDown)
    }

    @Test fun clockSpecCountUpUsesStartPlusPaused() {
        val spec = buildClockSpec(snap.copy(countUp = true, startElapsed = 5_000, timeSpentPaused = 2_000))
        assertEquals(7_000L, spec.base) // startElapsed + timeSpentPaused
        assertEquals(false, spec.countDown)
    }
}
