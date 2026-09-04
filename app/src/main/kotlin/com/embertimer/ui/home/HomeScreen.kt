package com.embertimer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.embertimer.EmberApp
import com.embertimer.data.db.ProfileMode
import com.embertimer.service.ServiceLauncher
import com.embertimer.service.TimerCommands
import com.embertimer.timer.DurationFormat
import com.embertimer.timer.EngineStatus
import com.embertimer.ui.heatmap.Heatmap
import com.embertimer.ui.heatmap.buildHeatmapModel
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.PathIcon
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onSettings: () -> Unit) {
    val app = LocalContext.current.applicationContext as EmberApp
    val vm: HomeViewModel = viewModel(factory = app.graph.vmFactory)
    val ui by vm.ui.collectAsStateWithLifecycle()
    val selectedDay by vm.selectedDay.collectAsStateWithLifecycle()
    val dayDetail by vm.dayDetail.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Task 7 / #10:数字位 = 倒计时剩余 / 正计时已走(计到快照,暂停定格)。
    // countUp elapsed 由 accruedWork 换算(phase 恒 WORK;倒计时分支保持既有 remaining 语义)
    var displayMillis by remember { mutableLongStateOf(0L) }

    LaunchedEffect(
        ui.snap?.status, ui.snap?.endElapsed, ui.snap?.timeAtPause,
        ui.snap?.startElapsed, ui.snap?.countUp,
    ) {
        while (true) {
            displayMillis = ui.snap?.let { s ->
                when {
                    s.countUp -> s.accruedWork(vm.time.elapsedRealtime())
                    s.status == EngineStatus.RUNNING -> s.remaining(vm.time.elapsedRealtime())
                    s.status == EngineStatus.PAUSED -> s.timeAtPause
                    else -> 0L
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
                title = {},
                navigationIcon = {
                    IconButton(onClick = onSettings) { PathIcon(IconPaths.SETTINGS, size = 24.dp, contentDescription = "设置") }
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
                        // 正计时判定来自目标配置(Task 7):暂停中切换/重启会以新 profile 模式重开
                        TimerCommands.restartPhase(
                            ctx, p.id, p.workMinutes * 60_000L, p.restMinutes * 60_000L,
                            countUp = p.mode == ProfileMode.COUNTUP,
                        )
                    }
                }
            })
            TimerCard(ui, displayMillis, onStart = {
                ui.profiles.firstOrNull { it.id == ui.activeProfileId }?.let { p ->
                    TimerCommands.start(
                        ctx, p.id, p.workMinutes * 60_000L, p.restMinutes * 60_000L,
                        countUp = p.mode == ProfileMode.COUNTUP,
                    )
                }
            }, onPause = { TimerCommands.pause(ctx) }, onResume = { TimerCommands.resume(ctx) },
                onSkip = { TimerCommands.skip(ctx) }, onStop = { TimerCommands.stop(ctx) },
                onGoSettings = onSettings)
            Text("今日 " + DurationFormat.hm(ui.todayMillis), style = MaterialTheme.typography.titleMedium)
            Card {
                Column(Modifier.padding(12.dp)) {
                    // R8:模型只依赖 ui.days,remember 键控在 days 上 —— remaining 每 250ms 刷新
                    // 触发整棵重组,若每次内联重建会每秒 4 次重算全历史格子模型
                    val heatmapModel = remember(ui.days) { buildHeatmapModel(ui.days, LocalDate.now()) }
                    Heatmap(heatmapModel, selectedDay) { vm.selectDay(it) }
                    DayDetailCard(dayDetail)
                }
            }
        }
    }
}

@Composable
private fun ProfileChips(ui: HomeUiState, onSelect: (com.embertimer.data.db.ProfileEntity) -> Unit) {
    val running = ui.snap?.status == EngineStatus.RUNNING
    if (ui.profiles.isEmpty()) {
        // #3 首装空态:无配置时 chips 行让位给引导文案,入口在下方计时卡“去设置新建”
        Text(
            "还没有配置,先去设置新建",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
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
