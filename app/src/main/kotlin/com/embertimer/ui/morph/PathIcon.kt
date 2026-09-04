package com.embertimer.ui.morph

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/** IconPaths 常量的逻辑栅格边长(全部 d 以 24x24 坐标系表达)。 */
internal const val GRID = 24f

/**
 * 静态图标绘制(spec D5):Kotlin 单源 path 圆头描边直绘,不经 painterResource。
 * [d] 为 24 栅格坐标,等比缩放到 [size];[strokeWidth] 单位 px,null 时随 size
 * 等比(24 栅格上 2dp -> size * 2/24)。contentDescription 为 null 时不挂语义
 * (装饰性,与 Icon(null) 对齐)。path 解析按 [d] 记忆化,图标切换才执行一次。
 */
@Composable
fun PathIcon(
    d: String,
    size: Dp,
    contentDescription: String?,
    tint: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
    strokeWidth: Float? = null,
) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Spacer(
        modifier
            .size(size)
            .then(if (contentDescription == null) Modifier else Modifier.semantics { this.contentDescription = contentDescription })
            .drawBehind {
                val s = this.size.width / GRID
                scale(s, s) {
                    drawPath(path, tint, style = strokeStyle(strokeWidth, this.size.width, s))
                }
            },
    )
}

/** 统一描边样式:2dp(24 栅格)等比 + 圆头圆角连接。宽度参数为 px 或缺省等比。 */
internal fun strokeStyle(strokeWidthPx: Float?, drawWidth: Float, scale: Float): Stroke = Stroke(
    width = (strokeWidthPx ?: drawWidth * (2f / GRID)) / scale,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
)
