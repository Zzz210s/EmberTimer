package com.embertimer.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import com.embertimer.EmberApp
import com.embertimer.di.AppGraph
import com.embertimer.timer.Checkpointer
import com.embertimer.timer.EngineEvent
import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase
import com.embertimer.timer.ReconcileAction
import com.embertimer.timer.Reconciler
import com.embertimer.timer.RuntimeSnapshot
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 前台计时服务:唯一负责通知/闹钟/提醒/落库反应的一方,也是唯一的引擎驱动者
 * (接收器不再直接改引擎,到期推进统一走本服务的对账/ticker)。
 *
 * 引擎事件流 replay=0(Fix Round 1):订阅之前发出的事件不会被补发,因此本服务用
 * 订阅握手保证顺序 —— 事件收集器挂上订阅(onSubscription 时完成 eventsSubscribed)
 * 之后,任何命令派发/ticker/对账才允许驱动引擎;5 秒超时兜底,防死收集器挂死命令。
 *
 * 所有引擎驱动逻辑(ticker 迭代、事件处理、命令派发、进程死亡对账)由单一
 * engineMutex 串行化,消除 checkpoint flush 与 settle 的交错双写(Concern 2)。
 * 事件 -> 服务反应的决策抽在 EventPolicy(纯 JVM 可测):settle 一律归属事件携带的
 * profileId;STOP 的拆除走排空感知路径(等 Reset 事件含 settle 落库处理完再停)。
 *
 * 前台化纪律(路由发现,Task 9 评审):所有 startForegroundService 入口 —— 含随后决定
 * 停止的路径(对账 STOP_SELF)—— 都必须在约 5 秒内调用 startForeground(API 31+ 超时抛
 * ForegroundServiceDidNotStartInTimeException)。故 onStartCommand 在发起任何异步处理前
 * 同步前台化:快照已就绪用真实通知,否则用最小占位,异步收集器随后替换。
 */
class TimerService : Service() {
    private lateinit var g: AppGraph
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastFlushElapsed = 0L
    private var lastFlushDate: String? = null

    /** 订阅握手:事件收集器挂上订阅后完成一次;命令派发/ticker/对账先等它(replay=0) */
    private val eventsSubscribed = CompletableDeferred<Unit>()

    /**
     * 首个 onStartCommand 已到达(验收发现,冷启动竞态):onCreate 启动的快照收集器在
     * Default 线程上可能在主线程尚未执行 onStartCommand 时就收到引擎的初始 null 快照,
     * 此时 awaitingSnapshot 仍为 false → 会走 IDLE 兜底 stopSelf() —— 而服务尚处于
     * startForegroundService 的 5 秒窗口内,从未 startForeground 就自停会被系统以
     * ForegroundServiceDidNotStartInTimeException 拉杀(每次点开始必现崩溃)。
     * 门控:快照收集器先等本 Deferred;onStartCommand 在设置完 awaitingSnapshot 之后
     * 再 complete(CompletableDeferred 的完成与等待有 happens-before,收集器必然
     * 观察到最终标志状态)。
     */
    private val firstCommandReceived = CompletableDeferred<Unit>()

    /** 串行化全部引擎驱动逻辑;flushCheckpoint/flushSettle 由调用方持锁调用(勿嵌套加锁) */
    private val engineMutex = Mutex()

    /**
     * START 命令已收到但引擎快照尚未落地期间,快照收集器不应因初始 null 拆除服务
     * (命令派发与收集器在 Default 线程池并发,无顺序保证,否则偶发“点开始服务即自毁”)。
     * 命令派发协程结束时清零。RESTART_PHASE 仅在 PAUSED(快照非空)时有意义,无需保护。
     */
    @Volatile private var awaitingSnapshot = false

    /**
     * STOP 命令的排空等待(F3a):ACTION_STOP 派发协程在 reset 前创建,
     * onEvent(Reset) 在 settle 落库后完成;派发协程限时等待后才允许拆除服务。
     */
    private var stopDrained: CompletableDeferred<Unit>? = null

    /**
     * STOP 已派发、事件尚未排空期间(F3a)。置位于 engineMutex 内 reset() 之前:
     * 引擎把快照置 null 的写发生在标志置位之后,快照收集器观察到 null 即能观察到本标志。
     * onSnapshot(null) 据此让位(拆除改由派发协程负责),派发协程 finally 清零。
     */
    private var stopDraining = false

