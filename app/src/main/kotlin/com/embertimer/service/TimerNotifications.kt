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
import com.embertimer.timer.DurationFormat
import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase
import com.embertimer.timer.RuntimeSnapshot

object TimerNotifications {
    const val CH_PROGRESS = "ember_progress"
    const val CH_REMINDER = "ember_reminder"
    const val CH_REPORT = "ember_report"
    const val ID_PROGRESS = 1
    const val ID_REMINDER = 2
    const val ID_REPORT_WEEK = 10
    const val ID_REPORT_MONTH = 11

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, context.getString(R.string.ch_progress), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDER, context.getString(R.string.ch_reminder), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // 铃声由 ReminderPlayer 播放
                enableVibration(false) // 震动由 ReminderPlayer 播放
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REPORT, context.getString(R.string.ch_report), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                setShowBadge(false)
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
        val phaseText = context.getString(
            if (snap.phase == Phase.WORK) R.string.state_work else R.string.state_rest,
        )
        val paused = snap.status == EngineStatus.PAUSED
        val builder = NotificationCompat.Builder(context, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(phaseText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(activityIntent(context))
        if (snap.countUp) {
            // 正计时(Task 7 / #10):无循环/到期/跳过 —— 文本只留模式;运行态用正向 chronometer
            // 显示已走时长(与倒计时 chronometer 同一机制,countDown=false),暂停态定格于文本
            builder.setContentText(
                if (paused) context.getString(R.string.nt_paused_elapsed, DurationFormat.ms(snap.timeAtPause))
                else context.getString(R.string.mode_countup),
            )
                .addAction(
                    if (paused) R.drawable.ic_play else R.drawable.ic_pause,
                    if (paused) context.getString(R.string.act_resume) else context.getString(R.string.act_pause),
                    serviceIntent(context, if (paused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE),
                )
                .addAction(R.drawable.ic_stop, context.getString(R.string.act_stop), serviceIntent(context, TimerService.ACTION_STOP))
            if (!paused) {
                // when = 本次运行已走时长的墙钟起点(endWall - 名义跨度 = startWall;
                // 每次 resume 引擎重锚 endWall,起点同步平移,暂停不计入)
                builder.setUsesChronometer(true)
                builder.setChronometerCountDown(false)
                builder.setWhen(snap.endWall - snap.durationMillis)
            }
        } else {
            builder.setContentText(
                context.getString(if (paused) R.string.nt_paused_cycle else R.string.nt_cycle, snap.cycleCount),
            )
                .addAction(
                    if (paused) R.drawable.ic_play else R.drawable.ic_pause,
                    if (paused) context.getString(R.string.act_resume) else context.getString(R.string.act_pause),
                    serviceIntent(context, if (paused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE),
                )
                .addAction(R.drawable.ic_skip_next, context.getString(R.string.act_skip), serviceIntent(context, TimerService.ACTION_SKIP))
                .addAction(R.drawable.ic_stop, context.getString(R.string.act_stop), serviceIntent(context, TimerService.ACTION_STOP))
            if (!paused) {
                // D2 方案 A:倒计时占标题行时间位(系统 chronometer 自动走秒);当前阶段进度条
                builder.setUsesChronometer(true)
                builder.setChronometerCountDown(true)
                builder.setWhen(snap.endWall)
                val remaining = (snap.endElapsed - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0)
                val total = snap.durationMillis
                builder.setProgress(total.toInt(), (total - remaining).coerceIn(0, total).toInt(), false)
            }
        }
        return builder.build()
    }

    fun phaseDone(context: Context, workFinished: Boolean): Notification {
        val title = context.getString(if (workFinished) R.string.done_work_title else R.string.done_rest_title)
        val text = context.getString(if (workFinished) R.string.done_rest_body else R.string.done_work_body)
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
