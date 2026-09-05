package com.embertimer.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.embertimer.data.db.EmberDatabase
import com.embertimer.timer.TimeProvider
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.6 误触规则:短于 1 分钟的段被清理且不残留合计 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PruneMisTouchTest {
    private val time = object : TimeProvider {
        var nowMs = 1000L
        override fun now() = nowMs
        override fun elapsedRealtime() = 0L
    }
    private var db: EmberDatabase? = null

    @After fun tearDown() {
        db?.close()
        db = null
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase("ember.db")
    }

    @Test fun pruneRemovesShortSessionsAndDeductsTotals() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = EmberDatabase.build(ctx)
        val repo = DailyTotalRepository(db!!, db!!.dailyTotalDao(), db!!.focusSessionDao(), time)
        val zone = ZoneId.of("UTC")
        // 同一天:一段 20s(误触)+ 一段 10 分钟(正常)
        val dayStart = java.time.LocalDate.of(2026, 9, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        repo.addWork("2026-09-05", 1L, 620_000L) // 20s+600s
        repo.recordWorkSession(1L, dayStart + 60_000, dayStart + 80_000, zone)
        repo.recordWorkSession(1L, dayStart + 1_800_000, dayStart + 2_400_000, zone)
        assertEquals(2, db!!.focusSessionDao().count())

        repo.pruneMisTouchSessions(60_000L, zone)

        assertEquals(1, db!!.focusSessionDao().count())
        val left = db!!.focusSessionDao().between(dayStart, dayStart + 86_400_000).single()
        assertEquals(dayStart + 1_800_000L, left.startAt)
        // 合计 620s -> 扣 20s = 600s
        val total = repo.breakdownByDate("2026-09-05").single().total
        assertEquals(600_000L, total)
        // 清理幂等:再跑一次无副作用
        repo.pruneMisTouchSessions(60_000L, zone)
        assertEquals(600_000L, repo.breakdownByDate("2026-09-05").single().total)
    }
}
