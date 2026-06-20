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
import com.example.smsspend.model.Insights
import com.example.smsspend.parser.Categorizer
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(vm: MainViewModel) {
    val period by vm.period.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val recent by vm.recentTxns.collectAsStateWithLifecycle()
    val anchor by vm.anchorDay.collectAsStateWithLifecycle()
    val investAsSpend by vm.investAsSpending.collectAsStateWithLifecycle()
    val salaryDates by vm.salaryDates.collectAsStateWithLifecycle()
    val insights by vm.insights.collectAsStateWithLifecycle()
    val balance by vm.balance.collectAsStateWithLifecycle()
    val prevSpent by vm.prevSpent.collectAsStateWithLifecycle()
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
                InsightsCard(
                    balance = balance,
                    insights = insights,
                    prevSpent = prevSpent
                )
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

            if (topMerchants.isNotEmpty()) {
                item {
                    Text(
                        "Top merchants",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
                    )
                }
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            topMerchants.take(8).forEach { m ->
                                MerchantBar(m.merchantClean, m.total, m.count) {
                                    vm.navigate(Screen.Merchant(m.merchantClean))
                                }
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
private fun InsightsCard(balance: Double, insights: Insights, prevSpent: Double) {
    Card(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (balance > 0) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Balance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${Format.omr(balance)} OMR",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider()
            }
            InsightLine("Per day", "${Format.omr(insights.perDay)} OMR",
                "day ${insights.daysElapsed}/${insights.daysTotal}")
            InsightLine("Projected this period", "${Format.omr(insights.projectedSpend)} OMR",
                trendSuffix(insights.projectedSpend, prevSpent))
            insights.runwayDays?.let {
                InsightLine("Runway at this pace", "~$it days", null)
            }
            insights.largest?.let {
                InsightLine("Largest", "${Format.omr(it.amount)} OMR", it.merchantClean)
            }
            insights.busiestDayLabel?.let {
                InsightLine("Busiest day", it, null)
            }
        }
    }
}

/** "+12% vs last" style suffix comparing the projection to the previous period's spend. */
private fun trendSuffix(projected: Double, prevSpent: Double): String? {
    if (prevSpent <= 0) return null
    val pct = ((projected - prevSpent) / prevSpent * 100).roundToInt()
    val arrow = if (pct >= 0) "▲" else "▼"
    return "$arrow${abs(pct)}% vs last"
}

@Composable
private fun InsightLine(label: String, value: String, hint: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MerchantBar(merchant: String, total: Double, count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(merchant, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, maxLines = 1)
            Text("$count item${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(Format.omr(total), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold)
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
