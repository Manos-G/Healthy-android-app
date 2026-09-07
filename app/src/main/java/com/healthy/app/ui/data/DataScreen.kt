package com.healthy.app.ui.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthy.app.ui.theme.HealthyColors

@Composable
fun DataScreen(
    modifier: Modifier = Modifier,
    vm: DataViewModel = viewModel(),
) {
    var showSources by remember { mutableStateOf(false) }
    if (showSources) {
        com.healthy.app.ui.sources.SourcesScreen(onClose = { showSources = false })
        return
    }
    val status by vm.status.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val surveyText by vm.survey.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<ExportKind?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val kind = pending
        pending = null
        if (uri != null && kind != null) vm.export(kind, uri)
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        item {
            SectionCard {
                Text(
                    "Take your data with you",
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "CSV opens in any spreadsheet. The JSON holds everything, including your settings, and is the one to keep.",
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                ExportKind.entries.forEach { kind ->
                    OutlinedButton(
                        onClick = {
                            pending = kind
                            picker.launch(kind.fileName)
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, HealthyColors.Rule),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = HealthyColors.Raised2,
                            contentColor = HealthyColors.Paper,
                        ),
                    ) {
                        Text(kind.label, fontSize = 14.sp)
                    }
                }
                if (status != null) {
                    Text(
                        status.orEmpty(),
                        color = HealthyColors.Sleep,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        item {
            SectionCard {
                Text(
                    "What is stored",
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(Modifier.padding(top = 8.dp)) {
                    CountRow("Nights", counts.nights)
                    CountRow("Drinks", counts.drinks)
                    CountRow("Notes", counts.notes)
                    CountRow("Weights", counts.weights)
                }
                Text(
                    "Export is the only copy. Uninstalling the app deletes the database, and so does a developer test run.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        item {
            SectionCard {
                Text(
                    "What Health Connect actually holds",
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Reads the last 7 days and reports what is there, rather than what it should be.",
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                OutlinedButton(
                    onClick = { vm.runSurvey() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HealthyColors.Rule),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = HealthyColors.Raised2,
                        contentColor = HealthyColors.Paper,
                    ),
                ) {
                    Text("Check", fontSize = 14.sp)
                }
                if (surveyText != null) {
                    Text(
                        surveyText.orEmpty(),
                        color = HealthyColors.Sleep,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        item {
            SectionCard {
                Text(
                    "Where the numbers come from",
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Every value this app calculates with, why it was chosen, and what it does not tell you.",
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                OutlinedButton(
                    onClick = { showSources = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HealthyColors.Rule),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = HealthyColors.Raised2,
                        contentColor = HealthyColors.Paper,
                    ),
                ) {
                    Text("Sources and references", fontSize = 14.sp)
                }
            }
        }

        item {
            SectionCard {
                Text(
                    "Still to come",
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Import, QR share and the settings for bedtime, half-life and the bedtime limit arrive with step 17. Export came early so there is a way back from a lost database.",
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CountRow(label: String, value: Int) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = HealthyColors.Muted, fontSize = 13.sp)
        Text(
            value.toString(),
            color = if (value == 0) HealthyColors.Muted else HealthyColors.Paper,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
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
