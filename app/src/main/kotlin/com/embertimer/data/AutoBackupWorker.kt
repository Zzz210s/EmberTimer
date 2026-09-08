package com.embertimer.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.embertimer.EmberApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** v1.9.11 自动备份 Worker:读设置的目标 SAF URI,导出全量 JSON 写入该 URI(每日周期调用)。失败静默(下次重试)。 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as EmberApp
        val settings = app.graph.settingsRepo

        // 未启用或未设目标 URI:静默成功
        if (!settings.autoBackupEnabled.first()) return Result.success()
        val uriStr = settings.backupUri.first() ?: return Result.success()

        return withContext(Dispatchers.IO) {
            try {
                val json = DataTransfer.exportJson(app.graph.db)
                val uri = android.net.Uri.parse(uriStr)
                // v1.9.13:用 BackupWriter 在目录下写固定文件名(覆盖),而非直接 openOutputStream(tree uri)
                val ok = BackupWriter.write(applicationContext, uri, json)
                if (ok) {
                    settings.setBackupLastAt(System.currentTimeMillis())
                    Result.success()
                } else Result.failure()
            } catch (e: Throwable) {
                Result.retry()
            }
        }
    }
}
