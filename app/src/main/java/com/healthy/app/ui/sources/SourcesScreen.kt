package com.healthy.app.ui.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthy.app.analysis.Sources
import com.healthy.app.ui.theme.HealthyColors

/**
 * Where every number comes from (spec 17).
 *
 * The point is stated in the spec: a user who can see the basis of a number
 * can judge the number. So each entry gives the value, where it came from, and
 * — the part that matters most — what the app does not know about it.
 */
@Composable
fun SourcesScreen(
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        item {
            Column {
                Text(
                    "Where these numbers come from",
                    color = HealthyColors.Paper,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Every calculation in this app rests on a value chosen for a " +
                        "reason. Here are the reasons, and the limits.",
                    color = HealthyColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        items(Sources.ENTRIES.size) { index ->
            val entry = Sources.ENTRIES[index]
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        entry.item,
                        color = HealthyColors.Paper,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        entry.value,
                        color = HealthyColors.Caffeine,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    entry.basis,
                    color = HealthyColors.Paper,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                HorizontalDivider(
                    color = HealthyColors.Rule,
                    modifier = Modifier.padding(vertical = 8.dp),
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
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        item {
            SectionCard {
                Text(
                    "The hypnogram",
                    color = HealthyColors.Sleep,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    Sources.HYPNOGRAM_DISCLAIMER,
                    color = HealthyColors.Paper,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            SectionCard {
                Text(
                    "The day boundary",
                    color = HealthyColors.Sleep,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    Sources.DAY_BOUNDARY_NOTE,
                    color = HealthyColors.Paper,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (onClose != null) {
            item {
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HealthyColors.Raised2,
                        contentColor = HealthyColors.Paper,
                    ),
                ) {
                    Text("Close")
                }
            }
        }
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
