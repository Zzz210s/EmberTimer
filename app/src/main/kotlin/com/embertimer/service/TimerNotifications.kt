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
        rv.setTextViewText(R.id.notif_title, phaseText)
        // 循环行:循环图标 + 计数(正计时无循环/无跳过 → 隐藏,倒计时独占本栏并等宽)
        if (snap.countUp) {
            rv.setViewVisibility(R.id.cycle_cell, android.view.View.GONE)
            rv.setViewVisibility(R.id.btn_skip, android.view.View.GONE)
        } else {
            rv.setViewVisibility(R.id.cycle_cell, android.view.View.VISIBLE)
            rv.setViewVisibility(R.id.btn_skip, android.view.View.VISIBLE)
            rv.setTextViewText(R.id.notif_cycle_text, context.getString(R.string.nt_cycle, snap.cycleCount))
        }
        // 时间:运行态 Chronometer(倒計/正計自动,<24h 无"天");超长用静态 HH:MM:SS,避免 chromometer 天数乱码
        val nowElapsed = SystemClock.elapsedRealtime()
        if (paused) {
            rv.setTextViewText(R.id.notif_time, DurationFormat.ms(snap.timeAtPause))
            rv.setViewVisibility(R.id.notif_time, android.view.View.VISIBLE)
        } else {
            val span = if (snap.countUp) (nowElapsed - snap.startElapsed - snap.timeSpentPaused).coerceAtLeast(0)
            else (snap.endElapsed - nowElapsed).coerceAtLeast(0)
            if (span < 24L * 3600_000L) {
                val base = if (snap.countUp) snap.endWall - snap.durationMillis else snap.endWall
                rv.setChronometerCountDown(R.id.notif_time, !snap.countUp)
                rv.setChronometer(R.id.notif_time, base, null, true)
            } else {
                rv.setTextViewText(R.id.notif_time, durationHM(span, snap.countUp))
            }
            rv.setViewVisibility(R.id.notif_time, android.view.View.VISIBLE)
        }
        // 图标按钮顺序:停止 | 暂停/恢复 | 跳过
        rv.setImageViewResource(R.id.btn_stop, R.drawable.ic_stop)
        rv.setOnClickPendingIntent(R.id.btn_stop, serviceIntent(context, TimerService.ACTION_STOP))
        rv.setImageViewResource(R.id.btn_pause, if (paused) R.drawable.ic_play else R.drawable.ic_pause)
        rv.setOnClickPendingIntent(R.id.btn_pause, serviceIntent(context, if (paused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE))
        if (!snap.countUp) {
            rv.setImageViewResource(R.id.btn_skip, R.drawable.ic_skip_next)
            rv.setOnClickPendingIntent(R.id.btn_skip, serviceIntent(context, TimerService.ACTION_SKIP))
        }

        return NotificationCompat.Builder(context, CH_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(phaseText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
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

    /** 超 24h 时的静态时长:HH:MM:SS(正计时从现在起累计,倒计为剩余) */
    private fun durationHM(spanMs: Long, countUp: Boolean): String {
        val t = (spanMs / 1000).coerceAtLeast(0)
        val h = t / 3600; val m = (t % 3600) / 60; val sec = t % 60
        return "%02d:%02d:%02d".format(java.util.Locale.ROOT, h, m, sec)
    }

    private fun serviceIntent(context: Context, action: String): PendingIntent = PendingIntent.getService(
        context, action.hashCode(), Intent(context, TimerService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
