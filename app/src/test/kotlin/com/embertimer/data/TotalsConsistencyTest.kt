package com.embertimer.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.embertimer.data.db.EmberDatabase
import com.embertimer.timer.TimeProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.10.8:合计与每日详情"同源"的护栏 + 自动备份的数据变动心跳。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TotalsConsistencyTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val db = EmberDatabase.build(ctx)
    private val now = 1_700_000_000_000L
    private val time = object : TimeProvider {
        override fun now(): Long = now
        override fun elapsedRealtime(): Long = now
    }
    private val repo = DailyTotalRepository(db, db.dailyTotalDao(), db.focusSessionDao(), time)
    private val zone: ZoneId = ZoneId.systemDefault()

    @After fun tearDown() = db.close()

    private fun todayMs(): Long = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()

    /** 间隔 <=3 分钟的相邻段落:展示合并为一条,合计 = 合并后时长(而不是各段原样相加) */
    @Test fun totalsEqualMergedSpans() = runTest {
        val t0 = todayMs() + 9 * 3_600_000L
        repo.recordWorkSessionSplit(1L, t0, t0 + 10 * 60_000L, emptyList(), zone = zone)
        repo.recordWorkSessionSplit(1L, t0 + 12 * 60_000L, t0 + 32 * 60_000L, emptyList(), zone = zone)
        val date = LocalDate.now().toString()
        val rows = repo.breakdownByDate(date)
        assertEquals(1, rows.size)
        // 10 + 2(空档) + 20 = 32 分钟 —— 与展示合并后的时间段之和一致
        assertEquals(32 * 60_000L, rows[0].total)
        val spans = mergeSessions(
            repo.sessionsBetween(todayMs(), todayMs() + 86_400_000L).map { it.startAt to it.endAt },
        )
        assertEquals(rows[0].total, spans.sumOf { it.second - it.first })
    }

    /** 超过 3 分钟的空档不并入:合计只算实际两段 */
    @Test fun longGapStaysSplit() = runTest {
        val t0 = todayMs() + 9 * 3_600_000L
        repo.recordWorkSessionSplit(1L, t0, t0 + 10 * 60_000L, emptyList(), zone = zone)
        repo.recordWorkSessionSplit(1L, t0 + 20 * 60_000L, t0 + 30 * 60_000L, emptyList(), zone = zone)
        val rows = repo.breakdownByDate(LocalDate.now().toString())
        assertEquals(20 * 60_000L, rows[0].total)
    }

    /** 删除配置:段落与合计级联清理 */
    @Test fun deletingProfileCascadesData() = runTest {
        val t0 = todayMs() + 9 * 3_600_000L
        repo.recordWorkSessionSplit(7L, t0, t0 + 30 * 60_000L, emptyList(), zone = zone)
        assertEquals(1, repo.breakdownByDate(LocalDate.now().toString()).size)
        repo.deleteProfileData(7L)
        assertTrue(repo.breakdownByDate(LocalDate.now().toString()).isEmpty())
        assertTrue(repo.sessionsBetween(todayMs(), todayMs() + 86_400_000L).isEmpty())
    }

    /** 数据变动心跳:任何写入都会改变它(自动备份的触发源) */
    @Test fun dataTickChangesOnWrite() = runTest {
        val before = repo.dataTick().first()
        repo.recordWorkSessionSplit(3L, todayMs() + 3_600_000L, todayMs() + 3_600_000L + 600_000L, emptyList(), zone = zone)
        val after = repo.dataTick().first()
        assertNotEquals(before, after)
    }
}
