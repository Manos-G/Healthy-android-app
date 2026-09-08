package com.healthy.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthy.app.core.HealthyDay
import com.healthy.app.ui.theme.HealthyColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * When it was actually drunk or eaten.
 *
 * Logging always stamped the moment the button was pressed, which is wrong
 * often enough to matter. For caffeine it is wrong in a way that changes the
 * answer: a dose taken two hours ago has already lost a quarter of itself, and
 * stamping it as now pushes the whole curve — and the bedtime figure the app
 * exists to give — too high.
 *
 * Offsets rather than a clock, because a person remembers "about two hours
 * ago" and does not remember 14:37. The resulting time is shown so there is
 * no doubt, including when the choice crosses the 04:00 boundary and lands on
 * the previous logical day.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhenPicker(
    minutesAgo: Int,
    now: Long,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    val at = now - minutesAgo * 60_000L

    Column(modifier) {
        Text("When", color = HealthyColors.Muted, fontSize = 11.sp)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OFFSETS.forEach { (label, minutes) ->
                val selected = minutes == minutesAgo
                OutlinedButton(
                    onClick = { onChange(minutes) },
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selected) HealthyColors.Sleep else HealthyColors.Rule,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) HealthyColors.Raised2 else HealthyColors.Raised,
                        contentColor = if (selected) HealthyColors.Paper else HealthyColors.Muted,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }
        Text(
            describe(at, now),
            color = if (minutesAgo == 0) HealthyColors.Muted else HealthyColors.Sleep,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun describe(at: Long, now: Long): String {
    val clock = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime().format(HHMM)
    if (at >= now - 30_000L) return "Logged at $clock, now."
    // Which logical day it lands on, since the 04:00 boundary makes that a
    // real question for anything backdated in the small hours.
    val sameDay = HealthyDay.dayOf(at) == HealthyDay.dayOf(now)
    return if (sameDay) {
        "Logged at $clock."
    } else {
        "Logged at $clock, which counts against the previous day."
    }
}

/** What a person actually remembers, rather than a clock they do not. */
private val OFFSETS: List<Pair<String, Int>> = listOf(
    "Now" to 0,
    "15 min" to 15,
    "30 min" to 30,
    "1 h" to 60,
    "2 h" to 120,
    "3 h" to 180,
    "4 h" to 240,
    "6 h" to 360,
    "8 h" to 480,
)
