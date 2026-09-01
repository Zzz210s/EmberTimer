package com.embertimer.service

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.embertimer.data.ReminderIntensity

class ReminderPlayer(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null

    fun play(intensity: ReminderIntensity) {
        val durMs = durationMs(intensity)
        if (durMs <= 0) return
        vibrate(pattern(intensity))
        stopRingtone()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        val rt = RingtoneManager.getRingtone(context, uri) ?: return
        rt.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone = rt
        rt.play()
        handler.postDelayed({ stopRingtone() }, durMs.toLong())
    }

    private fun vibrate(pattern: LongArray) {
        val v = context.getSystemService(Vibrator::class.java) ?: return
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    private fun stopRingtone() {
        ringtone?.stop()
        ringtone = null
    }

    companion object {
        fun durationMs(i: ReminderIntensity): Int = when (i) {
            ReminderIntensity.LIGHT -> 0
            ReminderIntensity.STANDARD -> 3_000
            ReminderIntensity.STRONG -> 5_000
        }

        fun pattern(i: ReminderIntensity): LongArray = when (i) {
            ReminderIntensity.LIGHT -> longArrayOf()
            ReminderIntensity.STANDARD -> longArrayOf(0, 500, 300, 500)
            ReminderIntensity.STRONG -> longArrayOf(0, 700, 300, 700, 300, 700)
        }
    }
}
