package com.embertimer.ui.settings

import com.embertimer.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.embertimer.ui.theme.ThemePack
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.embertimer.EmberApp
import com.embertimer.data.ReminderIntensity
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.PathIcon
import kotlinx.coroutines.launch

/** 设置页:精确闹钟横幅 + 配色 + 数据(备份/恢复) + 提醒强度。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as EmberApp
    val vm: SettingsViewModel = viewModel(factory = app.graph.vmFactory)
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val (launchExport, launchImport) = rememberBackupLaunchers()
    LaunchedEffect(Unit) { vm.refreshExactAlarm(ctx) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { PathIcon(IconPaths.BACK, size = 24.dp, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.exactAlarmBlocked) {
                item {
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.exact_alarm_hint), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = {
                                if (Build.VERSION.SDK_INT >= 31) {
                                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                }
                            }) { Text(stringResource(R.string.go_grant)) }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(stringResource(R.string.color_theme), style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ThemePack.entries.forEach { pack ->
                                Swatch(
                                    name = stringResource(pack.labelRes),
                                    color = pack.primary,
                                    selected = ui.themePack == pack,
                                    onClick = { scope.launch { vm.setThemePack(pack) } },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(stringResource(R.string.data_section), style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { launchExport("embertimer-backup.json") }) { Text(stringResource(R.string.export_data)) }
                            OutlinedButton(onClick = { launchImport() }) { Text(stringResource(R.string.import_data)) }
                        }
                        Text(
                            stringResource(R.string.backup_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        AutoBackupSection(vm)
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(stringResource(R.string.reminder_intensity), style = MaterialTheme.typography.titleMedium)
                        SingleChoiceSegmentedButtonRow {
                            ReminderIntensity.entries.forEachIndexed { index, intensity ->
                                SegmentedButton(
                                    selected = ui.intensity == intensity,
                                    onClick = { scope.launch { vm.setIntensity(intensity) } },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ReminderIntensity.entries.size,
                                    ),
                                ) {
                                    Text(
                                        when (intensity) {
                                            ReminderIntensity.LIGHT -> stringResource(R.string.intensity_light)
                                            ReminderIntensity.STANDARD -> stringResource(R.string.intensity_standard)
                                            ReminderIntensity.STRONG -> stringResource(R.string.intensity_strong)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(name: String, color: androidx.compose.ui.graphics.Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val border = if (selected) 2.dp else 1.dp
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .border(border, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(color))
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}