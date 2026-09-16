package com.embertimer.diag

import android.content.Context
import android.content.pm.ApplicationInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.12.2 **仅 debug 构建**的诊断日志(内存环形缓冲,不落盘、不上传)。
 *
 * 用途:真机上"通知栏为什么消失 / 到点为什么没切换"这类问题,靠 logcat 常常读不到
 * (部分机型/无线 adb 不返回日志),所以把关键节点记在应用内,由设置页「诊断(debug)」
 * 面板展示,再用 uiautomator 抓取 —— 不依赖 logcat。
 *
 * 正式版:`markEnabled()` 检测到非 debuggable 时 `enabled=false`,add() 直接返回(零开销)。
 */
object DiagLog {
    data class Entry(val at: Long, val tag: String, val text: String)

    @Volatile var enabled: Boolean = false
        private set

    private const val CAP = 80
    private val buf = ArrayDeque<Entry>()

    /** 应用启动时调用一次:按 FLAG_DEBUGGABLE 决定是否启用 */
    fun markEnabled(context: Context) {
        enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun add(tag: String, text: String) {
        if (!enabled) return
        synchronized(buf) {
            buf.addLast(Entry(System.currentTimeMillis(), tag, text))
            while (buf.size > CAP) buf.removeFirst()
        }
    }

    /** 最近 [limit] 条,按时间正序(界面自上而下 = 由旧到新) */
    fun recent(limit: Int = 30): List<Entry> = synchronized(buf) { buf.toList().takeLast(limit) }

    fun clear() = synchronized(buf) { buf.clear() }

    fun format(at: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(at))
}
