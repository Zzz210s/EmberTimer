package com.embertimer.ui.report

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.embertimer.ui.theme.MotionTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.embertimer.EmberApp
import com.embertimer.timer.DurationFormat
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.PathIcon

/** 报表屏(主页汉堡进入):周报/月报/时钟累计 三页签 + 明细 + 各时钟合计 + 空态 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(onBack: () -> Unit, initialRange: ReportRange = ReportRange.WEEK) {
    val app = LocalContext.current.applicationContext as EmberApp
    val vm: ReportViewModel = viewModel(factory = app.graph.vmFactory)
    val ui by vm.ui.collectAsStateWithLifecycle()
    // v1.1 顶栏汉堡/设置入口携带预选范围:VM 常驻 activity 级 store,每次进屏重放 setRange
    LaunchedEffect(initialRange) { vm.setRange(initialRange) }
    // 系统返回等同 Toolbar BACK:REPORT -> SETTINGS(pop),不结束 Activity
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("报表") },
                navigationIcon = {
                    IconButton(onClick = onBack) { PathIcon(IconPaths.BACK, size = 24.dp, contentDescription = "返回") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tabs = listOf(ReportRange.WEEK to "周报", ReportRange.MONTH to "月报", ReportRange.LIFETIME to "时钟累计")
            SingleChoiceSegmentedButtonRow {
                tabs.forEachIndexed { index, (range, label) ->
                    SegmentedButton(
                        selected = ui.range == range,
                        onClick = { vm.setRange(range) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                    ) { Text(label) }
                }
            }
            // 周/月/时钟累计内容直渲(曾包 AnimatedContent 时内容停留首帧旧 ui,直渲零状态依赖)
            if (ui.range == ReportRange.LIFETIME) {
                if (ui.profileTotals.isEmpty()) {
                    Text(
                        "还没有专注记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "各时钟累计(起用至今)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    ui.profileTotals.forEach { p -> TotalRow(p.profileName, p.millis) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    TotalRow("全部合计", ui.profileTotals.sumOf { it.millis })
                }
            } else if (ui.rows.isEmpty()) {
                Text(
                    "本期无专注记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ui.rows.forEach { row -> TotalRow(row.label, row.millis) }
                Text(
                    "各时钟合计",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ui.profileTotals.forEach { p -> TotalRow(p.profileName, p.millis) }
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, millis: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(DurationFormat.hm(millis), style = MaterialTheme.typography.bodyMedium)
    }
}
