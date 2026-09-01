package com.embertimer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.embertimer.EmberApp
import com.embertimer.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

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

/**
 * 广播接收器共用骨架:goAsync -> 协程(SupervisorJob + Default) -> 8s awaitReady 超时
 * -> finally pending.finish()。超时/窗口纪律集中在此一处,receiver 只保留门控与领域逻辑。
 */
fun BroadcastReceiver.goAsyncWithGraph(
    context: Context,
    body: suspend (AppGraph) -> Unit,
) {
    val pending = goAsync()
    val app = context.applicationContext as EmberApp
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            withTimeout(8_000) { app.graph.engine.awaitReady() }
            body(app.graph)
        } finally {
            pending.finish()
        }
    }
}
