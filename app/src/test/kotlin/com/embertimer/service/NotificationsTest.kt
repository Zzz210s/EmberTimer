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

    /** 进行/暂停两态固定三动作;运行态倒计时:when 指向阶段结束墙钟,chronometer 递减开关已设 */
    @Test fun inProgressThreeActionsAndCountdown() {
        TimerNotifications.ensureChannels(ctx)
        val running = TimerNotifications.inProgress(ctx, snap)
        val paused = TimerNotifications.inProgress(ctx, snap.copy(status = EngineStatus.PAUSED))
        assertEquals(listOf("暂停", "跳过", "重置"), running.actions.map { it.title.toString() })
        assertEquals(listOf("恢复", "跳过", "重置"), paused.actions.map { it.title.toString() })
        // PendingIntent 无公开 Intent 访问器,Robolectric 下经 shadow 读回封装的 service Intent
        assertEquals("com.embertimer.action.RESET", shadowOf(paused.actions[2].actionIntent).savedIntent.action)
        // 倒计时(D4):when 指向阶段结束墙钟,chronometer 递减开关已设
        assertEquals(snap.endWall, running.`when`)
        assertEquals(true, running.extras.getBoolean(NotificationCompat.EXTRA_CHRONOMETER_COUNT_DOWN))
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
