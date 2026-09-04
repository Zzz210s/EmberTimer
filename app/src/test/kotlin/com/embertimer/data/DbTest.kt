package com.embertimer.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.embertimer.data.db.EmberDatabase
import com.embertimer.di.AppGraph
import com.embertimer.timer.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class) // 绕过 EmberApp 真实装配,保持测试封闭
class DbTest {
    private lateinit var db: EmberDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var totals: DailyTotalRepository

    private val time = object : TimeProvider {
        var nowMs = 1000L
        override fun now() = nowMs
        override fun elapsedRealtime() = 0L
    }

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, EmberDatabase::class.java)
            .allowMainThreadQueries().build()
        profiles = ProfileRepository(db.profileDao(), time)
        totals = DailyTotalRepository(db, db.dailyTotalDao(), time)
    }
    @After fun tearDown() { db.close() }

    @Test fun freshDatabaseHasNoDefaultProfile() = runTest {
        // #3 首装空库:不再种默认“番茄”,空态由主页引导;任何库能力都不应写演示数据
        assertTrue(profiles.profiles.first().isEmpty())
        assertEquals(0, profiles.count())
    }

    @Test fun uniqueNameRejected() = runTest {
        profiles.create("A", 25, 5)
        profiles.create("A", 30, 10) // 同名:忽略,返回-1
        assertEquals(1, profiles.profiles.first().size)
    }

    @Test fun addWorkAccumulatesSameDaySameProfile() = runTest {
        val id = profiles.create("A", 25, 5)
        totals.addWork("2026-08-31", id, 60_000)
        totals.addWork("2026-08-31", id, 30_000)
        val day = totals.dayTotals("2026-01-01").first().single()
        assertEquals("2026-08-31", day.date)
        assertEquals(90_000L, day.total)
    }

    @Test fun dayTotalsSumsAcrossProfiles() = runTest {
        val a = profiles.create("A", 25, 5)
        val b = profiles.create("B", 50, 10)
        totals.addWork("2026-08-31", a, 60_000)
        totals.addWork("2026-08-31", b, 60_000)
        assertEquals(120_000L, totals.dayTotals("2026-01-01").first().single().total)
    }

    @Test fun profileTotalsSumsAcrossDays() = runTest {
        val a = profiles.create("A", 25, 5)
        profiles.create("B", 50, 10)
        totals.addWork("2026-08-30", a, 60_000)
        totals.addWork("2026-08-31", a, 60_000)
        totals.addWork("2026-08-31", 999L, 60_000) // 已删配置的孤儿数据照常计入
        val map = totals.profileTotals().first().associate { it.profileId to it.total }
        assertEquals(120_000L, map[a])
        assertEquals(60_000L, map[999L])
    }

    @Test fun dayTotalsFiltersByFromDate() = runTest {
        val a = profiles.create("A", 25, 5)
        totals.addWork("2026-01-01", a, 1)
        totals.addWork("2026-08-31", a, 1)
        val days = totals.dayTotals("2026-06-01").first()
        assertEquals(1, days.size)
        assertTrue(days[0].date >= "2026-06-01")
    }

    @Test fun breakdownByDateAggregatesPerProfile() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val g = AppGraph(ctx, useInMemoryDb = true, storeFileName = "db_breakdown")
        val d = LocalDate.of(2026, 9, 2).toString()
        g.totalsRepo.addWork(d, 1, 30 * 60_000L)
        g.totalsRepo.addWork(d, 1, 15 * 60_000L) // 同日同 profile 增量累加
        g.totalsRepo.addWork(d, 2, 60 * 60_000L)
        g.totalsRepo.addWork(LocalDate.of(2026, 9, 1).toString(), 1, 60 * 60_000L) // 其他日期不计
        val rows = g.totalsRepo.breakdownByDate(d).sortedBy { it.profileId }
        assertEquals(2, rows.size)
        assertEquals(45 * 60_000L, rows[0].total) // profile 1
        assertEquals(60 * 60_000L, rows[1].total) // profile 2
    }
}
