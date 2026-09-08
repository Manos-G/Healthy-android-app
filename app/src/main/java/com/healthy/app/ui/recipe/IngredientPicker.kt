package com.healthy.app.ui.recipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.healthy.app.data.entity.Product
import com.healthy.app.ui.theme.HealthyColors

/**
 * Choosing an ingredient, three ways (spec 13.3).
 *
 * Raised as an issue: the builder had a free-text name box, so an ingredient
 * was a label with no nutrition behind it and the finished dish came out at
 * zero calories. All three routes here attach a real product — the built-in
 * ingredient list, a barcode from the package, or values typed by hand — so a
 * recipe cannot quietly be empty.
 *
 * The list is the catalog plus everything already scanned or typed, together,
 * because to the person building a recipe those are the same kind of thing.
 */
@Composable
fun IngredientPicker(
    vm: RecipeViewModel,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Product>>(emptyList()) }
    var chosen by remember { mutableStateOf<Product?>(null) }
    var manual by remember { mutableStateOf(false) }

    LaunchedEffect(query) { results = vm.searchIngredients(query) }

    chosen?.let { product ->
        GramsDialog(
            product = product,
            onConfirm = { grams ->
                vm.addIngredient(product, grams)
                chosen = null
                onDismiss()
            },
            onDismiss = { chosen = null },
        )
        return
    }

    if (manual) {
        ManualIngredientDialog(
            onSave = { name, grams, kcal, protein, carbs, fat, fibre ->
                vm.addManualIngredient(name, grams, kcal, protein, carbs, fat, fibre)
                manual = false
                onDismiss()
            },
            onDismiss = { manual = false },
        )
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxHeight(0.78f).imePadding(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Add an ingredient",
                    color = HealthyColors.Paper,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Pick one and its calories and nutrients come with it. Scan the " +
                        "package when you have it — a brand beats a typical value.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A scanned food comes back as a product, which then only
                    // needs its weight — the same last step as a picked one.
                    com.healthy.app.scan.ScanButton(
                        label = "Scan a package",
                        suggestedKind = Product.KIND_FOOD,
                        onFood = { scanned -> chosen = scanned },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { manual = true }) {
                        Text("Type it in", color = HealthyColors.Sleep, fontSize = 12.sp)
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search ingredients", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = recipeFieldColours(),
                )

                if (results.isEmpty()) {
                    Text(
                        "Nothing matches “$query”. Scan it, or type it in with its " +
                            "values for 100 g.",
                        color = HealthyColors.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp)) {
                    items(results, key = { it.barcode }) { product ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { chosen = product }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(product.name, color = HealthyColors.Paper, fontSize = 14.sp)
                                Text(
                                    macroLine(product),
                                    color = HealthyColors.Muted,
                                    fontSize = 11.sp,
                                )
                            }
                            Text(
                                product.kcal100?.let { "${it.toInt()} kcal" } ?: "no values",
                                color = if (product.kcal100 != null) {
                                    HealthyColors.Sleep
                                } else {
                                    HealthyColors.Warn
                                },
                                fontSize = 12.sp,
                            )
                        }
                        HorizontalDivider(color = HealthyColors.Rule)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close", color = HealthyColors.Muted) }
                }
            }
        }
    }
}

private fun macroLine(product: Product): String {
    val parts = buildList {
        product.protein100?.let { add("P ${trim(it)}") }
        product.carbs100?.let { add("C ${trim(it)}") }
        product.fat100?.let { add("F ${trim(it)}") }
        product.fibre100?.let { add("fibre ${trim(it)}") }
    }
    return if (parts.isEmpty()) "per 100 g, values unknown" else parts.joinToString(" · ") + " per 100 g"
}

private fun trim(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

/** How much of it went in, weighed before cooking (spec 13.3). */
@Composable
private fun GramsDialog(
    product: Product,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var grams by remember { mutableStateOf("") }
    val value = grams.toDoubleOrNull()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    product.name,
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    macroLine(product),
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                OutlinedTextField(
                    value = grams,
                    onValueChange = { v -> grams = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("Grams, before cooking", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = recipeFieldColours(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                    ),
                )
                if (value != null && value > 0 && product.kcal100 != null) {
                    Text(
                        "${value.toInt()} g is ${(product.kcal100!! * value / 100).toInt()} kcal.",
                        color = HealthyColors.Sleep,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Back", color = HealthyColors.Muted) }
                    TextButton(
                        onClick = { value?.let(onConfirm) },
                        enabled = value != null && value > 0,
                    ) { Text("Add", color = HealthyColors.Sleep) }
                }
            }
        }
    }
}

/** Fruit from a market stall, bread from a bakery: no barcode to scan (spec 12.4). */
@Composable
private fun ManualIngredientDialog(
    onSave: (String, Double, Double?, Double?, Double?, Double?, Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var fibre by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.imePadding(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Type an ingredient",
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Values for 100 g, from the packet or a food table. It is kept, so " +
                        "it is searchable next time.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = recipeFieldColours(),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Num("kcal", kcal, Modifier.weight(1f)) { kcal = it }
                    Num("protein", protein, Modifier.weight(1f)) { protein = it }
                    Num("carbs", carbs, Modifier.weight(1f)) { carbs = it }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Num("fat", fat, Modifier.weight(1f)) { fat = it }
                    Num("fibre", fibre, Modifier.weight(1f)) { fibre = it }
                    Num("used, g", grams, Modifier.weight(1f)) { grams = it }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Back", color = HealthyColors.Muted) }
                    TextButton(
                        onClick = {
                            onSave(
                                name,
                                grams.toDoubleOrNull() ?: 0.0,
                                kcal.toDoubleOrNull(),
                                protein.toDoubleOrNull(),
                                carbs.toDoubleOrNull(),
                                fat.toDoubleOrNull(),
                                fibre.toDoubleOrNull(),
                            )
                        },
                        enabled = name.isNotBlank() && (grams.toDoubleOrNull() ?: 0.0) > 0,
                    ) { Text("Add", color = HealthyColors.Sleep) }
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
        colors = recipeFieldColours(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
        ),
    )
}
