package com.healthy.app.ui.hypnogram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthy.app.analysis.Hypnogram
import com.healthy.app.data.entity.StageBlock
import com.healthy.app.ui.theme.HealthyColors

/**
 * The heart rate hypnogram (spec 18.4).
 *
 * The smoothed heart rate across the night with a dot on each trough, and the
 * watch's stage blocks as a thin strip below on the same time axis. Showing
 * both is the point: a deep block from the watch with no fall in the heart
 * rate beneath it is the watch guessing, and the user can see that for
 * themselves rather than being told.
 */
@Composable
fun HypnogramChart(
    result: Hypnogram.Result,
    stageBlocks: List<StageBlock>,
    sleepStart: Long,
    sleepEnd: Long,
    modifier: Modifier = Modifier,
) {
    when (result) {
        is Hypnogram.Result.TooSparse -> {
            Column(modifier) {
                Text(
                    "Data too sparse",
                    color = HealthyColors.Paper,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "This night has ${result.samples} heart rate samples and needs " +
                        "${result.required}. Nothing is drawn rather than a curve " +
                        "invented between distant readings.",
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        is Hypnogram.Result.Found -> Column(modifier) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Figure("Cycles", result.cycleCount.toString())
                Figure(
                    "Cycle length",
                    result.cycleLengthMinutes?.let { "$it min" } ?: "—",
                )
                Figure("Lowest", "${result.lowestBpm} bpm")
            }

            Canvas(
                Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp)
            ) {
                val samples = result.smoothed
                if (samples.isEmpty() || sleepEnd <= sleepStart) return@Canvas
                val w = size.width
                val curveHeight = size.height - STRIP_HEIGHT - STRIP_GAP
                val span = (sleepEnd - sleepStart).toDouble()

                fun x(t: Long) = ((t - sleepStart) / span).toFloat() * w
                val lo = samples.minOf { it.bpm } - 2
                val hi = samples.maxOf { it.bpm } + 2
                fun y(bpm: Int) =
                    curveHeight - ((bpm - lo).toFloat() / (hi - lo)) * (curveHeight - 6f) - 3f

                // The baseline the minima are judged against.
                val baselineY = y(result.baselineBpm)
                drawLine(
                    color = HealthyColors.Rule,
                    start = Offset(0f, baselineY),
                    end = Offset(w, baselineY),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)),
                )

                val path = Path()
                samples.forEachIndexed { i, s ->
                    val px = x(s.timeMillis)
                    val py = y(s.bpm)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, color = HealthyColors.Warn, style = Stroke(width = 2.5f))

                result.minima.forEach { m ->
                    drawCircle(
                        color = HealthyColors.Sleep,
                        radius = 5f,
                        center = Offset(x(m.timeMillis), y(m.bpm)),
                    )
                }

                // The watch's own answer, on the same axis, for comparison.
                val stripTop = curveHeight + STRIP_GAP
                stageBlocks.forEach { block ->
                    val left = x(block.startTime)
                    val right = x(block.endTime)
                    drawRect(
                        color = block.type.stageColour(),
                        topLeft = Offset(left, stripTop),
                        size = Size((right - left).coerceAtLeast(1f), STRIP_HEIGHT),
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Legend("Heart rate", HealthyColors.Warn)
                Legend("Cycle low", HealthyColors.Sleep)
                Legend("Deep, from the watch", StageBlock.DEEP.stageColour())
            }

            Text(
                "A deep block from the watch with no fall in the heart rate " +
                    "beneath it is the watch guessing.",
                color = HealthyColors.Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, color = HealthyColors.Muted, fontSize = 11.sp)
        Text(value, color = HealthyColors.Paper, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Legend(label: String, colour: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.height(8.dp).padding(end = 4.dp)) { drawCircle(colour, radius = 4f) }
        Text(label, color = colour, fontSize = 10.sp)
    }
}

private fun String.stageColour(): Color = when (this) {
    StageBlock.DEEP -> HealthyColors.Sleep
    StageBlock.REM -> HealthyColors.Caffeine
    StageBlock.AWAKE -> HealthyColors.Warn
    else -> HealthyColors.Rule
}

private const val STRIP_HEIGHT = 14f
private const val STRIP_GAP = 8f
