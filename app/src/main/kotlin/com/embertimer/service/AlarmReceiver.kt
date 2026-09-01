package com.embertimer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.embertimer.timer.EngineStatus

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        goAsyncWithGraph(context) { g ->
            val snap = g.engine.snapshot.value
            val now = g.time.elapsedRealtime()
            when {
                snap != null && snap.status == EngineStatus.RUNNING && snap.endElapsed <= now ->
                    g.engine.onExpired()
                snap != null && snap.status == EngineStatus.RUNNING ->
                    g.alarmScheduler.arm(snap.endElapsed) // 闹钟可能在服务死后丢失,重武装
            }
            // 门控:仅 RUNNING 才起前台服务(onExpired 推进后重读快照,新阶段仍会起服务);
            // PAUSED 由 UI(TimerCommands)恢复,前台服务的正规前台化由 Task 11 负责
            if (g.engine.snapshot.value?.status == EngineStatus.RUNNING) {
                ServiceLauncher.ensureServiceRunning(context)
            }
        }
    }
}
