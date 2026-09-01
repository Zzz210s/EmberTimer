package com.embertimer.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.embertimer.data.ReminderIntensity
import com.embertimer.data.db.ProfileEntity
import com.embertimer.di.AppGraph
import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase
import com.embertimer.timer.RuntimeSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// 绕过 EmberApp 真实装配,保持测试封闭(同进程同 DataStore 文件多实例会抛异常,见 SettingsStoreTest 头注)
@Config(sdk = [34], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private fun snap(status: EngineStatus, profileId: Long = 1) = RuntimeSnapshot(
        profileId, 1, 1, Phase.WORK, status, 0, 0, 1, 0, 0, 0, 0, 0, 0, null, 0,
    )

    @Test fun crudAndTotals() = runTest {
        // 独立 store 文件名:同进程同文件多实例会抛异常(见 AlarmReceiverTest 头注)
        val g = AppGraph(ctx, useInMemoryDb = true, storeFileName = "sv1")
        g.bootstrap()
        val vm = SettingsViewModel(g)
        val id = vm.createProfile("深度", 50, 10)
        assertTrue(id > 0)
        assertEquals(-1L, vm.createProfile("深度", 50, 10)) // 重名拒绝
        vm.renameProfile(id, "深度专注")
        vm.editDurations(ProfileEntity(id, "x", 1, 1, 0), 45, 15)
        assertEquals(45, g.profileRepo.byId(id)!!.workMinutes)
        assertEquals("深度专注", g.profileRepo.byId(id)!!.name)
        vm.setIntensity(ReminderIntensity.STRONG)
        assertEquals(ReminderIntensity.STRONG, g.settingsRepo.reminderIntensity.first())
    }

    @Test fun editDurationsPolicy() = runTest {
        val g = AppGraph(ctx, useInMemoryDb = true, storeFileName = "sv2")
        g.bootstrap()
        val vm = SettingsViewModel(g)
        g.engine.restore(snap(EngineStatus.RUNNING))
        assertFalse(vm.editDurations(ProfileEntity(1, "a", 1, 1, 0), 30, 10)) // RUNNING 拒
        assertEquals(25, g.profileRepo.byId(1)!!.workMinutes) // IGNORED 不写(种子 25 保持)
        g.engine.restore(snap(EngineStatus.PAUSED))
        assertTrue(vm.editDurations(ProfileEntity(1, "a", 1, 1, 0), 30, 10)) // PAUSED 重开
    }

    @Test fun deletePolicy() = runTest {
        val g = AppGraph(ctx, useInMemoryDb = true, storeFileName = "sv3")
        g.bootstrap()
        val vm = SettingsViewModel(g)
        val only = g.profileRepo.profiles.first().first()
        assertFalse(vm.deleteProfile(only)) // 最后一条拒删
        vm.createProfile("B", 50, 10)
        g.engine.restore(snap(EngineStatus.PAUSED, profileId = only.id))
        assertTrue(vm.deleteProfile(only)) // 暂停中的活跃配置:先 reset 再删
        assertEquals(1, g.profileRepo.profiles.first().size)
    }
}
