package com.embertimer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DayTotal(val date: String, val total: Long)
data class ProfileTotal(val profileId: Long, val total: Long)
/** 报表区间明细:一行 = 某日某配置的累计专注时长(date 升序,profileId 升序) */
data class DayProfileTotal(val date: String, val profileId: Long, val total: Long)

@Dao
interface DailyTotalDao {
    @Query("SELECT workMillis FROM daily_total WHERE date = :date AND profileId = :profileId")
    suspend fun getWorkMillis(date: String, profileId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: DailyTotalEntity)

    @Query("SELECT date, SUM(workMillis) AS total FROM daily_total WHERE date >= :from GROUP BY date ORDER BY date")
    fun observeDayTotals(from: String): Flow<List<DayTotal>>

    @Query("SELECT profileId, SUM(workMillis) AS total FROM daily_total GROUP BY profileId")
    fun observeProfileTotals(): Flow<List<ProfileTotal>>

    @Query("SELECT profileId, SUM(workMillis) AS total FROM daily_total WHERE date = :date GROUP BY profileId")
    suspend fun breakdownByDate(date: String): List<ProfileTotal>

    @Query(
        "SELECT date, profileId, SUM(workMillis) AS total FROM daily_total " +
            "WHERE date >= :from AND date <= :to GROUP BY date, profileId ORDER BY date, profileId"
    )
    suspend fun rangeBreakdown(from: String, to: String): List<DayProfileTotal>
}
