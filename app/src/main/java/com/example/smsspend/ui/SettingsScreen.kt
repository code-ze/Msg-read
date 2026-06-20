package com.example.smsspend.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val anchor by vm.anchorDay.collectAsStateWithLifecycle()
    val investAsSpend by vm.investAsSpending.collectAsStateWithLifecycle()
    val liveMsx by vm.liveMsxPrices.collectAsStateWithLifecycle()
    val salary by vm.salaryAmount.collectAsStateWithLifecycle()
    val manualBalance by vm.manualBalance.collectAsStateWithLifecycle()
    val salaryDates by vm.salaryDates.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---- salary ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Salary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Your salary marks the start of each pay cycle. Leave at 0 to auto-detect it " +
                        "from your deposits, or pin the exact amount so casual money people send " +
                        "you is never mistaken for salary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AmountField(
                    label = "Salary amount (OMR, 0 = auto)",
                    initial = salary,
                    onSave = { vm.setSalaryAmount(it) }
                )
                Text(
                    if (salaryDates.isNotEmpty())
                        "Detected ${salaryDates.size} salary deposit(s) — cycles follow their real dates."
                    else
                        "No salary deposits detected yet; using the fallback day below.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- balance ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bank balance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Balance is read automatically from your bank SMS for trends and runway. " +
                        "Set a value here to override it (0 = use the latest from SMS).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AmountField(
                    label = "Manual balance (OMR, 0 = from SMS)",
                    initial = manualBalance,
                    onSave = { vm.setManualBalance(it) }
                )
            }
        }

        // ---- pay-cycle fallback day ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pay-cycle fallback day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Pay cycles normally follow your detected salary deposits. This fixed day is " +
                        "only used as a fallback until enough salary deposits are detected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Day $anchor of the month", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Slider(
                    value = anchor.toFloat(),
                    onValueChange = { vm.setAnchorDay(it.toInt().coerceIn(1, 31)) },
                    valueRange = 1f..31f,
                    steps = 29
                )
                Text(
                    "Tip: days 29–31 fall back to the last day in shorter months.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Investments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Count IPO subscriptions as spending")
                        Text(
                            if (investAsSpend)
                                "IPO buys are added to your 'Spent' total."
                            else
                                "IPO buys are tracked separately and kept out of 'Spent'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = investAsSpend,
                        onCheckedChange = { vm.setInvestAsSpending(it) }
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Live MSX prices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Fetch stock prices from MSX")
                        Text(
                            if (liveMsx)
                                "On — the app contacts msx.om to update holding prices. Best-effort; manual price is used if a fetch fails."
                            else
                                "Off — prices stay manual and the app makes no network calls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = liveMsx, onCheckedChange = { vm.setLiveMsxPrices(it) })
                }
            }
        }

        // ---- data export ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data export (debug)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Export everything the app has collected as JSON — transactions, categories, " +
                        "holdings, balances and settings — so you can review it or feed it back for help.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            vm.exportJson { json ->
                                copyToClipboard(context, "SMS Spend data", json)
                                Toast.makeText(context, "Copied JSON to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Text("  Copy JSON")
                    }
                    Button(
                        onClick = { vm.exportJson { json -> shareJson(context, json) } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Text("  Export file")
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (liveMsx)
                        "SMS stays on your device (READ_SMS). The only network use is fetching " +
                            "stock prices from MSX, which you enabled above. Exports go only where you send them."
                    else
                        "Everything stays on your device. The app only reads SMS (READ_SMS) and " +
                            "makes no network connections. Exports go only where you send them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A numeric OMR field with its own Save button so edits are committed deliberately. */
@Composable
private fun AmountField(label: String, initial: Double, onSave: (Double) -> Unit) {
    var text by remember(initial) {
        mutableStateOf(if (initial > 0) Format.omr(initial) else "")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        Button(onClick = { onSave(text.toDoubleOrNull() ?: 0.0) }) { Text("Save") }
    }
}

/** Writes the JSON to a cache file and opens the system share sheet via FileProvider. */
private fun shareJson(context: Context, json: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "smsspend-export.json")
    file.writeText(json)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export SMS Spend data"))
}
