package com.embertimer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.embertimer.EmberApp
import com.embertimer.service.ServiceLauncher
import com.embertimer.service.TimerCommands
import com.embertimer.timer.DurationFormat
import com.embertimer.timer.EngineStatus
import com.embertimer.ui.heatmap.Heatmap
import com.embertimer.ui.heatmap.buildHeatmapModel
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onSettings: () -> Unit) {
    val app = LocalContext.current.applicationContext as EmberApp
    val vm: HomeViewModel = viewModel(factory = app.graph.vmFactory)
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var remaining by remember { mutableLongStateOf(0L) }

    LaunchedEffect(ui.snap?.status, ui.snap?.endElapsed, ui.snap?.timeAtPause) {
        while (true) {
            remaining = ui.snap?.let { s ->
                when (s.status) {
                    EngineStatus.RUNNING -> s.remaining(vm.time.elapsedRealtime())
                    EngineStatus.PAUSED -> s.timeAtPause
                    EngineStatus.IDLE -> 0L
                }
            } ?: 0L
            delay(250)
        }
    }

    // 验收修复(用例 2):force-stop 会清除应用闹钟且 stopped state 拦截广播,重开应用后
    // 若无人在跑,UI 只回显引擎快照会冻结在 00:00。快照非空时确保服务在跑:无 action
    // 启动 -> 服务对账(过期推进落账/活跃重武装/空闲自停),幂等;服务已在跑时仅重复前台化。
    // key 为存在性布尔:阶段推进不重复触发,仅 null->非null/首帧带快照时启动一次。
    LaunchedEffect(ui.snap != null) {
        if (ui.snap != null) ServiceLauncher.ensureServiceRunning(ctx)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EmberTimer") },
                navigationIcon = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "设置") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileChips(ui, onSelect = { p ->
                scope.launch {
                    if (vm.selectProfile(p)) {
                        TimerCommands.restartPhase(ctx, p.id, p.workMinutes * 60_000L, p.restMinutes * 60_000L)
                    }
                }
            })
            TimerCard(ui, remaining, onStart = {
                ui.profiles.firstOrNull { it.id == ui.activeProfileId }?.let { p ->
                    TimerCommands.start(ctx, p.id, p.workMinutes * 60_000L, p.restMinutes * 60_000L)
                }
            }, onPause = { TimerCommands.pause(ctx) }, onResume = { TimerCommands.resume(ctx) },
                onSkip = { TimerCommands.skip(ctx) }, onReset = { TimerCommands.reset(ctx) })
            Text("今日 " + DurationFormat.hm(ui.todayMillis), style = MaterialTheme.typography.titleMedium)
            Card {
                Column(Modifier.padding(12.dp)) {
                    // R8:模型只依赖 ui.days,remember 键控在 days 上 —— remaining 每 250ms 刷新
                    // 触发整棵重组,若每次内联重建会每秒 4 次重算全历史格子模型
                    val heatmapModel = remember(ui.days) { buildHeatmapModel(ui.days, LocalDate.now()) }
                    Heatmap(heatmapModel, selected) { selected = it }
                    selected?.let { d ->
                        Text(
                            d.toString() + "：" + DurationFormat.hm(ui.days[d] ?: 0L),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileChips(ui: HomeUiState, onSelect: (com.embertimer.data.db.ProfileEntity) -> Unit) {
    val running = ui.snap?.status == EngineStatus.RUNNING
    // 横向滚动:设置页可建多配置后 chips 可能超出屏宽(Task 13 评审 H5,路由到本任务)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ui.profiles.forEach { p ->
            FilterChip(
                selected = p.id == ui.activeProfileId,
                enabled = !running,
                onClick = { onSelect(p) },
                label = { Text(p.name + " " + p.workMinutes + "/" + p.restMinutes) },
            )
        }
    }
}

@Composable
private fun TimerCard(
    ui: HomeUiState,
    remaining: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
) {
    val snap = ui.snap
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val phaseText = when {
                snap == null -> "空闲"
                snap.phase == com.embertimer.timer.Phase.WORK -> "工作中"
                else -> "休息中"
            }
            Text(phaseText, style = MaterialTheme.typography.titleMedium)
            Text(
                DurationFormat.ms(remaining),
                fontSize = 56.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Text("循环 ${snap?.cycleCount ?: 0}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (snap?.status) {
                    null, EngineStatus.IDLE -> Button(onClick = onStart, enabled = ui.ready && ui.activeProfileId != -1L) { Text("开始") }
                    EngineStatus.RUNNING -> Button(onClick = onPause) { Text("暂停") }
                    EngineStatus.PAUSED -> Button(onClick = onResume) { Text("恢复") }
                }
                if (snap != null && snap.status != EngineStatus.IDLE) {
                    OutlinedButton(onClick = onSkip) { Text("跳过") }
                    TextButton(onClick = onReset) { Text("重置") }
                }
            }
        }
    }
}
