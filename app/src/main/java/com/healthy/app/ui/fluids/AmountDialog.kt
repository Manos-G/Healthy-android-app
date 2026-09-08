package com.healthy.app.ui.fluids

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.healthy.app.core.Alcohol
import com.healthy.app.core.Beverage
import com.healthy.app.ui.theme.HealthyColors

/**
 * How much of the chosen drink, and what that works out to.
 *
 * The wheel opens on the drink's usual size, so the common case is still one
 * tap and a confirm. What changes as it moves is stated underneath — caffeine,
 * fluid and units — because a number that moves silently teaches nothing.
 */
@Composable
fun AmountDialog(
    beverage: Beverage,
    mlPerUnit: Double = Alcohol.DEFAULT_ML_PER_UNIT,
    startMl: Int = beverage.defaultMl,
    onConfirm: (amount: Int, minutesAgo: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember(beverage, startMl) { mutableIntStateOf(startMl) }
    var minutesAgo by remember(beverage) { mutableIntStateOf(0) }
    val now = remember(beverage) { System.currentTimeMillis() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    beverage.name,
                    color = HealthyColors.Paper,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    strengthLine(beverage),
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )

                VolumeWheel(
                    startMl = startMl,
                    unit = beverage.unit,
                    modifier = Modifier.padding(top = 14.dp),
                ) { amount = it }

                Text(
                    resultLine(beverage, amount, mlPerUnit),
                    color = HealthyColors.Sleep,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )

                com.healthy.app.ui.components.WhenPicker(
                    minutesAgo = minutesAgo,
                    now = now,
                    modifier = Modifier.padding(top = 14.dp),
                ) { minutesAgo = it }

                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = HealthyColors.Muted) }
                    TextButton(onClick = { onConfirm(amount, minutesAgo) }) {
                        Text("Log it", color = HealthyColors.Sleep)
                    }
                }
            }
        }
    }
}

private fun strengthLine(beverage: Beverage): String = buildString {
    val per = if (beverage.solid) "100 g" else "100 ml"
    if (beverage.caffeinePer100 > 0) {
        append("${beverage.caffeinePer100.trim()} mg caffeine per $per")
    }
    if (beverage.abv > 0) {
        if (isNotEmpty()) append(" · ")
        append("${beverage.abv.trim()}% alcohol")
    }
    if (isEmpty()) append("No caffeine, no alcohol")
}

private fun resultLine(beverage: Beverage, amount: Int, mlPerUnit: Double): String {
    val parts = buildList {
        beverage.fluidMlFor(amount).takeIf { it > 0 }?.let { add("$it ml of fluid") }
        beverage.caffeineMgFor(amount).takeIf { it > 0 }?.let { add("$it mg of caffeine") }
        Alcohol.units(amount, beverage.abv, mlPerUnit).takeIf { it > 0 }
            ?.let { add("${"%.1f".format(it)} units of alcohol") }
    }
    if (parts.isEmpty()) return "$amount ${beverage.unit}, counting towards nothing."
    val joined = when (parts.size) {
        1 -> parts[0]
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }
    return "Logs $joined."
}

private fun Double.trim(): String =
    if (this == toLong().toDouble()) toLong().toString() else "%.1f".format(this)
