package com.embertimer.ui.settings

import android.database.sqlite.SQLiteConstraintException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.embertimer.data.db.ProfileEntity
import com.embertimer.data.db.ProfileMode
import com.embertimer.service.TimerCommands
import com.embertimer.timer.EngineStatus
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.PathIcon
import kotlinx.coroutines.launch

/**
 * 时钟管理页(v1.3 设置重构):原设置页「时钟管理」区块独立成页,由主页配置下拉面板
 * 顶部「时钟管理」行进入。内容 = 配置卡片(名称/时长/模式标注/累计/编辑·删除,计时中
 * 锁定)+ 新建按钮 + 编辑/新建对话框。返回回主页。与设置页共享 activity 级 SettingsViewModel。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as EmberApp
    val vm: SettingsViewModel = viewModel(factory = app.graph.vmFactory)
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("时钟管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { PathIcon(IconPaths.BACK, size = 24.dp, contentDescription = "返回") }
                },
                actions = {
                    // v1.3 #5:新建收进标题栏最右 "+"(替换原底部整行按钮)
                    IconButton(onClick = { creating = true }) {
                        PathIcon(IconPaths.PLUS, size = 24.dp, contentDescription = "新建时钟")
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ui.profiles, key = { it.id }) { p ->
                val runningActive = ui.snap?.status == EngineStatus.RUNNING && ui.snap?.profileId == p.id
                val countUp = p.mode == ProfileMode.COUNTUP
                Card {
                    Column(Modifier.padding(12.dp).fillMaxWidth()) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                        val durationText = "${p.workMinutes} 分钟工作 / ${p.restMinutes} 分钟休息" +
                            if (countUp) " · 正计时" else ""
                        Text(durationText, style = MaterialTheme.typography.bodyMedium)
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
            if (ui.profiles.isEmpty()) {
                item {
                    Text(
                        "还没有时钟,点击右上角 + 新建",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    editing?.let { p ->
        ProfileEditDialog(
            initial = p,
            existing = ui.profiles,
            title = "编辑时钟",
            onDismiss = { editing = null },
            onConfirm = { name, w, r, mode ->
                scope.launch {
                    try {
                        if (name != p.name) vm.renameProfile(p.id, name)
                        if (vm.editDurations(p, w, r, mode)) {
                            TimerCommands.restartPhase(ctx, p.id, w * 60_000L, r * 60_000L, mode == ProfileMode.COUNTUP)
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
            title = "新建时钟",
            onDismiss = { creating = false },
            onConfirm = { name, w, r, mode ->
                scope.launch {
                    vm.createProfile(name, w, r, mode)
                    creating = false
                }
            },
        )
    }
}
