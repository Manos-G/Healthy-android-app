package com.healthy.app.ui.fluids

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.healthy.app.core.Alcohol
import com.healthy.app.core.Beverage
import com.healthy.app.core.BeverageCategory
import com.healthy.app.core.SafeLimits
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
fun FluidsScreen(
    snackbars: SnackbarHostState,
    modifier: Modifier = Modifier,
    vm: FluidsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lastLogged by vm.lastLogged.collectAsStateWithLifecycle()
    val energyPrompt by vm.energyPrompt.collectAsStateWithLifecycle()

    // The drink whose amount is being chosen, and the category whose full list
    // is open. Only one of each can be true at a time.
    var picking by remember { mutableStateOf<Beverage?>(null) }
    var browsing by remember { mutableStateOf<BeverageCategory?>(null) }

    // One tap logs; the snackbar is the only chance to take it back (spec 5.1).
    LaunchedEffect(lastLogged) {
        val logged = lastLogged ?: return@LaunchedEffect
        val result = snackbars.showSnackbar(
            message = loggedSummary(logged),
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
        item { FluidCard(state) }
        BeverageCategory.entries.forEach { category ->
            item {
                CategoryCard(
                    category = category,
                    state = state,
                    recent = state.recent[category].orEmpty(),
                    onPick = { picking = it },
                    onBrowse = { browsing = category },
                )
            }
        }
        item { EntriesCard(state, vm::setWindow, vm::delete) }
        item {
            com.healthy.app.scan.ScanButton(modifier = Modifier.fillMaxWidth())
        }
        item {
            com.healthy.app.ui.notes.QuickNoteButton(
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    browsing?.let { category ->
        BeveragePicker(
            category = category,
            custom = state.custom,
            mlPerUnit = state.mlPerUnit,
            onPick = { drink ->
                browsing = null
                picking = drink
            },
            onDismiss = { browsing = null },
        )
    }

    picking?.let { drink ->
        AmountDialog(
            beverage = drink,
            mlPerUnit = state.mlPerUnit,
            startMl = drink.defaultMl,
            onConfirm = { amount, minutesAgo ->
                picking = null
                vm.log(drink, amount, minutesAgo)
            },
            onDismiss = { picking = null },
        )
    }
}

/** What the snackbar says, which depends on what the drink actually was. */
private fun loggedSummary(logged: Drink): String {
    val parts = buildList {
        logged.mg.takeIf { it > 0 }?.let { add("$it mg") }
        logged.volumeMl.takeIf { it > 0 }?.let { add("$it ml") }
        logged.alcoholUnits.takeIf { it > 0 }?.let { add("${"%.1f".format(it)} units") }
    }
    return logged.name + if (parts.isEmpty()) "" else ", " + parts.joinToString(", ")
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
private fun HeroCard(state: FluidsState) {
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
            com.healthy.app.ui.sources.SourceLink(
                item = "Caffeine half-life",
                modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "At bedtime ${state.bedtimeLabel}",
                        color = HealthyColors.Muted,
                        fontSize = 13.sp,
                    )
                    com.healthy.app.ui.sources.SourceLink(
                        item = "Bedtime caffeine limit",
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
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
/**
 * Fluid and alcohol for the day (spec 9.3, 9.4).
 *
 * A bar that fills, and nothing else: no notification when below target and no
 * streak. The drinks themselves moved to the three category cards below, so
 * this states totals only.
 */
@Composable
private fun FluidCard(state: FluidsState) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Fluid",
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                com.healthy.app.ui.sources.SourceLink(
                    item = "Daily fluid target",
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
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
    }
}

/**
 * One category, showing only what this person actually drinks.
 *
 * Three hundred drinks are available and five are on screen: the last five
 * logged in this category, newest first. Everything else is one tap away
 * behind the search. A category with no history yet opens with a few sensible
 * starters rather than an empty card that teaches nothing.
 *
 * Every drink here opens the amount carousel rather than logging on the spot.
 * That is one extra tap and it buys the thing the old catalog could not do:
 * the same beer in a bottle and in a pint are different drinks, and the app
 * now knows which one it was.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CategoryCard(
    category: BeverageCategory,
    state: FluidsState,
    recent: List<Beverage>,
    onPick: (Beverage) -> Unit,
    onBrowse: () -> Unit,
) {
    val mlPerUnit = state.mlPerUnit
    val shown = remember(category, recent) {
        if (recent.isNotEmpty()) recent else starters(category)
    }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                category.label,
                color = HealthyColors.Paper,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            androidx.compose.material3.TextButton(onClick = onBrowse) {
                Text("All drinks", color = HealthyColors.Sleep, fontSize = 12.sp)
            }
        }

        LimitBar(category, state)

        Text(
            if (recent.isEmpty()) {
                "Nothing logged here yet. These are a start; your last five appear " +
                    "once you have used it."
            } else {
                "Your last ${recent.size}. Tap one to choose how much."
            },
            color = HealthyColors.Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shown.forEach { drink ->
                OutlinedButton(
                    onClick = { onPick(drink) },
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
                            badge(drink, mlPerUnit),
                            fontSize = 11.sp,
                            color = when (category) {
                                BeverageCategory.Caffeine -> HealthyColors.Caffeine
                                BeverageCategory.Alcohol -> HealthyColors.Warn
                                BeverageCategory.Water -> HealthyColors.Sleep
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Where the day stands against the published guideline for this category.
 *
 * These are intake figures, so they are drawn against a running total and not
 * on the caffeine curve — that curve plots milligrams still circulating, and a
 * daily intake limit has no meaning on that axis. Mixing the two would be the
 * same class of error as reading a speed off an odometer.
 *
 * Never coloured red and never notified (spec 12.1, 16.5). Past the line the
 * bar simply says so.
 */
@Composable
private fun LimitBar(category: BeverageCategory, state: FluidsState) {
    val spec = when (category) {
        BeverageCategory.Caffeine -> LimitSpec(
            value = state.totalMg.toDouble(),
            limit = SafeLimits.CAFFEINE_DAILY_MG.toDouble(),
            reading = "${state.totalMg} of ${SafeLimits.CAFFEINE_DAILY_MG} mg today",
            over = "Past ${SafeLimits.CAFFEINE_DAILY_MG} mg for the day. Bedtime is the " +
                "line that decides your sleep, and it is on the curve above.",
            source = "Daily caffeine limit",
            colour = HealthyColors.Caffeine,
        )

        BeverageCategory.Alcohol -> LimitSpec(
            value = state.weekAlcoholUnits,
            limit = SafeLimits.ALCOHOL_WEEKLY_UNITS,
            reading = "${"%.1f".format(state.weekAlcoholUnits)} of " +
                "${SafeLimits.ALCOHOL_WEEKLY_UNITS.toInt()} units this week",
            over = "Past ${SafeLimits.ALCOHOL_WEEKLY_UNITS.toInt()} units for the week. " +
                "The guideline is a week, not a day, so this resets seven days after " +
                "each drink rather than at midnight.",
            source = "Weekly alcohol limit",
            colour = HealthyColors.Warn,
        )

        BeverageCategory.Water -> LimitSpec(
            value = state.totalMl.toDouble(),
            limit = SafeLimits.FLUID_CAUTION_ML.toDouble(),
            reading = "${state.totalMl} ml today. Target ${state.fluidTargetMl}, " +
                "and past ${SafeLimits.FLUID_CAUTION_ML} more stops helping.",
            over = "Past ${SafeLimits.FLUID_CAUTION_ML} ml. More water is doing nothing " +
                "useful now unless you have been sweating hard.",
            source = "Fluid caution level",
            colour = HealthyColors.Sleep,
        )
    }

    val past = SafeLimits.exceeded(spec.value, spec.limit)

    Column(Modifier.padding(bottom = 10.dp)) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { SafeLimits.fraction(spec.value, spec.limit) },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = spec.colour,
            trackColor = HealthyColors.Raised2,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (past) spec.over else spec.reading,
                color = if (past) spec.colour else HealthyColors.Muted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            com.healthy.app.ui.sources.SourceLink(
                item = spec.source,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

private data class LimitSpec(
    val value: Double,
    val limit: Double,
    val reading: String,
    val over: String,
    val source: String,
    val colour: androidx.compose.ui.graphics.Color,
)

/** The button's second line: what one of these does, at the size shown. */
private fun badge(drink: Beverage, mlPerUnit: Double): String {
    val ml = drink.defaultMl
    return when {
        drink.abv > 0 ->
            "$ml ml · ${"%.1f".format(Alcohol.units(ml, drink.abv, mlPerUnit))} units"
        drink.caffeinePer100 > 0 -> "$ml ${drink.unit} · ${drink.caffeineMgFor(ml)} mg"
        else -> "$ml ml"
    }
}

/**
 * What a category offers before it knows anything about this user.
 *
 * Drawn from the catalog by name rather than written out again, so there is
 * still only one place a drink's figures are stated.
 */
private fun starters(category: BeverageCategory): List<Beverage> {
    val names = when (category) {
        BeverageCategory.Water ->
            listOf("Water, glass", "Water, bottle", "Sparkling water", "Orange juice", "Herbal infusion")
        BeverageCategory.Caffeine ->
            listOf("Freddo espresso", "Greek coffee", "Filter coffee", "Black tea", "Coca-Cola")
        BeverageCategory.Alcohol ->
            listOf("Lager", "Red wine, glass", "White wine, glass", "Ouzo, shot", "Tsipouro")
    }
    return names.mapNotNull(com.healthy.app.core.BeverageCatalog::byName)
}

/** The one figure that matters for this row. */
private fun entryFigure(entry: Drink): String = when {
    entry.alcoholUnits > 0 -> "%.1f units".format(entry.alcoholUnits)
    entry.mg > 0 -> "${entry.mg} mg"
    else -> "${entry.volumeMl} ml"
}

@Composable
private fun EntriesCard(
    state: FluidsState,
    onWindow: (com.healthy.app.core.LogWindow) -> Unit,
    onDelete: (Drink) -> Unit,
) {
    SectionCard {
        com.healthy.app.ui.components.WindowSelector(
            window = state.window,
            now = state.nowMillis,
            onChange = onWindow,
        )
        Text(
            if (state.window is com.healthy.app.core.LogWindow.Rolling) {
                "Everything you have drunk in the last 24 hours, whichever day the " +
                    "app counts it against. Step back to correct an earlier day."
            } else {
                "One whole day, 04:00 to 04:00."
            },
            color = HealthyColors.Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        if (state.entries.isEmpty()) {
            Text(
                "Nothing logged in this window.",
                color = HealthyColors.Muted,
                fontSize = 13.sp,
            )
            return@SectionCard
        }

        // The rows run newest first, so the boundary is crossed once, going
        // backwards. Everything below it counts against the previous day.
        var boundaryDrawn = false
        state.entries.forEach { entry ->
            val boundary = state.boundaryMillis
            if (boundary != null && !boundaryDrawn && entry.timestamp < boundary) {
                boundaryDrawn = true
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(color = HealthyColors.Rule, modifier = Modifier.weight(1f))
                    Text(
                        "  04:00 — counts against the previous day  ",
                        color = HealthyColors.Muted,
                        fontSize = 10.sp,
                    )
                    HorizontalDivider(color = HealthyColors.Rule, modifier = Modifier.weight(1f))
                }
            }
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
                // Whatever this drink actually contributed. A row that shows
                // "0 mg" against a beer says nothing useful about the beer.
                Text(
                    entryFigure(entry),
                    color = when {
                        entry.alcoholUnits > 0 -> HealthyColors.Warn
                        entry.mg > 0 -> HealthyColors.Caffeine
                        else -> HealthyColors.Sleep
                    },
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
