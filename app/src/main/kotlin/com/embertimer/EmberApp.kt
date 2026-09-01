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
    }
}
