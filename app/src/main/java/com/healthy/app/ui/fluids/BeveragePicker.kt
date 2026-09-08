package com.healthy.app.ui.fluids

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.healthy.app.core.Alcohol
import com.healthy.app.core.Beverage
import com.healthy.app.core.BeverageCatalog
import com.healthy.app.core.BeverageCategory
import com.healthy.app.ui.theme.HealthyColors

/**
 * The full list for one category, searchable.
 *
 * A hundred drinks will not fit on a card, and putting them there would bury
 * the five the user actually drinks. So the card shows the recent five and
 * this opens behind a "more" button, with a search field, because scrolling a
 * hundred names to find ouzo is worse than typing "ou".
 */
@Composable
fun BeveragePicker(
    category: BeverageCategory,
    custom: List<Beverage> = emptyList(),
    mlPerUnit: Double = Alcohol.DEFAULT_ML_PER_UNIT,
    onPick: (Beverage) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(category, query, custom) {
        val own = custom.filter { it.name.contains(query.trim(), ignoreCase = true) }
        own + BeverageCatalog.search(category, query)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            // Two thirds of the screen, with the list taking whatever is left
            // after the header. A list with its own maximum height pushed the
            // Close button off the bottom once the keyboard opened.
            modifier = Modifier.fillMaxHeight(0.72f).imePadding(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        category.label,
                        color = HealthyColors.Paper,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${results.size} drinks",
                        color = HealthyColors.Muted,
                        fontSize = 11.sp,
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HealthyColors.Paper,
                        unfocusedTextColor = HealthyColors.Paper,
                        focusedBorderColor = HealthyColors.Sleep,
                        unfocusedBorderColor = HealthyColors.Rule,
                        focusedLabelColor = HealthyColors.Sleep,
                        unfocusedLabelColor = HealthyColors.Muted,
                        cursorColor = HealthyColors.Sleep,
                    ),
                )

                if (results.isEmpty()) {
                    Text(
                        "Nothing matches \"$query\". Scan the barcode instead, or add it " +
                            "as your own drink.",
                        color = HealthyColors.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp),
                ) {
                    items(results, key = { it.name }) { drink ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(drink) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(drink.name, color = HealthyColors.Paper, fontSize = 14.sp)
                                Text(
                                    summary(drink, mlPerUnit),
                                    color = HealthyColors.Muted,
                                    fontSize = 11.sp,
                                )
                            }
                            Text(
                                "${drink.defaultMl} ${drink.unit}",
                                color = HealthyColors.Sleep,
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

/** What one of these does to the day, at its usual size. */
private fun summary(drink: Beverage, mlPerUnit: Double): String {
    val parts = buildList {
        drink.caffeineMgFor(drink.defaultMl).takeIf { it > 0 }?.let { add("$it mg") }
        Alcohol.units(drink.defaultMl, drink.abv, mlPerUnit).takeIf { it > 0 }
            ?.let { add("${"%.1f".format(it)} units at ${drink.abv.trimmed()}%") }
    }
    return parts.joinToString(" · ").ifEmpty { "fluid only" }
}

private fun Double.trimmed(): String =
    if (this == toLong().toDouble()) toLong().toString() else "%.1f".format(this)
