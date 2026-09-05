package com.embertimer.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.embertimer.data.db.DayProfileTotal
import com.embertimer.data.db.ProfileEntity
import com.embertimer.di.AppGraph
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class ReportRange { WEEK, MONTH, LIFETIME }

data class ReportRow(val label: String, val millis: Long)

data class ProfileTotalUi(val profileName: String, val millis: Long)

data class ReportUiState(
    val range: ReportRange = ReportRange.WEEK,
    val rows: List<ReportRow> = emptyList(),
    val profileTotals: List<ProfileTotalUi> = emptyList(),
)

/** 报表窗口(闭区间 ISO 日期):周 = 本周一(ISO 周一为一周首日)至 today;月 = 本月 1 日至 today。 */
fun reportWindow(range: ReportRange, today: LocalDate): Pair<String, String> = when (range) {
    ReportRange.WEEK -> {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        monday.toString() to today.toString()
    }
    ReportRange.MONTH -> today.withDayOfMonth(1).toString() to today.toString()
    ReportRange.LIFETIME -> LocalDate.ofEpochDay(0).toString() to today.toString()
}

/**
 * 明细行。周报:逐日行(label "MM-dd");月报:按周分桶(label "第 N 周(MM-dd~MM-dd)",
 * 桶 = 当月按日切 7 天段,跨月末尾桶止于 today)。raw 只含窗口内有数据的单元,按日期升序。
 */
fun reportRows(range: ReportRange, today: LocalDate, raw: List<DayProfileTotal>): List<ReportRow> {
    val byDate = raw.groupBy { it.date }.mapValues { (_, rs) -> rs.sumOf { it.total } }
    val dates = byDate.toSortedMap()
    return when (range) {
        ReportRange.WEEK -> dates.map { (date, m) -> ReportRow(date.substring(5), m) }
        ReportRange.LIFETIME -> emptyList() // 长期累计走 profileTotals,明细为空
        ReportRange.MONTH -> dates.entries
            .groupBy { (date, _) -> (LocalDate.parse(date).dayOfMonth - 1) / 7 + 1 }
            .map { (bucket, dayEntries) ->
                val start = today.withDayOfMonth((bucket - 1) * 7 + 1)
                val end = start.plusDays(6).let { if (it.isAfter(today)) today else it }
                val from = start.toString().substring(5)
                val to = end.toString().substring(5)
                ReportRow("第 $bucket 周($from~$to)", dayEntries.sumOf { it.value })
            }
    }
}

fun reportProfileTotals(
    profiles: List<ProfileEntity>,
    raw: List<DayProfileTotal>,
): List<ProfileTotalUi> = raw.groupBy { it.profileId }
    .map { (id, rs) ->
        ProfileTotalUi(
            profileName = profiles.firstOrNull { it.id == id }?.name ?: "已删除时钟",
            millis = rs.sumOf { it.total },
        )
    }
    .sortedByDescending { it.millis }

class ReportViewModel(
    val graph: AppGraph,
    /** 可注入时钟:报表窗口以今天为锚,测试传固定日期 */
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _range = MutableStateFlow(ReportRange.WEEK)
    private val _ui = MutableStateFlow(ReportUiState())
    val ui: StateFlow<ReportUiState> = _ui.asStateFlow()

    init {
        // 生产自动刷新:dayTotals(epoch) 是 daily_total 表失效信号,profiles 是 profile 表失效信号——
        // 范围切换、新增记录、设置页改名/删除配置都驱动同一重建。Report VM 常驻 activity 级
        // ViewModelStore,配置表并入 combine 后改名/删除无需再等新记录或切范围即重解析名称
        viewModelScope.launch {
            combine(
                _range,
                graph.totalsRepo.dayTotals(EPOCH),
                graph.profileRepo.profiles,
            ) { range, _, _ -> range }
                .collect { refreshInternal() }
        }
    }

    fun setRange(r: ReportRange) {
        _range.value = r
        // 乐观同步选中档:按钮立即切换,数据随后由自动重建落地(避免 DB 往返期间滞留旧高亮)
        _ui.value = _ui.value.copy(range = r)
    }

    /** 挂起重建:测试与自动刷新共用同一实现(避开 stateIn/flatMapLatest 的测试环境悬挂) */
    suspend fun refresh() {
        refreshInternal()
    }

    private suspend fun refreshInternal() {
        val range = _range.value
        val profiles = graph.profileRepo.profiles.first()
        if (range == ReportRange.LIFETIME) {
            val lt = graph.totalsRepo.profileTotals().first().map { p ->
                val name = profiles.firstOrNull { it.id == p.profileId }?.name ?: "已删除时钟"
                ProfileTotalUi(name, p.total)
            }.sortedByDescending { it.millis }
            _ui.value = ReportUiState(range = range, rows = emptyList(), profileTotals = lt)
            return
        }
        val today = clock()
        val (from, to) = reportWindow(range, today)
        val raw = graph.totalsRepo.rangeBreakdown(from, to)
        _ui.value = ReportUiState(
            range = range,
            rows = reportRows(range, today, raw),
            profileTotals = reportProfileTotals(profiles, raw),
        )
    }

    private companion object {
        /** 表变更信号窗:全历史起点(与主页 from 口径一致),只为触发 Room 失效重查 */
        val EPOCH: String = LocalDate.ofEpochDay(0).toString()
    }
}
