package com.healthy.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthy.app.ui.theme.HealthyColors

/**
 * The settings spec 5.4 and 9.4 ask for.
 *
 * Everything here changes a number the app calculates with, so each field says
 * what it affects. The caffeine note is the spec's own: the model uses a
 * constant half-life and ignores absorption, so it compares the user's days
 * against each other rather than measuring their blood.
 */
@Composable
fun SettingsScreen(
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        item {
            SectionCard {
                Title("Caffeine")
                EditableField(
                    label = "Target bedtime",
                    value = settings.targetBedtime,
                    hint = "HH:mm. The curve marks this, and the verdict is measured here.",
                ) { vm.setBedtime(it) }
                EditableField(
                    label = "Half-life, hours",
                    value = "%.1f".format(settings.halfLifeHours),
                    numeric = true,
                    hint = "Studies put the range at 4 to 6. Genetics change it.",
                ) { it.toDoubleOrNull()?.let(vm::setHalfLife) }
                EditableField(
                    label = "Bedtime limit, mg",
                    value = settings.bedtimeLimitMg.toString(),
                    numeric = true,
                    hint = "Below this the app calls you clear. A rule of thumb, not a fact about you.",
                ) { it.toIntOrNull()?.let(vm::setLimit) }
                Text(
                    "The model uses a constant half-life and ignores absorption time. " +
                        "Treat it as a way to compare your own days, not a measurement of your blood.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        item {
            SectionCard {
                Title("Fluid and alcohol")
                EditableField(
                    label = "Daily fluid target, ml",
                    value = settings.fluidTargetMl.toString(),
                    numeric = true,
                    hint = "The bar fills towards this. Nothing is sent when you are below it.",
                ) { it.toIntOrNull()?.let(vm::setFluidTarget) }
                EditableField(
                    label = "Millilitres of alcohol in one unit",
                    value = "%.1f".format(settings.mlPerAlcoholUnit),
                    numeric = true,
                    hint = "10 in the UK. Near 17.7 for a US standard drink. " +
                        "A unit means different things between countries.",
                ) { it.toDoubleOrNull()?.let(vm::setMlPerAlcoholUnit) }
                Text(
                    "Units are worked out from how much you poured and how strong it " +
                        "was, so a pint and a bottle of the same beer are no longer the " +
                        "same drink. This applies to drinks logged from now on; what you " +
                        "have already logged keeps the units it was recorded with.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Title("Remind me to log the night")
                        Text(
                            "Sent when your watch reports you woke, not at a fixed hour — " +
                                "a fixed alarm arrives during sleep on a day you wake late. " +
                                "No sound, no vibration.",
                            color = HealthyColors.Muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    val notifyPermission = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                    ) { granted -> if (granted) vm.setNotifyEnabled(true) }

                    androidx.compose.material3.Switch(
                        checked = settings.notifyEnabled,
                        onCheckedChange = { on ->
                            if (!on) {
                                vm.setNotifyEnabled(false)
                            } else if (android.os.Build.VERSION.SDK_INT >= 33) {
                                notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                vm.setNotifyEnabled(true)
                            }
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = HealthyColors.Ground,
                            checkedTrackColor = HealthyColors.Sleep,
                            uncheckedThumbColor = HealthyColors.Muted,
                            uncheckedTrackColor = HealthyColors.Raised2,
                        ),
                    )
                }
                if (settings.notifyEnabled) {
                    Text(
                        settings.lastJobRun
                            ?.let {
                                "Last checked " + java.time.Instant.ofEpochMilli(it)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .format(java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm")) +
                                    ". A time many hours old means the system stopped the job."
                            }
                            ?: "Not run yet. The first check happens within half an hour.",
                        color = HealthyColors.Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Row {
                        androidx.compose.material3.TextButton(onClick = { vm.checkNow() }) {
                            Text("Check now", color = HealthyColors.Sleep, fontSize = 13.sp)
                        }
                        androidx.compose.material3.TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings
                                                .ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                        )
                                    )
                                }
                            },
                        ) {
                            Text("Battery settings", color = HealthyColors.Muted, fontSize = 13.sp)
                        }
                    }
                    Text(
                        "A Pixel runs the job reliably without changing anything. " +
                            "That button only opens the system list if you want to exempt " +
                            "this app; it is not required.",
                        color = HealthyColors.Muted,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Title("Track menstrual cycle")
                        Text(
                            "Off by default. Turning it on adds a flow field to the morning " +
                                "screen and the cycle day to the comparison table, and asks " +
                                "Health Connect for menstrual data — only then, and only if " +
                                "you say yes.",
                            color = HealthyColors.Muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = settings.trackCycle,
                        onCheckedChange = { vm.setTrackCycle(it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = HealthyColors.Ground,
                            checkedTrackColor = HealthyColors.Sleep,
                            uncheckedThumbColor = HealthyColors.Muted,
                            uncheckedTrackColor = HealthyColors.Raised2,
                        ),
                    )
                }
                if (settings.trackCycle) {
                    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                        com.healthy.app.health.HealthConnect.requestContract()
                    ) { }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            launcher.launch(
                                com.healthy.app.health.HealthConnect.MENSTRUATION_PERMISSIONS
                            )
                        },
                    ) {
                        Text(
                            "Grant Health Connect menstrual access",
                            color = HealthyColors.Sleep,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        if (onClose != null) {
            item {
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HealthyColors.Raised2,
                        contentColor = HealthyColors.Paper,
                    ),
                ) { Text("Close") }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

/**
 * A field that only commits when the user presses Set.
 * Committing on every keystroke would rewrite the setting from "2" while the
 * user is still typing "2000".
 */
@Composable
private fun EditableField(
    label: String,
    value: String,
    numeric: Boolean = false,
    hint: String? = null,
    onCommit: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    val changed = draft != value

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(label, fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = fieldColours(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            ),
        )
        TextButton(onClick = { onCommit(draft) }, enabled = changed) {
            Text(
                "Set",
                color = if (changed) HealthyColors.Sleep else HealthyColors.Muted,
                fontSize = 13.sp,
            )
        }
    }
    if (hint != null) {
        Text(hint, color = HealthyColors.Muted, fontSize = 11.sp)
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
private fun fieldColours() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = HealthyColors.Paper,
    unfocusedTextColor = HealthyColors.Paper,
    focusedBorderColor = HealthyColors.Sleep,
    unfocusedBorderColor = HealthyColors.Rule,
    focusedLabelColor = HealthyColors.Sleep,
    unfocusedLabelColor = HealthyColors.Muted,
    cursorColor = HealthyColors.Sleep,
)
