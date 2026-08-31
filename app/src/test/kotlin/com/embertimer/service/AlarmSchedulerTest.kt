package com.embertimer.service

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.embertimer.timer.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmSchedulerTest {
    private val time = object : TimeProvider {
        override fun now() = 0L
        override fun elapsedRealtime() = 0L
    }

    @Test fun armSchedulesExactAlarm() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val sched = AlarmScheduler(ctx, time)
        sched.arm(123_456L)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val next = shadowOf(am).nextScheduledAlarm
        assertEquals(123_456L, next!!.triggerAtTime) // Robolectric 4.14: getter 可空
    }

    @Test fun cancelClearsAlarm() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val sched = AlarmScheduler(ctx, time)
        sched.arm(123_456L)
        sched.cancel()
        val am = ctx.getSystemService(AlarmManager::class.java)
        assertNull(shadowOf(am).nextScheduledAlarm)
    }

    @Test fun armTwiceKeepsSingleAlarm() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val sched = AlarmScheduler(ctx, time)
        sched.arm(1L); sched.arm(2L)
        val am = ctx.getSystemService(AlarmManager::class.java)
        assertEquals(1, shadowOf(am).scheduledAlarms.size) // FLAG_UPDATE_CURRENT 复用(4.14 API 名)
    }
}
