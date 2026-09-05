package com.embertimer.ui.settings

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.embertimer.data.db.ProfileEntity
import com.embertimer.service.TimerCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 时钟管理页的对话框宿主(编辑/新建/批量删除确认),与列表屏解耦保持各文件 <=200 行 */
@Composable
internal fun ProfileDialogHost(
    vm: SettingsViewModel,
    editing: ProfileEntity?,
    onEditChange: (ProfileEntity?) -> Unit,
    creating: Boolean,
    onCreateChange: (Boolean) -> Unit,
    confirmDelete: Boolean,
    onConfirmDeleteChange: (Boolean) -> Unit,
    deleteMode: Boolean,
    onDeleteModeExit: () -> Unit,
    runningActiveId: Long?,
    selectedIds: Set<Long>,
    profiles: List<ProfileEntity>,
) {
    val ctx: Context = LocalContext.current
    val scope: CoroutineScope = rememberCoroutineScope()

    editing?.let { p ->
        ProfileEditDialog(
            initial = p,
            existing = profiles,
            title = "编辑时钟",
            onDismiss = { onEditChange(null) },
            onConfirm = { name, w, r, mode ->
                scope.launch {
                    try {
                        if (name != p.name) vm.renameProfile(p.id, name)
                        if (vm.editDurations(p, w, r, mode)) {
                            TimerCommands.restartPhase(ctx, p.id, w * 60_000L, r * 60_000L, mode == com.embertimer.data.db.ProfileMode.COUNTUP)
                        }
                        onEditChange(null)
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
            existing = profiles,
            title = "新建时钟",
            onDismiss = { onCreateChange(false) },
            onConfirm = { name, w, r, mode ->
                scope.launch {
                    vm.createProfile(name, w, r, mode)
                    onCreateChange(false)
                }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { onConfirmDeleteChange(false) },
            title = { Text("删除所选时钟") },
            text = { Text("将删除 ${selectedIds.size} 个时钟及其累计记录?不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onConfirmDeleteChange(false)
                    scope.launch {
                        val targets = profiles.filter { it.id in selectedIds }
                        if (targets.size < profiles.size) {
                            targets.forEach { p ->
                                if (vm.deleteProfile(p)) {
                                    if (runningActiveId == p.id) TimerCommands.stop(ctx)
                                }
                            }
                        }
                        onDeleteModeExit()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { onConfirmDeleteChange(false) }) { Text("取消") } },
        )
    }
}
