package com.embertimer.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.embertimer.R
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.PathIcon

/** v1.9.8 相位行:状态图标 + 文本。空闲=月牙,工作中=火焰,休息中=咖啡(IconPaths 单源)。 */
@Composable
internal fun PhaseRow(text: String, phaseRes: Int) {
    val d = when (phaseRes) {
        R.string.state_work -> IconPaths.PHASE_WORK
        R.string.state_rest -> IconPaths.PHASE_REST
        else -> IconPaths.PHASE_IDLE
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PathIcon(
            d = d,
            size = 18.dp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
