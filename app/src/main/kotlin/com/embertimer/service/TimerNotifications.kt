package com.embertimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.embertimer.MainActivity
import com.embertimer.R
import com.embertimer.timer.DurationFormat
import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase
import com.embertimer.timer.RuntimeSnapshot

/**
 * 通知栏(v1.9.1 重构):单渠道单 ID,任何时刻最多一条。
 * 常驻:app 启动即弹空闲通知;计时开始后同 ID 替换为计时态。
 * 自定义 RemoteViews:图标按钮(终止|开始/暂停|跳过)、倒计时与标题同排等宽、循环图标。
 * 安全属性集:布局不含 android:tint / ?android:attr 背景(会 inflate 崩溃);
 * 图标与文字颜色均在代码里用 setColorFilter / setTextColor 注入。
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

    /** 引擎快照未就绪的最小占位通知:onStartCommand 同步前台化先顶上 */
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
        val fg = foregroundColor(context)
        val rv = RemoteViews(context.packageName, R.layout.notification_actions)

        // 行1 标题 + 循环图标/计数 + 倒计时(同排等宽)
        rv.setTextViewText(R.id.notif_title, phaseText)
        rv.setTextColor(R.id.notif_title, fg)
        rv.setViewVisibility(R.id.cycle_cell, if (countUp) android.view.View.GONE else android.view.View.VISIBLE)
        rv.setInt(R.id.notif_cycle, "setColorFilter", fg)
        rv.setTextViewText(R.id.notif_cycle_text, if (countUp) "" else snap.cycleCount.toString())
        rv.setTextColor(R.id.notif_cycle_text, fg)
        // 时间:运行态 chronometer(倒計/正計),暂停态定格文本
        if (paused) {
            rv.setTextViewText(R.id.notif_time, DurationFormat.ms(snap.timeAtPause))
        } else {
            val base = if (countUp) snap.endWall - snap.durationMillis else snap.endWall
            rv.setChronometerCountDown(R.id.notif_time, !countUp)
            rv.setChronometer(R.id.notif_time, base, null, true)
        }
        rv.setTextColor(R.id.notif_time, fg)

        // 行2 图标按钮:终止 | 开始/暂停 | 跳过(正计时无跳过)
        rv.setImageViewResource(R.id.btn_stop, R.drawable.ic_stop)
        rv.setInt(R.id.btn_stop, "setColorFilter", fg)
        rv.setOnClickPendingIntent(R.id.btn_stop, serviceIntent(context, TimerService.ACTION_STOP))
        rv.setImageViewResource(R.id.btn_pause, if (paused) R.drawable.ic_play else R.drawable.ic_pause)
        rv.setInt(R.id.btn_pause, "setColorFilter", fg)
        rv.setOnClickPendingIntent(R.id.btn_pause, serviceIntent(context, if (paused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE))
        if (countUp) {
            rv.setViewVisibility(R.id.btn_skip, android.view.View.GONE)
        } else {
            rv.setViewVisibility(R.id.btn_skip, android.view.View.VISIBLE)
            rv.setImageViewResource(R.id.btn_skip, R.drawable.ic_skip_next)
            rv.setInt(R.id.btn_skip, "setColorFilter", fg)
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

    /** 主题前景色(可着色的文字/图标色),供 setColorFilter / setTextColor 注入 */
    private fun foregroundColor(context: Context): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
        return if (tv.type == TypedValue.TYPE_REFERENCE) context.getColor(tv.resourceId) else tv.data
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
