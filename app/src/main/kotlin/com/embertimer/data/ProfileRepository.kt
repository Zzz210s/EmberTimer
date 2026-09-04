package com.embertimer.data

import com.embertimer.data.db.ProfileDao
import com.embertimer.data.db.ProfileEntity
import com.embertimer.data.db.ProfileMode
import com.embertimer.timer.TimeProvider
import kotlinx.coroutines.flow.Flow

class ProfileRepository(
    private val dao: ProfileDao,
    private val time: TimeProvider,
) {
    val profiles: Flow<List<ProfileEntity>> = dao.observeAll()

    /** 同名已存在时返回 -1(调用方忽略);mode 走 ProfileMode 常量,缺省 COUNTDOWN 保持既有调用语义 */
    suspend fun create(name: String, workMinutes: Int, restMinutes: Int, mode: Int = ProfileMode.COUNTDOWN): Long {
        if (dao.byName(name) != null) return -1L
        return dao.insert(
            ProfileEntity(
                name = name, workMinutes = workMinutes, restMinutes = restMinutes,
                createdAt = time.now(), mode = mode,
            )
        )
    }

    suspend fun rename(id: Long, name: String) {
        dao.byId(id)?.let { dao.update(it.copy(name = name)) }
    }

    /** mode 由调用方传入(Task 7 起对话框带模式选择;编辑已存在 profile 时应传 p.mode 以免误改) */
    suspend fun updateDurations(id: Long, workMinutes: Int, restMinutes: Int, mode: Int = ProfileMode.COUNTDOWN) {
        dao.byId(id)?.let {
            dao.update(it.copy(workMinutes = workMinutes, restMinutes = restMinutes, mode = mode))
        }
    }

    suspend fun delete(entity: ProfileEntity) = dao.delete(entity)
    suspend fun byId(id: Long) = dao.byId(id)
    /** 读取 profile 的计时模式;行不存在时按缺省倒计时处理 */
    suspend fun modeOf(id: Long): Int = dao.modeById(id) ?: ProfileMode.COUNTDOWN
    suspend fun count() = dao.count()
}
