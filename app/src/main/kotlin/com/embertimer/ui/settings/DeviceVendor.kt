package com.embertimer.ui.settings

import android.os.Build

/**
 * 厂商判定(用于"通知图标缓存"这类 OEM 特有行为的定向提示)。
 *
 * 背景实测(荣耀 MagicOS):通知栏头部的应用图标由**系统**绘制,且系统会把它**缓存**住,
 * 应用更新后缓存不刷新(改色/改形状/换资源名/卸载重装均无效,仅重启或切换系统主题才重建)。
 * 应用侧无法清除该缓存,因此这里只做"识别 + 给出可执行的刷新指引"。
 */
internal object DeviceVendor {
    private fun prop(key: String): String = runCatching {
        val c = Class.forName("android.os.SystemProperties")
        val m = c.getMethod("get", String::class.java)
        (m.invoke(null, key) as? String).orEmpty()
    }.getOrDefault("")

    /** 荣耀/华为(含 MagicOS/EMUI):通知图标由系统绘制并缓存 */
    fun cachesNotificationIcon(): Boolean {
        val id = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return id.contains("honor") || id.contains("huawei") ||
            prop("ro.build.version.emui").isNotEmpty() ||
            prop("ro.build.version.magic").isNotEmpty()
    }
}
