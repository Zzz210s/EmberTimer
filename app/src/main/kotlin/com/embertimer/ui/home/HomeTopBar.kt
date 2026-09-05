package com.embertimer.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.embertimer.data.db.ProfileEntity
import com.embertimer.timer.EngineStatus
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.PathIcon
import com.embertimer.ui.report.ReportRange

/**
 * 顶栏中央配置下拉(v1.1 #3/#1,取代已删除的 chips 行):当前配置名 + 展开箭头,
 * 点击弹出全部配置(菜单项仅名,#1);点击切换 active(计时中 disabled,口径与旧
 * chips 一致:仅 RUNNING 禁切,暂停中允许按策略重开)。无配置时中央占位"未选择"
 * (原 chips 行空态引导职责并入),点击直达设置。
 */
@Composable
internal fun ProfileDropdown(
    ui: HomeUiState,
    onSelect: (ProfileEntity) -> Unit,
    onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (ui.profiles.isEmpty()) {
            Text(
                "未选择",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onSettings),
            )
            return@Box
        }
        val running = ui.snap?.status == EngineStatus.RUNNING
        val activeName = ui.profiles.firstOrNull { it.id == ui.activeProfileId }?.name ?: "未选择"
        var expanded by remember { mutableStateOf(false) }
        Box(
            Modifier
                .clickable(onClick = { expanded = true })
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(activeName, style = MaterialTheme.typography.titleMedium)
                PathIcon(IconPaths.CHEVRON_DOWN, size = 20.dp, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ui.profiles.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.name) },
                        enabled = !running,
                        onClick = {
                            expanded = false
                            onSelect(p)
                        },
                    )
                }
            }
        }
    }
}

/** 顶栏右侧汉堡菜单(v1.1):周报/月报直达报表屏并预选对应 tab */
@Composable
internal fun ReportMenu(onOpenReport: (ReportRange) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            PathIcon(IconPaths.MENU, size = 24.dp, contentDescription = "菜单")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("周报") },
                onClick = {
                    expanded = false
                    onOpenReport(ReportRange.WEEK)
                },
            )
            DropdownMenuItem(
                text = { Text("月报") },
                onClick = {
                    expanded = false
                    onOpenReport(ReportRange.MONTH)
                },
            )
        }
    }
}
