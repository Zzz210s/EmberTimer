package com.embertimer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.embertimer.service.ReportAlarmActions
import com.embertimer.ui.home.HomeScreen
import com.embertimer.ui.report.ReportRange
import com.embertimer.ui.report.ReportScreen
import com.embertimer.ui.settings.ProfilesScreen
import com.embertimer.ui.settings.SettingsScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import com.embertimer.ui.theme.MotionTokens
import com.embertimer.ui.theme.rememberAnimationsEnabled
import com.embertimer.ui.theme.EmberTheme

/** 无导航库:三屏手写状态切换(主页/设置/报表,rememberSaveable 存 Int 序数) */
private enum class Screen { HOME, SETTINGS, REPORT, PROFILES }

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** 报表通知点开直达(v1.1 #5):cold start 经 onCreate 解析,热启动经 onNewIntent */
    private val pendingReportRange = mutableStateOf<ReportRange?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parseReportExtra(intent)
    }

    private fun parseReportExtra(intent: Intent?) {
        when (intent?.getStringExtra(ReportAlarmActions.EXTRA_REPORT_RANGE)) {
            "week" -> pendingReportRange.value = ReportRange.WEEK
            "month" -> pendingReportRange.value = ReportRange.MONTH
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        parseReportExtra(intent)
        setContent {
            EmberTheme {
                // 全屏底色垫底:切换过渡/透明层永不透出窗口白底(真机 edge-to-edge 闪白修复)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                var screenOrdinal by rememberSaveable { mutableStateOf(Screen.HOME.ordinal) }
                // 进程恢复兜底:enum 增删/重排后旧序数可能越界,回退主页
                val screen = Screen.entries.getOrNull(screenOrdinal) ?: Screen.HOME
                // v1.1 顶栏汉堡直达报表:携带预选 tab(周报/月报),与屏序数一并 rememberSaveable
                var reportRangeOrdinal by rememberSaveable { mutableStateOf(ReportRange.WEEK.ordinal) }
                val openReport: (ReportRange) -> Unit = { r ->
                    reportRangeOrdinal = r.ordinal
                    screenOrdinal = Screen.REPORT.ordinal
                }
                // 通知点开直达:每次值变化(冷启解析或 onNewIntent)触发一次跳转后清空
                LaunchedEffect(pendingReportRange.value) {
                    pendingReportRange.value?.let { r ->
                        pendingReportRange.value = null
                        openReport(r)
                    }
                }
                // v1.1 #7:三屏切换交叉过渡(进 160 出 100,轻上移);关闭动画直切。
                // 过渡窗两屏短暂共存,各自 VM 均为 activity 级,无重复副作用。
                val animationsOn = rememberAnimationsEnabled()
                val screenContent: @Composable () -> Unit = {
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            onSettings = { screenOrdinal = Screen.SETTINGS.ordinal },
                            onOpenReport = openReport,
                            onManageProfiles = { screenOrdinal = Screen.PROFILES.ordinal },
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            onBack = { screenOrdinal = Screen.HOME.ordinal },
                        )
                        Screen.PROFILES -> ProfilesScreen(
                            onBack = { screenOrdinal = Screen.HOME.ordinal },
                        )
                        Screen.REPORT -> ReportScreen(
                            onBack = { screenOrdinal = Screen.HOME.ordinal },
                            initialRange = ReportRange.entries.getOrElse(reportRangeOrdinal) { ReportRange.WEEK },
                        )
                    }
                }
                if (animationsOn) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            (fadeIn(tween(MotionTokens.TextSwapEnter.durationMillis)) +
                                slideInVertically(tween(MotionTokens.TextSwapEnter.durationMillis)) { it / 12 })
                                .togetherWith(
                                    fadeOut(tween(MotionTokens.TextSwapExit.durationMillis)) +
                                        slideOutVertically(tween(MotionTokens.TextSwapExit.durationMillis)) { -it / 12 },
                                )
                                .using(SizeTransform(clip = false))
                        },
                        label = "screenSwap",
                    ) { screenContent() }
                } else {
                    screenContent()
                }
                // v1.3 #1:手势/三键返回 = 子屏回主页,主页最小化到后台(不退出)。
                // 系统真正销毁(最近任务上滑/系统回收)才结束进程。
                BackHandler {
                    if (screen == Screen.HOME) moveTaskToBack(true)
                    else screenOrdinal = Screen.HOME.ordinal
                }
                }
            }
        }
    }
}
