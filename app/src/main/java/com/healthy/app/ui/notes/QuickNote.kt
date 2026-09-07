package com.healthy.app.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthy.app.core.HealthyDay
import com.healthy.app.ui.theme.HealthyColors

/**
 * The note button for the Today and morning screens (spec 15.3): one text
 * field and a save button, dated today unless the user changes it.
 */
@Composable
fun QuickNoteButton(
    date: String = HealthyDay.today(),
    modifier: Modifier = Modifier,
    vm: NotesViewModel = viewModel(),
) {
    var open by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    TextButton(onClick = { open = true }, modifier = modifier) {
        Text(
            if (saved) "Note saved" else "Add a note",
            color = if (saved) HealthyColors.Sleep else HealthyColors.Muted,
            fontSize = 13.sp,
        )
    }

    if (open) {
        var text by remember { mutableStateOf("") }
        var day by remember { mutableStateOf(date) }
        Dialog(onDismissRequest = { open = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Note",
                        color = HealthyColors.Paper,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = day,
                        onValueChange = { day = it },
                        label = { Text("Date", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        colors = noteFieldColors(),
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("teeth pain, sore throat, stress") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        colors = noteFieldColors(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { open = false }) {
                            Text("Cancel", color = HealthyColors.Muted)
                        }
                        TextButton(
                            onClick = {
                                vm.add(text, day)
                                saved = true
                                open = false
                            },
                            enabled = text.isNotBlank(),
                        ) {
                            Text("Save", color = HealthyColors.Sleep)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun noteFieldColors() = OutlinedTextFieldDefaults.colors(
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
