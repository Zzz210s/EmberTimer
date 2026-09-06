package com.embertimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.embertimer.MainActivity
import com.embertimer.R
import com.embertimer.timer.DurationFormat
import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase
import com.embertimer.timer.RuntimeSnapshot

/**
 * 通知栏(v1.9.0 重构):单渠道单 ID,任何时刻最多一条。
 * 常驻:app 启动即弹空闲通知;计时开始后同 ID 替换为计时态(MediaStyle 图标按钮 + chronometer 倒计时)。
 * 不再用自定义 RemoteViews(此前 android:tint/?attr 导致 inflate 崩溃)。
 */
object TimerNotifications {
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

    /** 引擎快照未就绪的最小占位通知:onStartCommand 同步前台化先顶上,异步收集器稍后用真实快照替换 */
    fun minimal(context: Context): Notification =
        NotificationCompat.Builder(context, CH_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.app_name))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(activityIntent(context))
            .build()

    /** 空闲常驻通知(app 启动即驻;计时开始后被同 ID 计时通知覆盖) */
    fun idle(context: Context): Notification =
        NotificationCompat.Builder(context, CH_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.state_idle))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(activityIntent(context))
            .build()

    /** 软件运行即显示常驻空闲通知(权限未授予/异常时静默降级) */
    fun showIdle(context: Context) {
        ensureChannels(context)
        try {
            context.getSystemService(NotificationManager::class.java)?.notify(ID_NOTIFY, idle(context))
        } catch (_: Throwable) {
        }
    }

    fun inProgress(context: Context, snap: RuntimeSnapshot): Notification {
        val phaseText = context.getString(
            if (snap.phase == Phase.WORK) R.string.state_work else R.string.state_rest,
        )
        val paused = snap.status == EngineStatus.PAUSED
        val countUp = snap.countUp

        val builder = NotificationCompat.Builder(context, CH_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(phaseText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(activityIntent(context))

        // 操作按钮(系统原生动作,稳定渲染,不用自定义 RemoteViews):停止 | 暂停/恢复 | 跳过;正计时无跳过
        builder.addAction(R.drawable.ic_stop, context.getString(R.string.act_stop), serviceIntent(context, TimerService.ACTION_STOP))
        builder.addAction(
            if (paused) R.drawable.ic_play else R.drawable.ic_pause,
            context.getString(if (paused) R.string.act_resume else R.string.act_pause),
            serviceIntent(context, if (paused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE),
        )
        if (!countUp) {
            builder.addAction(R.drawable.ic_skip_next, context.getString(R.string.act_skip), serviceIntent(context, TimerService.ACTION_SKIP))
        }

        // 时间:运行态 chronometer(倒計/正計),暂停态定格文本;<24h 无"天"(超长由 chronometer 自动省略)
        val nowElapsed = SystemClock.elapsedRealtime()
        if (paused) {
            builder.setContentText(DurationFormat.ms(snap.timeAtPause))
        } else {
            val base = if (countUp) snap.endWall - snap.durationMillis else snap.endWall
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(!countUp)
            builder.setWhen(base)
        }
        return builder.build()
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
