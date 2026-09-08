package com.healthy.app.scan

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.healthy.app.ui.theme.HealthyColors
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Showing an item as a QR code (spec 5.4).
 *
 * No server and no account: one phone draws a square, another reads it. The
 * item never leaves either device except as light on a screen.
 */
@Composable
fun QrShareDialog(payload: String, title: String, onDismiss: () -> Unit) {
    val bitmap: Bitmap? = remember(payload) {
        runCatching {
            BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, QR_PIXELS, QR_PIXELS)
        }.getOrNull()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(
                Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = HealthyColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Point another phone's Healthy scanner at this. Nothing is uploaded.",
                    color = HealthyColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR code for $title",
                        modifier = Modifier.size(240.dp),
                    )
                } else {
                    Text(
                        "This item is too large to fit in one code. A recipe with very " +
                            "many ingredients cannot be shared this way.",
                        color = HealthyColors.Warn,
                        fontSize = 12.sp,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Done", color = HealthyColors.Sleep) }
                }
            }
        }
    }
}

/**
 * Reading a shared item. A plain barcode scan, but the payload is one of ours
 * rather than a product code, so anything else is refused politely.
 */
@Composable
fun QrReceiveButton(
    onDecoded: (QrPayload.Decoded) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { onDecoded(QrPayload.decode(it)) }
    }
    TextButton(
        onClick = {
            scanner.launch(
                ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt("Point at a shared Healthy code")
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                }
            )
        },
        modifier = modifier,
    ) {
        Text("Receive a shared item", color = HealthyColors.Sleep, fontSize = 13.sp)
    }
}

private const val QR_PIXELS = 640
