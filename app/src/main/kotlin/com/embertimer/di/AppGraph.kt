package com.embertimer.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import com.embertimer.data.DailyTotalRepository
import com.embertimer.data.ProfileRepository
import com.embertimer.data.RuntimeStateStore
import com.embertimer.data.SettingsRepository
import com.embertimer.data.db.EmberDatabase
import com.embertimer.timer.SystemTimeProvider
import com.embertimer.timer.TimerEngine
import com.embertimer.ui.home.HomeViewModel
import com.embertimer.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

private val directExecutor = Executor { it.run() }

class AppGraph(
    context: Context,
    useInMemoryDb: Boolean = false,
    storeFileName: String = "ember_settings",
) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val time = SystemTimeProvider()

    val db: EmberDatabase = if (useInMemoryDb)
        // Robolectric legacy SQLite 影子按线程登记连接指针,Room 池化连接跨 arch_disk_io 线程
        // 复用会触发 Illegal connection pointer;测试路径用直通执行器把查询钉在调用线程上
        Room.inMemoryDatabaseBuilder(context, EmberDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
    else EmberDatabase.build(context)

    val profileRepo = ProfileRepository(db.profileDao(), time)
    val totalsRepo = DailyTotalRepository(db, db.dailyTotalDao(), time)

    private val ds = PreferenceDataStoreFactory.create(scope = appScope) {
        context.preferencesDataStoreFile(storeFileName)
    }
    val settingsRepo = SettingsRepository(ds)
    val runtimeStore = RuntimeStateStore(ds)

    val engine = TimerEngine(time, appScope, persist = { runtimeStore.save(it) })

    val alarmScheduler = com.embertimer.service.AlarmScheduler(context, time)
    val reminderPlayer = com.embertimer.service.ReminderPlayer(context)

    val vmFactory = viewModelFactory {
        // graph 即 this@AppGraph,闭包捕获(字面 graph 无成员可解析,需显式标签)
        initializer { HomeViewModel(this@AppGraph) }
        initializer { SettingsViewModel(this@AppGraph) }
    }

    suspend fun bootstrap() {
        profileRepo.seedIfEmpty()
        engine.restore(runtimeStore.flow.first())
    }

    fun bootstrapAsync() {
        appScope.launch { bootstrap() }
    }
}
