package com.embertimer.service

import android.content.Context
import android.content.Intent

object ServiceLauncher {
    /** 后台(广播)起服务:优先 startForegroundService,被拒则退回 startService */
    fun ensureServiceRunning(context: Context) {
        val intent = Intent(context, TimerService::class.java)
        try {
            context.startForegroundService(intent)
        } catch (_: Exception) {
            runCatching { context.startService(intent) }
        }
    }
}
