package com.embertimer.service

import com.embertimer.data.ReminderIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderConfigTest {
    @Test fun durations() {
        assertEquals(0, ReminderPlayer.durationMs(ReminderIntensity.LIGHT))
        assertEquals(3_000, ReminderPlayer.durationMs(ReminderIntensity.STANDARD))
        assertEquals(5_000, ReminderPlayer.durationMs(ReminderIntensity.STRONG))
    }
    @Test fun patterns() {
        val p = ReminderPlayer.pattern(ReminderIntensity.STANDARD)
        assertEquals(0L, p[0]) // 立即开始震动
        assertTrue(p.sum() <= ReminderPlayer.durationMs(ReminderIntensity.STANDARD))
        assertTrue(ReminderPlayer.pattern(ReminderIntensity.STRONG).sum() > 0)
    }
}
