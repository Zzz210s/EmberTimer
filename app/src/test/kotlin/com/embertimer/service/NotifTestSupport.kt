package com.embertimer.service

import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase
import com.embertimer.timer.RuntimeSnapshot

/** 通知测试共用构造与反射工具(v1.11.0 拆分,保持单文件 <=200 行) */
internal fun snapOf(
    phase: Phase = Phase.WORK,
    status: EngineStatus = EngineStatus.RUNNING,
    startElapsed: Long = 0L,
    endElapsed: Long = 60_000L,
    timeSpentPaused: Long = 0L,
    timeAtPause: Long = 0L,
    cycleCount: Int = 0,
    countUp: Boolean = false,
): RuntimeSnapshot = RuntimeSnapshot(
    profileId = 1, workMillis = 60_000, restMillis = 60_000, phase = phase, status = status,
    cycleCount = cycleCount, startElapsed = startElapsed, endElapsed = endElapsed, endWall = 0L,
    timeSpentPaused = timeSpentPaused, lastPauseTime = 0L, timeAtPause = timeAtPause,
    savedAtWall = 0L, savedAtElapsed = 0L, ckptDate = null, ckptAccum = 0L, countUp = countUp,
)
    /** 沿类层次查找字段(RemoteViews 的 Action 子类把 methodName 声明在父类) */
    internal fun fieldValue(target: Any, name: String): Any? {
        var c: Class<*>? = target.javaClass
        while (c != null) {
            runCatching {
                val f = c!!.getDeclaredField(name)
                f.isAccessible = true
                return f.get(target)
            }
            c = c.superclass
        }
        return null
    }

