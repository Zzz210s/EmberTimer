package com.embertimer.service

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import com.embertimer.di.AppGraph
import com.embertimer.timer.RuntimeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 前台化与强提醒封装(v1.3 拆分):startForeground 兼容层 + 阶段完成提醒(播放 + heads-up)。
 * 原 TimerService.goForeground/remind 行为逐字节搬移,锁外调用语义不变。
 */
internal class ServiceNotifier(
    private val svc: Service,
    private val graph: AppGraph,
    private val scope: CoroutineScope,
) {
    /** 前台化:快照可用时用进行中通知,否则(引擎未就绪/IDLE)用最小占位 */
    fun goForeground(snap: RuntimeSnapshot?) {
        val n = if (snap != null) TimerNotifications.inProgress(svc, snap)
        else TimerNotifications.minimal(svc)
        // FOREGROUND_SERVICE_TYPE_SPECIAL_USE 自 API 34 才存在:34 以下平台无法解析
        // manifest 中的 specialUse 位,传该类型会抛 IllegalArgumentException
        // (androidx ServiceCompat 在 29-33 上同样把它掩码掉)。34 以下用无类型重载。
        if (Build.VERSION.SDK_INT >= 34) {
            svc.startForeground(TimerNotifications.ID_NOTIFY, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            svc.startForeground(TimerNotifications.ID_NOTIFY, n)
        }
    }

    /**
     * 播放强提醒并发 heads-up 通知,数秒自停,无需交互。
     * 通知与播放均在锁外协程内执行(F8):ensureChannels/nm.notify 是同步 binder 调用,
     * 原先在 engineMutex 临界区内直接调用会拖长持锁时间。
     */
    fun remind(workFinished: Boolean) {
        scope.launch {
            val intensity = graph.settingsRepo.reminderIntensity.first()
            graph.reminderPlayer.play(intensity)
            if (Build.VERSION.SDK_INT >= 33 &&
                svc.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return@launch
            val nm = svc.getSystemService(NotificationManager::class.java) ?: return@launch
            TimerNotifications.ensureChannels(svc)
            runCatching {
                nm.notify(TimerNotifications.ID_NOTIFY, TimerNotifications.phaseDone(svc, workFinished))
            }
        }
    }
}
