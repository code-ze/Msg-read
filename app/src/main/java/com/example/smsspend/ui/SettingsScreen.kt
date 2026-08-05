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
import com.example.smsspend.parser.Categorizer
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
    val dailyLimit by vm.dailyLimit.collectAsStateWithLifecycle()
    val monthlyBudget by vm.monthlyBudget.collectAsStateWithLifecycle()
    val categoryBudgets by vm.categoryBudgets.collectAsStateWithLifecycle()
    val creditLimit by vm.creditLimit.collectAsStateWithLifecycle()
    val creditLimitTotal by vm.effectiveCreditLimit.collectAsStateWithLifecycle()
    val creditLimitInferred by vm.creditLimitInferred.collectAsStateWithLifecycle()
    val cardOwed by vm.cardOwed.collectAsStateWithLifecycle()
    val liveFx by vm.liveFxRates.collectAsStateWithLifecycle()
    val fxRates by vm.fxRates.collectAsStateWithLifecycle()
    val fxFetchedAt by vm.fxFetchedAt.collectAsStateWithLifecycle()

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

        // ---- credit card ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Credit card", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "What you owe is read automatically from the \"Available limit\" in each card " +
                        "SMS. Enter your total credit limit so the amount owed is exact — leave at " +
                        "0 and the app guesses it from the highest available credit it has seen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AmountField(
                    label = "Total credit limit (OMR, 0 = auto)",
                    initial = creditLimit,
                    onSave = { vm.setCreditLimit(it) }
                )
                Text(
                    if (cardOwed > 0)
                        "Currently owed ${Format.omr2(cardOwed)} OMR of ${Format.omr2(creditLimitTotal)} OMR" +
                            (if (creditLimitInferred) " (limit auto-detected)" else "")
                    else
                        "No card activity detected yet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Foreign-currency purchases are converted to rials using the drop in your " +
                        "available limit, so they match what the bank actually charged.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- budgets & limits ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Budgets & limits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Set a daily spending cap for the streak counter and alerts. Set a monthly " +
                        "budget to see a progress bar in Insights. Leave at 0 to disable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AmountField(
                    label = "Daily limit (OMR, 0 = off)",
                    initial = dailyLimit,
                    onSave = { vm.setDailyLimit(it) }
                )
                AmountField(
                    label = "Monthly budget (OMR, 0 = off)",
                    initial = monthlyBudget,
                    onSave = { vm.setMonthlyBudget(it) }
                )
            }
        }

        // ---- per-category budgets ----
        val spendingCats = Categorizer.allCategories.filter {
            it !in setOf(Categorizer.INCOME, Categorizer.DIVIDENDS)
        }
        var showAllCats by remember { mutableStateOf(false) }
        val catsToShow = if (showAllCats) spendingCats
            else spendingCats.filter { (categoryBudgets[it] ?: 0.0) > 0 }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Category budgets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedButton(
                        onClick = { showAllCats = !showAllCats },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(if (showAllCats) "Show less" else "Add category")
                    }
                }
                Text(
                    "Per-category limits appear as progress bars in Insights. " +
                        "Set to 0 to remove a category from the list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (catsToShow.isEmpty() && !showAllCats) {
                    Text("No category budgets set. Tap 'Add category' to set one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    catsToShow.forEach { cat ->
                        AmountField(
                            label = "$cat (OMR/month, 0 = off)",
                            initial = categoryBudgets[cat] ?: 0.0,
                            onSave = { vm.setCategoryBudget(cat, it) }
                        )
                    }
                }
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

        // ---- exchange rates / markup ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bank markup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Fetch mid-market exchange rates")
                        Text(
                            if (liveFx)
                                "On — daily reference rates are fetched to show what the bank added " +
                                    "on top of each foreign purchase. Cached for 6 hours."
                            else
                                "Off — markup is only shown for dollar-pegged currencies, which " +
                                    "need no network.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = liveFx, onCheckedChange = { vm.setLiveFxRates(it) })
                }
                Text(
                    if (fxFetchedAt > 0)
                        "Rates updated ${Format.dayTime(fxFetchedAt)} · ${fxRates.size} currencies"
                    else
                        "No rates fetched yet. USD and Gulf currencies still work — the rial's peg " +
                            "to the dollar is fixed, so they need no live rate at all.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Markup is only shown where the rial amount came from the bank itself. For a " +
                        "purchase the app had to convert on its own, comparing against the true " +
                        "rate would just measure the app's own guess.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    buildString {
                        append("SMS stays on your device (READ_SMS) and is never uploaded. ")
                        val uses = buildList {
                            if (liveMsx) add("share prices from MSX")
                            if (liveFx) add("mid-market exchange rates")
                        }
                        if (uses.isEmpty()) {
                            append("The app makes no network connections at all. ")
                        } else {
                            append("The only network use is fetching ")
                            append(uses.joinToString(" and "))
                            append(" — no transaction data is ever sent. ")
                        }
                        append("Exports go only where you send them.")
                    },
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
