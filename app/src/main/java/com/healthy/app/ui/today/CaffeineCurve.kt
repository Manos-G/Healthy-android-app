package com.healthy.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.healthy.app.core.Caffeine
import com.healthy.app.data.entity.Drink
import com.healthy.app.ui.theme.HealthyColors

/**
 * The caffeine level across the logical day (spec 5.1), drawn like the
 * prototype: a filled curve, a dashed line at the bedtime limit, a marker for
 * each dose, a bright line for now and a teal one for the target bedtime.
 */
@Composable
fun CaffeineCurve(
    state: TodayState,
    modifier: Modifier = Modifier,
) {
    val doses = state.allDoses
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val curve = state.curve
        if (curve.isEmpty() || state.dayEnd <= state.dayStart) return@Canvas

        val span = (state.dayEnd - state.dayStart).toDouble()
        fun yOf(v: Double): Float = (h - 6f - (v / state.curveMax).toFloat() * (h - 18f))
        fun xOfTime(t: Long): Float = ((t - state.dayStart) / span).toFloat() * w

        val line = Path()
        curve.forEachIndexed { i, v ->
            val x = i.toFloat() / (curve.size - 1) * w
            val y = yOf(v)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }

        val area = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        // The bedtime limit, so the curve can be read against it at a glance.
        val limitY = yOf(state.limitMg.toDouble())
        drawLine(
            color = HealthyColors.Rule,
            start = Offset(0f, limitY),
            end = Offset(w, limitY),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)),
        )

        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                listOf(HealthyColors.Caffeine.copy(alpha = 0.38f), HealthyColors.Caffeine.copy(alpha = 0f)),
            ),
        )
        drawPath(line, color = HealthyColors.Caffeine, style = Stroke(width = 2.5f))

        val bedtimeX = xOfTime(state.bedtimeMillis)
        if (bedtimeX in 0f..w) {
            drawLine(
                color = HealthyColors.Sleep,
                start = Offset(bedtimeX, 4f),
                end = Offset(bedtimeX, h - 4f),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 3f)),
            )
        }

        val nowX = xOfTime(state.nowMillis).coerceIn(0f, w)
        drawLine(
            color = HealthyColors.Paper.copy(alpha = 0.45f),
            start = Offset(nowX, 0f),
            end = Offset(nowX, h),
            strokeWidth = 1.5f,
        )

        doses.filter { it.timestamp in state.dayStart..state.dayEnd }.forEach { dose ->
            val level = Caffeine.levelAt(doses, dose.timestamp, state.halfLifeHours)
            drawCircle(
                color = HealthyColors.Caffeine,
                radius = 4f,
                center = Offset(xOfTime(dose.timestamp), yOf(level)),
            )
        }
    }
}
