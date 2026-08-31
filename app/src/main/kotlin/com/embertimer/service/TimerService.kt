package com.embertimer.service

import android.app.Service
import android.content.Intent

class TimerService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    companion object {
        const val ACTION_START = "com.embertimer.action.START"
        const val ACTION_PAUSE = "com.embertimer.action.PAUSE"
        const val ACTION_RESUME = "com.embertimer.action.RESUME"
        const val ACTION_RESET = "com.embertimer.action.RESET"
        const val ACTION_SKIP = "com.embertimer.action.SKIP"
        const val ACTION_RESTART_PHASE = "com.embertimer.action.RESTART_PHASE"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_WORK_MILLIS = "work_millis"
        const val EXTRA_REST_MILLIS = "rest_millis"
    }
}
