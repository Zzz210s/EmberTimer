package com.embertimer.service

import android.app.Service
import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull

/** 服务生命周期辅助(v1.3 拆分):STOP 排空拆除 + 事件订阅握手等待。 */
internal suspend fun TimerService.awaitStopDrainedAndTearDown() {
    val drained = stopDrained ?: return
    if (withTimeoutOrNull(3_000) { drained.await() } == null) {
        Log.w("TimerService", "stop/Reset-event settle drain timed out (bounded 3s); settle may be lost")
    }
    if (g.engine.snapshot.value == null) {
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}

/** 握手等待:5 秒兜底,超时则大声降级(此后引擎事件将无订阅者而静默丢弃) */
internal suspend fun TimerService.awaitEventsSubscribed() {
    if (withTimeoutOrNull(5_000) { eventsSubscribed.await() } == null) {
        Log.w("TimerService", "events subscriber handshake timed out; engine events will be dropped")
    }
}
