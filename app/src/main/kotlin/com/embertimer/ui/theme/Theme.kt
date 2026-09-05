package com.embertimer.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** 品牌回退色:ember 橙(动态色不可用时) */
private val EmberOrange = Color(0xFFF4511E)

/**
 * 主题(固定浅色):应用不提供深色模式(v1.4.2 起强制浅色,系统深色下保持浅色外观)。
 * 文字颜色规范(统一色板):
 *  - 主文字:一律主题默认 onSurface,不写死颜色(浅色近黑);
 *  - 次级/元信息(状态徽标、今日合计、时段小行、空态提示、占位、辅助说明、锁定提示):onSurfaceVariant;
 *  - 强调/危险:primary / error。
 * 任何 Text/图标颜色只允许取自以上集合,禁止裸 Color 常量。
 */
@Composable
fun EmberTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = when {
        // 动态取色仅走 light 通道(系统深色不切换配色)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        else -> lightColorScheme(primary = EmberOrange)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
