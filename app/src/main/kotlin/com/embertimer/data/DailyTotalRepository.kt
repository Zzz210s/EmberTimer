package com.embertimer.data

import androidx.room.withTransaction
import com.embertimer.data.db.DailyTotalDao
import com.embertimer.data.db.FocusSessionDao
import com.embertimer.data.db.DailyTotalEntity
import com.embertimer.data.db.FocusSessionEntity
import com.embertimer.data.db.DayProfileTotal
import com.embertimer.data.db.DayTotal
import com.embertimer.data.db.EmberDatabase
import com.embertimer.data.db.ProfileTotal
import com.embertimer.timer.TimeProvider
import kotlinx.coroutines.flow.Flow

class DailyTotalRepository(
    private val db: EmberDatabase,
    private val dao: DailyTotalDao,
    private val sessionDao: FocusSessionDao,
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

    /** 区间内每日每配置明细(from/to 闭区间);供报表按日/按配置在内存聚合 */
    suspend fun rangeBreakdown(from: String, to: String): List<DayProfileTotal> =
        dao.rangeBreakdown(from, to)

    /** 落一段专注(墙钟窗口),按本地午夜切分后逐段入库;zone 注入便于测试 */
    suspend fun recordWorkSession(
        profileId: Long,
        startAt: Long,
        endAt: Long,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ) {
        val rows = buildSessionRows(profileId, startAt, endAt, zone)
        if (rows.isNotEmpty()) db.withTransaction { sessionDao.insertAll(rows) }
    }

    /** 某本地日 [dayStartMs, dayEndMs) 内的全部段(升序);供每日详情小行 */
    suspend fun sessionsBetween(dayStartMs: Long, dayEndMs: Long): List<FocusSessionEntity> =
        sessionDao.between(dayStartMs, dayEndMs)

    /** 墙钟窗口内全部段(起点升序);供报表时段分布 */
    suspend fun sessionsBetweenMs(startMs: Long, endMs: Long): List<FocusSessionEntity> =
        sessionDao.betweenMs(startMs, endMs)
}
