package com.example.smsspend.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
            LazyColumn(Modifier.fillMaxWidth()) {
                items(txns, key = { it.key }) { t ->
                    TxnRow(t) { vm.navigate(Screen.Merchant(t.merchantClean)) }
                    HorizontalDivider(Modifier.padding(start = 42.dp))
                }
            }
        }
    }
}
