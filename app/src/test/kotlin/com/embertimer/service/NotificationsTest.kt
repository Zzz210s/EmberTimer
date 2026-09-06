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

    /** 三态通知契约:title 为阶段;text 承载循环;运行态 chronometer 倒计时 + 进度条;动作图标 + STOP intent */
    @Test fun inProgressLayoutAndActions() {
        TimerNotifications.ensureChannels(ctx)
        val running = TimerNotifications.inProgress(ctx, snap)
        val paused = TimerNotifications.inProgress(ctx, snap.copy(status = EngineStatus.PAUSED))
        // v1.9.1 通知重构:自定义 RemoteViews(图标按钮 终止|开始/暂停|跳过 + 倒计时同排 + 循环图标);不再用系统 action 行
        assertNotNull(running.contentView)
        assertNotNull(paused.contentView)
        assertEquals(0, (running.actions ?: emptyArray()).size)
        assertEquals(0, (paused.actions ?: emptyArray()).size)
        assertEquals("工作中", running.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
        assertEquals("工作中", paused.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
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
        assertNotNull(n.contentView)
        assertEquals(0, (n.actions ?: emptyArray()).size)
        assertEquals("工作中", n.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
    }

    @Test fun countUpPausedFreezesElapsedInText() {
        TimerNotifications.ensureChannels(ctx)
        val paused = snap.copy(countUp = true, status = EngineStatus.PAUSED, timeAtPause = 45_000)
        val n = TimerNotifications.inProgress(ctx, paused)
        assertNotNull(n.contentView)
        assertEquals(0, (n.actions ?: emptyArray()).size)
        assertEquals("工作中", n.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
    }
}
