package com.sleepguard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private const val CODE_CHARS = "abcdefghjkmnpqrstuvwxyz23456789"

private fun randomCode(): String =
    (1..5).joinToString("-") { (1..4).map { CODE_CHARS[Random.nextInt(CODE_CHARS.length)] }.joinToString("") }

/**
 * Strict mode's only way out: type a random 24-character code by hand. Deliberately
 * tedious so it works for real emergencies but not for "just one more video".
 */
@Composable
fun EmergencyUnlockDialog(onDismiss: () -> Unit, onUnlock: () -> Unit) {
    val code = remember { randomCode() }
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emergency unlock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Type this code exactly to end strict sleep mode. The night will be saved as unlocked.")
                Text(
                    code,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.lowercase() },
                    singleLine = true,
                    label = { Text("Code") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onUnlock, enabled = input.trim() == code) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep sleeping") } },
    )
}
