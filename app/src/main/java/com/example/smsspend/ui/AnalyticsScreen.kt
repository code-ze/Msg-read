package com.example.smsspend.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(vm: MainViewModel) {
    val period by vm.period.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val anchor by vm.anchorDay.collectAsStateWithLifecycle()
    val salaryDates by vm.salaryDates.collectAsStateWithLifecycle()
    val topMerchants by vm.topMerchants.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        PeriodBar(
            period = period,
            canStep = vm.canStep,
            anchorDay = anchor,
            salaryDetected = salaryDates.isNotEmpty(),
            onStep = { vm.stepPeriod(it) },
            onSelect = { vm.setPeriod(it) }
        )

        if (totals.byCategory.isEmpty()) {
            Text(
                "No spending in this period yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }

        val slices = remember(totals.byCategory) {
            groupTopSlices(
                totals.byCategory.map { ChartSlice(it.category, it.total, categoryColor(it.category)) },
                topN = 5
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            DonutChart(slices = slices, modifier = Modifier.size(200.dp), thickness = 28.dp) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Spent", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(Format.omr2(totals.spent), style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold)
                                    Text("OMR", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Categories") }
            items(totals.byCategory, key = { it.category }) { line ->
                val pct = if (totals.spent > 0) (line.total / totals.spent * 100).roundToInt() else 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.navigate(Screen.Category(line.category)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryDot(line.category, 12)
                    Text(line.category, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Text("$pct%", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp))
                    Text(Format.omr2(line.total), style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold)
                }
            }

            if (topMerchants.isNotEmpty()) {
                item { SectionHeader("Top merchants") }
                items(topMerchants.take(10), key = { "m_" + it.merchantClean }) { m ->
                    MerchantBar(m.merchantClean, m.total, m.count) {
                        vm.navigate(Screen.Merchant(m.merchantClean))
                    }
                }
            }
            item { Box(Modifier.height(24.dp)) }
        }
    }
}
