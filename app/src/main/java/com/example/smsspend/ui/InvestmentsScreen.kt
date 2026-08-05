package com.example.smsspend.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smsspend.data.Holding

private data class EditorState(val holding: Holding?, val name: String)

@Composable
fun InvestmentsScreen(vm: MainViewModel) {
    val holdings by vm.holdings.collectAsStateWithLifecycle()
    val dividends by vm.dividendsByCompany.collectAsStateWithLifecycle()
    val ipos by vm.ipoTxns.collectAsStateWithLifecycle()
    val ipoApps by vm.ipoApplications.collectAsStateWithLifecycle()
    val liveMsx by vm.liveMsxPrices.collectAsStateWithLifecycle()
    val pricesLoading by vm.pricesLoading.collectAsStateWithLifecycle()

    var editor by remember { mutableStateOf<EditorState?>(null) }

    val portfolioValue = holdings.sumOf { it.marketValue() }
    val totalDividends = dividends.sumOf { it.total }
    val totalIpo = ipos.sumOf { it.amount }

    LazyColumn(Modifier.fillMaxWidth()) {
        // ---- summary ----
        item {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("Portfolio value", Format.omr(portfolioValue) + " OMR",
                    MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatCard("Dividends", Format.omr(totalDividends) + " OMR",
                    categoryColor("Dividends"), Modifier.weight(1f))
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("IPO invested", Format.omr(totalIpo) + " OMR",
                    categoryColor("Investments"), Modifier.weight(1f))
                Box(Modifier.weight(1f)) {
                    if (liveMsx) {
                        OutlinedButton(
                            onClick = { vm.refreshPrices() },
                            enabled = !pricesLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (pricesLoading) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Text("  Prices")
                            }
                        }
                    }
                }
            }
        }

        // ---- holdings ----
        item {
            SectionHeaderRow("Holdings") {
                FilledTonalButton(onClick = { editor = EditorState(null, "") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Add")
                }
            }
        }
        if (holdings.isEmpty()) {
            item { Hint("Add a stock to track shares, price and value. Live price needs the MSX toggle in Settings.") }
        } else {
            items(holdings, key = { it.id }) { h ->
                HoldingRow(h) { editor = EditorState(h, h.name) }
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
        }

        // ---- dividends by company ----
        item { SectionHeaderRow("Dividends by company") {} }
        if (dividends.isEmpty()) {
            item { Hint("Cash dividends from your SMS will appear here, grouped by company.") }
        } else {
            items(dividends, key = { it.merchantClean }) { d ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { editor = EditorState(null, d.merchantClean) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.merchantClean, fontWeight = FontWeight.Medium, maxLines = 2)
                        Text("${d.count} payment${if (d.count == 1) "" else "s"} · tap to add as holding",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Format.omr(d.total), fontWeight = FontWeight.SemiBold,
                        color = categoryColor("Dividends"))
                }
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
        }

        // ---- IPOs ----
        item { SectionHeaderRow("IPO subscriptions") {} }
        if (ipos.isEmpty() && ipoApps.isEmpty()) {
            item { Hint("Your IPO subscriptions and application confirmations will be listed here.") }
        } else {
            items(ipos, key = { it.key }) { t ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.merchantClean, fontWeight = FontWeight.Medium, maxLines = 2)
                        Text(Format.day(t.date), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Format.omr(t.amount), fontWeight = FontWeight.SemiBold,
                        color = categoryColor("Investments"))
                }
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
            items(ipoApps, key = { it.reference }) { a ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Application ${a.reference}", fontWeight = FontWeight.Medium)
                        Text("Registered · ${Format.day(a.date)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
        }

        item { Box(Modifier.height(24.dp)) }
    }

    editor?.let { state ->
        HoldingDialog(
            state = state,
            onSave = { name, symbol, shares, price ->
                if (state.holding == null) {
                    vm.addHolding(name, symbol, shares, price)
                } else {
                    vm.updateHolding(
                        state.holding.copy(name = name, symbol = symbol, shares = shares, manualPrice = price)
                    )
                }
                editor = null
            },
            onDelete = state.holding?.let { h -> { vm.deleteHolding(h); editor = null } },
            onDismiss = { editor = null }
        )
    }
}

@Composable
private fun HoldingRow(h: Holding, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (h.symbol.isNotBlank()) "${h.name} (${h.symbol})" else h.name,
                fontWeight = FontWeight.SemiBold, maxLines = 2
            )
            val priceLabel = if (h.effectivePrice() > 0)
                "${trimNum(h.shares)} × ${Format.omr(h.effectivePrice())}" +
                    (if (h.lastPrice > 0) " · MSX" else " · manual")
            else "${trimNum(h.shares)} shares · set a price"
            Text(priceLabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (h.nextAgmDate > 0) {
                Text("AGM: ${Format.day(h.nextAgmDate)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary)
            }
        }
        Text(Format.omr(h.marketValue()) + " OMR", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HoldingDialog(
    state: EditorState,
    onSave: (name: String, symbol: String, shares: Double, price: Double) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(state.holding?.name ?: state.name) }
    var symbol by remember { mutableStateOf(state.holding?.symbol ?: "") }
    var shares by remember { mutableStateOf(state.holding?.shares?.let { trimNum(it) } ?: "") }
    var price by remember { mutableStateOf(state.holding?.manualPrice?.takeIf { it > 0 }?.let { Format.omr(it) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.holding == null) "Add holding" else "Edit holding") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Company name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = symbol, onValueChange = { symbol = it },
                    label = { Text("MSX symbol (e.g. OQEP)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = shares, onValueChange = { shares = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Shares") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = price, onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Manual price (OMR)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete holding", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim(),
                        symbol.trim(),
                        shares.toDoubleOrNull() ?: 0.0,
                        price.toDoubleOrNull() ?: 0.0
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SectionHeaderRow(title: String, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
}

/** Drops a trailing ".0" so whole share counts read cleanly. */
private fun trimNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
