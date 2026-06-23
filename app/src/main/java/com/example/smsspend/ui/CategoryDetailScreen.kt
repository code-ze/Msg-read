package com.example.smsspend.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun CategoryDetailScreen(vm: MainViewModel, category: String) {
    val period by vm.period.collectAsState()
    val txnsFlow = remember(period.start, period.endExclusive, category) {
        vm.txnsForCategory(period.start, period.endExclusive, category)
    }
    val txns by txnsFlow.collectAsState(initial = emptyList())

    val total = txns.sumOf { it.amount }

    Column(Modifier.fillMaxSize()) {
        PeriodBar(
            period = period,
            canStep = vm.canStep,
            anchorDay = vm.anchorDay.value,
            salaryDetected = vm.salaryDates.value.isNotEmpty(),
            onStep = { vm.stepPeriod(it) },
            onSelect = { vm.setPeriod(it) }
        )
        Text(
            "${Format.omr(total)} OMR · ${txns.size} item${if (txns.size == 1) "" else "s"}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        if (txns.isEmpty()) {
            Text(
                "No transactions in this category for the selected period.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            val byMerchant = remember(txns) {
                txns.groupBy { it.merchantClean }
                    .map { (name, list) -> Triple(name, list.sumOf { it.amount }, list.size) }
                    .sortedByDescending { it.second }
            }
            val bySub = remember(txns) {
                txns.groupBy { it.subcategory.ifBlank { "Unsorted" } }
                    .map { (name, list) -> Triple(name, list.sumOf { it.amount }, list.size) }
                    .sortedByDescending { it.second }
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                if (bySub.size > 1) {
                    item {
                        SubcategoryDonut(bySub, total)
                    }
                }
                if (byMerchant.size > 1) {
                    item {
                        Text(
                            "By merchant",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                        )
                    }
                    items(byMerchant, key = { "m_" + it.first }) { (name, sum, n) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { vm.navigate(Screen.Merchant(name)) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text("$n item${if (n == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Format.omr(sum), fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider(Modifier.padding(start = 16.dp))
                    }
                    item {
                        Text(
                            "All transactions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                        )
                    }
                }
                items(txns, key = { it.key }) { t ->
                    TxnRow(t) { vm.navigate(Screen.Merchant(t.merchantClean)) }
                    HorizontalDivider(Modifier.padding(start = 42.dp))
                }
            }
        }
    }
}

@Composable
private fun SubcategoryDonut(bySub: List<Triple<String, Double, Int>>, total: Double) {
    val slices = bySub.map { ChartSlice(it.first, it.second, categoryColor(it.first)) }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("By sub-category", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DonutChart(slices = slices, modifier = Modifier.size(160.dp), thickness = 22.dp) {
                    Text(Format.omr(total), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.padding(top = 12.dp)) {
                bySub.forEach { (name, sum, n) ->
                    val pct = if (total > 0) (sum / total * 100).roundToInt() else 0
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        CategoryDot(name, 12)
                        Text(name, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(start = 10.dp))
                        Text("$pct%", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 10.dp))
                        Text(Format.omr(sum), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
