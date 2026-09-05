package com.embertimer.ui.home

import android.content.Context
import android.os.Looper
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

    @Test fun dayDetailBreaksDownPerProfile() = runTest {
        val g = AppGraph(ctx, useInMemoryDb = true, storeFileName = "hv_daydetail")
        g.bootstrap()
        // #3 首装空库:无种子行,明细归属完全由测试自建的 id 决定
        val pomoId = g.profileRepo.create("番茄", 25, 5)
        val deepId = g.profileRepo.create("深度", 50, 10)
        val today = java.time.LocalDate.now()
        g.totalsRepo.addWork(today.toString(), pomoId, 30 * 60_000L)
        g.totalsRepo.addWork(today.toString(), deepId, 90 * 60_000L)
        val vm = HomeViewModel(g)
        vm.selectDay(today)
        val d = vm.dayDetail.first { it != null }!!
        assertEquals(2, d.rows.size)
        assertEquals(2 * 3_600_000L, d.totalMillis)
        assertEquals(90 * 60_000L, d.rows[0].millis)           // 按时长降序
        assertEquals("深度", d.rows[0].profileName)
        vm.selectDay(null)
        shadowOf(Looper.getMainLooper()).idle() // Robolectric 主 looper 暂停,Main 上的状态流恢复需手动泵
        assertNull(vm.dayDetail.value)
    }

    @Test fun dayDetailRefreshesWhenTotalsChange() = runTest {
        val g = AppGraph(ctx, useInMemoryDb = true, storeFileName = "hv_daydetail_live")
        g.bootstrap()
        val today = java.time.LocalDate.now()
        val id = g.profileRepo.create("专注", 25, 5)
        g.totalsRepo.addWork(today.toString(), id, 30 * 60_000L)
        val vm = HomeViewModel(g)
        vm.selectDay(today)
        assertEquals(30 * 60_000L, vm.dayDetail.first { it != null }!!.totalMillis)
        // 不重新 selectDay:Room 失效通知驱动 dayTotals 重发,detail 应自动反映新总额。
        // 失效链路在 Room 后台线程间逐跳推进,每跳回暂停的 Main looper 都需手动泵(同上
        // selectDay(null) 后 idle 的既有模式);泵多轮直到状态流换新值。
        g.totalsRepo.addWork(today.toString(), id, 20 * 60_000L)
        val deadline = System.currentTimeMillis() + 10_000
        while (vm.dayDetail.value?.totalMillis != 50 * 60_000L && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertEquals(50 * 60_000L, vm.dayDetail.value?.totalMillis)
    }

    @Test fun dayDetailLabelsDeletedProfile() = runTest {
        // 配置删除后 daily_total 行成孤儿(无 FK,热力图历史保留):
        // 孤儿行应显示固定文案而非 "?",时长与存活配置行均照实保留
        val g = AppGraph(ctx, useInMemoryDb = true, storeFileName = "hv_deleted")
        g.bootstrap()
        // #3 无种子行:存活/孤儿两行均由测试自建后删其一来构造
        val pomoId = g.profileRepo.create("番茄", 25, 5)
        val tempId = g.profileRepo.create("临时", 25, 5)
        val today = java.time.LocalDate.now()
        g.totalsRepo.addWork(today.toString(), pomoId, 30 * 60_000L)
        g.totalsRepo.addWork(today.toString(), tempId, 90 * 60_000L)
        g.profileRepo.delete(g.profileRepo.byId(tempId)!!)
        val vm = HomeViewModel(g)
        vm.selectDay(today)
        val d = vm.dayDetail.first { it != null }!!
        assertEquals(2, d.rows.size)
        assertEquals(120 * 60_000L, d.totalMillis) // 总额含孤儿行
        assertEquals("已删除时钟", d.rows[0].profileName) // 孤儿行 90 分钟,降序居首
        assertEquals(90 * 60_000L, d.rows[0].millis)
        assertEquals("番茄", d.rows[1].profileName) // 存活配置显示真实名称
        assertEquals(30 * 60_000L, d.rows[1].millis)
    }
}
