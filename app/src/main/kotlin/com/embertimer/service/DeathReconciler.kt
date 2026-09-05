package com.embertimer.service

import android.util.Log
import com.embertimer.di.AppGraph
import com.embertimer.timer.ReconcileAction
import com.embertimer.timer.Reconciler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 进程死亡/冷启动对账(v1.3 拆分):原 TimerService.reconcileAfterProcessDeath 行为搬移。
 * 依赖注入式隔离 service 副作用:前台化由 notifier 提供,自停由 onSelfStop 回调。
 */
internal class DeathReconciler(
    private val graph: AppGraph,
    private val mutex: Mutex,
    private val isAwaitingStart: () -> Boolean,
    private val notifier: ServiceNotifier,
    private val onSelfStop: () -> Unit,
) {
    /** 到期推进主腿,异常不得上抛(派发协程无捕获时,SupervisorJob 下未捕获异常虽不
     *  取消兄弟协程,但会直达线程默认 handler 拉崩进程);捕获记日志保活本腿。 */
    suspend fun run() {
        runCatching {
            mutex.withLock {
                when (Reconciler.decide(graph.engine.snapshot.value, graph.time.elapsedRealtime())) {
                    ReconcileAction.STOP_SELF -> {
                        // START 在途时不拆(F3b):null 对账可能抢在 START 落地前看到旧 null,
                        // 无条件 stopSelf 会把服务从 START 派发下抽走(取消闹钟武装/强制 checkpoint)
                        if (!isAwaitingStart()) onSelfStop()
                    }
                    ReconcileAction.FINISH_EXPIRED -> graph.engine.onExpired()
                    ReconcileAction.RESUME_ACTIVE -> graph.engine.snapshot.value?.let {
                        notifier.goForeground(it)
                        // 正计时无到期:重启对账不武装阶段到期闹钟(Task 7 / #10)
                        if (!it.countUp) graph.alarmScheduler.arm(it.endElapsed)
                    }
                    ReconcileAction.SHOW_PAUSED -> graph.engine.snapshot.value?.let { notifier.goForeground(it) }
                }
            }
        }.onFailure { Log.w("TimerService", "reconcile after process death failed", it) }
    }
}
