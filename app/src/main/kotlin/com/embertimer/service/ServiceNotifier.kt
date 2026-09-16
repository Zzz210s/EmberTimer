package com.embertimer.service

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.embertimer.R
import com.embertimer.di.AppGraph
import com.embertimer.timer.RuntimeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 通知/提醒反应(v1.12.0 重写为 **Context 版**):不再依赖 Service —— 因为到期推进要能
 * 在"只有广播唤起的进程"里完成(不需要前台服务),所以通知发布必须在无服务时也可用。
 * 前台化(startForeground)由服务通过 [attachForeground] 注入;未挂载时只发通知。
 */
class ServiceNotifier(
    private val context: Context,
    private val graph: AppGraph,
    private val scope: CoroutineScope,
) {
    /** 服务挂载时的前台化回调(未挂载 = null:仅 notify) */
    @Volatile private var foregroundSink: ((Notification) -> Unit)? = null

    fun attachForeground(sink: ((Notification) -> Unit)?) {
        foregroundSink = sink
    }

    /** 按快照发布计时/空闲通知;有服务挂载时同时前台化 */
    fun post(snap: RuntimeSnapshot?) {
        val n = if (snap != null) TimerNotifications.inProgress(context, snap)
        else TimerNotifications.minimal(context)
        runCatching {
            context.getSystemService(android.app.NotificationManager::class.java)
                ?.notify(TimerNotifications.ID_NOTIFY, n)
        }
        com.embertimer.diag.DiagLog.add("Notif", "发布通知 有快照=${snap != null} 前台化=${foregroundSink != null}")
        foregroundSink?.invoke(n)
    }

    /**
     * 播放强提醒并发 heads-up 通知,数秒自停,无需交互。
     * 通知与播放均在锁外协程内执行:ensureChannels/notify 是同步 binder 调用,
     * 在引擎锁临界区内直接调用会拖长持锁时间。
     */
    fun remind(workFinished: Boolean) {
        scope.launch {
            val intensity = graph.settingsRepo.reminderIntensity.first()
            graph.reminderPlayer.play(intensity)
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return@launch
            val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return@launch
            TimerNotifications.ensureChannels(context)
            runCatching {
                nm.notify(TimerNotifications.ID_NOTIFY, TimerNotifications.phaseDone(context, workFinished))
            }
        }
    }
}
