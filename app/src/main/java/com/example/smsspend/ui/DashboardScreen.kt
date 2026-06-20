package com.example.smsspend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smsspend.parser.Categorizer

@Composable
fun DashboardScreen(vm: MainViewModel) {
    val period by vm.period.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val recent by vm.recentTxns.collectAsStateWithLifecycle()
    val anchor by vm.anchorDay.collectAsStateWithLifecycle()
    val investAsSpend by vm.investAsSpending.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        PeriodBar(
            period = period,
            canStep = vm.canStep,
            anchorDay = anchor,
            onStep = { vm.stepPeriod(it) },
            onSelect = { vm.setPeriod(it) }
        )

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        "Spent", Format.omr(totals.spent) + " OMR",
                        MaterialTheme.colorScheme.error, Modifier.weight(1f)
                    )
                    StatCard(
                        "Income", Format.omr(totals.income) + " OMR",
                        categoryColor(Categorizer.INCOME), Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        "Net", Format.omrSigned(kotlin.math.abs(totals.net), totals.net >= 0) + " OMR",
                        if (totals.net >= 0) categoryColor(Categorizer.INCOME) else MaterialTheme.colorScheme.error,
                        Modifier.weight(1f)
                    )
                    StatCard(
                        if (investAsSpend) "Invested*" else "Invested",
                        Format.omr(totals.invested) + " OMR",
                        categoryColor(Categorizer.INVESTMENTS),
                        Modifier
                            .weight(1f)
                            .clickable { vm.navigate(Screen.Investments) }
                    )
                }
            }

            item {
                Text(
                    "By category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
                )
            }

            if (totals.byCategory.isEmpty()) {
                item {
                    Text(
                        "No spending in this period.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                item {
                    val max = totals.byCategory.maxOf { it.total }.coerceAtLeast(0.001)
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            totals.byCategory.forEach { line ->
                                CategoryBar(
                                    line.category, line.total, line.count, (line.total / max).toFloat()
                                ) { vm.navigate(Screen.Category(line.category)) }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
                )
            }
            if (recent.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet. Pull a refresh or pick another period.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(recent, key = { it.key }) { t ->
                    TxnRow(t) { vm.navigate(Screen.Merchant(t.merchantClean)) }
                    HorizontalDivider(Modifier.padding(start = 42.dp))
                }
            }
            item { Box(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CategoryBar(
    category: String,
    total: Double,
    count: Int,
    fraction: Float,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(category)
            Text(
                category,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).padding(start = 10.dp)
            )
            Text(
                Format.omr(total),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            Modifier
                .padding(top = 6.dp, start = 22.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(categoryColor(category))
            )
        }
        Text(
            "$count item${if (count == 1) "" else "s"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 22.dp, top = 2.dp)
        )
    }
}
