package com.embertimer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.embertimer.data.db.ProfileEntity

// 自 SettingsScreen 拆出(200 行规则):编辑/新建共用的配置输入对话框。
// internal 而非 private:SettingsScreen.kt 跨文件调用,app 模块内可见即止
@Composable
internal fun ProfileEditDialog(
    initial: ProfileEntity?,
    existing: List<ProfileEntity>,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, work: Int, rest: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var work by remember { mutableStateOf((initial?.workMinutes ?: 25).toString()) }
    var rest by remember { mutableStateOf((initial?.restMinutes ?: 5).toString()) }
    // name 唯一索引(Task 2):重名会触发 SQLiteConstraintException,必须对话框侧预验证
    val trimmed = name.trim()
    val nameTaken = existing.any { it.name == trimmed && (initial == null || it.id != initial.id) }
    val valid = name.isNotBlank() && !nameTaken &&
        work.toIntOrNull() in 1..180 && rest.toIntOrNull() in 1..60
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") })
                OutlinedTextField(value = work, onValueChange = { work = it }, label = { Text("工作分钟 (1-180)") })
                OutlinedTextField(value = rest, onValueChange = { rest = it }, label = { Text("休息分钟 (1-60)") })
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(name.trim(), work.toInt(), rest.toInt()) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
