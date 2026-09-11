package com.embertimer

import android.app.Application
import com.embertimer.di.AppGraph
import com.embertimer.service.TimerNotifications
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** open:测试用空 onCreate 子类注入受控 AppGraph(Robolectric 绕过真实装配) */
open class EmberApp : Application() {
    lateinit var graph: AppGraph

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.bootstrapAsync()
        TimerNotifications.ensureChannels(this)
        // v1.1 #5:报表通知闹钟(周日/月末 23:00)——每次进程冷启/开机补武装(闹钟不跨重启)
        com.embertimer.service.ReportAlarmScheduler(this).ensure()
        // v1.6 误触规则一次性清理:删除历史 <1 分钟段并扣回当日合计(SharedPreferences 标记只跑一次)
        val prefs = getSharedPreferences("ember_meta", MODE_PRIVATE)
        if (!prefs.getBoolean("pruned_mistouch_v16", false)) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                runCatching { graph.totalsRepo.pruneMisTouchSessions(60_000L) }
                prefs.edit().putBoolean("pruned_mistouch_v16", true).apply()
            }
        }
        // v1.10.8:历史段落按新的"3 分钟连续"规则重算一次当日合计(保证合计 == 每日详情时间段之和)
        if (!prefs.getBoolean("recomputed_v1108", false)) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                runCatching { graph.totalsRepo.recomputeAllDays() }
                prefs.edit().putBoolean("recomputed_v1108", true).apply()
            }
        }
        watchDataChanges()
    }

    /**
     * v1.10.8:自动备份触发 = **任何 App 数据变动**(尤其是计时累计变动)。
     * [AppGraph] 的 totalsRepo.dataTick 是数据表版本心跳,任何插入/更新/删除都会改变它;
     * 静默 [BACKUP_QUIET_MS] 后入队一次备份(WorkManager 同名 OneTime 自动合并,不堆积)。
     */
    private fun watchDataChanges() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            runCatching {
                graph.totalsRepo.dataTick()
                    .debounce(BACKUP_QUIET_MS)
                    .collect { com.embertimer.data.AutoBackupScheduler.scheduleNow(this@EmberApp) }
            }
        }
    }

    private companion object {
        /** 变动后静默期:避免计时过程中每个检查点都触发备份 */
        const val BACKUP_QUIET_MS = 20_000L
    }
}
