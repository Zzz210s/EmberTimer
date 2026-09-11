package com.embertimer.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * 备份写盘:在用户选定的 SAF 目录(tree Uri)下写入固定文件,已存在则覆盖 ——
 * 避免每次备份产生 (1)(2) 副本(用户要求覆盖,而非叠加版本)。自动/手动备份共用。
 *
 * 用 DocumentFile.listFiles().find{name} 而非 findFile:部分 provider 对 createFile 的
 * displayName/mime 解析不一致,findFile 可能匹配不上同名文件而重复 createFile 生成 (1)(2)。
 *
 * v1.11.2 修复:**必须先删除再新建**。此前直接 openOutputStream 覆盖,在外部存储 provider 上
 * 不会截断(新的 JSON 比旧的短时,旧内容尾部残留)→ 备份文件损坏、无法解析(实测踩到)。
 */
object BackupWriter {
    const val FILE_NAME = "embertimer-backup.json"
    private const val MIME = "application/json"

    fun write(context: Context, treeUri: Uri, json: String): Boolean = try {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        // 覆盖 = 删除同名文件后重新创建(确保截断,不受 provider 的 append 语义影响)
        tree.listFiles().filter { it.name == FILE_NAME }.forEach { it.delete() }
        val file = tree.createFile(MIME, FILE_NAME) ?: return false
        context.contentResolver.openOutputStream(file.uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null
    } catch (_: Throwable) {
        false
    }
}
