package com.healthy.app.ui.weight

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import com.healthy.app.analysis.WeightTrend
import com.healthy.app.ui.theme.HealthyColors
import kotlin.math.roundToInt

/**
 * The weight carousel (spec 8.1).
 *
 * Never an empty field: it opens on the previous reading and spans three
 * kilograms either side, because a body does not move further than that in a
 * day and a short carousel needs one movement of the thumb.
 *
 * It runs horizontally on purpose. A vertical wheel inside a vertically
 * scrolling page loses: Compose hands the drag to the inner scrollable, so
 * scrolling the page past the card silently changed the user's weight. A
 * horizontal drag cannot be confused with a vertical one.
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

    // The centred item is the selection. Content padding of two cells means
    // the first visible item already sits under the marker, so no extra offset
    // is added — doing that once put the reading two cells away from the lines.
    val selectedIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, values.lastIndex) }
    }
    val selected = values.getOrNull(selectedIndex)

    // One source of truth: whatever the marker shows is what gets reported, so
    // the button can never disagree with the carousel.
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                values.getOrNull(index.coerceIn(0, values.lastIndex))?.let(onValueChange)
            }
    }

    Column(modifier) {
        Text(
            selected?.let { "%.1f".format(it) } ?: "—",
            color = HealthyColors.Paper,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "kilograms",
            color = HealthyColors.Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Box(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            LazyRow(
                state = listState,
                flingBehavior = fling,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(horizontal = CELL_WIDTH.dp * VISIBLE_EITHER_SIDE),
            ) {
                items(values.size) { index ->
                    val value = values[index]
                    val distance = kotlin.math.abs(index - selectedIndex)
                    Column(
                        modifier = Modifier.width(CELL_WIDTH.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // A taller mark every whole kilogram, so the scale reads
                        // as a ruler rather than a row of numbers.
                        val whole = kotlin.math.abs(value - Math.round(value)) < 0.01
                        VerticalDivider(
                            modifier = Modifier.height(if (whole) 22.dp else 12.dp),
                            color = if (distance == 0) HealthyColors.Sleep else HealthyColors.Rule,
                        )
                        if (whole) {
                            Text(
                                "${value.roundToInt()}",
                                color = HealthyColors.Muted,
                                fontSize = 10.sp,
                                modifier = Modifier.alpha(if (distance < 25) 1f else 0f),
                            )
                        }
                    }
                }
            }
            // The marker the selection sits under.
            VerticalDivider(
                modifier = Modifier.height(30.dp),
                thickness = 2.dp,
                color = HealthyColors.Caffeine,
            )
        }
    }
}

private const val CELL_WIDTH = 14
private const val VISIBLE_EITHER_SIDE = 11
