package com.example.smsspend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smsspend.model.Recommendations

@Composable
fun InsightsScreen(vm: MainViewModel) {
    val period by vm.period.collectAsStateWithLifecycle()
    val anchor by vm.anchorDay.collectAsStateWithLifecycle()
    val salaryDates by vm.salaryDates.collectAsStateWithLifecycle()
    val recs by vm.recommendations.collectAsStateWithLifecycle()
    val safe by vm.safeToSpend.collectAsStateWithLifecycle()
    val insights by vm.insights.collectAsStateWithLifecycle()
    val balance by vm.balance.collectAsStateWithLifecycle()

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
            item { SafeToSpendCard(safe, insights.perDay, balance) }

            item { SectionHeader("Recommendations") }

            if (recs.isEmpty()) {
                item {
                    Text(
                        "You're on track — no actions needed right now. Recommendations appear as " +
                            "spending patterns, idle cash, or rising bills show up.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(recs, key = { it.kind + it.title }) { rec -> RecCard(rec) }
            }

            item {
                Text(
                    "How this works: Safe-to-spend = balance ÷ days left in the cycle. Idle cash = " +
                        "balance above a 6-month buffer of your average spend. Fixed-cost audits compare " +
                        "this period to your 3-month rolling average. All computed on-device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            item { Box(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SafeToSpendCard(safe: Double, perDay: Double, balance: Double) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Safe to spend", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (safe > 0) Format.omr2(safe) else "—",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("  OMR/day", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            val pace = when {
                safe <= 0 -> "Add your balance to see a daily budget."
                perDay <= safe -> "You're under your daily budget — nice pace."
                else -> "You're spending ${Format.omr2(perDay)} OMR/day, above this budget."
            }
            Text(pace, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun RecCard(rec: Recommendations.Rec) {
    val accent = when (rec.kind) {
        Recommendations.SAFE_TO_SPEND -> MaterialTheme.colorScheme.error
        Recommendations.MICRO_LEAK -> Color(0xFFE0A45B)
        Recommendations.IDLE_CASH -> MaterialTheme.colorScheme.tertiary
        Recommendations.FIXED_COST -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.padding(16.dp)) {
            Box(Modifier.size(10.dp).background(accent, CircleShape).padding(top = 6.dp))
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(rec.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(rec.body, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
