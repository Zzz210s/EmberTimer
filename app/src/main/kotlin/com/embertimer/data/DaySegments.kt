package com.embertimer.data

import java.time.Instant
import java.time.ZoneId

/**
 * v1.10:每日详情「时段明细」的展示规则(取代 v1.8.3 的"暂停超过阈值即分段"规则)。
 *
 * 入库粒度不变(仍按暂停窗分段,保留长暂停造成的时间空档),但**可见的分合由本文件决定**:
 * 前一段结束 -> 后一段开始的间隔不超过 [MERGE_GAP_MS] 即合并为一条;超过则保留为两条。
 * 因此旧规则(阈值决定一切)被废除——现在只有"间隔 > 3 分钟"才会显示成两段。
 */
const val MERGE_GAP_MS: Long = 3 * 60_000

/**
 * 合并相邻段:输入为各段 [startAt, endAt](墙钟 ms),输出按起点升序的合并结果。
 * 间隔(后段起点 - 前段终点) <= [maxGapMs] 时并入前段(终点取较大者);否则断开。
 * 空段(终点 <= 起点)先被剔除。
 */
fun mergeSessions(
    sessions: List<Pair<Long, Long>>,
    maxGapMs: Long = MERGE_GAP_MS,
): List<Pair<Long, Long>> {
    val sorted = sessions.filter { it.second > it.first }.sortedBy { it.first }
    if (sorted.isEmpty()) return emptyList()
    val out = ArrayList<Pair<Long, Long>>(sorted.size)
    var curStart = sorted[0].first
    var curEnd = sorted[0].second
    for (i in 1 until sorted.size) {
        val (s, e) = sorted[i]
        if (s - curEnd <= maxGapMs) {
            if (e > curEnd) curEnd = e
        } else {
            out += curStart to curEnd
            curStart = s
            curEnd = e
        }
    }
    out += curStart to curEnd
    return out
}

/** 一天中的大时段;展示文案由 UI 层映射到字符串资源(中英双语) */
enum class DayPeriod { DAWN, EARLY_MORNING, MORNING, AFTERNOON, EVENING }

/** 以段起点所在小时判定大时段:凌晨 0-5 / 早上 6-8 / 上午 9-11 / 下午 12-17 / 晚上 18-23 */
fun dayPeriodOf(startWallMs: Long, zone: ZoneId = ZoneId.systemDefault()): DayPeriod =
    when (Instant.ofEpochMilli(startWallMs).atZone(zone).hour) {
        in 0..5 -> DayPeriod.DAWN
        in 6..8 -> DayPeriod.EARLY_MORNING
        in 9..11 -> DayPeriod.MORNING
        in 12..17 -> DayPeriod.AFTERNOON
        else -> DayPeriod.EVENING
    }
