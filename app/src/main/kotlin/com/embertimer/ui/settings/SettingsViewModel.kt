package com.embertimer.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.embertimer.data.ReminderIntensity
import com.embertimer.data.db.ProfileEntity
import com.embertimer.di.AppGraph
import com.embertimer.timer.EnginePolicy
import com.embertimer.timer.PolicyAction
import com.embertimer.timer.RuntimeSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val profiles: List<ProfileEntity> = emptyList(),
    val totals: Map<Long, Long> = emptyMap(),
    val intensity: ReminderIntensity = ReminderIntensity.STANDARD,
    val snap: RuntimeSnapshot? = null,
    val exactAlarmBlocked: Boolean = false,
)

class SettingsViewModel(val graph: AppGraph) : ViewModel() {

    private val _exactAlarmBlocked = kotlinx.coroutines.flow.MutableStateFlow(false)

    val ui: StateFlow<SettingsUiState> = combine(
        graph.profileRepo.profiles,
        graph.totalsRepo.profileTotals(),
        graph.settingsRepo.reminderIntensity,
        graph.engine.snapshot,
        _exactAlarmBlocked,
    ) { profiles, totals, intensity, snap, blocked ->
        SettingsUiState(profiles, totals.associate { it.profileId to it.total }, intensity, snap, blocked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun refreshExactAlarm(context: Context) {
        viewModelScope.launch {
            val blocked = Build.VERSION.SDK_INT >= 31 &&
                !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            _exactAlarmBlocked.value = blocked
        }
    }

    suspend fun createProfile(name: String, workMinutes: Int, restMinutes: Int): Long =
        graph.profileRepo.create(name, workMinutes, restMinutes)

    suspend fun renameProfile(id: Long, name: String) = graph.profileRepo.rename(id, name)

    /** @return true 时调用方需发 TimerCommands.restartPhase */
    suspend fun editDurations(p: ProfileEntity, workMinutes: Int, restMinutes: Int): Boolean {
        val action = EnginePolicy.onEditDurations(graph.engine.snapshot.value, p.id)
        if (action == PolicyAction.IGNORED) return false
        graph.profileRepo.updateDurations(p.id, workMinutes, restMinutes)
        return action == PolicyAction.RESTART_PHASE
    }

    /** @return true 时调用方需先发 TimerCommands.stop 再删除 */
    suspend fun deleteProfile(p: ProfileEntity): Boolean {
        val action = EnginePolicy.onDelete(graph.engine.snapshot.value, p.id, graph.profileRepo.count().toInt())
        when (action) {
            PolicyAction.IGNORED -> return false
            PolicyAction.RESET_THEN_DELETE -> {
                graph.profileRepo.delete(p)
                return true // 调用方发 stop(顺序:reset 引擎结算后清快照;DB 行已删)
            }
            PolicyAction.DELETE -> { graph.profileRepo.delete(p); return false }
            else -> return false
        }
    }

    /** suspend 落库(非计划里的 viewModelScope 发射后不管):Robolectric 主循环暂停, fire-and-forget
     *  对测试不可见;改为挂起语义与 selectProfile(H1 pin settings writes)一致 */
    suspend fun setIntensity(i: ReminderIntensity) = graph.settingsRepo.setReminderIntensity(i)
}
