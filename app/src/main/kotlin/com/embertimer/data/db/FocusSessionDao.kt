package com.embertimer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FocusSessionDao {
    @Insert
    suspend fun insertAll(rows: List<FocusSessionEntity>)

    /** 某日(本地时区 [dayStartMs, dayEndMs))内全部段,按开始时间升序 */
    @Query(
        "SELECT * FROM focus_session WHERE startAt >= :dayStartMs AND startAt < :dayEndMs " +
            "ORDER BY startAt"
    )
    suspend fun between(dayStartMs: Long, dayEndMs: Long): List<FocusSessionEntity>

    @Query("SELECT COUNT(*) FROM focus_session")
    suspend fun count(): Int
}
