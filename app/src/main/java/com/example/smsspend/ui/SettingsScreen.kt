package com.example.smsspend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val anchor by vm.anchorDay.collectAsStateWithLifecycle()
    val investAsSpend by vm.investAsSpending.collectAsStateWithLifecycle()
    val liveMsx by vm.liveMsxPrices.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pay-cycle fallback day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Pay cycles normally follow your auto-detected salary deposits. This fixed day is " +
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

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (liveMsx)
                        "SMS stays on your device (READ_SMS). The only network use is fetching " +
                            "stock prices from MSX, which you enabled above."
                    else
                        "Everything stays on your device. The app only reads SMS (READ_SMS) and " +
                            "makes no network connections.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
