package com.healthy.app.ui.fluids

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthy.app.ui.theme.HealthyColors
import kotlin.math.abs

/**
 * How much of it (spec 7, extended).
 *
 * Built on the weight carousel, which took three attempts to get right, so its
 * lessons are carried over rather than rediscovered: it runs horizontally
 * because a vertical wheel inside a scrolling page steals the page's drag; the
 * side padding is measured rather than guessed, or the marker points at one
 * value while the caller is handed another; and every piece of remembered
 * state is keyed on the value list, or a rebuilt wheel keeps reporting an
 * index from the list it had before.
 *
 * The step is coarse on purpose. Nobody pours 237 ml, and a 1 ml step would
 * need three thumb-lengths to cross a pint.
 */
@Composable
fun VolumeWheel(
    startMl: Int,
    unit: String,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit,
) {
    // The drink's own volume is spliced into the scale, so a 568 ml pint opens
    // on 568 rather than on the nearest round number the ruler happens to have.
    val values = remember(startMl) {
        if (startMl > 0 && startMl !in VOLUMES) (VOLUMES + startMl).sorted() else VOLUMES
    }
    val startIndex = remember(values, startMl) {
        values.indexOfFirst { it >= startMl }.takeIf { it >= 0 } ?: values.lastIndex
    }

    val listState = remember(values, startIndex) { LazyListState(startIndex, 0) }
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val cellPx = with(LocalDensity.current) { CELL_WIDTH.dp.toPx() }

    val selectedIndex by remember(listState, values) {
        derivedStateOf {
            val nudge = if (listState.firstVisibleItemScrollOffset > cellPx / 2) 1 else 0
            (listState.firstVisibleItemIndex + nudge).coerceIn(0, values.lastIndex)
        }
    }
    val selected = values.getOrNull(selectedIndex)

    LaunchedEffect(listState, values) {
        snapshotFlow { selectedIndex }
            .collect { index -> values.getOrNull(index)?.let(onValueChange) }
    }

    Column(modifier) {
        Text(
            selected?.toString() ?: "—",
            color = HealthyColors.Paper,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (unit == "g") "grams" else "millilitres",
            color = HealthyColors.Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(top = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val sidePadding = (maxWidth - CELL_WIDTH.dp) / 2
            LazyRow(
                state = listState,
                flingBehavior = fling,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(horizontal = sidePadding),
            ) {
                items(values.size) { index ->
                    val value = values[index]
                    val distance = abs(index - selectedIndex)
                    // A taller mark and a number every hundred, so the scale
                    // reads as a ruler rather than a row of digits.
                    val labelled = value % 100 == 0
                    Column(
                        modifier = Modifier.width(CELL_WIDTH.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        VerticalDivider(
                            modifier = Modifier.height(if (labelled) 20.dp else 11.dp),
                            color = if (distance == 0) HealthyColors.Sleep else HealthyColors.Rule,
                        )
                        if (labelled) {
                            Text(
                                "$value",
                                color = HealthyColors.Muted,
                                fontSize = 9.sp,
                                modifier = Modifier.alpha(if (distance < 20) 1f else 0f),
                            )
                        }
                    }
                }
            }
            VerticalDivider(
                modifier = Modifier.height(28.dp),
                thickness = 2.dp,
                color = HealthyColors.Caffeine,
            )
        }
    }
}

/**
 * The volumes offered, fine where drinks are small and coarse where they are
 * large: 5 ml steps through a shot of ouzo, 50 ml steps past half a litre.
 */
private val VOLUMES: List<Int> = buildList {
    (5..100 step 5).forEach { add(it) }
    (110..500 step 10).forEach { add(it) }
    (525..1000 step 25).forEach { add(it) }
    (1050..2000 step 50).forEach { add(it) }
}

private const val CELL_WIDTH = 14
