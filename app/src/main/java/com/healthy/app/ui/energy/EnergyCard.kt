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
            Text(
                "Energy",
                color = HealthyColors.Paper,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )

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
                        if (plan.rateKgPerWeek != 0.0) {
                            append(" Your goal of ${"%+.2f".format(plan.rateKgPerWeek)} kg a week ")
                            append("makes the daily target ${plan.targetKcal}.")
                        }
                        plan.weeksToTarget?.let { weeks ->
                            append(" At that rate the goal weight is about $weeks week")
                            if (weeks != 1) append("s")
                            append(" away.")
                        }
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
    var height by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }

    Column(Modifier.padding(top = 12.dp)) {
        Text(
            "Only used for the starting estimate and to stop the target dropping " +
                "below your basal rate.",
            color = HealthyColors.Muted,
            fontSize = 11.sp,
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Height cm", height, Modifier.weight(1f)) { height = it }
            Field("Age", age, Modifier.weight(1f)) { age = it }
            Field("Goal kg", target, Modifier.weight(1f)) { target = it }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.setBody(null, null, sexMale = true) }) {
                Text("Male", color = HealthyColors.Sleep, fontSize = 12.sp)
            }
            TextButton(onClick = { vm.setBody(null, null, sexMale = false) }) {
                Text("Female", color = HealthyColors.Sleep, fontSize = 12.sp)
            }
            TextButton(
                onClick = {
                    vm.setBody(height.toDoubleOrNull(), age.toIntOrNull(), null)
                    target.toDoubleOrNull()?.let(vm::setGoalTarget)
                },
            ) {
                Text("Save", color = HealthyColors.Sleep, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }) },
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
