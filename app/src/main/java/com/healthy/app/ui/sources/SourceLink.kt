package com.healthy.app.ui.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.healthy.app.analysis.Sources
import com.healthy.app.ui.theme.HealthyColors

/**
 * A link beside a number, to the entry that explains it (spec 17).
 *
 * The spec's reasoning is the whole point: a user who can see the basis of a
 * number can judge the number. A figure with no visible provenance has to be
 * taken on trust, and several of these are rules of thumb rather than
 * measurements of this person.
 *
 * It is a small mark rather than a button, because it sits beside a value and
 * must not compete with it.
 */
@Composable
fun SourceLink(
    item: String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val entry = remember(item) { Sources.ENTRIES.firstOrNull { it.item == item } }
    if (entry == null) return

    Surface(
        shape = CircleShape,
        color = HealthyColors.Raised2,
        modifier = modifier.clickable { open = true },
    ) {
        Text(
            "?",
            color = HealthyColors.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }

    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            entry.item,
                            color = HealthyColors.Paper,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            entry.value,
                            color = HealthyColors.Caffeine,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        entry.basis,
                        color = HealthyColors.Paper,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    HorizontalDivider(
                        color = HealthyColors.Rule,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    Text(
                        "What this does not tell you",
                        color = HealthyColors.Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        entry.limitation,
                        color = HealthyColors.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { open = false }) {
                            Text("Close", color = HealthyColors.Sleep)
                        }
                    }
                }
            }
        }
    }
}
