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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val ready: Boolean = false,
    val profiles: List<ProfileEntity> = emptyList(),
    val activeProfileId: Long = -1,
    val snap: RuntimeSnapshot? = null,
    val todayMillis: Long = 0,
    val days: Map<LocalDate, Long> = emptyMap(),
)

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
