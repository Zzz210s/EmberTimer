package com.embertimer.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.embertimer.timer.RuntimeSnapshot
import com.embertimer.timer.TimeProvider

/**
 * 到期闹钟(v1.12.0 重写)。不使用 `setAlarmClock`(会给状态栏加闹钟图标,改动 UI/UX),
 * 改用**双精确闹钟冗余**:
 *  - primary(elapsed = 到期时刻)  上界:主到点
 *  - safety (elapsed = 到期 +45s) 上界:主闹钟被 OEM/Doze 吞掉时的第二次机会
 * 两者都是 `setExactAndAllowWhileIdle`(声明 USE_EXACT_ALARM 后系统安装即授予,不显示图标),
 * 未授权/被撤销时逐级降级为 `setAndAllowWhileIdle`。
 *
 * 取消:两个一起取消(改阶段/暂停/终止/正计时都不留残余闹钟)。
 */
open class AlarmScheduler(private val context: Context, private val time: TimeProvider) {
    private val am = context.getSystemService(AlarmManager::class.java)

    /** 按计划武装(幂等:同名 PendingIntent 覆盖既有武装) */
    fun arm(plan: AlarmPlan) {
        armAt(plan.primaryElapsed, REQ_PRIMARY)
        armAt(plan.safetyElapsed, REQ_SAFETY)
    }

    /** 便捷入口:直接从快照推导计划并武装(计划为空=取消) */
    fun arm(snap: RuntimeSnapshot?) {
        val plan = alarmPlanFor(snap, time.elapsedRealtime())
        if (plan == null) cancel() else arm(plan)
    }

    private fun armAt(elapsed: Long, requestCode: Int) {
        if (elapsed <= time.elapsedRealtime()) return
        val pi = pendingIntent(requestCode)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsed, pi)
            return
        }
        try {
            scheduleExactAlarm(elapsed, pi)
        } catch (e: SecurityException) {
            // 授权在 canScheduleExactAlarms 与 setExact* 之间被撤销(TOCTOU):降级 inexact,
            // 不捕获会让异常沿调用链上抛(接收器路径未捕获即拉崩进程)
            Log.w(TAG, "exact alarm denied (SecurityException); falling back to inexact", e)
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsed, pi)
        }
    }

    fun cancel() {
        listOf(REQ_PRIMARY, REQ_SAFETY).forEach { req ->
            val pi = pendingIntent(req)
            am.cancel(pi)
            pi.cancel()
        }
    }

    /** 可覆写的最小接缝(测试注入 SecurityException 验证降级不崩不丢) */
    internal open fun scheduleExactAlarm(elapsed: Long, pi: PendingIntent) {
        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsed, pi)
    }

    private fun pendingIntent(requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_EXPIRY),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "AlarmScheduler"
        internal const val ACTION_EXPIRY = "com.embertimer.action.EXPIRY"
        private const val REQ_PRIMARY = 0x1001
        private const val REQ_SAFETY = 0x1002
    }
}
