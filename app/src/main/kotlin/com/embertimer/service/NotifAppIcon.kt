package com.embertimer.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path

/**
 * 通知里显示的应用图标 = **运行时从 PackageManager 读取的当前图标**(圆形裁切)。
 * 为什么自带而不交给系统:实测(荣耀 MagicOS)系统在通知里画的是它**缓存的应用图标**,
 * 缓存不会随应用更新刷新(改颜色/改形状/改资源名/卸载重装均无效,只有重启才重建)。
 * 自己渲染 => 图标随版本即时更新,不需要重启。
 */
internal fun appIconBitmap(context: Context, size: Int = 96): Bitmap? = runCatching {
    val d = context.packageManager.getApplicationIcon(context.packageName)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.clipPath(Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) })
    d.setBounds(0, 0, size, size)
    d.draw(canvas)
    bmp
}.getOrNull()
