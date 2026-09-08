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
    val context = androidx.compose.ui.platform.LocalContext.current
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
                    "Together in one room: they open Healthy and scan this square. " +
                        "Anywhere else: Send to a friend, and pick Messenger, WhatsApp " +
                        "or anything else. Nothing is uploaded either way.",
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
                Text(
                    "The message carries the recipe itself, not a reference to it, so " +
                        "opening it adds the whole thing — ingredients and their values " +
                        "included. It passes through no server of ours.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { shareItem(context, payload, title, bitmap) }) {
                        Text("Send to a friend", color = HealthyColors.Sleep)
                    }
                    TextButton(onClick = onDismiss) { Text("Done", color = HealthyColors.Muted) }
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
        // decodeAny, so a square and a pasted link both work.
        result.contents?.let { onDecoded(QrPayload.decodeAny(it)) }
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

/**
 * Hands the item to whatever the user shares with — Messenger, WhatsApp, mail.
 *
 * The text carries a link that holds the whole item, because a QR code is
 * useless through a chat: the square arrives on the reader's own screen and a
 * phone cannot scan itself. The image goes along too, for the case where they
 * are sitting together and one of them scans the other's screen.
 *
 * Written to the app's own cache and handed over through a FileProvider, so
 * the other app gets a one-time grant rather than a world-readable file.
 */
private fun shareItem(
    context: android.content.Context,
    payload: String,
    title: String,
    bitmap: Bitmap?,
) {
    // The code on its own line and nothing after it, so a long-press selects
    // it cleanly. Messenger will not make a healthy:// link tappable, so the
    // code — not the link — is what the instructions point at.
    val message = buildString {
        append("$title — a recipe from Healthy.\n\n")
        append(QrPayload.toCode(payload))
        append("\n\nTo add it: open Healthy, Food tab, \"Paste a shared item\". ")
        append("The code holds the whole recipe, so nothing is downloaded.")
    }

    val imageUri = bitmap?.let { runCatching { cacheQr(context, it, title) }.getOrNull() }

    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        putExtra(android.content.Intent.EXTRA_TEXT, message)
        putExtra(android.content.Intent.EXTRA_SUBJECT, title)
        if (imageUri != null) {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, imageUri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Send $title"))
}

private fun cacheQr(context: android.content.Context, bitmap: Bitmap, title: String): android.net.Uri {
    val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
    val safe = title.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
    val file = java.io.File(dir, "healthy-$safe.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return androidx.core.content.FileProvider.getUriForFile(
        context,
        context.packageName + ".shared",
        file,
    )
}

private const val QR_PIXELS = 640
