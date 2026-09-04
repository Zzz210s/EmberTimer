package com.embertimer.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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

/** 设置页「周报/月报」入口打开的报表屏:周/月切换 + 明细 + 尾部各配置合计 + 空态 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as EmberApp
    val vm: ReportViewModel = viewModel(factory = app.graph.vmFactory)
    val ui by vm.ui.collectAsStateWithLifecycle()

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
            val tabs = listOf(ReportRange.WEEK to "周报", ReportRange.MONTH to "月报")
            SingleChoiceSegmentedButtonRow {
                tabs.forEachIndexed { index, (range, label) ->
                    SegmentedButton(
                        selected = ui.range == range,
                        onClick = { vm.setRange(range) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                    ) { Text(label) }
                }
            }
            if (ui.rows.isEmpty()) {
                Text(
                    "本期无专注记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ui.rows.forEach { row -> TotalRow(row.label, row.millis) }
                Text(
                    "各配置合计",
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
