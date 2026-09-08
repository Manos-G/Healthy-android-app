package com.healthy.app.ui.recipe

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthy.app.analysis.Energy
import com.healthy.app.analysis.Recipes
import com.healthy.app.ui.theme.HealthyColors

/**
 * Recipes (spec 13).
 *
 * A user eats the same meals many times. Without this the app is a form to
 * fill in twice a day, and they stop after two weeks. A saved dish is two taps
 * afterwards.
 */
@Composable
fun RecipeScreen(
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    targetKcal: Int? = null,
    vm: RecipeViewModel = viewModel(),
) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var building by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        item {
            SectionCard {
                Text("Recipes", color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "A dish you cook again. Add what went in, weigh what came out, " +
                        "and a portion is two taps from then on.",
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                OutlinedButton(
                    onClick = { building = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HealthyColors.Rule),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = HealthyColors.Raised2,
                        contentColor = HealthyColors.Paper,
                    ),
                ) { Text("Build a recipe", fontSize = 14.sp) }

                message?.let {
                    Text(it, color = HealthyColors.Sleep, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (cards.isEmpty()) {
            item {
                Text(
                    "No recipes yet.",
                    color = HealthyColors.Muted,
                    fontSize = 13.sp,
                )
            }
        }

        items(cards.size) { index ->
            RecipeRow(cards[index], targetKcal, vm)
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

    if (building) {
        BuilderDialog(draft, vm, onDone = { building = false })
    }
}

@Composable
private fun RecipeRow(card: RecipeCard, targetKcal: Int?, vm: RecipeViewModel) {
    var open by remember { mutableStateOf(false) }
    var weighed by remember { mutableStateOf("") }
    var sharing by remember { mutableStateOf(false) }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(card.recipe.name, color = HealthyColors.Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    card.resolved?.let {
                        "${it.per100g.kcal.toInt()} kcal per 100 g · " +
                            "${card.recipe.cookedGrams.toInt()} g cooked · " +
                            "${card.recipe.portions} portions"
                    } ?: "Needs a cooked weight before it can be used.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                )
            }
            TextButton(onClick = { open = !open }) {
                Text(if (open) "Hide" else "Log", color = HealthyColors.Sleep, fontSize = 13.sp)
            }
        }

        if (sharing) {
            com.healthy.app.scan.QrShareDialog(
                payload = com.healthy.app.scan.QrPayload.encode(card.recipe, card.items),
                title = card.recipe.name,
                onDismiss = { sharing = false },
            )
        }

        card.problem?.let { problem ->
            Text(
                when (problem) {
                    Recipes.Problem.NoCookedWeight ->
                        "Weigh the finished dish. Without it every portion from this recipe is wrong."
                    is Recipes.Problem.BothSources ->
                        "“${problem.itemName}” points at both a product and a recipe."
                    is Recipes.Problem.TooDeep ->
                        "Recipes can hold recipes two levels deep, no further."
                    is Recipes.Problem.Circular ->
                        "This recipe contains itself."
                },
                color = HealthyColors.Warn,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (open && card.resolved != null) {
            HorizontalDivider(color = HealthyColors.Rule, modifier = Modifier.padding(vertical = 10.dp))

            // Spec 13.5: weigh it, a share of the dish, or one portion.
            Recipes.onePortionOf(card.recipe)?.let { portion ->
                PortionButton(
                    label = "One portion, ${portion.grams.toInt()} g",
                    kcal = Recipes.nutrientsFor(card.resolved, portion.grams).kcal,
                    targetKcal = targetKcal,
                ) { vm.logPortion(card, portion.grams) }
            }
            listOf(0.5 to "Half the dish", 0.25 to "A quarter").forEach { (fraction, label) ->
                val share = Recipes.shareOf(card.recipe, fraction)
                PortionButton(
                    label = "$label, ${share.grams.toInt()} g",
                    kcal = Recipes.nutrientsFor(card.resolved, share.grams).kcal,
                    targetKcal = targetKcal,
                ) { vm.logPortion(card, share.grams) }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = weighed,
                    onValueChange = { v -> weighed = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("Weigh it, grams", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = recipeFieldColours(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                    ),
                )
                TextButton(
                    onClick = { weighed.toDoubleOrNull()?.let { vm.logPortion(card, it) } },
                    enabled = weighed.toDoubleOrNull()?.let { it > 0 } == true,
                ) { Text("Log", color = HealthyColors.Sleep) }
            }

            Text(
                "Weighing is the correct one. The other two assume the dish came out " +
                    "at ${card.recipe.cookedGrams.toInt()} g, as it did the day it was saved.",
                color = HealthyColors.Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row {
                TextButton(onClick = { sharing = true }) {
                    Text("Share as QR", color = HealthyColors.Sleep, fontSize = 12.sp)
                }
                TextButton(onClick = { vm.delete(card) }) {
                    Text("Delete recipe", color = HealthyColors.Warn, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PortionButton(label: String, kcal: Double, targetKcal: Int?, onClick: () -> Unit) {
    val share = Energy.percentOfTarget(kcal, targetKcal)
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HealthyColors.Rule),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = HealthyColors.Raised2,
            contentColor = HealthyColors.Paper,
        ),
    ) {
        Text(
            label + " — ${kcal.toInt()} kcal" + (share?.let { ", $it% of today" } ?: ""),
            fontSize = 12.sp,
        )
    }
}

/** Spec 13.3 and 13.4: ingredients first, then the weight of the finished dish. */
@Composable
private fun BuilderDialog(draft: List<DraftItem>, vm: RecipeViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var cooked by remember { mutableStateOf("") }
    var portions by remember { mutableStateOf("4") }
    var itemName by remember { mutableStateOf("") }
    var itemGrams by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDone) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Build a recipe", color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = recipeFieldColours(),
                )

                Text(
                    "Ingredients, weighed before cooking.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                draft.forEachIndexed { index, item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.name, color = HealthyColors.Paper, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("${item.grams.toInt()} g", color = HealthyColors.Muted, fontSize = 12.sp)
                        IconButton(onClick = { vm.removeDraftItem(index) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = HealthyColors.Muted)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Ingredient", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f),
                        colors = recipeFieldColours(),
                    )
                    OutlinedTextField(
                        value = itemGrams,
                        onValueChange = { v -> itemGrams = v.filter { it.isDigit() || it == '.' } },
                        label = { Text("g", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f),
                        colors = recipeFieldColours(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                        ),
                    )
                    TextButton(
                        onClick = {
                            itemGrams.toDoubleOrNull()?.let { g ->
                                vm.addDraftItem(DraftItem(itemName.ifBlank { "Ingredient" }, g))
                                itemName = ""
                                itemGrams = ""
                            }
                        },
                        enabled = itemGrams.toDoubleOrNull() != null,
                    ) { Text("Add", color = HealthyColors.Sleep, fontSize = 12.sp) }
                }

                HorizontalDivider(color = HealthyColors.Rule, modifier = Modifier.padding(vertical = 10.dp))

                Text(
                    "Put the empty pot on the scale, then the full pot. The difference " +
                        "is the cooked weight. A stew loses water and rice absorbs it, so " +
                        "without this every portion afterwards is wrong.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = cooked,
                        onValueChange = { v -> cooked = v.filter { it.isDigit() || it == '.' } },
                        label = { Text("Cooked weight g", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = recipeFieldColours(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                        ),
                    )
                    OutlinedTextField(
                        value = portions,
                        onValueChange = { v -> portions = v.filter { it.isDigit() } },
                        label = { Text("Portions", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f),
                        colors = recipeFieldColours(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                    )
                }
                Text(
                    "Raw so far: ${vm.draftRawGrams().toInt()} g.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )

                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.clearDraft(); onDone() }) {
                        Text("Cancel", color = HealthyColors.Muted)
                    }
                    TextButton(
                        onClick = {
                            vm.save(name, cooked.toDoubleOrNull() ?: 0.0, portions.toIntOrNull() ?: 1, onDone)
                        },
                        enabled = name.isNotBlank() && (cooked.toDoubleOrNull() ?: 0.0) > 0 && draft.isNotEmpty(),
                    ) { Text("Save recipe", color = HealthyColors.Sleep) }
                }
            }
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
private fun recipeFieldColours() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = HealthyColors.Paper,
    unfocusedTextColor = HealthyColors.Paper,
    focusedBorderColor = HealthyColors.Sleep,
    unfocusedBorderColor = HealthyColors.Rule,
    focusedLabelColor = HealthyColors.Sleep,
    unfocusedLabelColor = HealthyColors.Muted,
    cursorColor = HealthyColors.Sleep,
)
