package com.healthy.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthy.app.core.CatalogDrink
import com.healthy.app.data.entity.Drink
import com.healthy.app.data.entity.Night
import com.healthy.app.ui.components.RatingScale
import com.healthy.app.ui.theme.HealthyColors
import com.healthy.app.ui.theme.HeroNumeral
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun Long.asClock(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime().format(HHMM)

@Composable
fun TodayScreen(
    snackbars: SnackbarHostState,
    modifier: Modifier = Modifier,
    vm: TodayViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lastLogged by vm.lastLogged.collectAsStateWithLifecycle()
    val energyPrompt by vm.energyPrompt.collectAsStateWithLifecycle()

    // One tap logs; the snackbar is the only chance to take it back (spec 5.1).
    LaunchedEffect(lastLogged) {
        val logged = lastLogged ?: return@LaunchedEffect
        val result = snackbars.showSnackbar(
            message = "${logged.name}, ${logged.mg} mg",
            actionLabel = "Undo",
        )
        if (result == SnackbarResult.ActionPerformed) vm.undoLast() else vm.clearSnackbar()
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        item { HeroCard(state) }
        energyPrompt?.let { night ->
            item {
                EnergyPromptCard(night) { rating -> vm.rateEnergy(night.date, rating) }
            }
        }
        item { FluidCard(state, vm::logFluid) }
        item { CatalogCard(state.catalog, vm::log) }
        item { EntriesCard(state.entries, vm::delete) }
    }
}

/**
 * Collects the 15:00 energy rating (spec 5.2).
 *
 * The morning form cannot ask for this: at 08:00 the user has no idea what
 * their afternoon will feel like. So the morning saves alertness alone and
 * this card appears here after 15:00 for the night that is still missing it.
 */
@Composable
private fun EnergyPromptCard(night: Night, onRate: (Int) -> Unit) {
    SectionCard {
        Text(
            "How is your energy?",
            color = HealthyColors.Paper,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "For the night of ${night.date}. One tap finishes it.",
            color = HealthyColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        RatingScale(
            value = null,
            lowLabel = "flat",
            highLabel = "strong",
            modifier = Modifier.padding(top = 12.dp),
        ) { picked -> picked?.let(onRate) }
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
private fun HeroCard(state: TodayState) {
    SectionCard {
        Text(
            "Caffeine in your body now",
            color = HealthyColors.Muted,
            fontSize = 13.sp,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = state.nowMg.toString(),
                style = HeroNumeral,
                color = HealthyColors.Caffeine,
            )
            Text(
                text = " mg",
                color = HealthyColors.Muted,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        CaffeineCurve(
            state = state,
            modifier = Modifier.fillMaxWidth().height(130.dp).padding(top = 12.dp),
        )

        HorizontalDivider(
            color = HealthyColors.Rule,
            modifier = Modifier.padding(vertical = 14.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "At bedtime ${state.bedtimeLabel}",
                    color = HealthyColors.Muted,
                    fontSize = 13.sp,
                )
                Text(
                    "${state.bedtimeMg} mg",
                    color = HealthyColors.Paper,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Verdict(state.isClear)
        }

        Text(
            "Logged today: ${state.totalMg} mg, ${state.totalMl} ml",
            color = HealthyColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun Verdict(isClear: Boolean) {
    val colour = if (isClear) HealthyColors.Sleep else HealthyColors.Warn
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colour.copy(alpha = 0.16f),
    ) {
        Text(
            text = if (isClear) "clear" else "still active",
            color = colour,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/**
 * Fluid for the day (spec 9.3).
 *
 * A bar that fills, and nothing else: no notification when below target and no
 * streak. Caffeinated drinks already counted themselves when they were logged
 * (spec 9.1), so these buttons are only for the ones with no caffeine.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FluidCard(state: TodayState, onLog: (com.healthy.app.core.FluidDrink) -> Unit) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("Fluid", color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${state.totalMl} of ${state.fluidTargetMl} ml",
                color = HealthyColors.Muted,
                fontSize = 13.sp,
            )
        }

        val fraction = if (state.fluidTargetMl > 0) {
            (state.totalMl.toFloat() / state.fluidTargetMl).coerceIn(0f, 1f)
        } else {
            0f
        }
        androidx.compose.material3.LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp),
            color = HealthyColors.Sleep,
            trackColor = HealthyColors.Raised2,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )

        if (state.alcoholUnits > 0) {
            Text(
                "Alcohol today: ${"%.1f".format(state.alcoholUnits)} units. " +
                    "The morning screen reads this; you do not type it again.",
                color = HealthyColors.Caffeine,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.fluidCatalog.forEach { drink ->
                OutlinedButton(
                    onClick = { onLog(drink) },
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = HealthyColors.Raised2,
                        contentColor = HealthyColors.Paper,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HealthyColors.Rule),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp,
                    ),
                ) {
                    Column {
                        Text(drink.name, fontSize = 13.sp)
                        Text(
                            "${drink.volumeMl} ml",
                            fontSize = 11.sp,
                            color = HealthyColors.Sleep,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogCard(catalog: List<CatalogDrink>, onLog: (CatalogDrink) -> Unit) {
    SectionCard {
        Text(
            "One tap logs it now",
            color = HealthyColors.Paper,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Caffeine and fluid are recorded together.",
            color = HealthyColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        // A plain wrapping row rather than a nested LazyVerticalGrid, which
        // cannot measure inside a LazyColumn item.
        FlowGrid(catalog, onLog)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowGrid(catalog: List<CatalogDrink>, onLog: (CatalogDrink) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        catalog.forEach { drink ->
            OutlinedButton(
                onClick = { onLog(drink) },
                shape = RoundedCornerShape(10.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = HealthyColors.Raised2,
                    contentColor = HealthyColors.Paper,
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, HealthyColors.Rule),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp,
                ),
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(drink.name, fontSize = 13.sp, textAlign = TextAlign.Start)
                    Text(
                        "${drink.mg} mg",
                        fontSize = 11.sp,
                        color = HealthyColors.Caffeine,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntriesCard(entries: List<Drink>, onDelete: (Drink) -> Unit) {
    SectionCard {
        Text(
            "Today",
            color = HealthyColors.Paper,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (entries.isEmpty()) {
            Text(
                "Nothing logged yet today.",
                color = HealthyColors.Muted,
                fontSize = 13.sp,
            )
            return@SectionCard
        }
        entries.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.timestamp.asClock(),
                    color = HealthyColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    entry.name,
                    color = HealthyColors.Paper,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${entry.mg} mg",
                    color = HealthyColors.Caffeine,
                    fontSize = 13.sp,
                )
                IconButton(onClick = { onDelete(entry) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Delete ${entry.name}",
                        tint = HealthyColors.Muted,
                    )
                }
            }
        }
    }
}
