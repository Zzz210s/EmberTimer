package com.embertimer

import android.app.Application
import com.embertimer.di.AppGraph
import com.embertimer.service.TimerNotifications

class EmberApp : Application() {
    lateinit var graph: AppGraph

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.bootstrapAsync()
        TimerNotifications.ensureChannels(this)
    }
}
