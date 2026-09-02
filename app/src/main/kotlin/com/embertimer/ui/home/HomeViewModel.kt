package com.embertimer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.embertimer.data.db.ProfileEntity
import com.embertimer.di.AppGraph
import com.embertimer.timer.EnginePolicy
import com.embertimer.timer.PolicyAction
import com.embertimer.timer.RuntimeSnapshot
import com.embertimer.timer.TimeProvider
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val ready: Boolean = false,
    val profiles: List<ProfileEntity> = emptyList(),
    val activeProfileId: Long = -1,
    val snap: RuntimeSnapshot? = null,
    val todayMillis: Long = 0,
    val days: Map<LocalDate, Long> = emptyMap(),
)

data class DayDetailRow(val profileName: String, val millis: Long, val index: Int)
data class DayDetailUi(val date: LocalDate, val totalMillis: Long, val rows: List<DayDetailRow>)

class HomeViewModel(val graph: AppGraph) : ViewModel() {
    val time: TimeProvider get() = graph.time
    private val today: LocalDate = LocalDate.now()

    /** D2:全历史数据窗口 —— 热力图 v2 从最早记录渲染到 today,不再按周数截断 */
    private val from: String = LocalDate.ofEpochDay(0).toString()

    val ui: StateFlow<HomeUiState> = combine(
        graph.engine.ready,
        graph.profileRepo.profiles,
        graph.settingsRepo.activeProfileId,
        graph.engine.snapshot,
        graph.totalsRepo.dayTotals(from),
    ) { ready, profiles, active, snap, totals ->
        HomeUiState(
            ready = ready,
            profiles = profiles,
            activeProfileId = if (profiles.any { it.id == active }) active else profiles.firstOrNull()?.id ?: -1,
            snap = snap,
            todayMillis = totals.firstOrNull { it.date == today.toString() }?.total ?: 0,
            days = totals.associate { LocalDate.parse(it.date) to it.total },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _selectedDay = MutableStateFlow<LocalDate?>(null)
    val selectedDay: StateFlow<LocalDate?> = _selectedDay.asStateFlow()
    fun selectDay(d: LocalDate?) { _selectedDay.value = d }

    @OptIn(ExperimentalCoroutinesApi::class)
    val dayDetail: StateFlow<DayDetailUi?> = _selectedDay
        .flatMapLatest { day ->
            if (day == null) flowOf(null)
            else graph.totalsRepo.let { repo ->
                // 以选中日总额为键驱动重查:dayTotals 是 Room 失效通知流,新增记录后会重发,
                // 每次变化重新查询 breakdownByDate,避免选中期间卡片停留在旧总额上
                repo.dayTotals(from)
                    .map { totals -> totals.firstOrNull { it.date == day.toString() }?.total ?: 0L }
                    .distinctUntilChanged()
                    .map { repo.breakdownByDate(day.toString()) }
                    .combine(graph.profileRepo.profiles) { rows, profiles ->
                        DayDetailUi(
                            date = day,
                            totalMillis = rows.sumOf { it.total },
                            rows = rows.sortedByDescending { it.total }.mapIndexed { i, r ->
                                DayDetailRow(
                                    profileName = profiles.firstOrNull { it.id == r.profileId }?.name ?: "?",
                                    millis = r.total,
                                    index = i,
                                )
                            },
                        )
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** @return true 时调用方需发 TimerCommands.restartPhase */
    suspend fun selectProfile(p: ProfileEntity): Boolean {
        return when (EnginePolicy.onSwitchProfile(graph.engine.snapshot.value)) {
            PolicyAction.RESTART_PHASE -> {
                graph.settingsRepo.setActiveProfile(p.id)
                true
            }
            PolicyAction.SET_ACTIVE -> {
                graph.settingsRepo.setActiveProfile(p.id)
                false
            }
            else -> false
        }
    }
}
