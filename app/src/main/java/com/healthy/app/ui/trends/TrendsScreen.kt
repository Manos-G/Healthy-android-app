package com.healthy.app.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthy.app.analysis.Trends
import com.healthy.app.ui.theme.HealthyColors
import kotlin.math.abs

@Composable
fun TrendsScreen(
    modifier: Modifier = Modifier,
    vm: TrendsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        item { MeansCard(state) }
        item { SleepChartCard(state) }
        item { CaffeineChartCard(state) }
        item { ComparisonCard(state) }
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
private fun Title(text: String, subtitle: String? = null) {
    Text(text, color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    if (subtitle != null) {
        Text(
            subtitle,
            color = HealthyColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun Double?.fmt(digits: Int = 1): String =
    this?.let { "%.${digits}f".format(it) } ?: "—"

@Composable
private fun MeansCard(state: TrendsState) {
    SectionCard {
        Title("Your averages", "Across ${state.nightCount} logged night${if (state.nightCount == 1) "" else "s"}.")
        Column(Modifier.padding(top = 10.dp)) {
            MeanRow("Sleep", state.means.sleepHours.fmt(1), "h")
            MeanRow("Alertness", state.means.alertness.fmt(1), "of 5")
            MeanRow("Energy at 15:00", state.means.energy.fmt(1), "of 5")
            MeanRow("Resting heart rate", state.means.restingHr.fmt(0), "bpm")
            MeanRow("Caffeine", state.means.caffeineMg.fmt(0), "mg")
            MeanRow("Wake-ups", state.means.wakeups.fmt(1), "")
        }
    }
}

@Composable
private fun MeanRow(label: String, value: String, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, color = HealthyColors.Muted, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = if (value == "—") HealthyColors.Muted else HealthyColors.Paper,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (unit.isNotEmpty()) {
                Text(" $unit", color = HealthyColors.Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SleepChartCard(state: TrendsState) {
    SectionCard {
        Title("Sleep and alertness", "The last 30 nights, most recent on the right.")
        Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 12.dp)) {
            val points = state.points
            if (points.isEmpty()) return@Canvas
            val w = size.width
            val h = size.height
            val step = if (points.size > 1) w / (points.size - 1) else w

            // Sleep hours, scaled to a fixed 0..12 h so nights are comparable
            // between visits rather than rescaling with the best night.
            drawSeries(points.map { it.sleepHours }, 12.0, HealthyColors.Sleep, step, h)
            // Alertness 1..5 on the same box; the shapes are what matter here,
            // not a shared unit.
            drawSeries(points.map { it.alertness?.toDouble() }, 5.0, HealthyColors.Caffeine, step, h)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend("Hours slept", HealthyColors.Sleep)
            Legend("Alertness", HealthyColors.Caffeine)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    values: List<Double?>,
    max: Double,
    colour: androidx.compose.ui.graphics.Color,
    step: Float,
    h: Float,
) {
    val path = Path()
    var started = false
    values.forEachIndexed { i, value ->
        if (value == null) {
            // A gap is a gap. Joining across a night with no data would draw a
            // line the user never lived.
            started = false
            return@forEachIndexed
        }
        val x = i * step
        val y = h - (value / max).coerceIn(0.0, 1.0).toFloat() * (h - 6f) - 3f
        if (!started) {
            path.moveTo(x, y)
            started = true
        } else {
            path.lineTo(x, y)
        }
        drawCircle(colour, radius = 2.5f, center = Offset(x, y))
    }
    drawPath(path, color = colour, style = Stroke(width = 2f))
}

@Composable
private fun Legend(label: String, colour: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.height(8.dp).padding(end = 4.dp)) {
            drawCircle(colour, radius = 4f)
        }
        Text(label, color = colour, fontSize = 11.sp)
    }
}

@Composable
private fun CaffeineChartCard(state: TrendsState) {
    SectionCard {
        Title(
            "Caffeine each day",
            "A high day total matters less than a high level at bedtime.",
        )
        Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 12.dp)) {
            val points = state.points
            if (points.isEmpty()) return@Canvas
            val w = size.width
            val h = size.height
            val step = if (points.size > 1) w / (points.size - 1) else w
            val max = maxOf(
                points.maxOfOrNull { it.caffeineMg }?.toDouble() ?: 0.0,
                state.limitMg * 2.0,
                100.0,
            )

            val limitY = h - (state.limitMg / max).toFloat() * (h - 6f) - 3f
            drawLine(
                color = HealthyColors.Rule,
                start = Offset(0f, limitY),
                end = Offset(w, limitY),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)),
            )

            drawSeries(points.map { it.caffeineMg.toDouble() }, max, HealthyColors.Caffeine, step, h)
            drawSeries(points.map { it.bedtimeMg.toDouble() }, max, HealthyColors.Warn, step, h)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend("Day total", HealthyColors.Caffeine)
            Legend("At bedtime", HealthyColors.Warn)
            Legend("Your limit", HealthyColors.Rule)
        }
    }
}

@Composable
private fun ComparisonCard(state: TrendsState) {
    SectionCard {
        Title(
            "Good days against bad days",
            "Your nights sorted by how you felt. The best third sits against the worst third.",
        )

        if (state.comparison.isEmpty()) {
            Text(
                "${state.ratedCount} of ${Trends.MINIMUM_RATED_NIGHTS} rated nights.",
                color = HealthyColors.Caffeine,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Rate ${Trends.MINIMUM_RATED_NIGHTS - state.ratedCount} more and this table opens. " +
                    "Fewer than that cannot tell a pattern from a coincidence.",
                color = HealthyColors.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            return@SectionCard
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        ) {
            Text("", modifier = Modifier.weight(1.6f))
            Text("best", color = HealthyColors.Sleep, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("worst", color = HealthyColors.Warn, fontSize = 11.sp, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = HealthyColors.Rule)

        state.comparison.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1.6f)) {
                    Text(row.label, color = HealthyColors.Paper, fontSize = 12.sp)
                    if (row.unit.isNotEmpty()) {
                        Text(row.unit, color = HealthyColors.Muted, fontSize = 10.sp)
                    }
                }
                Text(
                    row.best.fmt(1),
                    color = HealthyColors.Sleep,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    row.worst.fmt(1),
                    color = HealthyColors.Warn,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        HorizontalDivider(color = HealthyColors.Rule, modifier = Modifier.padding(top = 6.dp))
        Text(
            "A difference that stays as the nights increase is a real effect. " +
                "A difference from a few nights is noise.",
            color = HealthyColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
