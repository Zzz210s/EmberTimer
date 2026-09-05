package com.embertimer.service

import android.app.Service
import android.content.Intent
import android.util.Log
import com.embertimer.EmberApp
import com.embertimer.di.AppGraph
import com.embertimer.timer.EngineEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 前台计时服务:唯一的引擎驱动者与通知/闹钟/落库反应方。replay=0 事件流用订阅握手
 * 保证顺序(5s 兜底);全部引擎驱动由 engineMutex 串行化。v1.3 拆分(纯搬移,行为不变):
 * 前台化/提醒 ServiceNotifier;检查点 TickLedger;事件反应 EventApplier;对账 DeathReconciler;
 * 到期轮询 TickDriver。前台化纪律:onStartCommand 在异步处理前同步 startForeground。
 */
class TimerService : Service() {
    internal lateinit var g: AppGraph
    private lateinit var notifier: ServiceNotifier
    private lateinit var ledger: TickLedger
    private lateinit var reconciler: DeathReconciler
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 订阅握手:事件收集器挂上订阅后完成一次;命令派发/ticker/对账先等它(replay=0) */
    internal val eventsSubscribed = CompletableDeferred<Unit>()

    /** 冷启动竞态门控:快照收集器等首个 onStartCommand 写完标志(否则 IDLE 兜底 stopSelf
     *  落在 startForegroundService 5 秒窗口内被拉杀)。 */
    private val firstCommandReceived = CompletableDeferred<Unit>()

    /** 串行化全部引擎驱动逻辑;落库辅助类由调用方持锁调用(勿嵌套加锁) */
    private val engineMutex = Mutex()

    /**
     * START 命令已收到但引擎快照尚未落地期间,快照收集器不应因初始 null 拆除服务。
     * 命令派发协程结束时清零。RESTART_PHASE 仅在 PAUSED(快照非空)时有意义。
     */
    @Volatile private var awaitingSnapshot = false

    /**
     * STOP 命令的排空等待(F3a):ACTION_STOP 派发协程在 reset 前创建,
     * onEvent(Reset) 在 settle 落库后完成;派发协程限时等待后才允许拆除服务。
     */
    internal var stopDrained: CompletableDeferred<Unit>? = null

    /**
     * STOP 已派发、事件尚未排空期间(F3a)。置位于 engineMutex 内 reset() 之前:
     * onSnapshot(null) 观察到 null 即能观察到本标志,据此让位(拆除由派发协程负责)。
     */
    private var stopDraining = false

    override fun onCreate() {
        super.onCreate()
        g = (application as EmberApp).graph
        notifier = ServiceNotifier(this, g, scope)
        ledger = TickLedger(g)
        reconciler = DeathReconciler(
            graph = g,
            mutex = engineMutex,
            isAwaitingStart = { awaitingSnapshot },
            notifier = notifier,
            onSelfStop = {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            },
        )
        scope.launch {
            g.engine.awaitReady()
            launch {
                firstCommandReceived.await()
                g.engine.snapshot.collect { onSnapshot(it) }
            }
            launch {
                g.engine.events
                    .onSubscription { eventsSubscribed.complete(Unit) }
                    .collect { onEvent(it) }
            }
            // ticker 首轮即可能触发 onExpired,必须等订阅握手完成后再启动
            awaitEventsSubscribed()
            TickDriver(g, ledger, engineMutex).loop(scope)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action0 = intent?.action
        if (action0 == ACTION_START) awaitingSnapshot = true
        firstCommandReceived.complete(Unit)
        // 同步前台化后再异步处理;null/intent-less(START_STICKY/ServiceLauncher)同走对账。
        notifier.goForeground(g.engine.snapshot.value)
        val action = intent?.action
        if (action == null) {
            scope.launch {
                g.engine.awaitReady()
                awaitEventsSubscribed()
                reconciler.run()
            }
            return START_STICKY
        }
        val createsSnapshot = action == ACTION_START
        scope.launch {
            g.engine.awaitReady()
            awaitEventsSubscribed()
            try {
                engineMutex.withLock {
                    when (action) {
                        ACTION_START -> g.engine.start(
                            intent.getLongExtra(EXTRA_PROFILE_ID, -1),
                            intent.getLongExtra(EXTRA_WORK_MILLIS, 0),
                            intent.getLongExtra(EXTRA_REST_MILLIS, 0),
                            countUp = intent.getBooleanExtra(EXTRA_COUNT_UP, false),
                        )
                        ACTION_PAUSE -> g.engine.pause()
                        ACTION_RESUME -> g.engine.resume()
                        ACTION_STOP -> {
                            // F3a:排空标志锁内置位;onSnapshot(null) 让位,拆除由派发协程负责
                            stopDraining = true
                            stopDrained = CompletableDeferred()
                            g.engine.reset()
                        }
                        ACTION_SKIP -> g.engine.skip()
                        ACTION_RESTART_PHASE -> g.engine.restartPhase(
                            intent.getLongExtra(EXTRA_PROFILE_ID, -1),
                            intent.getLongExtra(EXTRA_WORK_MILLIS, 0),
                            intent.getLongExtra(EXTRA_REST_MILLIS, 0),
                            countUp = intent.getBooleanExtra(EXTRA_COUNT_UP, false),
                        )
                    }
                }
                if (action == ACTION_STOP) awaitStopDrainedAndTearDown()
            } finally {
                if (createsSnapshot) awaitingSnapshot = false
                if (action == ACTION_STOP) stopDraining = false
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun onSnapshot(snap: com.embertimer.timer.RuntimeSnapshot?) {
        // F4:快照收集器不允许异常杀死(杀死后服务失去前台化/拆除反应)
        runCatching {
            when {
                snap != null -> notifier.goForeground(snap)
                awaitingSnapshot -> {} // START 在途:初始 null 不是终态,勿拆除
                // F3a:STOP 排空中,拆除由 ACTION_STOP 派发协程在 settle 落库后负责
                stopDraining -> {}
                else -> {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }.onFailure { Log.w(TAG, "snapshot handler failed for $snap", it) }
    }

    private suspend fun onEvent(ev: EngineEvent) {
        engineMutex.withLock {
            // F4:单事件失败(如 Room 异常)不得杀死收集器(replay=0 下挂掉即后续事件静默丢);
            // 不自动重试 flush(增量落库,盲目重试会重复计数)。
            runCatching {
                val resetSeen = EventApplier(g, ledger, notifier).apply(ev)
                // F3a:Reset 事件的 settle 已(尝试)落库,派发协程据此继续拆除
                if (resetSeen) stopDrained?.complete(Unit)
            }.onFailure { Log.w(TAG, "event handler failed for $ev", it) }
        }
    }

    companion object {
        private const val TAG = "TimerService"
        const val ACTION_START = "com.embertimer.action.START"
        const val ACTION_PAUSE = "com.embertimer.action.PAUSE"
        const val ACTION_RESUME = "com.embertimer.action.RESUME"
        const val ACTION_STOP = "com.embertimer.action.STOP"
        const val ACTION_SKIP = "com.embertimer.action.SKIP"
        const val ACTION_RESTART_PHASE = "com.embertimer.action.RESTART_PHASE"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_WORK_MILLIS = "work_millis"
        const val EXTRA_REST_MILLIS = "rest_millis"
        const val EXTRA_COUNT_UP = "count_up"
    }
}
