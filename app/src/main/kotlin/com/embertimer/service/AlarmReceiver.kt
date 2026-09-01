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
            // 已到期不在此处推进引擎(事件 replay=0,无订阅者时 settle 会丢):
            // 统一交服务完成 —— ensureServiceRunning -> 对账 FINISH_EXPIRED ->
            // engine.onExpired()(事件收集器经订阅握手已挂上);服务存活时 1s ticker 亦是兜底
            if (snap != null && snap.status == EngineStatus.RUNNING && snap.endElapsed > now) {
                g.alarmScheduler.arm(snap.endElapsed) // 未到期:闹钟可能在服务死后丢失,重武装
            }
            // 门控:仅 RUNNING 才起前台服务(含已到期场景,由服务对账推进;
            // PAUSED 由 UI(TimerCommands)恢复,前台服务的正规前台化由 Task 11 负责)
            if (snap != null && snap.status == EngineStatus.RUNNING) {
                ServiceLauncher.ensureServiceRunning(context)
            }
        }
    }
}
