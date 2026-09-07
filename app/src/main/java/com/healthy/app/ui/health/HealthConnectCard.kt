package com.healthy.app.ui.health

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthy.app.health.HealthConnect
import com.healthy.app.health.PermissionsRationaleActivity
import com.healthy.app.ui.theme.HealthyColors

/**
 * The Health Connect state on the morning screen (spec 3.1).
 *
 * When Health Connect is missing or permission is refused, this card explains
 * the situation and the manual form below it keeps working. The spec is
 * explicit that an unavailable provider hides the sync button rather than
 * blocking entry.
 */
@Composable
fun HealthConnectCard(
    state: HealthConnectUiState,
    onGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }

    // Re-read the granted set either way: a partial grant is a real outcome,
    // and the card must show what actually happened rather than what was asked.
    val permissionLauncher = rememberLauncherForActivityResult(
        HealthConnect.requestContract()
    ) { onGranted() }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Health Connect",
                color = HealthyColors.Paper,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                state.message,
                color = if (state.isGranted) HealthyColors.Sleep else HealthyColors.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (state.canRequest) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { permissionLauncher.launch(HealthConnect.READ_PERMISSIONS) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HealthyColors.Sleep,
                            contentColor = HealthyColors.Ground,
                        ),
                    ) {
                        Text("Connect", fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = { showRationale = true }) {
                        Text("What is read?", color = HealthyColors.Muted, fontSize = 13.sp)
                    }
                }
            }

            if (state.isGranted) {
                Text(
                    "Use Sync below to fill the sensor fields for the selected night.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }

    // Starting an activity is a side effect, so it must not run during
    // composition; a recomposition would fire it again.
    LaunchedEffect(showRationale) {
        if (showRationale) {
            showRationale = false
            context.startActivity(Intent(context, PermissionsRationaleActivity::class.java))
        }
    }
}

data class HealthConnectUiState(
    val availability: HealthConnect.Availability,
    val isGranted: Boolean,
) {
    val canRequest: Boolean
        get() = availability == HealthConnect.Availability.Available && !isGranted

    val message: String
        get() = when {
            availability == HealthConnect.Availability.Unavailable ->
                "Not available on this device. Everything below is typed by hand, which works the same."
            availability == HealthConnect.Availability.NeedsUpdate ->
                "Health Connect needs updating before it can be read. The form below still works."
            isGranted ->
                "Connected. Sleep, heart rate and blood oxygen can be read."
            else ->
                "Not connected. Grant read access and the app fills these fields for you."
        }
}
