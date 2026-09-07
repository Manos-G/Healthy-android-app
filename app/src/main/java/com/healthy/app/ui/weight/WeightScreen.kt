package com.healthy.app.ui.weight

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthy.app.ui.theme.HealthyColors
import kotlin.math.abs

@Composable
fun WeightScreen(
    modifier: Modifier = Modifier,
    vm: WeightViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf<Double?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        item {
            SectionCard {
                Text(
                    if (state.todayLogged) "Today's weight" else "Weigh in",
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    state.latest?.let { "Last reading ${"%.1f".format(it.weightKg)} kg on ${it.date}." }
                        ?: "No readings yet. The wheel starts at 70 kg.",
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )

                WeightWheel(
                    previousKg = state.latest?.weightKg,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { picked = it }

                Button(
                    onClick = {
                        picked?.let { kg ->
                            vm.save(kg) { wrote ->
                                status = if (wrote) {
                                    "Saved ${"%.1f".format(kg)} kg, and written to Health Connect."
                                } else {
                                    "Saved ${"%.1f".format(kg)} kg. Health Connect did not accept the write."
                                }
                            }
                        }
                    },
                    enabled = picked != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HealthyColors.Caffeine,
                        contentColor = HealthyColors.Ground,
                        disabledContainerColor = HealthyColors.Raised2,
                        disabledContentColor = HealthyColors.Muted,
                    ),
                ) {
                    Text(
                        picked?.let { "Save ${"%.1f".format(it)} kg" } ?: "Move the wheel",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (status != null) {
                    Text(
                        status.orEmpty(),
                        color = HealthyColors.Sleep,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item { TrendCard(state) }
    }
}

@Composable
private fun TrendCard(state: WeightState) {
    SectionCard {
        Text("Trend", color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

        val change = state.changeOver30Days
        Text(
            when {
                state.points.size < 2 -> "Two readings are needed before a trend means anything."
                change == null -> "—"
                abs(change) < 0.05 -> "Level across the last 30 days."
                change < 0 -> "Down ${"%.1f".format(abs(change))} kg across the last 30 days."
                else -> "Up ${"%.1f".format(change)} kg across the last 30 days."
            },
            color = if (state.points.size < 2) HealthyColors.Muted else HealthyColors.Sleep,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (state.points.size >= 2) {
            Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp)) {
                val points = state.points
                val w = size.width
                val h = size.height
                val values = points.flatMap { listOf(it.weightKg, it.trendKg) }
                val lo = values.min() - 0.3
                val hi = values.max() + 0.3
                val span = (hi - lo).coerceAtLeast(0.1)
                val step = if (points.size > 1) w / (points.size - 1) else w

                fun y(v: Double) = (h - ((v - lo) / span).toFloat() * (h - 8f) - 4f)

                // The daily readings are the quiet element: they are noise
                // from water, food and glycogen (spec 8.2).
                points.forEachIndexed { i, p ->
                    drawCircle(
                        color = HealthyColors.Muted.copy(alpha = 0.55f),
                        radius = 2.5f,
                        center = Offset(i * step, y(p.weightKg)),
                    )
                }
                // The trend is the signal, so it is the strong element.
                val path = Path()
                points.forEachIndexed { i, p ->
                    val x = i * step
                    val yy = y(p.trendKg)
                    if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
                }
                drawPath(path, color = HealthyColors.Sleep, style = Stroke(width = 3f))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(state.points.first().date, color = HealthyColors.Muted, fontSize = 10.sp)
                Text(state.points.last().date, color = HealthyColors.Muted, fontSize = 10.sp)
            }
        }

        Text(
            "Weigh at the same time each day, after you wake and before you eat. " +
                "A different time gives a different number for the same body.",
            color = HealthyColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
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
