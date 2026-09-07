package com.healthy.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthy.app.ui.theme.HealthyColors

/**
 * A 1 to 5 rating (spec 5.2), used for alertness in the morning and for the
 * 15:00 energy on the Today screen.
 *
 * Tapping the selected value clears it, so a mis-tap is recoverable without a
 * separate control. A null rating is a real state: the comparison table counts
 * a night with one rating (spec 5.2).
 */
@Composable
fun RatingScale(
    value: Int?,
    lowLabel: String,
    highLabel: String,
    modifier: Modifier = Modifier,
    onPick: (Int?) -> Unit,
) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..5).forEach { n ->
                val selected = value == n
                OutlinedButton(
                    onClick = { onPick(if (selected) null else n) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selected) HealthyColors.Sleep else HealthyColors.Rule,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) {
                            HealthyColors.Sleep.copy(alpha = 0.18f)
                        } else {
                            HealthyColors.Raised2
                        },
                        contentColor = if (selected) HealthyColors.Sleep else HealthyColors.Paper,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                ) {
                    Text(
                        "$n",
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(lowLabel, color = HealthyColors.Muted, fontSize = 11.sp)
            Text(highLabel, color = HealthyColors.Muted, fontSize = 11.sp)
        }
    }
}
