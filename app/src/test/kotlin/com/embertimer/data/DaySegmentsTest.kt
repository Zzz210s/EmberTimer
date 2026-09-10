package com.embertimer.data

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** v1.10:每日详情时段合并(间隔 <= 3 分钟)与大时段标识纯函数测试 */
class DaySegmentsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun ms(h: Int, m: Int) =
        java.time.LocalDateTime.of(2026, 9, 4, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test fun gapWithinThreeMinutesMerges() {
        val out = mergeSessions(listOf(ms(9, 0) to ms(9, 25), ms(9, 27) to ms(9, 50)))
        assertEquals(1, out.size)
        assertEquals(ms(9, 0), out[0].first)
        assertEquals(ms(9, 50), out[0].second)
    }

    @Test fun gapOverThreeMinutesStaysSeparate() {
        val out = mergeSessions(listOf(ms(9, 0) to ms(9, 25), ms(9, 30) to ms(9, 50)))
        assertEquals(2, out.size)
        assertEquals(ms(9, 25), out[0].second)
        assertEquals(ms(9, 30), out[1].first)
    }

    @Test fun exactlyThreeMinutesMergesInclusive() {
        val out = mergeSessions(listOf(ms(9, 0) to ms(9, 25), ms(9, 28) to ms(9, 50)))
        assertEquals(1, out.size)
    }

    @Test fun threeMinutesPlusOneSecondSplits() {
        val out = mergeSessions(listOf(ms(9, 0) to ms(9, 25), (ms(9, 28) + 1_000) to ms(9, 50)))
        assertEquals(2, out.size)
    }

    @Test fun unsortedInputIsSortedAndMerged() {
        val out = mergeSessions(listOf(ms(9, 27) to ms(9, 50), ms(9, 0) to ms(9, 25)))
        assertEquals(1, out.size)
        assertEquals(ms(9, 0), out[0].first)
        assertEquals(ms(9, 50), out[0].second)
    }

    @Test fun degenerateSegmentsAreDropped() {
        assertEquals(0, mergeSessions(listOf(ms(9, 0) to ms(9, 0), ms(10, 0) to ms(9, 0))).size)
        assertEquals(0, mergeSessions(emptyList()).size)
    }

    @Test fun nestedSegmentKeepsWidestEnd() {
        val out = mergeSessions(listOf(ms(9, 0) to ms(9, 40), ms(9, 10) to ms(9, 20)))
        assertEquals(1, out.size)
        assertEquals(ms(9, 40), out[0].second)
    }

    /** 凌晨 0-5 / 早上 6-8 / 上午 9-11 / 下午 12-17 / 晚上 18-23 */
    @Test fun periodBucketsByStartHour() {
        assertEquals(DayPeriod.DAWN, dayPeriodOf(ms(0, 0), zone))
        assertEquals(DayPeriod.DAWN, dayPeriodOf(ms(5, 59), zone))
        assertEquals(DayPeriod.EARLY_MORNING, dayPeriodOf(ms(6, 0), zone))
        assertEquals(DayPeriod.EARLY_MORNING, dayPeriodOf(ms(8, 59), zone))
        assertEquals(DayPeriod.MORNING, dayPeriodOf(ms(9, 0), zone))
        assertEquals(DayPeriod.MORNING, dayPeriodOf(ms(11, 59), zone))
        assertEquals(DayPeriod.AFTERNOON, dayPeriodOf(ms(12, 0), zone))
        assertEquals(DayPeriod.AFTERNOON, dayPeriodOf(ms(17, 59), zone))
        assertEquals(DayPeriod.EVENING, dayPeriodOf(ms(18, 0), zone))
        assertEquals(DayPeriod.EVENING, dayPeriodOf(ms(23, 59), zone))
    }
}
