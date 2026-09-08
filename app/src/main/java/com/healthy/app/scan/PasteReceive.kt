package com.healthy.app.scan

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.healthy.app.ui.theme.HealthyColors

/**
 * Taking in a shared item that arrived as text.
 *
 * The route that works everywhere. A QR code needs both people in one room,
 * and a link needs a messenger that hands links to the system — Messenger does
 * not, it opens them in its own browser. A code that is copied and pasted has
 * no such dependency: it survives any chat app, any mail client, and being
 * written down.
 *
 * The clipboard is read first, because in practice the code was just copied
 * out of a chat, and the field is there for when it was not.
 */
@Composable
fun PasteReceiveButton(
    onDecoded: (QrPayload.Decoded) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }

    TextButton(
        onClick = {
            text = clipboardText(context).orEmpty()
            open = true
        },
        modifier = modifier,
    ) {
        Text("Paste a shared item", color = HealthyColors.Sleep, fontSize = 13.sp)
    }

    if (!open) return

    val decoded = remember(text) { text.takeIf { it.isNotBlank() }?.let(QrPayload::decodeAny) }
    val summary = when (decoded) {
        is QrPayload.Decoded.Dish ->
            "“${decoded.recipe.name}”, ${decoded.items.size} ingredients."
        is QrPayload.Decoded.Food -> "“${decoded.product.name}”."
        is QrPayload.Decoded.NotOurs -> decoded.reason
        null -> "Nothing on the clipboard. Paste the message you were sent."
    }

    Dialog(onDismissRequest = { open = false }) {
        Card(
            modifier = Modifier.imePadding(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HealthyColors.Raised),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Paste a shared item",
                    color = HealthyColors.Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Paste the whole message. The code inside it is found for you.",
                    color = HealthyColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Message or code", fontSize = 11.sp) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HealthyColors.Paper,
                        unfocusedTextColor = HealthyColors.Paper,
                        focusedBorderColor = HealthyColors.Sleep,
                        unfocusedBorderColor = HealthyColors.Rule,
                        focusedLabelColor = HealthyColors.Sleep,
                        unfocusedLabelColor = HealthyColors.Muted,
                        cursorColor = HealthyColors.Sleep,
                    ),
                )
                Text(
                    summary,
                    color = if (decoded is QrPayload.Decoded.Dish || decoded is QrPayload.Decoded.Food) {
                        HealthyColors.Sleep
                    } else {
                        HealthyColors.Muted
                    },
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { open = false }) {
                        Text("Cancel", color = HealthyColors.Muted)
                    }
                    TextButton(
                        onClick = {
                            decoded?.let(onDecoded)
                            open = false
                            text = ""
                        },
                        enabled = decoded is QrPayload.Decoded.Dish ||
                            decoded is QrPayload.Decoded.Food,
                    ) { Text("Add it", color = HealthyColors.Sleep) }
                }
            }
        }
    }
}

private fun clipboardText(context: Context): String? =
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull()
