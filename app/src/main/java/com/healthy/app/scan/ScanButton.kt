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
import com.healthy.app.data.entity.Product
import com.healthy.app.ui.theme.HealthyColors
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * The scan button (spec 11.1): one tap opens the camera, one scan closes it.
 */
@Composable
fun ScanButton(
    modifier: Modifier = Modifier,
    /** Which kind the screen expects; only pre-selects for a new barcode. */
    suggestedKind: String = Product.KIND_DRINK,
    /** Where a food goes. The food screen asks how much was eaten. */
    onFood: ((Product) -> Unit)? = null,
    label: String = "Scan a barcode",
    vm: ScanViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { vm.onBarcode(it, suggestedKind) }
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
        Text(label, fontSize = 14.sp)
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
            onCorrect = { vm.correct(s.product, s.drinkId) },
        )

        is ScanViewModel.State.Review -> ReviewDialog(
            product = s.product,
            correcting = s.drinkId != null,
            onSave = { corrected -> vm.saveReviewed(corrected, s.drinkId) },
            onDismiss = vm::dismiss,
        )

        is ScanViewModel.State.NeedsCaffeine -> CaffeineDialog(
            productName = listOfNotNull(s.product.brand, s.product.name)
                .joinToString(" ").trim().ifBlank { s.product.name },
            initialVolume = s.product.volumeMl,
            guess = s.suggestion,
            onSave = { mg, perMl, totalMl, name ->
                vm.saveCaffeine(s.product, mg, perMl, totalMl, name)
            },
            onDismiss = vm::dismiss,
        )

        is ScanViewModel.State.Unknown -> if (suggestedKind == Product.KIND_FOOD) {
            // A food needs values for 100 g, not a caffeine figure, and that
            // form already exists on the food screen.
            ResultDialog(
                title = "Not found",
                body = (s.reason?.let { "Could not reach Open Food Facts. " }
                    ?: "This barcode is not in Open Food Facts. ") +
                    "Use \"Type a food with no barcode\" below to enter it once.",
                onDismiss = vm::dismiss,
            )
        } else {
            CaffeineDialog(
                productName = "",
                initialVolume = null,
                heading = s.reason?.let { "Could not reach Open Food Facts. Type it once instead." }
                    ?: "Not in Open Food Facts. Type it once and it is stored.",
                onSave = { mg, perMl, totalMl, name ->
                    vm.saveNewProduct(s.barcode, name, mg, perMl, totalMl)
                },
                onDismiss = vm::dismiss,
            )
        }

        is ScanViewModel.State.NeedsKind -> KindDialog(
            product = s.product,
            correcting = s.correcting,
            onChoose = { kind -> vm.chooseKind(s.product, kind) },
            onDismiss = vm::dismiss,
        )

        is ScanViewModel.State.NeedsPortion -> {
            // Handing the product on is the food screen's job; from anywhere
            // else this says so rather than silently doing nothing.
            if (onFood != null) {
                androidx.compose.runtime.LaunchedEffect(s.product.barcode) {
                    onFood(s.product)
                    vm.dismiss()
                }
            } else {
                ResultDialog(
                    title = s.product.name,
                    body = "This is stored as a food. Log it from the Food tab, " +
                        "where the app can ask how much you ate.",
                    onDismiss = vm::dismiss,
                )
            }
        }

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

/**
 * Spec 11.1: a new barcode asks which kind it is.
 *
 * The two answers lead to completely different questions — caffeine for a
 * drink, a portion for a food — so guessing wrong costs the user two dialogs
 * instead of one.
 */
@Composable
private fun KindDialog(
    product: Product,
    correcting: Boolean = false,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    listOfNotNull(product.brand, product.name).joinToString(" ").trim()
                        .ifBlank { product.name },
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (correcting) {
                        "This is filed as a ${product.kind}, but you scanned it from the " +
                            "other screen. Which is it? Changing it here fixes it for good."
                    } else {
                        "New to this app. Which is it? A drink is logged for its caffeine " +
                            "and fluid; a food is logged for its weight and nutrients."
                    },
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onChoose(Product.KIND_DRINK) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, HealthyColors.Rule),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = HealthyColors.Raised2,
                            contentColor = HealthyColors.Paper,
                        ),
                    ) { Text("Drink", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = { onChoose(Product.KIND_FOOD) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, HealthyColors.Rule),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = HealthyColors.Raised2,
                            contentColor = HealthyColors.Paper,
                        ),
                    ) { Text("Food", fontSize = 13.sp) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = HealthyColors.Muted) }
                }
            }
        }
    }
}

@Composable
private fun ResultDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onCorrect: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(title, color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(body, color = HealthyColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    // A stored product is only asked about once, so without a
                    // way back in a wrong figure would stay wrong forever.
                    if (onCorrect != null) {
                        TextButton(onClick = onCorrect) {
                            Text("Fix these values", color = HealthyColors.Muted)
                        }
                    }
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
    guess: CaffeineReference.Guess? = null,
    heading: String? = null,
    onSave: (Int, Int, Int, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(productName) }
    var mg by remember { mutableStateOf(guess?.mgPer100Ml?.toString() ?: "") }
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
                    NumberBox("mg on label", mg, Modifier.weight(1f)) { mg = it }
                    NumberBox("per this ml", perMl, Modifier.weight(1f)) { perMl = it }
                    NumberBox("whole can ml", totalMl, Modifier.weight(1f)) { totalMl = it }
                }
                Text(
                    computed?.let { "That is $it mg for the whole ${totalValue} ml." }
                        ?: "Left to right: the number on the label, the volume it refers " +
                        "to, then how much the container actually holds. 32, 100, 250 " +
                        "means a 250 ml can at 32 mg per 100 ml — 80 mg.",
                    color = if (computed != null) HealthyColors.Sleep else HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // Reported from the phone: a 250 ml can logged as 100 ml,
                // because the label's "per 100 ml" was typed into both boxes.
                if (totalValue == 100 && perValue == 100) {
                    Text(
                        "A 100 ml can is unusual. If the label reads \"per 100 ml\", that " +
                            "belongs in the middle box only — the last one is the size of " +
                            "the container, often 250, 330 or 500.",
                        color = HealthyColors.Warn,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (guess != null) {
                    Text(
                        "The ${guess.mgPer100Ml} is a guess from ${guess.basis}, not this " +
                            "product's own figure. Replace it with the label if you have it.",
                        color = HealthyColors.Caffeine,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
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
