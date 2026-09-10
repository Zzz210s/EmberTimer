package com.embertimer.service

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.atomic.AtomicReference

/**
 * v1.10.3:通知内展示 **应用图标** —— 修复"通知栏图标与 app 图标不同"。
 *
 * 状态栏小图标受平台约束只能是单色剪影(ic_notif_flame),但通知内容行直接放**真实 app 图标位图**,
 * 来源与桌面图标完全一致(PackageManager.getApplicationIcon),因此两处观感统一。
 * 位图带进程内缓存,避免每次 notify 都解析自适应图标。
 */
private val cache = AtomicReference<Bitmap?>()

internal fun appIconBitmap(context: Context): Bitmap? =
    cache.get() ?: runCatching {
        context.packageManager.getApplicationIcon(context.packageName)
            .toBitmap(144, 144)
            .also { cache.set(it) }
    }.getOrNull()
