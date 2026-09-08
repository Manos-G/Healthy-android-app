package com.healthy.app.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthy.app.analysis.Nutrition
import com.healthy.app.analysis.NutrientTargets
import com.healthy.app.ui.sources.SourceLink
import com.healthy.app.ui.theme.HealthyColors

/**
 * Nutrient targets (spec 16.4, 16.5).
 *
 * A bar and a number for each, with the seven-day mean beside today. No bar is
 * ever red, nothing is sent when a ceiling is passed, and there is no streak:
 * a ceiling exceeded is information, not a verdict.
 */
@Composable
fun NutrientCard(
    today: Nutrition.Totals,
    recentDays: List<Nutrition.Totals>,
    bodyWeightKg: Double?,
    energyTargetKcal: Int?,
    modifier: Modifier = Modifier,
) {
    val targets = NutrientTargets.defaults(bodyWeightKg, energyTargetKcal)
    val progress = NutrientTargets.progress(targets, today, recentDays)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Nutrients", color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (bodyWeightKg == null || energyTargetKcal == null) {
                    "Log a weight and set an energy target to see all of these. " +
                        "Two of them are shares of those numbers."
                } else {
                    "Today against the seven-day mean. One day means little; a week means something."
                },
                color = HealthyColors.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            progress.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.target.name, color = HealthyColors.Paper, fontSize = 12.sp)
                    SourceLink(
                        item = sourceItemFor(row.target.name),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Text(
                        if (row.target.kind == NutrientTargets.Kind.Floor) "  at least" else "  at most",
                        color = HealthyColors.Muted,
                        fontSize = 10.sp,
                    )
                    Text(
                        " ${"%.0f".format(row.target.amount)} ${row.target.unit}",
                        color = HealthyColors.Muted,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${"%.0f".format(row.today)} ${row.target.unit}",
                        // Met reads teal, unmet reads plain. Never red: spec
                        // 16.5 forbids colouring a bar as a failure.
                        color = if (row.met) HealthyColors.Sleep else HealthyColors.Paper,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                LinearProgressIndicator(
                    progress = { (row.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp),
                    color = if (row.met) HealthyColors.Sleep else HealthyColors.Muted,
                    trackColor = HealthyColors.Raised2,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(
                    "${row.percent}% of target · seven-day mean ${"%.0f".format(row.weekMean)} ${row.target.unit}",
                    color = HealthyColors.Muted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Text(
                "No notification is sent about any of these, and passing a ceiling is " +
                    "not marked as a failure.",
                color = HealthyColors.Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun sourceItemFor(name: String): String = when (name) {
    "Protein" -> "Protein target"
    "Fibre" -> "Fibre target"
    "Sugar", "Saturated fat" -> "Sugar and saturated fat ceilings"
    else -> "Salt ceiling"
}
