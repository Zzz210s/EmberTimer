package com.embertimer.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** D6:animator 缩放为 0 = 系统"关闭动画",所有动效退化为瞬时切换 */
fun animationsEnabledFor(animatorScale: Float): Boolean = animatorScale != 0f

/**
 * 尊重系统"关闭动画"(D6):animator 时长缩放为 0 时返回 false,
 * 本应用全部动效(morph/按压缩放/徽标弹跳)退化为瞬时切换。
 * 读一次后按组合生命周期记住;读取失败(如权限/平台差异)回落 true(动效开启)。
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        val scale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
            )
        } catch (_: Exception) {
            1f
        }
        animationsEnabledFor(scale)
    }
}
