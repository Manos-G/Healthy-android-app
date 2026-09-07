package com.healthy.app.ui.weight

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthy.app.analysis.WeightTrend
import com.healthy.app.ui.theme.HealthyColors
import kotlin.math.roundToInt

/**
 * The weight wheel (spec 8.1).
 *
 * Never an empty field. It starts at the previous reading and spans three
 * kilograms either side, because a body does not move further than that in a
 * day and a short wheel needs one thumb movement rather than many.
 */
@Composable
fun WeightWheel(
    previousKg: Double?,
    modifier: Modifier = Modifier,
    onValueChange: (Double) -> Unit,
) {
    val range = remember(previousKg) { WeightTrend.wheelRange(previousKg) }
    val values = remember(range) {
        val steps = ((range.endInclusive - range.start) / WeightTrend.STEP_KG).roundToInt()
        (0..steps).map { range.start + it * WeightTrend.STEP_KG }
    }
    val startIndex = remember(values, previousKg) {
        val target = WeightTrend.wheelStart(previousKg)
        values.indexOfFirst { it >= target - 0.001 }.coerceAtLeast(0)
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    // The centred row is the selection, so the reading follows the scroll
    // rather than needing a separate confirm step.
    val selected by remember {
        derivedStateOf {
            values.getOrNull(listState.firstVisibleItemIndex + CENTRE_OFFSET)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { selected }.collect { value -> value?.let(onValueChange) }
    }

    Box(modifier.height(ROW_HEIGHT.dp * VISIBLE_ROWS), contentAlignment = Alignment.Center) {
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = ROW_HEIGHT.dp * CENTRE_OFFSET),
        ) {
            items(values.size) { index ->
                val value = values[index]
                val isSelected = index == listState.firstVisibleItemIndex + CENTRE_OFFSET
                Text(
                    text = "%.1f".format(value),
                    color = if (isSelected) HealthyColors.Paper else HealthyColors.Muted,
                    fontSize = if (isSelected) 28.sp else 18.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        // Two rules mark the selected row, so the centre is unambiguous.
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = HealthyColors.Sleep.copy(alpha = 0.5f))
            androidx.compose.foundation.layout.Spacer(Modifier.height(ROW_HEIGHT.dp))
            HorizontalDivider(color = HealthyColors.Sleep.copy(alpha = 0.5f))
        }
    }
}

private const val ROW_HEIGHT = 44
private const val VISIBLE_ROWS = 5
private const val CENTRE_OFFSET = 2
