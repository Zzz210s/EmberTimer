package com.embertimer

import android.app.Application
import com.embertimer.di.AppGraph
import com.embertimer.service.TimerNotifications
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
    }
}
