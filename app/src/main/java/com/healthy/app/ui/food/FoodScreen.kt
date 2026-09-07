package com.healthy.app.ui.food

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthy.app.analysis.Nutrition
import com.healthy.app.data.entity.MealEntry
import com.healthy.app.data.entity.Product
import com.healthy.app.ui.theme.HealthyColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Food (spec 12).
 *
 * The purpose is correlation with sleep and alertness, not a calorie budget.
 * Nothing here is coloured red, nothing is compared against a target, and
 * there is no streak — the totals are shown because the comparison table needs
 * them, not to be judged.
 */
@Composable
fun FoodScreen(
    modifier: Modifier = Modifier,
    vm: FoodViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val pending by vm.pendingPortion.collectAsStateWithLifecycle()
    var manual by remember { mutableStateOf(false) }
    // The day's target, so every calorie figure can say what share of it it is.
    val energy = androidx.lifecycle.viewmodel.compose.viewModel<com.healthy.app.ui.energy.EnergyViewModel>()
    val energyState by energy.state.collectAsStateWithLifecycle()
    val targetKcal = energyState.plan?.targetKcal

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        item { com.healthy.app.ui.energy.EnergyCard(vm = energy) }
        item { TotalsCard(state.totals, state.today.size, targetKcal) }

        item {
            SectionCard {
                Title("Add something", "Scan it, search what you have eaten before, or type it once.")
                com.healthy.app.scan.ScanButton(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    suggestedKind = Product.KIND_FOOD,
                    label = "Scan a food",
                    onFood = vm::choosePortionFor,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    label = { Text("Search your foods", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = foodFieldColours(),
                )
                state.searchResults.forEach { product ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.choosePortionFor(product) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name, color = HealthyColors.Paper, fontSize = 13.sp)
                            Text(
                                product.kcal100?.let { "${it.toInt()} kcal per 100 g" } ?: "no values",
                                color = HealthyColors.Muted,
                                fontSize = 11.sp,
                            )
                        }
                        Text("Log", color = HealthyColors.Sleep, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = { manual = true }) {
                    Text("Type a food with no barcode", color = HealthyColors.Sleep, fontSize = 13.sp)
                }
            }
        }

        item {
            SectionCard {
                Title("Eaten today")
                if (state.today.isEmpty()) {
                    Text(
                        "Nothing logged yet.",
                        color = HealthyColors.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    state.today.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                item.entry.timestamp.asClock(),
                                color = HealthyColors.Muted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.product?.name ?: "Unknown",
                                    color = HealthyColors.Paper,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    "${item.entry.grams.toInt()} g, ${item.entry.mealType}",
                                    color = HealthyColors.Muted,
                                    fontSize = 11.sp,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${item.totals.kcal.toInt()} kcal",
                                    color = HealthyColors.Caffeine,
                                    fontSize = 12.sp,
                                )
                                com.healthy.app.analysis.Energy
                                    .percentOfTarget(item.totals.kcal, targetKcal)
                                    ?.let { pct ->
                                        Text("$pct%", color = HealthyColors.Muted, fontSize = 10.sp)
                                    }
                            }
                            IconButton(onClick = { vm.delete(item.entry) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove",
                                    tint = HealthyColors.Muted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pending?.let { product ->
        PortionDialog(
            product = product,
            targetKcal = targetKcal,
            onLog = { grams, mealType -> vm.log(product, grams, mealType) },
            onDismiss = vm::cancelPortion,
        )
    }

    if (manual) {
        ManualFoodDialog(
            onSave = { name, kcal, protein, carbs, fat, fibre, grams, meal ->
                vm.saveManualFood(name, kcal, protein, carbs, fat, fibre, grams, meal)
                manual = false
            },
            onDismiss = { manual = false },
        )
    }
}

private fun Long.asClock(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime().format(HHMM)

@Composable
private fun TotalsCard(totals: Nutrition.Totals, count: Int, targetKcal: Int?) {
    SectionCard {
        Title("Today", "$count item${if (count == 1) "" else "s"} logged.")
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Figure(
                "Energy",
                "${totals.kcal.toInt()}",
                "kcal",
                percent = com.healthy.app.analysis.Energy.percentOfTarget(totals.kcal, targetKcal),
            )
            Figure("Protein", "${totals.protein.toInt()}", "g")
            Figure("Carbs", "${totals.carbs.toInt()}", "g")
            Figure("Fat", "${totals.fat.toInt()}", "g")
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Figure("Fibre", "${totals.fibre.toInt()}", "g")
            Figure("Sugar", "${totals.sugar.toInt()}", "g")
            Figure("Salt", "%.1f".format(totals.salt), "g")
            Figure("Magnesium", "${(totals.magnesium * 1000).toInt()}", "mg")
        }
        Text(
            "These are here so the comparison table has something to compare. " +
                "Magnesium and vitamin D have a plausible link to sleep; energy and " +
                "protein do not, and are not in that table.",
            color = HealthyColors.Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun Figure(label: String, value: String, unit: String, percent: Int? = null) {
    Column {
        Text(label, color = HealthyColors.Muted, fontSize = 10.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(" $unit", color = HealthyColors.Muted, fontSize = 10.sp)
        }
        // Absent until a target exists, because spec 12.1 keeps every target
        // off by default and a percentage of nothing is not a number.
        if (percent != null) {
            Text("$percent%", color = HealthyColors.Caffeine, fontSize = 11.sp)
        }
    }
}

/**
 * The portion problem (spec 12.3).
 *
 * A barcode gives values for 100 g and says nothing about how much was eaten,
 * so the app has to ask. One serving is hidden when the field is absent.
 */
@Composable
private fun PortionDialog(
    product: Product,
    targetKcal: Int?,
    onLog: (Double, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var weighed by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf(MealEntry.SNACK) }
    val options = Nutrition.portionsFor(product)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(product.name, color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    product.kcal100?.let { "${it.toInt()} kcal per 100 g" } ?: "No values recorded",
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        MealEntry.BREAKFAST to "breakfast",
                        MealEntry.LUNCH to "lunch",
                        MealEntry.DINNER to "dinner",
                        MealEntry.SNACK to "snack",
                    ).forEach { (value, label) ->
                        val selected = mealType == value
                        TextButton(
                            onClick = { mealType = value },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (selected) HealthyColors.Sleep else HealthyColors.Muted,
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Text(label, fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(color = HealthyColors.Rule, modifier = Modifier.padding(vertical = 10.dp))

                options.forEach { portion ->
                    val kcal = Nutrition.forGrams(product, portion.grams).kcal
                    val share = com.healthy.app.analysis.Energy.percentOfTarget(kcal, targetKcal)
                    val label = buildString {
                        append(
                            when (portion) {
                                is Nutrition.Portion.WholePack -> "Whole pack, ${portion.grams.toInt()} g"
                                is Nutrition.Portion.OneServing -> "One serving, ${portion.grams.toInt()} g"
                                else -> ""
                            }
                        )
                        if (kcal > 0) append(" — ${kcal.toInt()} kcal")
                        share?.let { append(", $it% of today") }
                    }
                    OutlinedButton(
                        onClick = { onLog(portion.grams, mealType) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, HealthyColors.Rule),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = HealthyColors.Raised2,
                            contentColor = HealthyColors.Paper,
                        ),
                    ) {
                        Text(label, fontSize = 13.sp)
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = weighed,
                        onValueChange = { v -> weighed = v.filter { it.isDigit() || it == '.' } },
                        label = { Text("Weigh it, grams", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = foodFieldColours(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                        ),
                    )
                    TextButton(
                        onClick = { weighed.toDoubleOrNull()?.let { onLog(it, mealType) } },
                        enabled = weighed.toDoubleOrNull()?.let { it > 0 } == true,
                    ) {
                        Text("Log", color = HealthyColors.Sleep)
                    }
                }

                weighed.toDoubleOrNull()?.takeIf { it > 0 }?.let { grams ->
                    val kcal = Nutrition.forGrams(product, grams).kcal
                    val share = com.healthy.app.analysis.Energy.percentOfTarget(kcal, targetKcal)
                    Text(
                        "${grams.toInt()} g is ${kcal.toInt()} kcal" +
                            (share?.let { ", $it% of today's target" } ?: "") + ".",
                        color = HealthyColors.Sleep,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    "A kitchen scale gives a correct number. An estimate by eye is " +
                        "wrong by 30 percent or more.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = HealthyColors.Muted) }
                }
            }
        }
    }
}

/** A food with no barcode: fruit, vegetables, bread from a bakery (spec 12.4). */
@Composable
private fun ManualFoodDialog(
    onSave: (String, Double?, Double?, Double?, Double?, Double?, Double, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var fibre by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("A food with no barcode", color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Typed once and kept, so next time it is one tap from the search above.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = foodFieldColours(),
                )
                Text(
                    "Values for 100 g",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Num("kcal", kcal, Modifier.weight(1f)) { kcal = it }
                    Num("protein", protein, Modifier.weight(1f)) { protein = it }
                    Num("carbs", carbs, Modifier.weight(1f)) { carbs = it }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Num("fat", fat, Modifier.weight(1f)) { fat = it }
                    Num("fibre", fibre, Modifier.weight(1f)) { fibre = it }
                    Num("ate, g", grams, Modifier.weight(1f)) { grams = it }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = HealthyColors.Muted) }
                    TextButton(
                        onClick = {
                            onSave(
                                name, kcal.toDoubleOrNull(), protein.toDoubleOrNull(),
                                carbs.toDoubleOrNull(), fat.toDoubleOrNull(), fibre.toDoubleOrNull(),
                                grams.toDoubleOrNull() ?: 0.0, MealEntry.SNACK,
                            )
                        },
                        enabled = name.isNotBlank() && (grams.toDoubleOrNull() ?: 0.0) > 0,
                    ) {
                        Text("Save and log", color = HealthyColors.Sleep)
                    }
                }
            }
        }
    }
}

@Composable
private fun Num(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }) },
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        modifier = modifier,
        colors = foodFieldColours(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
        ),
    )
}

@Composable
private fun Title(text: String, subtitle: String? = null) {
    Text(text, color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    if (subtitle != null) {
        Text(subtitle, color = HealthyColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
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
private fun foodFieldColours() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = HealthyColors.Paper,
    unfocusedTextColor = HealthyColors.Paper,
    focusedBorderColor = HealthyColors.Sleep,
    unfocusedBorderColor = HealthyColors.Rule,
    focusedLabelColor = HealthyColors.Sleep,
    unfocusedLabelColor = HealthyColors.Muted,
    cursorColor = HealthyColors.Sleep,
)
