package com.embertimer.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.embertimer.R
import com.embertimer.data.DataTransfer
import com.embertimer.EmberApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** v1.9.10 备份/恢复(SAF):备份到任意文件/网盘位置;恢复选同一文件合并。两个 launcher 供 SettingsScreen 用。 */
@Composable
internal fun rememberBackupLaunchers(): Pair<(String) -> Unit, () -> Unit> {
    val app = LocalContext.current.applicationContext as EmberApp
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = DataTransfer.exportJson(app.graph.db)
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            }
            Toast.makeText(ctx, ctx.getString(R.string.data_exported), Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                }
                val counts = DataTransfer.importJson(app.graph.db, text)
                Toast.makeText(ctx, ctx.getString(R.string.data_imported, counts.dailyTotals), Toast.LENGTH_SHORT).show()
                true
            }.getOrDefault(false)
            if (!ok) Toast.makeText(ctx, ctx.getString(R.string.data_import_failed), Toast.LENGTH_SHORT).show()
        }
    }

    return Pair(
        { name -> exportLauncher.launch(name) },
        { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
    )
}