    override fun onCreate() {
        super.onCreate()
        g = (application as EmberApp).graph
        scope.launch {
            g.engine.awaitReady()
            // 门控见 firstCommandReceived:初始 null 快照必须等首个 onStartCommand 的标志写完
            launch {
                firstCommandReceived.await()
                g.engine.snapshot.collect { onSnapshot(it) }
            }
            launch {
                g.engine.events
                    // onSubscription 在订阅注册完成后执行:此刻起后续事件必达本收集器
                    .onSubscription { eventsSubscribed.complete(Unit) }
                    .collect { onEvent(it) }
            }
            // ticker 首轮即可能触发 onExpired(引擎变更),必须等订阅握手完成后再启动
            awaitEventsSubscribed()
            tickerLoop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 门控标志先行(快照收集器在等 firstCommandReceived,完成后它看到的必是最终状态):
        // START 在途标志在前,再放行初始快照发射,最后才同步前台化与异步处理
        val action0 = intent?.action
        if (action0 == ACTION_START) awaitingSnapshot = true
        firstCommandReceived.complete(Unit)
        // 先同步前台化(见类注释),再异步处理;null intent(START_STICKY 重启)与
        // 无 action intent(ServiceLauncher)同走对账。
        goForeground(g.engine.snapshot.value)
        val action = intent?.action
        if (action == null) {
            scope.launch {
                g.engine.awaitReady()
                awaitEventsSubscribed()
                reconcileAfterProcessDeath()
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
                        )
                        ACTION_PAUSE -> g.engine.pause()
                        ACTION_RESUME -> g.engine.resume()
                        ACTION_STOP -> {
                            // F3a:排空标志在锁内置位(reset 之前):onSnapshot(null) 看到它
                            // 就让位,拆除由本派发协程在 settle 落库后统一负责
                            stopDraining = true
                            stopDrained = CompletableDeferred()
                            g.engine.reset()
                        }
                        ACTION_SKIP -> g.engine.skip()
                        ACTION_RESTART_PHASE -> g.engine.restartPhase(
                            intent.getLongExtra(EXTRA_PROFILE_ID, -1),
                            intent.getLongExtra(EXTRA_WORK_MILLIS, 0),
                            intent.getLongExtra(EXTRA_REST_MILLIS, 0),
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

    /**
     * STOP 的排空感知拆除(F3a):等 Reset 事件(含 settle 落库)处理完再停服,
     * 否则 onSnapshot(null) 的立即 stopSelf 会在事件处理前取消作用域、丢 settle。
     * 3 秒上限:收集器死亡/DB 挂死时不无限等待(超时仅记日志,settle 可能丢失,有界)。
     * 等待期间可能有新 START 落地,仅当快照仍为空才真正拆除。
     */
    private suspend fun awaitStopDrainedAndTearDown() {
        val drained = stopDrained ?: return
        if (withTimeoutOrNull(3_000) { drained.await() } == null) {
            Log.w(TAG, "stop/Reset-event settle drain timed out (bounded 3s); settle may be lost")
        }
        if (g.engine.snapshot.value == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** 握手等待:5 秒兜底,超时则放弃保证继续执行(死收集器不挂死命令);
     *  超时必须大声降级(F6):此后引擎事件将无订阅者而静默丢弃 */
    private suspend fun awaitEventsSubscribed() {
        if (withTimeoutOrNull(5_000) { eventsSubscribed.await() } == null) {
            Log.w(TAG, "events subscriber handshake timed out; engine events will be dropped")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun onSnapshot(snap: RuntimeSnapshot?) {
        // F4:快照收集器同样不允许异常杀死(异常杀死后服务失去前台化/拆除反应)
        runCatching {
            when {
                snap != null -> goForeground(snap)
                awaitingSnapshot -> {} // START 在途:初始 null 不是终态,勿拆除
                // F3a:STOP 排空中,拆除由 ACTION_STOP 派发协程在 settle 落库后负责;
                // 此处立即 stopSelf 会在 Reset 事件处理前取消作用域、丢 settle
                stopDraining -> {}
                else -> {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }.onFailure { Log.w(TAG, "snapshot handler failed for $snap", it) }
    }

    /** 前台化:快照可用时用进行中通知,否则(引擎未就绪/IDLE)用最小占位 */
    private fun goForeground(snap: RuntimeSnapshot?) {
        val n = if (snap != null) TimerNotifications.inProgress(this, snap)
        else TimerNotifications.minimal(this)
        // FOREGROUND_SERVICE_TYPE_SPECIAL_USE 自 API 34 才存在:34 以下平台无法解析
        // manifest 中的 specialUse 位,传该类型会抛 IllegalArgumentException
        // (androidx ServiceCompat 在 29-33 上同样把它掩码掉)。34 以下用无类型重载。
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(TimerNotifications.ID_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(TimerNotifications.ID_PROGRESS, n)
        }
    }

    private suspend fun onEvent(ev: EngineEvent) {
        engineMutex.withLock {
            // F4:单个事件处理失败(如 Room 写异常)不得杀死收集器 —— replay=0 下收集器挂掉
            // 意味着后续全部事件静默丢失。捕获记日志保持存活;不自动重试 flush:落库是增量式的,
            // 盲目重试可能重复计数,留待下一 checkpoint 对账
            runCatching {
                for (fx in EventPolicy.decide(ev, g.engine.snapshot.value)) {
                    when (fx) {
                        is EventEffect.Settle -> flushSettle(fx.millis, fx.profileId)
                        is EventEffect.Arm -> g.alarmScheduler.arm(fx.endElapsed)
                        EventEffect.CancelAlarm -> g.alarmScheduler.cancel()
                        EventEffect.ForceCheckpoint -> flushCheckpoint(force = true)
                        is EventEffect.Remind -> remind(fx.workFinished)
                    }
                }
                // F3a:Reset 事件的 settle 已(尝试)落库,派发协程据此继续拆除
                if (ev is EngineEvent.Reset) stopDrained?.complete(Unit)
            }.onFailure { Log.w(TAG, "event handler failed for $ev", it) }
        }
    }

    /**
     * 播放强提醒并发 heads-up 通知,数秒自停,无需交互。
     * 通知与播放均在锁外协程内执行(F8):ensureChannels/nm.notify 是同步 binder 调用,
     * 原先在 engineMutex 临界区内直接调用会拖长持锁时间
     */
    private fun remind(workFinished: Boolean) {
        scope.launch {
            val intensity = g.settingsRepo.reminderIntensity.first()
            g.reminderPlayer.play(intensity)
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return@launch
            val nm = getSystemService(NotificationManager::class.java) ?: return@launch
            TimerNotifications.ensureChannels(this@TimerService)
            runCatching {
                nm.notify(TimerNotifications.ID_REMINDER, TimerNotifications.phaseDone(this@TimerService, workFinished))
            }
        }
    }

    private fun tickerLoop() = scope.launch {
        while (isActive) {
            engineMutex.withLock {
                val snap = g.engine.snapshot.value
                if (snap != null && snap.status == EngineStatus.RUNNING &&
                    snap.endElapsed <= g.time.elapsedRealtime()
                ) {
                    g.engine.onExpired()
                }
                val now = g.time.elapsedRealtime()
                val today = LocalDate.now().toString()
                if (lastFlushDate != today || now - lastFlushElapsed >= 60_000) {
                    flushCheckpoint(force = false)
                }
            }
            delay(1_000)
        }
    }

    /**
     * 60 秒增量落库;force 用于阶段切换/启动时。
     * 调用方必须已持有 engineMutex(内部不再加锁,嵌套加锁会死锁)。
     */
    private suspend fun flushCheckpoint(force: Boolean) {
        val snap = g.engine.snapshot.value ?: return
        val now = g.time.elapsedRealtime()
        val today = LocalDate.now().toString()
        val f = Checkpointer.compute(snap, now, today)
        if (f.deltaMillis > 0 || (force && snap.status == EngineStatus.RUNNING && snap.phase == Phase.WORK)) {
            g.totalsRepo.addWork(f.date, snap.profileId, f.deltaMillis)
            g.engine.onCheckpointFlushed(f.date, f.newAccum)
        }
        lastFlushElapsed = now
        lastFlushDate = today
    }

    /**
     * settle(重置/跳过/阶段结束)直接落库。调用方必须已持有 engineMutex。
     * profileId 一律由事件携带(Concern 3/F2):事件发出与处理之间快照可能已被
     * STOP/换 profile 重启清空或替换,不得事后读快照取归属。
     */
    private suspend fun flushSettle(settleMillis: Long, profileId: Long) {
        if (settleMillis <= 0) return
        g.totalsRepo.addWork(LocalDate.now().toString(), profileId, settleMillis)
    }

    private suspend fun reconcileAfterProcessDeath() {
        // 调用方(null intent 派发)已 awaitReady + 订阅握手后才进入,此处不再重复等待(F9)
        engineMutex.withLock {
            when (Reconciler.decide(g.engine.snapshot.value, g.time.elapsedRealtime())) {
                ReconcileAction.STOP_SELF -> {
                    // START 在途时不拆(F3b):null 对账可能抢在 START 落地前看到旧 null,
                    // 无条件 stopSelf 会把服务从 START 派发下抽走(取消闹钟武装/强制 checkpoint)
                    if (!awaitingSnapshot) {
                        // 同步前台化已在 onStartCommand 完成,此处停止不会触发
                        // ForegroundServiceDidNotStartInTimeException
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
                ReconcileAction.FINISH_EXPIRED -> g.engine.onExpired()
                ReconcileAction.RESUME_ACTIVE -> g.engine.snapshot.value?.let {
                    goForeground(it)
                    g.alarmScheduler.arm(it.endElapsed)
                }
                ReconcileAction.SHOW_PAUSED -> g.engine.snapshot.value?.let { goForeground(it) }
            }
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
    }
}
