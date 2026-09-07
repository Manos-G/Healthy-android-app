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
            productName = listOfNotNull(s.product.brand, s.product.name)
                .joinToString(" ").trim().ifBlank { s.product.name },
            initialVolume = s.product.volumeMl,
            onSave = { mg, perMl, totalMl, name ->
                vm.saveCaffeine(s.product, mg, perMl, totalMl, name)
            },
            onDismiss = vm::dismiss,
        )

        is ScanViewModel.State.Unknown -> CaffeineDialog(
            productName = "",
            initialVolume = null,
            heading = s.reason?.let { "Could not reach Open Food Facts. Type it once instead." }
                ?: "Not in Open Food Facts. Type it once and it is stored.",
            onSave = { mg, perMl, totalMl, name ->
                vm.saveNewProduct(s.barcode, name, mg, perMl, totalMl)
            },
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
    onSave: (Int, Int, Int, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(productName) }
    var mg by remember { mutableStateOf("") }
    // Labels almost always state a figure per 100 ml, so that is the default;
    // a can that gives the whole amount is handled by setting this to the
    // container size.
    var perMl by remember { mutableStateOf("100") }
    var totalMl by remember { mutableStateOf(initialVolume?.toString() ?: "") }

    val mgValue = mg.toIntOrNull()
    val perValue = perMl.toIntOrNull()
    val totalValue = totalMl.toIntOrNull()
    val computed = if (mgValue != null && perValue != null && perValue > 0 && totalValue != null && totalValue > 0) {
        com.healthy.app.scan.OpenFoodFacts.totalMg(mgValue, perValue, totalValue)
    } else {
        null
    }

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
                    heading
                        ?: "Found in Open Food Facts, but the entry carries no caffeine " +
                        "figure — most colas do not, while most energy drinks do. " +
                        "Copy what the label says. This barcode will not ask again.",
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
                    NumberBox("Caffeine mg", mg, Modifier.weight(1f)) { mg = it }
                    NumberBox("per ml", perMl, Modifier.weight(1f)) { perMl = it }
                    NumberBox("Can holds ml", totalMl, Modifier.weight(1f)) { totalMl = it }
                }
                Text(
                    computed?.let { "That is $it mg for the whole ${totalValue} ml." }
                        ?: "Example: a label reading 32 mg per 100 ml on a 330 ml can is 106 mg.",
                    color = if (computed != null) HealthyColors.Sleep else HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = HealthyColors.Muted) }
                    TextButton(
                        onClick = {
                            onSave(mgValue ?: 0, perValue ?: 100, totalValue ?: 0, name)
                        },
                        enabled = computed != null && (productName.isNotBlank() || name.isNotBlank()),
                    ) {
                        Text("Save", color = HealthyColors.Sleep)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }) },
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        modifier = modifier,
        colors = scanFieldColours(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Number,
        ),
    )
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
