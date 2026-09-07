package com.healthy.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthy.app.data.entity.Note
import com.healthy.app.ui.theme.HealthyColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * The notes screen (spec 15.4): a month calendar with a dot on each day that
 * has a note, a search that runs as you type, and a carousel of the matches.
 *
 * Notes matter more than they look: pain and illness change sleep more than
 * caffeine does, so a note is often the explanation for a bad night that no
 * sensor recorded.
 */
@Composable
fun NotesScreen(
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    vm: NotesViewModel = viewModel(),
) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val selected by vm.selectedDate.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val days by vm.daysWithNotes.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<Note?>(null) }
    var composing by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        item { CalendarCard(month, days, selected, vm) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                label = { Text("Search notes", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        query.isNotBlank() -> "${notes.size} matching"
                        selected != null -> "Notes on $selected"
                        else -> "${notes.size} note${if (notes.size == 1) "" else "s"}"
                    },
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                )
                TextButton(onClick = { composing = true }) {
                    Text("Add a note", color = HealthyColors.Sleep, fontSize = 13.sp)
                }
            }
        }
        item {
            if (notes.isEmpty()) {
                Text(
                    if (query.isNotBlank() || selected != null) {
                        "Nothing here."
                    } else {
                        "No notes yet. A line about how you feel explains a bad night " +
                            "better than any number here."
                    },
                    color = HealthyColors.Muted,
                    fontSize = 13.sp,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(notes.size) { index ->
                        NoteCard(notes[index]) { editing = notes[index] }
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

    if (composing) {
        NoteEditor(
            initial = null,
            date = selected ?: com.healthy.app.core.HealthyDay.today(),
            onSave = { text, date -> vm.add(text, date); composing = false },
            onDismiss = { composing = false },
        )
    }

    editing?.let { note ->
        NoteEditor(
            initial = note,
            date = note.date,
            onSave = { text, _ -> vm.update(note, text); editing = null },
            onDelete = { vm.delete(note); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CalendarCard(
    month: YearMonth,
    daysWithNotes: Set<String>,
    selected: String?,
    vm: NotesViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.showMonth(month.minusMonths(1)) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = HealthyColors.Paper,
                    )
                }
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { vm.showMonth(month.plusMonths(1)) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = HealthyColors.Paper,
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                    Text(
                        label,
                        color = HealthyColors.Muted,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            // Monday-first grid, with the leading blanks the month needs.
            val first = month.atDay(1)
            val lead = (first.dayOfWeek.value - 1)
            val cells = lead + month.lengthOfMonth()
            val rows = (cells + 6) / 7

            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    repeat(7) { column ->
                        val index = row * 7 + column
                        val dayNumber = index - lead + 1
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (dayNumber in 1..month.lengthOfMonth()) {
                                val date: LocalDate = month.atDay(dayNumber)
                                DayCell(
                                    day = dayNumber,
                                    hasNote = date.toString() in daysWithNotes,
                                    isSelected = date.toString() == selected,
                                    onClick = { vm.selectDate(date.toString()) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, hasNote: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .width(32.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(
                    if (isSelected) HealthyColors.Sleep.copy(alpha = 0.25f) else androidx.compose.ui.graphics.Color.Transparent,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$day",
                color = if (isSelected) HealthyColors.Sleep else HealthyColors.Paper,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .size(4.dp)
                .background(
                    if (hasNote) HealthyColors.Caffeine else androidx.compose.ui.graphics.Color.Transparent,
                    CircleShape,
                )
        )
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(220.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(note.date, color = HealthyColors.Caffeine, fontSize = 11.sp)
            Text(
                note.text,
                color = HealthyColors.Paper,
                fontSize = 13.sp,
                maxLines = 6,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun NoteEditor(
    initial: Note?,
    date: String,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var day by remember { mutableStateOf(date) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    if (initial == null) "New note" else "Edit note",
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
                    colors = fieldColors(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Note", fontSize = 11.sp) },
                    placeholder = { Text("teeth pain, sore throat, stress") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = fieldColors(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text("Delete", color = HealthyColors.Warn)
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = HealthyColors.Muted)
                    }
                    TextButton(
                        onClick = { onSave(text, day) },
                        enabled = text.isNotBlank(),
                    ) {
                        Text("Save", color = HealthyColors.Sleep)
                    }
                }
            }
        }
    }
}

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
