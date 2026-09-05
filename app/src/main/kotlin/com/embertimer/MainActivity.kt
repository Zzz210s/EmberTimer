package com.embertimer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.embertimer.ui.home.HomeScreen
import com.embertimer.ui.report.ReportScreen
import com.embertimer.ui.settings.SettingsScreen
import com.embertimer.ui.theme.EmberTheme

/** 无导航库:三屏手写状态切换(主页/设置/报表,rememberSaveable 存 Int 序数) */
private enum class Screen { HOME, SETTINGS, REPORT }

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            EmberTheme {
                var screenOrdinal by rememberSaveable { mutableStateOf(Screen.HOME.ordinal) }
                // 进程恢复兜底:enum 增删/重排后旧序数可能越界,回退主页
                val screen = Screen.entries.getOrNull(screenOrdinal) ?: Screen.HOME
                when (screen) {
                    Screen.HOME -> HomeScreen(onSettings = { screenOrdinal = Screen.SETTINGS.ordinal })
                    Screen.SETTINGS -> SettingsScreen(
                        onBack = { screenOrdinal = Screen.HOME.ordinal },
                        onOpenReport = { screenOrdinal = Screen.REPORT.ordinal },
                    )
                    Screen.REPORT -> ReportScreen(onBack = { screenOrdinal = Screen.SETTINGS.ordinal })
                }
            }
        }
    }
}
