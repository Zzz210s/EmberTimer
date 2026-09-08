package com.embertimer.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * v1.9.13 备份写盘:在用户选定的 SAF 目录(tree Uri)下写入固定文件,已存在则覆盖 ——
 * 避免每次备份产生 (1)(2) 副本(用户要求覆盖,而非叠加版本)。自动/手动备份共用。
 *
 * 用 DocumentFile.listFiles().find{name} 而非 findFile:部分 provider 对 createFile 的
 * displayName/mime 解析不一致,findFile 可能匹配不上同名文件而重复 createFile 生成 (1)(2)。
 */
object BackupWriter {
    const val FILE_NAME = "embertimer-backup.json"
    private const val MIME = "application/json"

    fun write(context: Context, treeUri: Uri, json: String): Boolean {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        val existing = tree.listFiles().firstOrNull { it.name == FILE_NAME }
        val file = existing ?: tree.createFile(MIME, FILE_NAME) ?: return false
        return try {
            context.contentResolver.openOutputStream(file.uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null
        } catch (_: Throwable) {
            false
        }
    }
}
