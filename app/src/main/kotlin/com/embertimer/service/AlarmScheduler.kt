package com.embertimer.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.embertimer.timer.TimeProvider

class AlarmScheduler(private val context: Context, private val time: TimeProvider) {
    private val am = context.getSystemService(AlarmManager::class.java)

    fun arm(endElapsed: Long) {
        if (endElapsed <= time.elapsedRealtime()) return
        val pi = pendingIntent()
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
        }
    }

    fun cancel() {
        val pi = pendingIntent()
        am.cancel(pi)
        pi.cancel()
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
