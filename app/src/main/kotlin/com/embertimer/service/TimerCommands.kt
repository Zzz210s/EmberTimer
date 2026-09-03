package com.embertimer.service

import android.content.Context
import android.content.Intent

/** UI -> 服务的命令入口。start 用 startForegroundService(Activity 前台调用合法),其余走 startService */
object TimerCommands {
    fun start(context: Context, profileId: Long, workMillis: Long, restMillis: Long) {
        context.startForegroundService(
            intent(context, TimerService.ACTION_START)
                .putExtra(TimerService.EXTRA_PROFILE_ID, profileId)
                .putExtra(TimerService.EXTRA_WORK_MILLIS, workMillis)
                .putExtra(TimerService.EXTRA_REST_MILLIS, restMillis)
        )
    }

    fun pause(context: Context) = context.startService(intent(context, TimerService.ACTION_PAUSE))
    fun resume(context: Context) = context.startService(intent(context, TimerService.ACTION_RESUME))
    fun stop(context: Context) = context.startService(intent(context, TimerService.ACTION_STOP))
    fun skip(context: Context) = context.startService(intent(context, TimerService.ACTION_SKIP))

    fun restartPhase(context: Context, profileId: Long, workMillis: Long, restMillis: Long) {
        context.startService(
            intent(context, TimerService.ACTION_RESTART_PHASE)
                .putExtra(TimerService.EXTRA_PROFILE_ID, profileId)
                .putExtra(TimerService.EXTRA_WORK_MILLIS, workMillis)
                .putExtra(TimerService.EXTRA_REST_MILLIS, restMillis)
        )
    }

    private fun intent(context: Context, action: String) =
        Intent(context, TimerService::class.java).setAction(action)
}
