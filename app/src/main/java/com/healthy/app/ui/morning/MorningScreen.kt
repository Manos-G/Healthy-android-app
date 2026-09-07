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
import com.healthy.app.ui.theme.HealthyColors

@Composable
fun MorningScreen(
    modifier: Modifier = Modifier,
    vm: MorningViewModel = viewModel(),
) {
    val form by vm.form.collectAsStateWithLifecycle()
    val saved by vm.savedDates.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
    ) {
        item { DateCard(form, saved, vm) }
        item { SleepCard(form, vm) }
        item { AlertnessCard(form, vm) }
        item { ContextCard(form, vm) }
        item { SaveCard(form, vm) }
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
            ) { picked -> vm.update { it.copy(sleepStart = picked) } }
            TimeField(
                label = "Woke up",
                value = form.sleepEnd,
                modifier = Modifier.weight(1f),
            ) { picked -> vm.update { it.copy(sleepEnd = picked) } }
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
            NumberField("Wake-ups", form.wakeups, Modifier.weight(1f)) { v ->
                vm.update { it.copy(wakeups = v) }
            }
            NumberField("Resting HR", form.restingHr, Modifier.weight(1f)) { v ->
                vm.update { it.copy(restingHr = v) }
            }
            NumberField("Blood O2 %", form.spo2, Modifier.weight(1f), decimal = true) { v ->
                vm.update { it.copy(spo2 = v) }
            }
        }
        Text(
            "Step 5 fills these from Health Connect. Leave a field empty if you do not know it — empty means not reported, which is not the same as zero.",
            color = HealthyColors.Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp),
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
            "Your 15:00 energy is asked for on the Today screen this afternoon. You cannot know it yet.",
            color = HealthyColors.Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
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
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val allowed = if (decimal) "0123456789." else "0123456789"
            onChange(raw.filter { it in allowed })
        },
        label = { Text(label, fontSize = 12.sp) },
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
    onPicked: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Column(modifier) {
        Text(label, color = HealthyColors.Muted, fontSize = 12.sp)
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
