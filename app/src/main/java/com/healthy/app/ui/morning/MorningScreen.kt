package com.healthy.app.ui.morning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthy.app.ui.components.RatingScale
import com.healthy.app.ui.health.HealthConnectCard
import com.healthy.app.ui.theme.HealthyColors

@Composable
fun MorningScreen(
    openNight: com.healthy.app.NightRequest? = null,
    modifier: Modifier = Modifier,
    vm: MorningViewModel = viewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(openNight) {
        if (openNight != null) vm.selectDate(openNight.date)
    }
    val form by vm.form.collectAsStateWithLifecycle()
    val saved by vm.savedDates.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
    ) {
        item { DateCard(form, saved, vm) }
        item { HealthConnectCard(health, onGranted = vm::refreshHealthConnect) }
        if (health.isGranted) {
            item { SyncCard(form, vm) }
        }
        item { SleepCard(form, vm) }
        if (form.hypnogram != null) {
            item { HypnogramCard(form) }
        }
        item { AlertnessCard(form, vm) }
        if (form.trackCycle) {
            item { CycleCard(form, vm) }
        }
        item { ContextCard(form, vm) }
        item { SaveCard(form, vm) }
        item {
            com.healthy.app.ui.notes.QuickNoteButton(
                date = form.date,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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

@Composable
private fun CardTitle(text: String, subtitle: String? = null) {
    Text(text, color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    if (subtitle != null) {
        Text(
            subtitle,
            color = HealthyColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun DateCard(form: MorningForm, saved: Set<String>, vm: MorningViewModel) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.shiftDate(-1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous night",
                    tint = HealthyColors.Paper,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    form.date,
                    color = HealthyColors.Paper,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        form.date in saved -> "saved"
                        else -> "not logged yet"
                    },
                    color = if (form.date in saved) HealthyColors.Sleep else HealthyColors.Muted,
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = { vm.shiftDate(1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next night",
                    tint = HealthyColors.Paper,
                )
            }
        }
        Text(
            "The date a night is filed under is the day the sleep started.",
            color = HealthyColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * The sync button (spec 3.4). Manual only: a background sync would cost
 * battery for data the user only looks at once a day.
 */
@Composable
private fun SyncCard(form: MorningForm, vm: MorningViewModel) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                CardTitle("Fill from the watch", "Reads the night of ${form.date}.")
            }
            Button(
                onClick = { vm.sync() },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HealthyColors.Sleep,
                    contentColor = HealthyColors.Ground,
                ),
            ) {
                Text("Sync", fontWeight = FontWeight.SemiBold)
            }
        }
        if (form.syncMessage != null) {
            Text(
                form.syncMessage.orEmpty(),
                color = HealthyColors.Paper,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (form.editedFields.isNotEmpty()) {
            Text(
                "Marked with ✎: your value, kept through a sync. Clear the field to hand it back.",
                color = HealthyColors.Caffeine,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SleepCard(form: MorningForm, vm: MorningViewModel) {
    SectionCard {
        CardTitle("Sleep")
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TimeField(
                label = "Fell asleep",
                value = form.sleepStart,
                modifier = Modifier.weight(1f),
                edited = SyncedField.SLEEP_START in form.editedFields,
            ) { picked -> vm.edit(SyncedField.SLEEP_START) { it.copy(sleepStart = picked) } }
            TimeField(
                label = "Woke up",
                value = form.sleepEnd,
                modifier = Modifier.weight(1f),
                edited = SyncedField.SLEEP_END in form.editedFields,
            ) { picked -> vm.edit(SyncedField.SLEEP_END) { it.copy(sleepEnd = picked) } }
        }
        Text(
            form.durationLabel,
            color = HealthyColors.Sleep,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 10.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NumberField(
                "Wake-ups", form.wakeups, Modifier.weight(1f),
                edited = SyncedField.WAKEUPS in form.editedFields,
            ) { v -> vm.edit(SyncedField.WAKEUPS) { it.copy(wakeups = v) } }
            NumberField(
                "Resting HR", form.restingHr, Modifier.weight(1f),
                edited = SyncedField.RESTING_HR in form.editedFields,
            ) { v -> vm.edit(SyncedField.RESTING_HR) { it.copy(restingHr = v) } }
            NumberField(
                "Blood O2 %", form.spo2, Modifier.weight(1f), decimal = true,
                edited = SyncedField.SPO2 in form.editedFields,
            ) { v -> vm.edit(SyncedField.SPO2) { it.copy(spo2 = v) } }
        }
        if (form.stageSummary != null) {
            Text(
                form.stageSummary.orEmpty(),
                color = HealthyColors.Sleep,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Text(
            "Leave a field empty if you do not know it — empty means not reported, which is not the same as zero. A field you type is marked and a later sync will not overwrite it.",
            color = HealthyColors.Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * The hypnogram (spec 18.4), and both cycle lengths side by side (spec 18.5).
 *
 * Two numbers that agree give confidence; two that disagree show the watch is
 * guessing. Both results are useful, so neither is hidden.
 */
@Composable
private fun HypnogramCard(form: MorningForm) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "The shape of the night",
                color = HealthyColors.Paper,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            com.healthy.app.ui.sources.SourceLink(
                item = "Heart rate cycle detection",
                modifier = Modifier.padding(start = 6.dp),
            )
            com.healthy.app.ui.sources.SourceLink(
                item = "Sleep stages",
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            "Measured from heart rate, not from the watch's stages.",
            color = HealthyColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        com.healthy.app.ui.hypnogram.HypnogramChart(
            result = form.hypnogram!!,
            stageBlocks = form.stageBlocks,
            sleepStart = form.sleepStartMillis,
            sleepEnd = form.sleepEndMillis,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Cycle, from the watch", color = HealthyColors.Muted, fontSize = 11.sp)
                Text(
                    form.watchCycleMinutes?.let { "$it min" } ?: "too few blocks",
                    color = HealthyColors.Paper,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Cycle, from heart rate", color = HealthyColors.Muted, fontSize = 11.sp)
                Text(
                    form.heartCycleMinutes?.let { "$it min" } ?: "—",
                    color = HealthyColors.Sleep,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Text(
            "This chart shows heart rate, not brain activity. Only an EEG measures " +
                "sleep stages. It finds the rhythm of the night from a real " +
                "measurement; it does not name the stages.",
            color = HealthyColors.Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun AlertnessCard(form: MorningForm, vm: MorningViewModel) {
    SectionCard {
        CardTitle(
            "How awake are you?",
            "Rate this an hour after waking, not the moment you open your eyes.",
        )
        RatingScale(
            value = form.alertness,
            lowLabel = "foggy",
            highLabel = "sharp",
            modifier = Modifier.padding(top = 12.dp),
        ) { picked -> vm.update { it.copy(alertness = picked) } }
        Text(
            "Your 15:00 energy is asked for on the Fluids screen this afternoon. You cannot know it yet.",
            color = HealthyColors.Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * The flow field (spec 10.3). Present only when the toggle is on; the app
 * never asks about a gender and shows nothing here otherwise.
 */
@Composable
private fun CycleCard(form: MorningForm, vm: MorningViewModel) {
    SectionCard {
        CardTitle(
            "Cycle",
            form.cycleDay?.let { "Day $it." } ?: "No period start recorded yet.",
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("none", "light", "medium", "heavy").forEach { option ->
                val selected = form.flow == option
                androidx.compose.material3.OutlinedButton(
                    onClick = { vm.update { it.copy(flow = if (selected) null else option) } },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selected) HealthyColors.Sleep else HealthyColors.Rule,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) {
                            HealthyColors.Sleep.copy(alpha = 0.18f)
                        } else {
                            HealthyColors.Raised2
                        },
                        contentColor = if (selected) HealthyColors.Sleep else HealthyColors.Paper,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 4.dp,
                        vertical = 8.dp,
                    ),
                ) {
                    Text(option, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ContextCard(form: MorningForm, vm: MorningViewModel) {
    SectionCard {
        CardTitle("That day", "Caffeine you logged on ${form.date}.")
        Text(
            if (form.caffeineCount == 0) {
                "No caffeine logged."
            } else {
                "${form.caffeineMg} mg from ${form.caffeineCount} drink" +
                    if (form.caffeineCount > 1) "s" else ""
            },
            color = HealthyColors.Caffeine,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NumberField("Alcohol, units", form.alcoholUnits, Modifier.weight(1f), decimal = true) { v ->
                vm.update { it.copy(alcoholUnits = v) }
            }
            NumberField("Room temp °C", form.roomTempC, Modifier.weight(1f), decimal = true) { v ->
                vm.update { it.copy(roomTempC = v) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeField("Last meal", form.lastMeal, Modifier.weight(1f)) { picked ->
                vm.update { it.copy(lastMeal = picked) }
            }
            Column(Modifier.weight(1f)) {
                Text("Exercise", color = HealthyColors.Muted, fontSize = 12.sp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    listOf("none", "light", "hard").forEach { option ->
                        val selected = form.exercise == option
                        TextButton(
                            onClick = {
                                vm.update { it.copy(exercise = if (selected) null else option) }
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 8.dp,
                                vertical = 2.dp,
                            ),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (selected) HealthyColors.Sleep else HealthyColors.Muted,
                            ),
                        ) {
                            Text(option, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = form.notes,
            onValueChange = { v -> vm.update { it.copy(notes = v) } },
            label = { Text("Notes") },
            placeholder = { Text("Anything unusual — stress, illness, late screen time") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = fieldColors(),
            minLines = 2,
        )
    }
}

@Composable
private fun SaveCard(form: MorningForm, vm: MorningViewModel) {
    SectionCard {
        Button(
            onClick = { vm.save() },
            enabled = form.canSave,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HealthyColors.Caffeine,
                contentColor = HealthyColors.Ground,
                disabledContainerColor = HealthyColors.Raised2,
                disabledContentColor = HealthyColors.Muted,
            ),
        ) {
            Text(if (form.existing) "Update night" else "Save night", fontWeight = FontWeight.SemiBold)
        }
        if (form.savedAt != null) {
            Text(
                "Saved.",
                color = HealthyColors.Sleep,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (!form.canSave) {
            Text(
                "Add the sleep times or a rating before saving.",
                color = HealthyColors.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// --- fields ---------------------------------------------------------------

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = HealthyColors.Paper,
    unfocusedTextColor = HealthyColors.Paper,
    focusedBorderColor = HealthyColors.Sleep,
    unfocusedBorderColor = HealthyColors.Rule,
    focusedLabelColor = HealthyColors.Sleep,
    unfocusedLabelColor = HealthyColors.Muted,
    cursorColor = HealthyColors.Sleep,
    focusedPlaceholderColor = HealthyColors.Muted,
    unfocusedPlaceholderColor = HealthyColors.Muted,
)

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    edited: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val allowed = if (decimal) "0123456789." else "0123456789"
            onChange(raw.filter { it in allowed })
        },
        label = { Text(if (edited) "$label ✎" else label, fontSize = 12.sp) },
        singleLine = true,
        modifier = modifier,
        colors = fieldColors(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    edited: Boolean = false,
    onPicked: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Column(modifier) {
        Text(
            if (edited) "$label ✎" else label,
            color = if (edited) HealthyColors.Caffeine else HealthyColors.Muted,
            fontSize = 12.sp,
        )
        TextButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, HealthyColors.Rule),
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (value == null) HealthyColors.Muted else HealthyColors.Paper,
            ),
        ) {
            Text(value ?: "--:--", fontSize = 15.sp)
        }
    }

    if (open) {
        val parsed = value?.toLocalTimeOrNull()
        val pickerState = rememberTimePickerState(
            initialHour = parsed?.hour ?: 23,
            initialMinute = parsed?.minute ?: 0,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { open = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = HealthyColors.Paper, fontSize = 15.sp)
                    TimePicker(state = pickerState, modifier = Modifier.padding(top = 12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { open = false }) {
                            Text("Cancel", color = HealthyColors.Muted)
                        }
                        TextButton(onClick = {
                            onPicked(
                                "%02d:%02d".format(pickerState.hour, pickerState.minute)
                            )
                            open = false
                        }) {
                            Text("Set", color = HealthyColors.Sleep)
                        }
                    }
                }
            }
        }
    }
}
