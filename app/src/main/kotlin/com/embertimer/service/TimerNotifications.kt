package com.embertimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.embertimer.MainActivity
import com.embertimer.R
import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase
import com.embertimer.timer.RuntimeSnapshot

object TimerNotifications {
    const val CH_PROGRESS = "ember_progress"
    const val CH_REMINDER = "ember_reminder"
    const val ID_PROGRESS = 1
    const val ID_REMINDER = 2

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "计时进行中", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDER, "阶段提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // 铃声由 ReminderPlayer 播放
                enableVibration(false) // 震动由 ReminderPlayer 播放
            }
        )
    }

    /** 引擎快照未就绪时的最小占位通知:onStartCommand 同步前台化先顶上,异步收集器稍后用真实快照替换 */
    fun minimal(context: Context): Notification =
        NotificationCompat.Builder(context, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("EmberTimer")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(activityIntent(context))
            .build()

    fun inProgress(context: Context, snap: RuntimeSnapshot): Notification {
        val phaseText = when {
            snap.phase == Phase.WORK -> "工作中"
            else -> "休息中"
        }
        val paused = snap.status == EngineStatus.PAUSED
        val builder = NotificationCompat.Builder(context, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(phaseText)
            .setContentText(if (paused) "已暂停 · 循环 ${snap.cycleCount}" else "循环 ${snap.cycleCount}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(activityIntent(context))
            .addAction(
                if (paused) R.drawable.ic_play else R.drawable.ic_pause,
                if (paused) "恢复" else "暂停",
                serviceIntent(context, if (paused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE),
            )
            .addAction(R.drawable.ic_skip_next, "跳过", serviceIntent(context, TimerService.ACTION_SKIP))
            .addAction(R.drawable.ic_stop, "终止", serviceIntent(context, TimerService.ACTION_STOP))
        if (!paused) {
            // D2 方案 A:倒计时占标题行时间位(系统 chronometer 自动走秒);当前阶段进度条
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
            builder.setWhen(snap.endWall)
            val remaining = (snap.endElapsed - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0)
            val total = snap.durationMillis
            builder.setProgress(total.toInt(), (total - remaining).coerceIn(0, total).toInt(), false)
        }
        return builder.build()
    }

    fun phaseDone(context: Context, workFinished: Boolean): Notification {
        val title = if (workFinished) "工作完成" else "休息结束"
        val text = if (workFinished) "休息一下" else "开始新一轮工作"
        return NotificationCompat.Builder(context, CH_REMINDER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(activityIntent(context))
            .build()
    }

    private fun activityIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun serviceIntent(context: Context, action: String): PendingIntent = PendingIntent.getService(
        context, action.hashCode(), Intent(context, TimerService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
