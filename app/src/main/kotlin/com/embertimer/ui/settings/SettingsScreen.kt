package com.embertimer.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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

/**
 * 设置页(v1.3 重构后瘦身):仅保留 精确闹钟授权横幅 + 提醒强度 两块;
 * 时长配置已独立为「配置管理」页(主页配置下拉面板顶部入口)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as EmberApp
    val vm: SettingsViewModel = viewModel(factory = app.graph.vmFactory)
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.refreshExactAlarm(ctx) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { PathIcon(IconPaths.BACK, size = 24.dp, contentDescription = "返回") }
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
}
