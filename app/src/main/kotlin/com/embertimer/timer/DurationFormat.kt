package com.embertimer.timer

object DurationFormat {
    /** "X 小时 Y 分钟"; 0 -> "0 分钟"; 不足 1 分钟向上取整(59,999ms -> "1 分钟") */
    fun hm(millis: Long): String {
        val totalMinutes = (millis + 59_999) / 60_000 // 向上取整
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0L -> "$m 分钟"
            else -> "$h 小时 $m 分钟"
        }
    }

    /** "mm:ss",超过一小时也继续累计分钟;负数钳为 0 */
    fun ms(millis: Long): String {
        val t = (millis / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(t / 60, t % 60)
    }
}
