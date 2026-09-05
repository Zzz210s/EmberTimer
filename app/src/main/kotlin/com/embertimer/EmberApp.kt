package com.embertimer

import android.app.Application
import com.embertimer.di.AppGraph
import com.embertimer.service.TimerNotifications

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
    }
}
