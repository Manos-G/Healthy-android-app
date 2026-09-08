package com.healthy.app.scan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.healthy.app.data.entity.Product
import com.healthy.app.ui.theme.HealthyColors

/**
 * What the lookup returned, in editable boxes (the user's request).
 *
 * Open Food Facts is edited by the public. A quantity of 100 ml on a 250 ml
 * can, a missing caffeine figure, an energy value for the wrong variant — all
 * are common, and storing them unseen means the app believes a wrong number
 * for as long as that barcode exists. Everything used in a calculation is
 * shown and can be corrected before it is saved.
 *
 * The nutrients sit behind a disclosure, because the two fields that matter
 * for a drink are the caffeine and the volume, and burying those under twelve
 * boxes would be worse than not showing them at all.
 */
@Composable
fun ReviewDialog(
    product: Product,
    correcting: Boolean = false,
    onSave: (Product) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(product.name) }
    var mg by remember { mutableStateOf(product.mg?.toString() ?: "") }
    var volume by remember { mutableStateOf(product.volumeMl?.toString() ?: "") }
    var kcal by remember { mutableStateOf(product.kcal100?.let { fmt(it) } ?: "") }
    var protein by remember { mutableStateOf(product.protein100?.let { fmt(it) } ?: "") }
    var carbs by remember { mutableStateOf(product.carbs100?.let { fmt(it) } ?: "") }
    var sugar by remember { mutableStateOf(product.sugar100?.let { fmt(it) } ?: "") }
    var fat by remember { mutableStateOf(product.fat100?.let { fmt(it) } ?: "") }
    var satFat by remember { mutableStateOf(product.saturatedFat100?.let { fmt(it) } ?: "") }
    var fibre by remember { mutableStateOf(product.fibre100?.let { fmt(it) } ?: "") }
    var salt by remember { mutableStateOf(product.salt100?.let { fmt(it) } ?: "") }
    var pack by remember { mutableStateOf(product.packGrams?.toString() ?: "") }
    var serving by remember { mutableStateOf(product.servingGrams?.toString() ?: "") }
    var expanded by remember { mutableStateOf(false) }

    val isDrink = product.kind == Product.KIND_DRINK

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(
                Modifier.padding(18.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    if (correcting) "Fix these values" else "Check what was found",
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (correcting) {
                        "Whatever you change here is saved against the barcode and the " +
                            "drink already logged is corrected too, not logged twice."
                    } else {
                        "Open Food Facts is edited by the public and is often wrong about " +
                            "container size. Correct anything before it is saved; it will " +
                            "not ask again for this barcode."
                    },
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )

                Field("Name", name, KeyboardType.Text) { name = it }

                if (isDrink) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Field("Caffeine mg, whole drink", mg, modifier = Modifier.weight(1f)) { mg = it }
                        Field("Container ml", volume, modifier = Modifier.weight(1f)) { volume = it }
                    }
                    Text(
                        "Both are for the whole container, not per 100 ml. A can stating " +
                            "32 mg per 100 ml and holding 250 ml is 80 mg and 250 ml here.",
                        color = HealthyColors.Caffeine,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Field("Pack grams", pack, modifier = Modifier.weight(1f)) { pack = it }
                        Field("Serving grams", serving, modifier = Modifier.weight(1f)) { serving = it }
                    }
                }

                HorizontalDivider(color = HealthyColors.Rule, modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Nutrients per 100 " + if (isDrink) "ml" else "g",
                        color = HealthyColors.Paper,
                        fontSize = 13.sp,
                    )
                    Text(
                        if (expanded) "Hide" else "Show",
                        color = HealthyColors.Sleep,
                        fontSize = 12.sp,
                    )
                }

                AnimatedVisibility(expanded) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Field("kcal", kcal, modifier = Modifier.weight(1f)) { kcal = it }
                            Field("protein", protein, modifier = Modifier.weight(1f)) { protein = it }
                            Field("carbs", carbs, modifier = Modifier.weight(1f)) { carbs = it }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Field("sugar", sugar, modifier = Modifier.weight(1f)) { sugar = it }
                            Field("fat", fat, modifier = Modifier.weight(1f)) { fat = it }
                            Field("sat fat", satFat, modifier = Modifier.weight(1f)) { satFat = it }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Field("fibre", fibre, modifier = Modifier.weight(1f)) { fibre = it }
                            Field("salt", salt, modifier = Modifier.weight(1f)) { salt = it }
                        }
                        Text(
                            "An empty box means not recorded, which is not the same as zero.",
                            color = HealthyColors.Muted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = HealthyColors.Muted) }
                    TextButton(
                        onClick = {
                            onSave(
                                product.copy(
                                    name = name.ifBlank { product.name },
                                    mg = mg.toIntOrNull(),
                                    volumeMl = volume.toIntOrNull(),
                                    packGrams = pack.toIntOrNull(),
                                    servingGrams = serving.toIntOrNull(),
                                    kcal100 = kcal.toDoubleOrNull(),
                                    protein100 = protein.toDoubleOrNull(),
                                    carbs100 = carbs.toDoubleOrNull(),
                                    sugar100 = sugar.toDoubleOrNull(),
                                    fat100 = fat.toDoubleOrNull(),
                                    saturatedFat100 = satFat.toDoubleOrNull(),
                                    fibre100 = fibre.toDoubleOrNull(),
                                    salt100 = salt.toDoubleOrNull(),
                                )
                            )
                        },
                        enabled = name.isNotBlank(),
                    ) {
                        Text(
                            if (correcting) "Save correction" else "Save and log",
                            color = HealthyColors.Sleep,
                        )
                    }
                }
            }
        }
    }
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Decimal,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            onChange(
                if (keyboard == KeyboardType.Text) raw
                else raw.filter { it.isDigit() || it == '.' }
            )
        },
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = HealthyColors.Paper,
            unfocusedTextColor = HealthyColors.Paper,
            focusedBorderColor = HealthyColors.Sleep,
            unfocusedBorderColor = HealthyColors.Rule,
            focusedLabelColor = HealthyColors.Sleep,
            unfocusedLabelColor = HealthyColors.Muted,
            cursorColor = HealthyColors.Sleep,
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
    )
}
