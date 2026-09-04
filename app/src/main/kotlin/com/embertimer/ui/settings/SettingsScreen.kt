package com.embertimer.ui.settings

import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.embertimer.EmberApp
import com.embertimer.data.ReminderIntensity
import com.embertimer.data.db.ProfileEntity
import com.embertimer.service.TimerCommands
import com.embertimer.timer.DurationFormat
import com.embertimer.timer.EngineStatus
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.PathIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as EmberApp
    val vm: SettingsViewModel = viewModel(factory = app.graph.vmFactory)
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshExactAlarm(ctx) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { IconButton(onClick = onBack) { PathIcon(IconPaths.BACK, size = 24.dp, contentDescription = "返回") } },
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
                            Text("精确闹钟未授权,后台切换可能有分钟级延迟", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = {
                                if (Build.VERSION.SDK_INT >= 31) {
                                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                }
                            }) { Text("去授权") }
                        }
                    }
                }
            }
            item {
                Text("时长配置", style = MaterialTheme.typography.titleMedium)
            }
            items(ui.profiles, key = { it.id }) { p ->
                val runningActive = ui.snap?.status == EngineStatus.RUNNING &&
                    ui.snap?.profileId == p.id
                Card {
                    Column(Modifier.padding(12.dp).fillMaxWidth()) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                        Text("${p.workMinutes} 分钟工作 / ${p.restMinutes} 分钟休息")
                        Text("累计 " + DurationFormat.hm(ui.totals[p.id] ?: 0L))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !runningActive, onClick = { editing = p }) { Text("编辑") }
                            TextButton(
                                enabled = !runningActive && ui.profiles.size > 1,
                                onClick = {
                                    scope.launch {
                                        if (vm.deleteProfile(p)) TimerCommands.stop(ctx)
                                    }
                                },
                            ) { Text("删除") }
                        }
                        if (runningActive) Text("计时进行中,不可修改", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                Button(onClick = { creating = true }) { Text("新建配置") }
            }
            item {
                Text("提醒强度", style = MaterialTheme.typography.titleMedium)
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
                                    ReminderIntensity.LIGHT -> "轻"
                                    ReminderIntensity.STANDARD -> "标准"
                                    ReminderIntensity.STRONG -> "强"
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { p ->
        ProfileEditDialog(
            initial = p,
            existing = ui.profiles,
            title = "编辑配置",
            onDismiss = { editing = null },
            onConfirm = { name, w, r ->
                scope.launch {
                    try {
                        // 名称变更需显式 rename(editDurations 只落时长);先 rename 后时长:
                        // rename 重名抛 SQLiteConstraintException 时不动时长、保持对话框
                        if (name != p.name) vm.renameProfile(p.id, name)
                        if (vm.editDurations(p, w, r)) {
                            TimerCommands.restartPhase(ctx, p.id, w * 60_000L, r * 60_000L)
                        }
                        editing = null
                    } catch (_: SQLiteConstraintException) {
                        // 预验证后不应到达(极端并发兜底):对话框保持开启由用户改名
                    }
                }
            },
        )
    }
    if (creating) {
        ProfileEditDialog(
            initial = null,
            existing = ui.profiles,
            title = "新建配置",
            onDismiss = { creating = false },
            onConfirm = { name, w, r ->
                scope.launch {
                    vm.createProfile(name, w, r) // 对话框已预验证重名,-1 不应出现
                    creating = false
                }
            },
        )
    }
}
