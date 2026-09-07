package com.healthy.app.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthy.app.ui.theme.HealthyColors
import kotlin.math.abs

@Composable
fun WeightScreen(
    modifier: Modifier = Modifier,
    vm: WeightViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf<Double?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        item {
            SectionCard {
                Text(
                    if (state.todayLogged) "Today's weight" else "Weigh in",
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    state.latest?.let { "Last reading ${"%.1f".format(it.weightKg)} kg on ${it.date}." }
                        ?: "No readings yet. The wheel starts at 70 kg.",
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )

                WeightWheel(
                    previousKg = state.latest?.weightKg,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { picked = it }

                Button(
                    onClick = {
                        picked?.let { kg ->
                            vm.save(kg) { wrote ->
                                status = if (wrote) {
                                    "Saved ${"%.1f".format(kg)} kg, and written to Health Connect."
                                } else {
                                    "Saved ${"%.1f".format(kg)} kg. Health Connect did not accept the write."
                                }
                            }
                        }
                    },
                    enabled = picked != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HealthyColors.Caffeine,
                        contentColor = HealthyColors.Ground,
                        disabledContainerColor = HealthyColors.Raised2,
                        disabledContentColor = HealthyColors.Muted,
                    ),
                ) {
                    Text(
                        picked?.let { "Save ${"%.1f".format(it)} kg" } ?: "Move the wheel",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (status != null) {
                    Text(
                        status.orEmpty(),
                        color = HealthyColors.Sleep,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item { TrendCard(state) }
        if (state.bodyFatPoints.size >= 2) {
            item { BodyFatCard(state) }
        }
        item { GoalCard(state, vm) }
        item { ImportCard(vm) }
    }
}

/**
 * Body fat, smoothed like the weight (spec 8.4). Shown only when a scale has
 * actually measured it; a dumb scale leaves this card off the screen entirely
 * rather than drawing a flat zero.
 */
@Composable
private fun BodyFatCard(state: WeightState) {
    SectionCard {
        Text("Body fat", color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        val latest = state.bodyFatPoints.last()
        Text(
            "${"%.1f".format(latest.weightKg)} percent measured, " +
                "${"%.1f".format(latest.trendKg)} percent smoothed.",
            color = HealthyColors.Sleep,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Canvas(Modifier.fillMaxWidth().height(90.dp).padding(top = 12.dp)) {
            val points = state.bodyFatPoints
            val w = size.width
            val h = size.height
            val values = points.flatMap { listOf(it.weightKg, it.trendKg) }
            val lo = values.min() - 0.5
            val hi = values.max() + 0.5
            val span = (hi - lo).coerceAtLeast(0.1)
            val step = if (points.size > 1) w / (points.size - 1) else w
            fun y(v: Double) = (h - ((v - lo) / span).toFloat() * (h - 8f) - 4f)

            points.forEachIndexed { i, p ->
                drawCircle(
                    color = HealthyColors.Muted.copy(alpha = 0.5f),
                    radius = 2.5f,
                    center = Offset(i * step, y(p.weightKg)),
                )
            }
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = i * step
                val yy = y(p.trendKg)
                if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            drawPath(path, color = HealthyColors.Caffeine, style = Stroke(width = 3f))
        }
        Text(
            "A bioimpedance scale estimates body composition from electrical " +
                "resistance. Hydration changes the result. The trend across weeks " +
                "is useful. One reading is not.",
            color = HealthyColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * Goal modes (spec 8.5). The default is no goal, and the spec forbids asking
 * for a goal weight anywhere in plain entry — this is the only place it is
 * offered, and only because the user chose to come here.
 */
@Composable
private fun GoalCard(state: WeightState, vm: WeightViewModel) {
    SectionCard {
        Text("Goal", color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                com.healthy.app.data.HealthySettings.GOAL_NONE to "No goal",
                com.healthy.app.data.HealthySettings.GOAL_HOLD to "Hold",
                com.healthy.app.data.HealthySettings.GOAL_CHANGE to "Change",
            ).forEach { (mode, label) ->
                val selected = state.goalMode == mode
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        if (mode == com.healthy.app.data.HealthySettings.GOAL_NONE) vm.setNoGoal()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selected) HealthyColors.Sleep else HealthyColors.Rule,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) HealthyColors.Sleep.copy(alpha = 0.18f) else HealthyColors.Raised2,
                        contentColor = if (selected) HealthyColors.Sleep else HealthyColors.Paper,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }

        when (state.goalMode) {
            com.healthy.app.data.HealthySettings.GOAL_NONE -> {
                Text(
                    "The app records your weight and shows the trend. It sets no target and asks for no goal weight.",
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                GoalSetters(state, vm)
            }

            com.healthy.app.data.HealthySettings.GOAL_HOLD -> {
                Text(
                    "Holding ${"%.1f".format(state.holdTargetKg ?: 0.0)} kg, " +
                        "with a band of plus or minus ${WeightGoalConstants.BAND} kg.",
                    color = HealthyColors.Paper,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                val status = state.holdStatus
                Text(
                    status?.message
                        ?: if (status?.insideBand == true) {
                            "Your trend is inside the band."
                        } else {
                            "Your trend is outside the band, but not yet for a week."
                        },
                    color = if (status?.message != null) HealthyColors.Caffeine else HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "Compared against the trend, never the daily reading. A daily weight leaves any band constantly.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                GoalSetters(state, vm)
            }

            else -> {
                val progress = state.progress
                Text(
                    "Target ${"%+.2f".format(state.rateKgPerWeek ?: 0.0)} kg a week.",
                    color = HealthyColors.Paper,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    progress?.actualRateKgPerWeek
                        ?.let { "Actual ${"%+.2f".format(it)} kg a week across the last 14 days." }
                        ?: "Not enough readings yet to say what you are actually doing.",
                    color = HealthyColors.Sleep,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "These two numbers tell you whether the plan works. No other number is necessary.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                GoalSetters(state, vm)
            }
        }

        if (state.rateRefusal != null) {
            Text(
                state.rateRefusal.orEmpty(),
                color = HealthyColors.Warn,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

private object WeightGoalConstants {
    val BAND = com.healthy.app.analysis.WeightGoal.HOLD_BAND_KG.toInt()
}

@Composable
private fun GoalSetters(state: WeightState, vm: WeightViewModel) {
    var holdText by remember { mutableStateOf(state.holdTargetKg?.let { "%.1f".format(it) } ?: "") }
    var rateText by remember { mutableStateOf(state.rateKgPerWeek?.let { "%.2f".format(it) } ?: "") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = holdText,
            onValueChange = { holdText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Hold at kg", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = goalFieldColors(),
        )
        androidx.compose.material3.TextButton(
            onClick = { holdText.toDoubleOrNull()?.let(vm::setHoldGoal) },
            enabled = holdText.toDoubleOrNull() != null,
        ) {
            Text("Set", color = HealthyColors.Sleep, fontSize = 13.sp)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = rateText,
            onValueChange = { rateText = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
            label = { Text("Change kg a week", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = goalFieldColors(),
        )
        androidx.compose.material3.TextButton(
            onClick = { rateText.toDoubleOrNull()?.let(vm::setChangeGoal) },
            enabled = rateText.toDoubleOrNull() != null,
        ) {
            Text("Set", color = HealthyColors.Sleep, fontSize = 13.sp)
        }
    }

    Text(
        state.maxRateKgPerWeek
            ?.let { "A rate is set, never a date. The most this app will set is ${"%.2f".format(it)} kg a week, which is 1 percent of your weight." }
            ?: "A rate is set, never a date. A missed date makes an app demand a larger deficit every week.",
        color = HealthyColors.Muted,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun goalFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = HealthyColors.Paper,
    unfocusedTextColor = HealthyColors.Paper,
    focusedBorderColor = HealthyColors.Sleep,
    unfocusedBorderColor = HealthyColors.Rule,
    focusedLabelColor = HealthyColors.Sleep,
    unfocusedLabelColor = HealthyColors.Muted,
    cursorColor = HealthyColors.Sleep,
)

@Composable
private fun ImportCard(vm: WeightViewModel) {
    val status by vm.importStatus.collectAsStateWithLifecycle()
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importOpenScale(uri) }

    SectionCard {
        Text("From a scale", color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Health Connect first, then an OpenScale CSV, then the wheel above. " +
                "A date already stored is left alone either way, so both are safe to repeat.",
            color = HealthyColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        androidx.compose.material3.OutlinedButton(
            onClick = { vm.syncFromHealthConnect() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, HealthyColors.Rule),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = HealthyColors.Raised2,
                contentColor = HealthyColors.Paper,
            ),
        ) {
            Text("Read weights from Health Connect", fontSize = 14.sp)
        }
        androidx.compose.material3.OutlinedButton(
            onClick = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, HealthyColors.Rule),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = HealthyColors.Raised2,
                contentColor = HealthyColors.Paper,
            ),
        ) {
            Text("Import OpenScale CSV", fontSize = 14.sp)
        }
        if (status != null) {
            Text(
                status.orEmpty(),
                color = HealthyColors.Sleep,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun TrendCard(state: WeightState) {
    SectionCard {
        Text("Trend", color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

        val change = state.changeOver30Days
        Text(
            when {
                state.points.size < 2 -> "Two readings are needed before a trend means anything."
                change == null -> "—"
                abs(change) < 0.05 -> "Level across the last 30 days."
                change < 0 -> "Down ${"%.1f".format(abs(change))} kg across the last 30 days."
                else -> "Up ${"%.1f".format(change)} kg across the last 30 days."
            },
            color = if (state.points.size < 2) HealthyColors.Muted else HealthyColors.Sleep,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (state.points.size >= 2) {
            Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp)) {
                val points = state.points
                val w = size.width
                val h = size.height
                val values = points.flatMap { listOf(it.weightKg, it.trendKg) }
                val lo = values.min() - 0.3
                val hi = values.max() + 0.3
                val span = (hi - lo).coerceAtLeast(0.1)
                val step = if (points.size > 1) w / (points.size - 1) else w

                fun y(v: Double) = (h - ((v - lo) / span).toFloat() * (h - 8f) - 4f)

                // The daily readings are the quiet element: they are noise
                // from water, food and glycogen (spec 8.2).
                points.forEachIndexed { i, p ->
                    drawCircle(
                        color = HealthyColors.Muted.copy(alpha = 0.55f),
                        radius = 2.5f,
                        center = Offset(i * step, y(p.weightKg)),
                    )
                }
                // The trend is the signal, so it is the strong element.
                val path = Path()
                points.forEachIndexed { i, p ->
                    val x = i * step
                    val yy = y(p.trendKg)
                    if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
                }
                drawPath(path, color = HealthyColors.Sleep, style = Stroke(width = 3f))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(state.points.first().date, color = HealthyColors.Muted, fontSize = 10.sp)
                Text(state.points.last().date, color = HealthyColors.Muted, fontSize = 10.sp)
            }
        }

        Text(
            "Weigh at the same time each day, after you wake and before you eat. " +
                "A different time gives a different number for the same body.",
            color = HealthyColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}
