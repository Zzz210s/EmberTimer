package com.embertimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import com.embertimer.MainActivity
import com.embertimer.R
import com.embertimer.timer.DurationFormat
import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase
import com.embertimer.timer.RuntimeSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 通知栏:单渠道单 ID,任何时刻最多一条。启动即弹空闲通知,计时后同 ID 替换为计时态。
 * 小图标统一为应用图标同款火焰(v1.10.3);内容行直接放真实 app 图标位图。
 * 安全属性集:布局不含 Space/裸 View/?attr 背景(会 inflate 崩溃),颜色走 XML 主题属性。
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
            .setSmallIcon(R.drawable.ic_notif_flame)
            .setContentTitle(context.getString(R.string.app_name))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(activityIntent(context))
            .build()

    /** 空闲常驻通知:RemoteViews(相位图标 + 时钟名 + 右侧启动图标按钮),计时后被同 ID 覆盖 */
    fun idle(context: Context, profile: com.embertimer.data.db.ProfileEntity?): Notification {
        val rv = RemoteViews(context.packageName, R.layout.notification_idle)
        rv.setImageViewResource(R.id.idle_phase, R.drawable.ic_phase_idle)
        val name = profile?.name ?: context.getString(R.string.unselected_placeholder)
        rv.setTextViewText(R.id.idle_name, name)
        if (profile != null) {
            rv.setViewVisibility(R.id.idle_start, View.VISIBLE)
            rv.setImageViewResource(R.id.idle_start, R.drawable.ic_play)
            rv.setOnClickPendingIntent(R.id.idle_start, startPendingIntent(context, profile))
            rv.setContentDescription(R.id.idle_start, context.getString(R.string.notif_start))
        } else {
            rv.setViewVisibility(R.id.idle_start, View.GONE)
        }
        return NotificationCompat.Builder(context, CH_TIMER)
            .setSmallIcon(R.drawable.ic_notif_flame)
            .setContentTitle(name)
            .setContentText(" ")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(activityIntent(context))
            .setCustomContentView(rv)
            .build()
    }

    /** 软件运行即显示常驻空闲通知(权限未授予/异常时静默降级);显示当前时钟名与启动按钮 */
    fun showIdle(context: Context) {
        ensureChannels(context)
        val app = context.applicationContext as com.embertimer.EmberApp
        app.graph.appScope.launch {
            val pid = app.graph.settingsRepo.activeProfileId.first()
            val profile = if (pid != -1L) app.graph.profileRepo.byId(pid) else null
            try {
                context.getSystemService(NotificationManager::class.java)?.notify(ID_NOTIFY, idle(context, profile))
            } catch (_: Throwable) {
            }
        }
    }

    fun inProgress(context: Context, snap: RuntimeSnapshot): Notification {
        val phaseText = context.getString(
            if (snap.phase == Phase.WORK) R.string.state_work else R.string.state_rest,
        )
        val paused = snap.status == EngineStatus.PAUSED
        val countUp = snap.countUp
        val rv = RemoteViews(context.packageName, R.layout.notification_actions)

        // 行1:app 图标 + 相位图标 + 循环计数 + 倒计时(同排等宽)
        rv.setImageViewResource(
            R.id.notif_phase,
            when {
                snap == null -> R.drawable.ic_phase_idle
                snap.phase == Phase.WORK -> R.drawable.ic_phase_work
                else -> R.drawable.ic_phase_rest
            },
        )
        rv.setTextViewText(R.id.notif_title, "")
        rv.setViewVisibility(R.id.cycle_cell, if (countUp) android.view.View.GONE else android.view.View.VISIBLE)
        rv.setTextViewText(R.id.notif_cycle_text, if (countUp) "" else snap.cycleCount.toString())
        // Chronometer 的 base 必须基于 elapsedRealtime(墙钟 endWall 会导致倒计时错/空);暂停态定格文本
        if (paused) {
            rv.setTextViewText(R.id.notif_time, DurationFormat.ms(snap.timeAtPause))
        } else {
            val spec = buildClockSpec(snap)
            rv.setChronometerCountDown(R.id.notif_time, spec.countDown)
            rv.setChronometer(R.id.notif_time, spec.base, null, true)
        }

        // 行2 图标按钮:终止 | 开始/暂停 | 跳过(正计时无跳过)
        rv.setImageViewResource(R.id.btn_stop, R.drawable.ic_stop)
        rv.setOnClickPendingIntent(R.id.btn_stop, serviceIntent(context, TimerService.ACTION_STOP))
        rv.setImageViewResource(R.id.btn_pause, if (paused) R.drawable.ic_play else R.drawable.ic_pause)
        rv.setOnClickPendingIntent(R.id.btn_pause, serviceIntent(context, if (paused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE))
        if (countUp) {
            rv.setViewVisibility(R.id.btn_skip, android.view.View.GONE)
        } else {
            rv.setViewVisibility(R.id.btn_skip, android.view.View.VISIBLE)
            rv.setImageViewResource(R.id.btn_skip, R.drawable.ic_skip_next)
            rv.setOnClickPendingIntent(R.id.btn_skip, serviceIntent(context, TimerService.ACTION_SKIP))
        }

        return NotificationCompat.Builder(context, CH_TIMER)
            .setSmallIcon(R.drawable.ic_notif_flame)
            .setContentTitle(phaseText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(activityIntent(context))
            // 纯 custom content view(DecoratedCustomViewStyle 在部分机型渲染异常);
            // 颜色由布局 XML 主题属性解析,适配深浅通知底。
            .setCustomContentView(rv)
            .setCustomBigContentView(rv)
            .build()
    }

    fun phaseDone(context: Context, workFinished: Boolean): Notification {
        val title = context.getString(if (workFinished) R.string.done_work_title else R.string.done_rest_title)
        val text = context.getString(if (workFinished) R.string.done_rest_body else R.string.done_work_body)
        return NotificationCompat.Builder(context, CH_TIMER)
            .setSmallIcon(R.drawable.ic_notif_flame)
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

    /** 空闲通知“启动”按钮:直接对服务发 ACTION_START(startForegroundService 由用户点击触发合法) */
    private fun startPendingIntent(context: Context, profile: com.embertimer.data.db.ProfileEntity): PendingIntent = PendingIntent.getService(
        context, profile.id.hashCode(),
        TimerCommands.startIntent(
            context, profile.id,
            profile.workMinutes * 60_000L, profile.restMinutes * 60_000L,
            profile.mode == com.embertimer.data.db.ProfileMode.COUNTUP,
        ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun serviceIntent(context: Context, action: String): PendingIntent = PendingIntent.getService(
        context, action.hashCode(), Intent(context, TimerService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** 运行态时钟基线(v1.9.4):基于 SystemClock.elapsedRealtime() 的绝对基 —— Chronometer 只认这条时间轴。 */
internal data class ClockSpec(val base: Long, val countDown: Boolean)

internal fun buildClockSpec(snap: RuntimeSnapshot): ClockSpec =
    if (snap.countUp) ClockSpec(snap.startElapsed + snap.timeSpentPaused, false)
    else ClockSpec(snap.endElapsed, true)
