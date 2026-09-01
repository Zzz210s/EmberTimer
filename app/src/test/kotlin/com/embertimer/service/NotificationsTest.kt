package com.embertimer.service

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase
import com.embertimer.timer.RuntimeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

    @Test fun inProgressHasTwoActions() {
        TimerNotifications.ensureChannels(ctx)
        val n = TimerNotifications.inProgress(ctx, snap)
        assertEquals(2, n.actions.size)
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
