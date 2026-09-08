package com.embertimer.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.embertimer.R
import com.embertimer.EmberApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** v1.9.11 自动备份区块:开关 + 目标网盘目录选择 + 上次备份时间。保证 SettingsScreen ≤200 行。 */
@Composable
internal fun AutoBackupSection(vm: SettingsViewModel) {
    val app = LocalContext.current.applicationContext as EmberApp
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui = vm.ui.collectAsStateWithLifecycle()
    val backupLast = ui.value.backupLastAt

    val pickDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) scope.launch { vm.setBackupUri(ctx, uri.toString()) }
    }

    HorizontalDivider(Modifier.padding(vertical = 10.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.autobackup_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.autobackup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = ui.value.autoBackup,
            onCheckedChange = { on -> vm.setAutoBackup(ctx, on) },
        )
    }
    if (ui.value.autoBackup) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDir.launch(null) }) {
                Text(stringResource(R.string.autobackup_pick_target))
            }
        }
        Text(
            if (ui.value.backupUri.isNullOrEmpty()) stringResource(R.string.autobackup_no_target)
            else stringResource(R.string.autobackup_target, ui.value.backupUri!!),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            stringResource(R.string.autobackup_uninstall_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (backupLast > 0L) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        Text(
            stringResource(R.string.autobackup_last, fmt.format(Date(backupLast))),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
