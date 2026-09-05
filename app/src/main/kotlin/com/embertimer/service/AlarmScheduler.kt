package com.embertimer.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.embertimer.timer.TimeProvider

/** open:测试子类覆写 scheduleExactAlarm 注入 SecurityException(arm 降级路径) */
open class AlarmScheduler(private val context: Context, private val time: TimeProvider) {
    private val am = context.getSystemService(AlarmManager::class.java)

    fun arm(endElapsed: Long) {
        if (endElapsed <= time.elapsedRealtime()) return
        val pi = pendingIntent()
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            // 未授权 SCHEDULE_EXACT_ALARM:直接 inexact 兜底(仍可唤醒、可入 Doze 维护窗口)
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
            return
        }
        try {
            scheduleExactAlarm(endElapsed, pi)
        } catch (e: SecurityException) {
            // #4:授权在 canScheduleExactAlarms 与 setExact* 之间被系统撤销(TOCTOU)。
            // 不捕获会让异常沿调用链上抛:onEvent 里被吞(闹钟根本没武装),对账路径
            // (reconcileAfterProcessDeath)未捕获直达默认 handler 拉崩进程。降级 inexact。
            Log.w(TAG, "exact alarm denied (SecurityException); falling back to inexact", e)
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
        }
    }

    /** 可覆写的最小接缝(测试注入 SecurityException 验证降级不崩不丢) */
    internal open fun scheduleExactAlarm(endElapsed: Long, pi: PendingIntent) {
        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
    }

    fun cancel() {
        val pi = pendingIntent()
        am.cancel(pi)
        pi.cancel()
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "AlarmScheduler"
    }
}
