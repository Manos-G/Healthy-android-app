package com.healthy.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthy.app.core.LogWindow
import com.healthy.app.ui.theme.HealthyColors

/**
 * Which stretch of the log is on screen, and a way to step through it.
 *
 * Without this there was no way to see, correct or delete anything from an
 * earlier day: the list showed the current logical day and nothing else, so a
 * mistake logged yesterday was permanent as far as the app was concerned.
 *
 * The arrow towards now is disabled at the newest position rather than hidden,
 * so the control keeps its shape and the title does not jump about.
 */
@Composable
fun WindowSelector(
    window: LogWindow,
    now: Long,
    modifier: Modifier = Modifier,
    onChange: (LogWindow) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onChange(LogWindow.earlier(window, now)) },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Earlier",
                tint = HealthyColors.Sleep,
            )
        }

        Text(
            LogWindow.label(window, now),
            color = HealthyColors.Paper,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )

        val newer = LogWindow.later(window, now)
        IconButton(
            onClick = { newer?.let(onChange) },
            enabled = newer != null,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Later",
                tint = if (newer != null) HealthyColors.Sleep else HealthyColors.Rule,
            )
        }
    }
}
