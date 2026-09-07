package com.healthy.app.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthy.app.ui.theme.HealthyColors
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * The scan button (spec 11.1): one tap opens the camera, one scan closes it.
 */
@Composable
fun ScanButton(
    modifier: Modifier = Modifier,
    vm: ScanViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(vm::onBarcode)
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) scanner.launch(scanOptions()) }

    val context = androidx.compose.ui.platform.LocalContext.current

    OutlinedButton(
        onClick = {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) scanner.launch(scanOptions())
            else cameraPermission.launch(android.Manifest.permission.CAMERA)
        },
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HealthyColors.Rule),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = HealthyColors.Raised2,
            contentColor = HealthyColors.Paper,
        ),
    ) {
        Text("Scan a barcode", fontSize = 14.sp)
    }

    when (val s = state) {
        is ScanViewModel.State.Logged -> ResultDialog(
            title = "Logged",
            body = buildString {
                append(s.product.name)
                s.product.mg?.let { append(", $it mg") }
                s.product.volumeMl?.let { append(", $it ml") }
                append(".")
                append(if (s.fromCache) " Read from this phone, no network used." else " Looked up once; it is stored now.")
            },
            onDismiss = vm::dismiss,
        )

        is ScanViewModel.State.NeedsCaffeine -> CaffeineDialog(
            productName = s.product.name,
            initialVolume = s.product.volumeMl,
            onSave = { mg, ml, name -> vm.saveCaffeine(s.product, mg, ml, name) },
            onDismiss = vm::dismiss,
        )

        is ScanViewModel.State.Unknown -> CaffeineDialog(
            productName = "",
            initialVolume = null,
            heading = s.reason?.let { "Could not reach Open Food Facts. Type it once instead." }
                ?: "Not in Open Food Facts. Type it once and it is stored.",
            onSave = { mg, ml, name -> vm.saveNewProduct(s.barcode, name, mg, ml) },
            onDismiss = vm::dismiss,
        )

        is ScanViewModel.State.Working -> ResultDialog(
            title = "Looking up",
            body = "Checking this phone first, then Open Food Facts.",
            onDismiss = vm::dismiss,
        )

        ScanViewModel.State.Idle -> Unit
    }
}

private fun scanOptions() = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.PRODUCT_CODE_TYPES)
    setPrompt("Point at the barcode")
    setBeepEnabled(false)
    setOrientationLocked(false)
}

@Composable
private fun ResultDialog(title: String, body: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(title, color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(body, color = HealthyColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Done", color = HealthyColors.Sleep) }
                }
            }
        }
    }
}

/**
 * Asked once per barcode, never again (spec 11.4). The user reads the number
 * off the can, which is the only reliable source when Open Food Facts has no
 * caffeine value — and it usually does not.
 */
@Composable
private fun CaffeineDialog(
    productName: String,
    initialVolume: Int?,
    heading: String? = null,
    onSave: (Int, Int, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(productName) }
    var mg by remember { mutableStateOf("") }
    var ml by remember { mutableStateOf(initialVolume?.toString() ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    if (productName.isBlank()) "New drink" else productName,
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    heading ?: "Open Food Facts has no caffeine value for this. Read it off the can; you will not be asked again.",
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (productName.isBlank()) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        colors = scanFieldColours(),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = mg,
                        onValueChange = { v -> mg = v.filter { it.isDigit() } },
                        label = { Text("Caffeine mg", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = scanFieldColours(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                    )
                    OutlinedTextField(
                        value = ml,
                        onValueChange = { v -> ml = v.filter { it.isDigit() } },
                        label = { Text("Volume ml", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = scanFieldColours(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = HealthyColors.Muted) }
                    TextButton(
                        onClick = { onSave(mg.toIntOrNull() ?: 0, ml.toIntOrNull() ?: 0, name) },
                        enabled = mg.toIntOrNull() != null && (productName.isNotBlank() || name.isNotBlank()),
                    ) {
                        Text("Save", color = HealthyColors.Sleep)
                    }
                }
            }
        }
    }
}

@Composable
private fun scanFieldColours() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = HealthyColors.Paper,
    unfocusedTextColor = HealthyColors.Paper,
    focusedBorderColor = HealthyColors.Sleep,
    unfocusedBorderColor = HealthyColors.Rule,
    focusedLabelColor = HealthyColors.Sleep,
    unfocusedLabelColor = HealthyColors.Muted,
    cursorColor = HealthyColors.Sleep,
)
