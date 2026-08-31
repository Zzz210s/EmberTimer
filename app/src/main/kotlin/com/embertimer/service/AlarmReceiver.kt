package com.embertimer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.embertimer.EmberApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as EmberApp
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeout(8_000) { app.graph.engine.awaitReady() }
                val g = app.graph
                val snap = g.engine.snapshot.value
                val now = g.time.elapsedRealtime()
                when {
                    snap != null && snap.status == com.embertimer.timer.EngineStatus.RUNNING && snap.endElapsed <= now ->
                        g.engine.onExpired()
                    snap != null && snap.status == com.embertimer.timer.EngineStatus.RUNNING ->
                        g.alarmScheduler.arm(snap.endElapsed) // 闹钟可能在服务死后丢失,重武装
                }
                ServiceLauncher.ensureServiceRunning(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** BootReceiver 复用的收尾逻辑标记(直接调用 onReceive 等价) */
    }
}
