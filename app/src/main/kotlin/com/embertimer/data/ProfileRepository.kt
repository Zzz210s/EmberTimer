package com.embertimer.data

import com.embertimer.data.db.ProfileDao
import com.embertimer.data.db.ProfileEntity
import com.embertimer.timer.TimeProvider
import kotlinx.coroutines.flow.Flow

class ProfileRepository(
    private val dao: ProfileDao,
    private val time: TimeProvider,
) {
    val profiles: Flow<List<ProfileEntity>> = dao.observeAll()

    suspend fun seedIfEmpty() {
        if (dao.count() == 0) {
            dao.insert(ProfileEntity(name = "番茄", workMinutes = 25, restMinutes = 5, createdAt = time.now()))
        }
    }

    suspend fun create(name: String, workMinutes: Int, restMinutes: Int): Long {
        if (dao.byName(name) != null) return -1L
        return dao.insert(ProfileEntity(name = name, workMinutes = workMinutes, restMinutes = restMinutes, createdAt = time.now()))
    }

    suspend fun rename(id: Long, name: String) {
        dao.byId(id)?.let { dao.update(it.copy(name = name)) }
    }

    suspend fun updateDurations(id: Long, workMinutes: Int, restMinutes: Int) {
        dao.byId(id)?.let { dao.update(it.copy(workMinutes = workMinutes, restMinutes = restMinutes)) }
    }

    suspend fun delete(entity: ProfileEntity) = dao.delete(entity)
    suspend fun byId(id: Long) = dao.byId(id)
    suspend fun count() = dao.count()
}
