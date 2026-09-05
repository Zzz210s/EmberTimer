package com.embertimer.ui.settings
import android.database.sqlite.SQLiteConstraintException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Alignment
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
/** 时钟管理:点卡片编辑;垃圾桶进删除模式(勾选+底部删除选中);运行中时钟不可选。 */
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
    var deleteMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val runningActiveId = if (ui.snap?.status == EngineStatus.RUNNING) ui.snap?.profileId else null
    BackHandler(enabled = deleteMode) { deleteMode = false; selectedIds = emptySet() }
    fun toggleSelect(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("时钟管理") },
                navigationIcon = {
                    IconButton(onClick = if (deleteMode) { { deleteMode = false; selectedIds = emptySet() } } else onBack) {
                        PathIcon(IconPaths.BACK, size = 24.dp, contentDescription = if (deleteMode) "退出删除" else "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        deleteMode = !deleteMode
                        selectedIds = emptySet()
                    }) {
                        PathIcon(
                            IconPaths.TRASH, size = 24.dp,
                            contentDescription = "删除管理",
                            tint = if (deleteMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!deleteMode) {
                        IconButton(onClick = { creating = true }) {
                            PathIcon(IconPaths.PLUS, size = 24.dp, contentDescription = "新建时钟")
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ui.profiles, key = { it.id }) { p ->
                    val runningActive = runningActiveId == p.id
                    val selected = p.id in selectedIds
                    val canTouch = !deleteMode || (!runningActive && ui.profiles.size > 1)
                    Card(
                        onClick = {
                            when {
                                deleteMode -> if (canTouch) toggleSelect(p.id)
                                !runningActive -> editing = p
                            }
                        },
                        enabled = canTouch || (!deleteMode && !runningActive),
                        border = if (deleteMode && selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                            val durationText = "${p.workMinutes} 分钟工作 / ${p.restMinutes} 分钟休息" +
                                if (p.mode == ProfileMode.COUNTUP) " · 正计时" else ""
                            Text(durationText, style = MaterialTheme.typography.bodyMedium)
                            if (deleteMode) {
                                Text(
                                    if (selected) "已选择删除" else "点击选择",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (runningActive) Text("计时中,不可修改", style = MaterialTheme.typography.labelSmall)
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
            if (deleteMode) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { deleteMode = false; selectedIds = emptySet() }) { Text("取消") }
                    TextButton(
                        enabled = selectedIds.isNotEmpty(),
                        onClick = { confirmDelete = true },
                        modifier = Modifier.align(Alignment.CenterVertically),
                    ) {
                        Text("删除选中(${selectedIds.size})", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    ProfileDialogHost(
        vm = vm,
        editing = editing, onEditChange = { editing = it },
        creating = creating, onCreateChange = { creating = it },
        confirmDelete = confirmDelete, onConfirmDeleteChange = { confirmDelete = it },
        deleteMode = deleteMode, onDeleteModeExit = { deleteMode = false; selectedIds = emptySet() },
        runningActiveId = runningActiveId,
        selectedIds = selectedIds,
        profiles = ui.profiles,
    )
}
