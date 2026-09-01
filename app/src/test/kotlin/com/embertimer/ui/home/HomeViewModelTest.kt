package com.embertimer.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
@Config(sdk = [34], application = android.app.Application::class) // 绕过 EmberApp 真实装配,保持测试封闭
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private fun snap(status: EngineStatus) = RuntimeSnapshot(
        profileId = 1, workMillis = 1, restMillis = 1, phase = Phase.WORK, status = status,
        cycleCount = 0, startElapsed = 0, endElapsed = 1, endWall = 0,
        timeSpentPaused = 0, lastPauseTime = 0, timeAtPause = 0,
        savedAtWall = 0, savedAtElapsed = 0, ckptDate = null, ckptAccum = 0,
    )

    @Test fun selectProfilePolicy() = runTest {
        // 新增第二个 @Test 必须用独立 store 文件名:同进程同文件多实例会抛异常(见 AlarmReceiverTest 头注)
        val g = AppGraph(ctx, useInMemoryDb = true, storeFileName = "hv1")
        g.bootstrap()
        val vm = HomeViewModel(g)
        // RUNNING:忽略
        g.engine.restore(snap(EngineStatus.RUNNING))
        assertFalse(vm.selectProfile(ProfileEntity(9, "X", 25, 5, 0)))
        assertEquals(-1L, g.settingsRepo.activeProfileId.first()) // IGNORED 不写
        // PAUSED:重开
        g.engine.restore(snap(EngineStatus.PAUSED))
        assertTrue(vm.selectProfile(ProfileEntity(9, "X", 25, 5, 0)))
        assertEquals(9L, g.settingsRepo.activeProfileId.first())
        // IDLE:仅设 active
        g.engine.restore(null)
        assertFalse(vm.selectProfile(ProfileEntity(8, "Y", 25, 5, 0)))
        assertEquals(8L, g.settingsRepo.activeProfileId.first()) // SET_ACTIVE 确实落库
    }
}
