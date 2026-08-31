package com.embertimer.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.embertimer.data.db.EmberDatabase
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    @Test fun seedCreatesDefaultWhenEmpty() = runTest {
        profiles.seedIfEmpty()
        val list = profiles.profiles.first()
        assertEquals(1, list.size)
        assertEquals("番茄", list[0].name)
        assertEquals(25, list[0].workMinutes)
        assertEquals(5, list[0].restMinutes)
    }

    @Test fun seedIsIdempotent() = runTest {
        profiles.seedIfEmpty(); profiles.seedIfEmpty()
        assertEquals(1, profiles.profiles.first().size)
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
}
