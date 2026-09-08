package com.healthy.app.ui.energy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthy.app.analysis.Energy
import com.healthy.app.ui.theme.HealthyColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The energy target (spec 16).
 *
 * The number is measured, not calculated: mean intake plus whatever the weight
 * trend says the body supplied or stored. A formula from height, weight, age
 * and sex is often 300 kcal wrong, so it is only ever a start value, and it
 * says so until the measured window replaces it.
 *
 * Nothing here is coloured red and nothing is sent when the user goes over.
 */
@Composable
fun EnergyCard(
    modifier: Modifier = Modifier,
    vm: EnergyViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showBody by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Energy",
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                com.healthy.app.ui.sources.SourceLink(
                    item = "Measured energy need",
                    modifier = Modifier.padding(start = 6.dp),
                )
                com.healthy.app.ui.sources.SourceLink(
                    item = "Energy in 1 kg of tissue",
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            val plan = state.plan
            if (plan == null) {
                Text(
                    "No target yet. Add your height, age and sex, log a weight, then " +
                        "calculate — the app will estimate to begin with and measure " +
                        "from your own food and weight once it can.",
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${state.consumedTodayKcal}",
                            color = HealthyColors.Paper,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            " / ${plan.targetKcal} kcal",
                            color = HealthyColors.Muted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    state.percentOfTarget?.let { pct ->
                        Text(
                            "$pct%",
                            color = HealthyColors.Caffeine,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = {
                        if (plan.targetKcal > 0) {
                            (state.consumedTodayKcal.toFloat() / plan.targetKcal).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp),
                    // Never red above the target (spec 16.5).
                    color = HealthyColors.Sleep,
                    trackColor = HealthyColors.Raised2,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )

                // The two numbers side by side, because "what I need" and
                // "what I am aiming at" are different questions and the
                // difference between them is the whole plan.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Figure("Need to hold", "${plan.maintenance.kcal}", HealthyColors.Paper)
                    Figure("Target today", "${plan.targetKcal}", HealthyColors.Sleep)
                    Figure(
                        "Difference",
                        "%+d".format(plan.targetKcal - plan.maintenance.kcal),
                        if (plan.targetKcal == plan.maintenance.kcal) {
                            HealthyColors.Muted
                        } else {
                            HealthyColors.Caffeine
                        },
                    )
                    Figure(
                        "Left today",
                        "${plan.targetKcal - state.consumedTodayKcal}",
                        HealthyColors.Paper,
                    )
                }

                GoalLine(state, plan)

                Text(
                    buildString {
                        append("Holding your weight takes about ${plan.maintenance.kcal} kcal")
                        append(
                            if (plan.maintenance.source == Energy.Source.Measured) {
                                ", measured from ${plan.maintenance.days} days of your own data."
                            } else {
                                ", estimated from a formula. It is often 300 kcal out."
                            }
                        )
                    },
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )

                if (plan.clampedToBasal) {
                    Text(
                        "That rate would put the target below your basal rate of " +
                            "${plan.basalKcal} kcal, which is what the body spends at rest. " +
                            "The target is held there instead. Choose a slower rate.",
                        color = HealthyColors.Warn,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Text(
                if (state.canMeasure) {
                    "Ready to measure from ${state.loggedDays} logged days."
                } else {
                    "${state.loggedDays} of ${state.neededDays} fully logged days. " +
                        "Below that the app can only estimate."
                },
                color = HealthyColors.Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 10.dp),
            )

            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                OutlinedButton(
                    onClick = vm::recalculate,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HealthyColors.Rule),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = HealthyColors.Raised2,
                        contentColor = HealthyColors.Paper,
                    ),
                ) {
                    Text("Recalculate energy need", fontSize = 13.sp)
                }
                TextButton(onClick = { showBody = !showBody }) {
                    Text(if (showBody) "Hide" else "About you", color = HealthyColors.Sleep, fontSize = 12.sp)
                }
            }

            state.calculatedAt?.let { at ->
                Text(
                    "Last calculated " + Instant.ofEpochMilli(at)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("d MMM HH:mm")) + ".",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            state.message?.let { message ->
                Text(
                    message,
                    color = HealthyColors.Sleep,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (showBody || state.missingBody) {
                BodyFields(vm)
            }
        }
    }
}

/**
 * Height, age and sex. Used only for the starting estimate and for the basal
 * floor that stops a target going below what the body spends at rest — once
 * the measured window fills, they stop affecting the number.
 */
@Composable
private fun BodyFields(vm: EnergyViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Keyed on what was saved, so the boxes show the stored figures instead of
    // sitting empty and looking as though nothing was kept.
    var height by remember(state.heightCm) { mutableStateOf(state.heightCm?.let { fmt(it) } ?: "") }
    var age by remember(state.ageYears) { mutableStateOf(state.ageYears?.toString() ?: "") }
    var target by remember(state.goalTargetKg) {
        mutableStateOf(state.goalTargetKg?.let { fmt(it) } ?: "")
    }
    var rate by remember(state.goalRateKgPerWeek) {
        mutableStateOf(state.goalRateKgPerWeek?.let { "%.2f".format(it) } ?: "")
    }

    Column(Modifier.padding(top = 12.dp)) {
        Text(
            "Height, age and sex are only used for the starting estimate and to stop " +
                "the target dropping below your basal rate. The goal weight and rate " +
                "are the same ones as on the Weight tab.",
            color = HealthyColors.Muted,
            fontSize = 11.sp,
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Height cm", height, Modifier.weight(1f)) { height = it }
            Field("Age", age, Modifier.weight(1f)) { age = it }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Goal kg", target, Modifier.weight(1f)) { target = it }
            Field("kg per week", rate, Modifier.weight(1f), signed = true) { rate = it }
        }
        Text(
            "A negative rate loses weight: -0.5 means half a kilo a week off, which " +
                "is about 550 kcal a day below what you need.",
            color = HealthyColors.Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SexButton("Male", state.sexMale == true) { vm.setBody(null, null, sexMale = true) }
            SexButton("Female", state.sexMale == false) { vm.setBody(null, null, sexMale = false) }
            TextButton(
                onClick = {
                    vm.setBody(height.toDoubleOrNull(), age.toIntOrNull(), null)
                    vm.setGoal(rate.toDoubleOrNull(), target.toDoubleOrNull())
                },
            ) {
                Text("Save", color = HealthyColors.Sleep, fontSize = 12.sp)
            }
        }
    }
}

/** One labelled number in the need/target row. */
@Composable
private fun Figure(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, color = HealthyColors.Muted, fontSize = 10.sp)
        Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Where the weight goal stands, in one line. */
@Composable
private fun GoalLine(state: EnergyState, plan: Energy.Plan) {
    val target = state.goalTargetKg
    val now = state.bodyWeightKg
    Text(
        buildString {
            if (target == null) {
                append("No goal weight set. Add one below and the app will say how long it takes.")
            } else {
                append("Goal ${fmt(target)} kg")
                if (now != null) {
                    val gap = target - now
                    append(", now ${fmt(now)} kg — ${fmt(kotlin.math.abs(gap))} kg ")
                    append(if (gap < 0) "to lose." else if (gap > 0) "to gain." else "there.")
                }
                if (plan.rateKgPerWeek == 0.0) {
                    append(" No rate set, so the target is simply what holds your weight.")
                } else {
                    append(" At ${"%+.2f".format(plan.rateKgPerWeek)} kg a week")
                    plan.weeksToTarget?.let { weeks ->
                        append(", about $weeks week")
                        if (weeks != 1) append("s")
                        append(" away")
                    }
                    append(".")
                }
            }
        },
        color = HealthyColors.Paper,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun SexButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            if (selected) "$label ✓" else label,
            color = if (selected) HealthyColors.Paper else HealthyColors.Sleep,
            fontSize = 12.sp,
        )
    }
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier,
    signed: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        // A losing rate is negative, so the minus has to survive the filter.
        onValueChange = { v ->
            onChange(v.filter { it.isDigit() || it == '.' || (signed && it == '-') })
        },
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = HealthyColors.Paper,
            unfocusedTextColor = HealthyColors.Paper,
            focusedBorderColor = HealthyColors.Sleep,
            unfocusedBorderColor = HealthyColors.Rule,
            focusedLabelColor = HealthyColors.Sleep,
            unfocusedLabelColor = HealthyColors.Muted,
            cursorColor = HealthyColors.Sleep,
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
        ),
    )
}
