package com.embertimer.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
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

    /** 串行化全部引擎驱动逻辑;flushCheckpoint/flushSettle 由调用方持锁调用(勿嵌套加锁) */
    private val engineMutex = Mutex()

    /**
     * START 命令已收到但引擎快照尚未落地期间,快照收集器不应因初始 null 拆除服务
     * (命令派发与收集器在 Default 线程池并发,无顺序保证,否则偶发“点开始服务即自毁”)。
     * 命令派发协程结束时清零。RESTART_PHASE 仅在 PAUSED(快照非空)时有意义,无需保护。
     */
    @Volatile private var awaitingSnapshot = false

    override fun onCreate() {
        super.onCreate()
        g = (application as EmberApp).graph
        scope.launch {
            g.engine.awaitReady()
            launch { g.engine.snapshot.collect { onSnapshot(it) } }
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
        if (createsSnapshot) awaitingSnapshot = true
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
                        ACTION_RESET -> g.engine.reset()
                        ACTION_SKIP -> g.engine.skip()
                        ACTION_RESTART_PHASE -> g.engine.restartPhase(
                            intent.getLongExtra(EXTRA_PROFILE_ID, -1),
                            intent.getLongExtra(EXTRA_WORK_MILLIS, 0),
                            intent.getLongExtra(EXTRA_REST_MILLIS, 0),
                        )
                    }
                }
            } finally {
                if (createsSnapshot) awaitingSnapshot = false
            }
        }
        return START_STICKY
    }

    /** 握手等待:5 秒兜底,超时则放弃保证继续执行(死收集器不挂死命令) */
    private suspend fun awaitEventsSubscribed() {
        withTimeoutOrNull(5_000) { eventsSubscribed.await() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun onSnapshot(snap: RuntimeSnapshot?) {
        if (snap == null) {
            if (awaitingSnapshot) return // START 在途:初始 null 不是终态,勿拆除
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        goForeground(snap)
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
            when (ev) {
                is EngineEvent.PhaseStarted -> {
                    g.alarmScheduler.arm(ev.endElapsed)
                    flushCheckpoint(force = true)
                }
                is EngineEvent.Resumed -> g.alarmScheduler.arm(ev.endElapsed)
                is EngineEvent.PhaseFinished -> {
                    g.alarmScheduler.cancel()
                    g.engine.snapshot.value?.let { flushSettle(ev.settleMillis, it.profileId) }
                    if (ev.auto) remind(ev.finished == Phase.WORK)
                }
                is EngineEvent.PhaseRestarted -> {
                    g.alarmScheduler.cancel()
                    // 结算归属旧 profile:换 profile 重开时已累计工作量不跟新 profile 走(Concern 3)
                    flushSettle(ev.settleMillis, ev.profileId)
                    g.alarmScheduler.arm(ev.endElapsed)
                }
                is EngineEvent.Paused -> g.alarmScheduler.cancel()
                is EngineEvent.Reset -> {
                    g.alarmScheduler.cancel()
                    flushSettle(ev.settleMillis, ev.profileId)
                }
            }
        }
    }

    /** 播放强提醒并发 heads-up 通知,数秒自停,无需交互 */
    private fun remind(workFinished: Boolean) {
        scope.launch {
            val intensity = g.settingsRepo.reminderIntensity.first()
            g.reminderPlayer.play(intensity)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        TimerNotifications.ensureChannels(this)
        runCatching {
            nm.notify(TimerNotifications.ID_REMINDER, TimerNotifications.phaseDone(this, workFinished))
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
     * profileId 语义:Reset/PhaseRestarted 由事件携带(快照已空/已指向新 profile ——
     * PhaseRestarted 携带重启前快照的旧 profileId);PhaseFinished 用后继快照的
     * profileId(finishAndAdvance 复制 profileId,语义不变)。
     */
    private suspend fun flushSettle(settleMillis: Long, profileId: Long) {
        if (settleMillis <= 0) return
        g.totalsRepo.addWork(LocalDate.now().toString(), profileId, settleMillis)
    }

    private suspend fun reconcileAfterProcessDeath() {
        g.engine.awaitReady()
        engineMutex.withLock {
            when (Reconciler.decide(g.engine.snapshot.value, g.time.elapsedRealtime())) {
                ReconcileAction.STOP_SELF -> {
                    // 同步前台化已在 onStartCommand 完成,此处停止不会触发
                    // ForegroundServiceDidNotStartInTimeException
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
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
        const val ACTION_START = "com.embertimer.action.START"
        const val ACTION_PAUSE = "com.embertimer.action.PAUSE"
        const val ACTION_RESUME = "com.embertimer.action.RESUME"
        const val ACTION_RESET = "com.embertimer.action.RESET"
        const val ACTION_SKIP = "com.embertimer.action.SKIP"
        const val ACTION_RESTART_PHASE = "com.embertimer.action.RESTART_PHASE"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_WORK_MILLIS = "work_millis"
        const val EXTRA_REST_MILLIS = "rest_millis"
    }
}
