package com.embertimer.data

import androidx.room.withTransaction
import com.embertimer.data.db.DailyTotalDao
import com.embertimer.data.db.DailyTotalEntity
import com.embertimer.data.db.DayTotal
import com.embertimer.data.db.EmberDatabase
import com.embertimer.data.db.ProfileTotal
import com.embertimer.timer.TimeProvider
import kotlinx.coroutines.flow.Flow

class DailyTotalRepository(
    private val db: EmberDatabase,
    private val dao: DailyTotalDao,
    private val time: TimeProvider,
) {
    /** API 26-28 的 SQLite 不支持 UPSERT 语法,用事务读-改-写兼容 */
    suspend fun addWork(date: String, profileId: Long, deltaMillis: Long) {
        if (deltaMillis <= 0) return
        db.withTransaction {
            val cur = dao.getWorkMillis(date, profileId) ?: 0L
            dao.upsert(DailyTotalEntity(date, profileId, cur + deltaMillis, time.now()))
        }
    }

    fun dayTotals(from: String): Flow<List<DayTotal>> = dao.observeDayTotals(from)
    fun profileTotals(): Flow<List<ProfileTotal>> = dao.observeProfileTotals()

    suspend fun breakdownByDate(date: String): List<ProfileTotal> = dao.breakdownByDate(date)
}
