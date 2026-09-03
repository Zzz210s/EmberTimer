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
@Config(sdk = [34])
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
        assertNotNull(nm.getNotificationChannel(TimerNotifications.CH_PROGRESS))
        assertNotNull(nm.getNotificationChannel(TimerNotifications.CH_REMINDER))
    }

    /** 三态通知契约:title 为阶段;text 承载循环;运行态 chronometer 倒计时 + 进度条;动作图标 + STOP intent */
    @Test fun inProgressLayoutAndActions() {
        TimerNotifications.ensureChannels(ctx)
        val running = TimerNotifications.inProgress(ctx, snap)
        val paused = TimerNotifications.inProgress(ctx, snap.copy(status = EngineStatus.PAUSED))
        assertEquals(listOf("暂停", "跳过", "终止"), running.actions.map { it.title.toString() })
        assertEquals(listOf("恢复", "跳过", "终止"), paused.actions.map { it.title.toString() })
        assertEquals("com.embertimer.action.STOP", shadowOf(paused.actions[2].actionIntent).savedIntent.action)
        // D2:title 只剩阶段文案(倒计时由 chronometer 占标题行时间位)
        assertEquals("工作中", running.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
        assertEquals("循环 1", running.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
        assertEquals("已暂停 · 循环 1", paused.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
        // 倒计时(D4):when 指向阶段结束墙钟,chronometer 递减开关已设
        assertEquals(snap.endWall, running.`when`)
        assertEquals(true, running.extras.getBoolean(NotificationCompat.EXTRA_CHRONOMETER_COUNT_DOWN))
        // 进度条:阶段总量 + 已进行量(RUNNING 态)
        assertEquals(snap.durationMillis.toInt(), running.extras.getInt(NotificationCompat.EXTRA_PROGRESS_MAX))
        assertEquals(true, running.extras.getInt(NotificationCompat.EXTRA_PROGRESS) >= 0)
        // 暂停态:无 chronometer、无进度
        assertEquals(false, paused.extras.getBoolean(NotificationCompat.EXTRA_SHOW_CHRONOMETER, false))
        assertEquals(0, paused.extras.getInt(NotificationCompat.EXTRA_PROGRESS_MAX))
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
        assertEquals(TimerNotifications.CH_PROGRESS, n.channelId)
        assertEquals(true, (n.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
    }
}
