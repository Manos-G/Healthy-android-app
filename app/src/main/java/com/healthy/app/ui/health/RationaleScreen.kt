package com.healthy.app.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthy.app.health.HealthConnect
import com.healthy.app.ui.theme.HealthyColors

/**
 * The rationale screen (spec 3.2). It must say which data the app reads and
 * why, in plain words.
 *
 * It also says what the app does *not* do, because that is the part a user
 * cannot verify from a permission dialog: nothing leaves the phone, there is
 * no account, and the data is readable and deletable by the user at any time.
 */
@Composable
fun RationaleScreen(
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        // The activity draws edge to edge, so the content must keep clear of
        // the status and navigation bars itself.
        modifier = modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        item {
            Column {
                Text(
                    "What Healthy reads",
                    color = HealthyColors.Paper,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Healthy compares the days you feel good against the days you do not, " +
                        "and shows which inputs differ. It needs the measurements your watch " +
                        "already writes to Health Connect.",
                    color = HealthyColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        items(HealthConnect.EXPLANATIONS.size) { index ->
            val item = HealthConnect.EXPLANATIONS[index]
            SectionCard {
                Text(
                    item.title,
                    color = HealthyColors.Sleep,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    item.why,
                    color = HealthyColors.Paper,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item {
            SectionCard {
                Text(
                    "Later, and only when you use the feature",
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Healthy asks to write weight and fluid when you first log them, so other " +
                        "apps can read what you record here. It asks for menstrual data only if " +
                        "you turn that tracking on. It never asks for anything it is not using.",
                    color = HealthyColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item {
            SectionCard {
                Text(
                    "What Healthy does not do",
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(Modifier.padding(top = 8.dp)) {
                    listOf(
                        "Nothing leaves your phone. There is no account and no sync.",
                        "No analytics and no crash reporting.",
                        "The only network request the app ever makes is a barcode lookup you " +
                            "start yourself, and only when the code is not already stored.",
                        "You can export everything, and you can delete it.",
                    ).forEach { line ->
                        Text(
                            "— $line",
                            color = HealthyColors.Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                HorizontalDivider(
                    color = HealthyColors.Rule,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                Text(
                    "You can withdraw any of this in Health Connect at any time. " +
                        "Healthy keeps working; the fields it cannot fill stay empty rather " +
                        "than showing a zero.",
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
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
