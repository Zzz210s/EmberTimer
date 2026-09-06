package com.embertimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.embertimer.MainActivity
import com.embertimer.R
import com.embertimer.timer.DurationFormat
import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase
import com.embertimer.timer.RuntimeSnapshot

object TimerNotifications {
    // v1.4.3:单渠道单 ID —— 通知栏任何时刻最多一条(前台进度/完成提醒/报表总结同槽位)
    const val CH_TIMER = "ember_timer"
    const val ID_NOTIFY = 1

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_TIMER, context.getString(R.string.ch_app), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null) // 铃声/震动由 ReminderPlayer 播放
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    /** 引擎快照未就绪时的最小占位通知:onStartCommand 同步前台化先顶上,异步收集器稍后用真实快照替换 */
    fun minimal(context: Context): Notification =
        NotificationCompat.Builder(context, CH_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("EmberTimer")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false) // 隐藏通知时间戳相对文案("刚刚"),倒计时/时长由 chronometer 展示
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(activityIntent(context))
            .build()

    fun inProgress(context: Context, snap: RuntimeSnapshot): Notification {
        val phaseText = context.getString(
            if (snap.phase == Phase.WORK) R.string.state_work else R.string.state_rest,
        )
        val paused = snap.status == EngineStatus.PAUSED
        val rv = RemoteViews(context.packageName, R.layout.notification_actions)
        rv.setTextViewText(R.id.notif_title, if (snap.countUp) context.getString(R.string.mode_countup) else "$phaseText · ${context.getString(if (paused) R.string.nt_cycle else R.string.nt_cycle, snap.cycleCount)}")
        // 时间:运行态 Chronometer(倒计时 backward / 正计时 forward),暂停态定格文本
        if (paused) {
            rv.setTextViewText(R.id.notif_time, DurationFormat.ms(snap.timeAtPause))
            rv.setViewVisibility(R.id.notif_time, android.view.View.VISIBLE)
        } else {
            val base = if (snap.countUp) snap.endWall - snap.durationMillis else snap.endWall
            rv.setChronometerCountDown(R.id.notif_time, !snap.countUp)
            rv.setChronometer(R.id.notif_time, base, null, true)
            rv.setViewVisibility(R.id.notif_time, android.view.View.VISIBLE)
        }
        // 图标操作按钮(正计时隐藏 跳过)
        rv.setImageViewResource(R.id.btn_pause, if (paused) R.drawable.ic_play else R.drawable.ic_pause)
        rv.setOnClickPendingIntent(R.id.btn_pause, serviceIntent(context, if (paused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE))
        if (snap.countUp) {
            rv.setViewVisibility(R.id.btn_skip, android.view.View.GONE)
        } else {
            rv.setViewVisibility(R.id.btn_skip, android.view.View.VISIBLE)
            rv.setImageViewResource(R.id.btn_skip, R.drawable.ic_skip_next)
            rv.setOnClickPendingIntent(R.id.btn_skip, serviceIntent(context, TimerService.ACTION_SKIP))
        }
        rv.setImageViewResource(R.id.btn_stop, R.drawable.ic_stop)
        rv.setOnClickPendingIntent(R.id.btn_stop, serviceIntent(context, TimerService.ACTION_STOP))

        return NotificationCompat.Builder(context, CH_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(phaseText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false) // 隐藏通知时间戳相对文案("刚刚"),倒计时由 RemoteViews chronometer 展示
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(activityIntent(context))
            .setCustomContentView(rv)
            .setCustomBigContentView(rv)
            .build()
    }

    fun phaseDone(context: Context, workFinished: Boolean): Notification {
        val title = context.getString(if (workFinished) R.string.done_work_title else R.string.done_rest_title)
        val text = context.getString(if (workFinished) R.string.done_rest_body else R.string.done_work_body)
        return NotificationCompat.Builder(context, CH_TIMER)
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
