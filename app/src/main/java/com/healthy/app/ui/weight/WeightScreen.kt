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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import com.healthy.app.data.entity.Weight
import androidx.compose.material3.HorizontalDivider
import com.healthy.app.data.HealthySettings
import com.healthy.app.ui.theme.HealthyColors
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun WeightScreen(
    modifier: Modifier = Modifier,
    vm: WeightViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Cleared when the latest reading changes, so a value picked against the
    // empty-state range cannot survive into the real one.
    var picked by remember(state.latest?.date) { mutableStateOf<Double?>(null) }
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
        item { HistoryCard(state, vm) }
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
/**
 * Goal modes (spec 8.5). The default is no goal, and the goal weight is asked
 * for only here, because spec 8.1 forbids it anywhere in ordinary entry.
 */
@Composable
private fun GoalCard(state: WeightState, vm: WeightViewModel) {
    // Which mode the user is looking at. It follows the stored mode, but a tap
    // moves it immediately so the card responds even before a value exists —
    // the three buttons used to be inert for Hold and Change, which made them
    // look broken.
    var viewing by remember(state.goalMode) { mutableStateOf(state.goalMode) }

    var holdText by remember(state.holdTargetKg) {
        mutableStateOf(state.holdTargetKg?.let { "%.1f".format(it) } ?: "")
    }
    var rateText by remember(state.rateKgPerWeek) {
        mutableStateOf(state.rateKgPerWeek?.let { "%.2f".format(it) } ?: "")
    }
    var goalText by remember(state.goalTargetKg) {
        mutableStateOf(state.goalTargetKg?.let { "%.1f".format(it) } ?: "")
    }

    // Said once per milestone. The acknowledgement is stored, so it does not
    // reappear on every visit, and a new goal clears it.
    state.celebrate?.let { milestone ->
        CelebrationDialog(
            percent = milestone,
            targetKg = state.goalTargetKg,
            onDismiss = { vm.acknowledgeCelebration(milestone) },
        )
    }

    SectionCard {
        Text("Goal", color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                HealthySettings.GOAL_NONE to "No goal",
                HealthySettings.GOAL_HOLD to "Hold",
                HealthySettings.GOAL_CHANGE to "Change",
            ).forEach { (mode, label) ->
                val selected = viewing == mode
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        viewing = mode
                        when (mode) {
                            HealthySettings.GOAL_NONE -> vm.setNoGoal()
                            // Re-selecting a mode that already has a value
                            // turns it straight back on; otherwise the field
                            // below is waiting.
                            HealthySettings.GOAL_HOLD ->
                                state.holdTargetKg?.let(vm::setHoldGoal)
                            else ->
                                state.rateKgPerWeek?.let(vm::setChangeGoal)
                        }
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

        when (viewing) {
            HealthySettings.GOAL_NONE -> Text(
                "The app records your weight and shows the trend. It sets no target " +
                    "and asks for no goal weight.",
                color = HealthyColors.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp),
            )

            HealthySettings.GOAL_HOLD -> {
                if (state.goalMode == HealthySettings.GOAL_HOLD) {
                    val status = state.holdStatus
                    Text(
                        "Holding ${"%.1f".format(state.holdTargetKg ?: 0.0)} kg, " +
                            "with a band of plus or minus " +
                            "${com.healthy.app.analysis.WeightGoal.HOLD_BAND_KG.toInt()} kg.",
                        color = HealthyColors.Paper,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        status?.message
                            ?: if (status?.insideBand == true) "Your trend is inside the band."
                            else "Your trend is outside the band, but not yet for a week.",
                        color = if (status?.message != null) HealthyColors.Caffeine else HealthyColors.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    Text(
                        "Set a weight to hold. The app compares your trend against a band " +
                            "around it, never the daily reading.",
                        color = HealthyColors.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                GoalField(
                    label = "Hold at kg",
                    value = holdText,
                    onValueChange = { holdText = it },
                    onSet = { holdText.toDoubleOrNull()?.let(vm::setHoldGoal) },
                )
                Text(
                    "Compared against the trend, never the daily reading. A daily weight " +
                        "leaves any band constantly.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            else -> {
                if (state.goalMode == HealthySettings.GOAL_CHANGE) {
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
                } else {
                    Text(
                        "Set a rate, not a date. A missed date makes an app demand a larger " +
                            "deficit every week; a rate stays honest. Use a minus for losing.",
                        color = HealthyColors.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                GoalField(
                    label = "Change kg a week",
                    value = rateText,
                    allowMinus = true,
                    onValueChange = { rateText = it },
                    onSet = { rateText.toDoubleOrNull()?.let(vm::setChangeGoal) },
                )
                // The goal weight is set here as well as on the Food tab; both
                // write the same setting, so the two screens cannot disagree.
                GoalField(
                    label = "Goal weight kg",
                    value = goalText,
                    onValueChange = { goalText = it },
                    onSet = { goalText.toDoubleOrNull()?.let(vm::setGoalWeight) },
                )
                state.dailyTargetKcal?.let { kcal ->
                    Text(
                        "That makes the daily energy target $kcal kcal, which is the same " +
                            "number the Food tab shows.",
                        color = HealthyColors.Sleep,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    state.maxRateKgPerWeek
                        ?.let { "The most this app will set is ${"%.2f".format(it)} kg a week, which is 1 percent of your weight." }
                        ?: "Log a weight first, so the app knows what 1 percent of it is.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
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

@Composable
private fun GoalField(
    label: String,
    value: String,
    allowMinus: Boolean = false,
    onValueChange: (String) -> Unit,
    onSet: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = { raw ->
                onValueChange(raw.filter { it.isDigit() || it == '.' || (allowMinus && it == '-') })
            },
            label = { Text(label, fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = goalFieldColors(),
        )
        androidx.compose.material3.TextButton(
            onClick = onSet,
            enabled = value.toDoubleOrNull() != null,
        ) {
            Text(
                "Set",
                color = if (value.toDoubleOrNull() != null) HealthyColors.Sleep else HealthyColors.Muted,
                fontSize = 13.sp,
            )
        }
    }
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

    SectionCard {
        Text("From a scale", color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Health Connect first, then the wheel above. A date already stored is " +
                "left alone, so this is safe to repeat.",
            color = HealthyColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        androidx.compose.material3.OutlinedButton(
            onClick = { vm.syncFromHealthConnect() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, HealthyColors.Rule),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = HealthyColors.Raised2,
                contentColor = HealthyColors.Paper,
            ),
        ) {
            Text("Read weights from Health Connect", fontSize = 14.sp)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trend", color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            com.healthy.app.ui.sources.SourceLink(
                item = "Weight smoothing factor",
                modifier = Modifier.padding(start = 6.dp),
            )
        }

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

        state.holdTargetKg?.takeIf { state.goalMode == HealthySettings.GOAL_HOLD }?.let { target ->
            Text(
                "Holding ${"%.1f".format(target)} kg.",
                color = HealthyColors.Sleep,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        state.goalProgressPercent?.let { percent ->
            Text(
                "$percent% of the way there",
                color = HealthyColors.Paper,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            androidx.compose.material3.LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp),
                color = HealthyColors.Sleep,
                trackColor = HealthyColors.Raised2,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            // Measured on the smoothed trend, not this morning's reading: a
            // day of salt moves the scale a kilogram, and a congratulation
            // that arrives on water and leaves again is worse than none.
            Text(
                "Measured on the trend line, so it does not jump with a salty day.",
                color = HealthyColors.Muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        state.goalTargetKg?.takeIf { state.goalMode == HealthySettings.GOAL_CHANGE }?.let { target ->
            val current = state.points.lastOrNull()?.trendKg
            Text(
                buildString {
                    append("Heading for ${"%.1f".format(target)} kg")
                    current?.let { append(", ${"%.1f".format(kotlin.math.abs(it - target))} kg to go") }
                    state.progress?.actualRateKgPerWeek?.let {
                        append(". Actual ${"%+.2f".format(it)} kg a week")
                    }
                    append(".")
                },
                color = HealthyColors.Sleep,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Only the recent stretch. A year of readings compressed into a phone
        // width hides the last fortnight, which is the part being acted on.
        val shown = remember(state.points) { state.points.takeLast(CHART_DAYS) }

        if (shown.size >= 2) {
            /*
             * Scaled to the readings, not to the goal.
             *
             * Including the goal weight in the range meant a target ten kilos
             * away stretched the axis over ten kilos, and the actual readings
             * — which move by tenths — collapsed into a flat line at the top.
             * The chart is for reading the trend, so the trend decides the
             * scale and the goal line appears only when it happens to fall
             * inside it.
             */
            val firstDay = remember(shown) { LocalDate.parse(shown.first().date).toEpochDay() }
            val lastDay = remember(shown) { LocalDate.parse(shown.last().date).toEpochDay() }
            val projectedDays = (state.projection.size - 1).coerceAtLeast(0)
            val spanDays = ((lastDay - firstDay) + projectedDays).coerceAtLeast(1L).toFloat()

            val readings = shown.flatMap { listOf(it.weightKg, it.trendKg) } + state.projection
            val lo = readings.min() - 0.2
            val hi = readings.max() + 0.2

            Canvas(Modifier.fillMaxWidth().height(160.dp).padding(top = 12.dp)) {
                val w = size.width
                val h = size.height
                val span = (hi - lo).coerceAtLeast(0.4)
                val top = 10f
                val bottom = h - 10f

                fun y(v: Double) = bottom - ((v - lo) / span).toFloat() * (bottom - top)
                fun x(dayOffset: Long) = (dayOffset / spanDays) * w

                var gridKg = kotlin.math.ceil(lo * 2) / 2
                while (gridKg <= hi) {
                    drawLine(
                        color = HealthyColors.Rule.copy(alpha = 0.4f),
                        start = Offset(0f, y(gridKg)),
                        end = Offset(w, y(gridKg)),
                        strokeWidth = 1f,
                    )
                    gridKg += 0.5
                }

                state.goalTargetKg?.takeIf { it in lo..hi }?.let { goal ->
                    drawLine(
                        color = HealthyColors.Caffeine.copy(alpha = 0.7f),
                        start = Offset(0f, y(goal)),
                        end = Offset(w, y(goal)),
                        strokeWidth = 1.5f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect
                            .dashPathEffect(floatArrayOf(3f, 5f)),
                    )
                }

                // The daily readings are the quiet element: they are noise
                // from water, food and glycogen (spec 8.2).
                shown.forEach { p ->
                    drawCircle(
                        color = HealthyColors.Muted.copy(alpha = 0.55f),
                        radius = 2.5f,
                        center = Offset(x(LocalDate.parse(p.date).toEpochDay() - firstDay), y(p.weightKg)),
                    )
                }

                // The trend is the signal, so it is the strong element.
                val path = Path()
                shown.forEachIndexed { i, p ->
                    val px = x(LocalDate.parse(p.date).toEpochDay() - firstDay)
                    val py = y(p.trendKg)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, color = HealthyColors.Sleep, style = Stroke(width = 3f))

                // Spec 8.6: the plan, starting exactly where the trend stops.
                if (state.projection.size >= 2) {
                    val projPath = Path()
                    state.projection.forEachIndexed { i, value ->
                        val px = x(lastDay - firstDay + i)
                        val py = y(value)
                        if (i == 0) projPath.moveTo(px, py) else projPath.lineTo(px, py)
                    }
                    drawPath(
                        projPath,
                        color = HealthyColors.Caffeine,
                        style = Stroke(
                            width = 2f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect
                                .dashPathEffect(floatArrayOf(6f, 6f)),
                        ),
                    )
                }
            }

            // Dates below, weights beside. They were both in a row under the
            // chart, so a pair of kilogram figures sat where the axis labels
            // belong and read as the x axis run backwards.
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(shortDate(shown.first().date), color = HealthyColors.Muted, fontSize = 10.sp)
                Text(
                    "%.1f – %.1f kg".format(lo, hi),
                    color = HealthyColors.Rule,
                    fontSize = 10.sp,
                )
                Text(
                    if (state.projection.size >= 2) {
                        shortDate(LocalDate.ofEpochDay(lastDay + projectedDays).toString())
                    } else {
                        shortDate(shown.last().date)
                    },
                    color = if (state.projection.size >= 2) {
                        HealthyColors.Caffeine
                    } else {
                        HealthyColors.Muted
                    },
                    fontSize = 10.sp,
                )
            }

            if (state.projection.size >= 2) {
                Text(
                    "Dashed line is where the current rate leads, reaching " +
                        "${"%.1f".format(state.projection.last())} kg on " +
                        longDate(LocalDate.ofEpochDay(lastDay + projectedDays).toString()) + ".",
                    color = HealthyColors.Caffeine,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (state.points.size > CHART_DAYS) {
                Text(
                    "Last $CHART_DAYS days. The whole series is in the history below.",
                    color = HealthyColors.Muted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
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

/**
 * Every reading, newest first, and a way to correct one.
 *
 * The chart showed the shape of the series and nothing else, so a reading
 * entered as 87 instead of 78 could be seen as a spike and not reached. A
 * tap opens it.
 */
@Composable
private fun HistoryCard(state: WeightState, vm: WeightViewModel) {
    var editing by remember { mutableStateOf<Weight?>(null) }

    SectionCard {
        Text(
            "History",
            color = HealthyColors.Paper,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (state.history.isEmpty()) {
            Text(
                "Nothing logged yet.",
                color = HealthyColors.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            return@SectionCard
        }
        Text(
            "Tap a reading to correct or remove it.",
            color = HealthyColors.Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )

        // The change against the reading before it, which is what a person
        // actually scans a list of weights for.
        state.history.take(HISTORY_SHOWN).forEachIndexed { index, weight ->
            val previous = state.history.getOrNull(index + 1)
            val delta = previous?.let { weight.weightKg - it.weightKg }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editing = weight }
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    weight.date,
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.width(86.dp),
                )
                Text(
                    "%.1f kg".format(weight.weightKg),
                    color = HealthyColors.Paper,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                if (delta != null && abs(delta) >= 0.05) {
                    Text(
                        "%+.1f".format(delta),
                        color = HealthyColors.Muted,
                        fontSize = 12.sp,
                    )
                }
                if (weight.source != Weight.MANUAL) {
                    Text(
                        " synced",
                        color = HealthyColors.Rule,
                        fontSize = 10.sp,
                    )
                }
            }
            HorizontalDivider(color = HealthyColors.Rule)
        }
        if (state.history.size > HISTORY_SHOWN) {
            Text(
                "${state.history.size - HISTORY_SHOWN} older readings not shown. " +
                    "The full series is in the CSV export.",
                color = HealthyColors.Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    editing?.let { weight ->
        EditWeightDialog(
            weight = weight,
            onSave = { kg ->
                vm.editWeight(weight, kg)
                editing = null
            },
            onDelete = {
                vm.deleteWeight(weight)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun EditWeightDialog(
    weight: Weight,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(weight) { mutableStateOf("%.1f".format(weight.weightKg)) }
    val value = text.toDoubleOrNull()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    weight.date,
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (weight.source == Weight.MANUAL) {
                        "Entered by hand."
                    } else {
                        "Came from ${weight.source}. Correcting it here makes it yours, " +
                            "and the next sync will leave it alone."
                    },
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("Kilograms", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = goalFieldColors(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.TextButton(onClick = onDelete) {
                        Text("Delete", color = HealthyColors.Warn, fontSize = 13.sp)
                    }
                    Row {
                        androidx.compose.material3.TextButton(onClick = onDismiss) {
                            Text("Cancel", color = HealthyColors.Muted, fontSize = 13.sp)
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { value?.let(onSave) },
                            enabled = value != null && value > 0,
                        ) {
                            Text("Save", color = HealthyColors.Sleep, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A word at each tenth of the way.
 *
 * Deliberately a dialog that has to be dismissed rather than a banner that
 * lingers: it is said once, and then the screen goes back to being a set of
 * numbers. Nothing here is a streak, and missing a milestone costs nothing.
 */
@Composable
private fun CelebrationDialog(percent: Int, targetKg: Double?, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(
                Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (percent >= 100) "There." else "$percent%",
                    color = HealthyColors.Sleep,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when {
                        percent >= 100 && targetKg != null ->
                            "You reached ${"%.1f".format(targetKg)} kg. The trend line got " +
                                "there, which is the one that counts."
                        percent >= 100 -> "You reached your goal weight."
                        percent >= 50 -> "Past halfway. The trend is doing what you asked of it."
                        else -> "$percent% of the way to your goal weight."
                    },
                    color = HealthyColors.Paper,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Text("Good", color = HealthyColors.Sleep)
                }
            }
        }
    }
}

/** How much of the series the chart shows. Beyond a month it stops being readable. */
private const val CHART_DAYS = 30

private val SHORT_DATE = java.time.format.DateTimeFormatter.ofPattern("d MMM")
private val LONG_DATE = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")

private fun shortDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(SHORT_DATE) }.getOrDefault(iso)

private fun longDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(LONG_DATE) }.getOrDefault(iso)

private const val HISTORY_SHOWN = 30

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
