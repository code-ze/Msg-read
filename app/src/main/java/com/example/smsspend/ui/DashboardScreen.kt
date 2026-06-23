package com.example.smsspend.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smsspend.data.BalanceSnapshot
import com.example.smsspend.data.TxnEntity
import com.example.smsspend.model.Insights
import com.example.smsspend.parser.Categorizer
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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
    val balanceSeries by vm.balanceSeries.collectAsStateWithLifecycle()
    val safeToSpend by vm.safeToSpend.collectAsStateWithLifecycle()
    val dailyLimit by vm.dailyLimit.collectAsStateWithLifecycle()
    val todaySpend by vm.todaySpend.collectAsStateWithLifecycle()

    val isCurrent = period.endExclusive > System.currentTimeMillis()
    var selectedPoint by remember { mutableStateOf<BalanceSnapshot?>(null) }
    var nearbyTxns by remember { mutableStateOf<List<TxnEntity>>(emptyList()) }

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
                HeroCard(
                    balance = balance,
                    safeToSpend = safeToSpend,
                    isCurrent = isCurrent,
                    periodEndLabel = Format.day(minOf(period.endExclusive, System.currentTimeMillis())),
                    points = balanceSeries,
                    onTapPoint = { p ->
                        selectedPoint = p
                        vm.loadTxnsAround(p.date) { nearbyTxns = it }
                    }
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("Income", Format.omr2(totals.income) + " OMR",
                        categoryColor(Categorizer.INCOME), Modifier.weight(1f))
                    StatCard("Spent", Format.omr2(totals.spent) + " OMR",
                        MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        "Net", Format.omr2Signed(abs(totals.net), totals.net >= 0) + " OMR",
                        if (totals.net >= 0) categoryColor(Categorizer.INCOME) else MaterialTheme.colorScheme.error,
                        Modifier.weight(1f)
                    )
                    StatCard(
                        if (investAsSpend) "Invested*" else "Invested",
                        Format.omr2(totals.invested) + " OMR",
                        categoryColor(Categorizer.INVESTMENTS),
                        Modifier.weight(1f).clickable { vm.selectTab(Screen.Investments) }
                    )
                }
            }

            item { QuickInsightsCard(insights, prevSpent, dailyLimit, todaySpend) }

            if (topMerchants.isNotEmpty()) {
                item {
                    SectionHeader("Top merchants")
                }
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            topMerchants.take(6).forEach { m ->
                                MerchantBar(m.merchantClean, m.total, m.count) {
                                    vm.navigate(Screen.Merchant(m.merchantClean))
                                }
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Recent activity") }
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
                items(recent.take(12), key = { it.key }) { t ->
                    TxnRow(t) { vm.navigate(Screen.Merchant(t.merchantClean)) }
                    HorizontalDivider(Modifier.padding(start = 42.dp))
                }
                if (recent.size > 12) {
                    item {
                        Text(
                            "See all ${recent.size} in Activity →",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.selectTab(Screen.Transactions) }
                                .padding(16.dp)
                        )
                    }
                }
            }
            item { Box(Modifier.height(24.dp)) }
        }
    }

    selectedPoint?.let { point ->
        ModalBottomSheet(onDismissRequest = { selectedPoint = null }) {
            BalancePointDetail(point, nearbyTxns) { merchant ->
                selectedPoint = null
                vm.navigate(Screen.Merchant(merchant))
            }
        }
    }
}

@Composable
private fun HeroCard(
    balance: Double,
    safeToSpend: Double,
    isCurrent: Boolean,
    periodEndLabel: String,
    points: List<BalanceSnapshot>,
    onTapPoint: (BalanceSnapshot) -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (isCurrent) "Total balance" else "Balance on $periodEndLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (balance > 0) Format.money(balance) else "—",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "  OMR",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            if (isCurrent && safeToSpend > 0) {
                Text(
                    "Safe to spend ≈ ${Format.omr2(safeToSpend)} OMR/day until next salary",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (points.size >= 2) {
                InteractiveBalanceChart(
                    points = points,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 14.dp),
                    onTap = onTapPoint
                )
                Text(
                    "Tap the line to see what happened on any day",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun BalancePointDetail(
    point: BalanceSnapshot,
    txns: List<TxnEntity>,
    onMerchant: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
        Text(Format.day(point.date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Balance ${Format.money(point.balance)} OMR",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Biggest movements around this date",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        if (txns.isEmpty()) {
            Text(
                "No transactions found in this window.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            txns.take(8).forEach { t ->
                TxnRow(t) { onMerchant(t.merchantClean) }
                HorizontalDivider(Modifier.padding(start = 42.dp))
            }
        }
    }
}

@Composable
private fun QuickInsightsCard(
    insights: Insights,
    prevSpent: Double,
    dailyLimit: Double,
    todaySpend: Double
) {
    val limitOver = dailyLimit > 0 && todaySpend > dailyLimit
    val limitNear = dailyLimit > 0 && todaySpend > dailyLimit * 0.8
    val limitColor: Color? = when {
        limitOver -> MaterialTheme.colorScheme.error
        limitNear -> Color(0xFFE0A45B)
        else -> null
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Quick insights", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            InsightLine("Per day", "${Format.omr2(insights.perDay)} OMR",
                "day ${insights.daysElapsed}/${insights.daysTotal}")
            if (dailyLimit > 0) {
                val hint = when {
                    limitOver -> "over your ${Format.omr2(dailyLimit)} limit!"
                    limitNear -> "near your ${Format.omr2(dailyLimit)} limit"
                    else -> "limit ${Format.omr2(dailyLimit)} OMR/day"
                }
                InsightLine(
                    "Today",
                    "${Format.omr2(todaySpend)} OMR",
                    hint,
                    valueColor = limitColor
                )
            }
            InsightLine("Projected this period", "${Format.omr2(insights.projectedSpend)} OMR",
                trendSuffix(insights.projectedSpend, prevSpent))
            insights.runwayDays?.let {
                InsightLine("Runway", "~$it days", "balance ÷ avg daily spend")
            }
            insights.largest?.let {
                InsightLine("Largest", "${Format.omr2(it.amount)} OMR", it.merchantClean)
            }
            insights.busiestDayLabel?.let { InsightLine("Busiest day", it, null) }
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
internal fun InsightLine(label: String, value: String, hint: String?, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface
            )
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun MerchantBar(merchant: String, total: Double, count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(merchant, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("$count item${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(Format.omr2(total), style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}
