package com.embertimer.service

import android.util.Log
import com.embertimer.di.AppGraph
import com.embertimer.timer.EngineStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 到期驱动主腿(v1.3 拆分):1s 轮询到期推进 + 60s 检查点落账,引擎锁内执行。
 * #4 加固:单次迭代内任何异常都不得杀死循环(ticker 死亡后到期推进只剩闹钟/对账两腿;
 * 闹钟未授权退化为 inexact 时 Doze 下可被大幅推迟 → “完成卡住”)。捕获记日志后继续
 * 调度(自愈);CancellationException 原样上抛(onDestroy 取消作用域的正常退出路径)。
 */
internal class TickDriver(
    private val graph: AppGraph,
    private val ledger: TickLedger,
    private val mutex: Mutex,
) {
    fun loop(scope: CoroutineScope) = scope.launch {
        while (isActive) {
            try {
                mutex.withLock {
                    val snap = graph.engine.snapshot.value
                    if (snap != null && snap.status == EngineStatus.RUNNING &&
                        snap.endElapsed <= graph.time.elapsedRealtime()
                    ) {
                        graph.engine.onExpired()
                    }
                    ledger.flush(graph.engine.snapshot.value, graph.time.elapsedRealtime(), force = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("TimerService", "ticker iteration failed; continuing (self-heal)", e)
            }
            // v1.11.0 自适应节拍(省电):剩余时间还长时 15s 一次即可 —— 精确到点由 EXACT 闹钟负责、
            // 通知栏倒计时由系统 Chronometer 绘制、界面倒计时由 UI 自己的 1s 帧驱动,故服务侧无需 1s 轮询。
            // 临近到点(<=5s)才回到 500ms 高频,保证阶段切换及时。
            delay(nextDelayMs(graph.engine.snapshot.value, graph.time.elapsedRealtime()))
        }
    }
}

/** 下一次轮询间隔:临近到点(<=5s)高频 500ms,其余 15s(60s 检查点落账精度足够) */
internal fun nextDelayMs(snap: com.embertimer.timer.RuntimeSnapshot?, nowElapsed: Long): Long {
    if (snap == null || snap.status != EngineStatus.RUNNING || snap.countUp) return 15_000L
    val remaining = snap.endElapsed - nowElapsed
    return if (remaining <= 5_000L) 500L else 15_000L
}
