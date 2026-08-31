package com.embertimer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.embertimer.EmberApp
import com.embertimer.timer.StateRestorer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as EmberApp
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeout(8_000) { app.graph.engine.awaitReady() }
                val g = app.graph
                g.engine.snapshot.value?.let { snap ->
                    val restored = StateRestorer.afterBoot(snap, g.time.now(), g.time.elapsedRealtime())
                    g.engine.adoptRestored(restored)
                }
                ServiceLauncher.ensureServiceRunning(context)
            } finally {
                pending.finish()
            }
        }
    }
}
