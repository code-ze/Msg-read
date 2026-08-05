package com.example.smsspend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smsspend.parser.TxnType

/**
 * Merchant "smart view": all-time stats for one merchant (total, count, average, first/last
 * seen) plus the full history, and a one-tap control to re-categorize every transaction
 * from this merchant.
 */
@Composable
fun MerchantDetailScreen(vm: MainViewModel, merchant: String) {
    val txnsFlow = remember(merchant) { vm.txnsForMerchant(merchant) }
    val txns by txnsFlow.collectAsState(initial = emptyList())
    var picking by remember { mutableStateOf(false) }

    val total = txns.sumOf { it.amount }
    val count = txns.size
    val avg = if (count > 0) total / count else 0.0
    val category = txns.firstOrNull()?.category ?: ""
    val subcategory = txns.firstOrNull()?.subcategory ?: ""
    val categoryLabel = if (subcategory.isNotBlank()) "$category › $subcategory" else category
    val isIncome = txns.firstOrNull()
        ?.let { runCatching { TxnType.valueOf(it.type) }.getOrNull() }
        ?.isIncome ?: false
    val last = txns.maxByOrNull { it.date }?.date
    val first = txns.minByOrNull { it.date }?.date

    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryDot(if (subcategory.isNotBlank()) subcategory else category, 16)
                    Text(
                        categoryLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 10.dp)
                    )
                    OutlinedButton(onClick = { picking = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Text("  Re-categorize")
                    }
                }
                HorizontalDivider()
                StatLine(if (isIncome) "Total received" else "Total spent", "${Format.omr(total)} OMR")
                StatLine("Transactions", count.toString())
                StatLine("Average", "${Format.omr(avg)} OMR")
                if (first != null) StatLine("First seen", Format.day(first))
                if (last != null) StatLine("Last seen", Format.day(last))
            }
        }

        Text(
            "History",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            items(txns, key = { it.key }) { t ->
                TxnRow(t) { picking = true }
                HorizontalDivider(Modifier.padding(start = 42.dp))
            }
        }
    }

    if (picking) {
        val categories by vm.categories.collectAsState()
        RecategorizeSheet(
            merchant = merchant,
            categories = categories,
            initialCategory = txns.firstOrNull()?.category ?: "",
            initialSubcategory = txns.firstOrNull()?.subcategory ?: "",
            onApply = { cat, sub ->
                vm.setMerchantCategory(merchant, cat, sub)
                picking = false
            },
            onAddCategory = { name, parent -> vm.addCategory(name, parent) },
            onDismiss = { picking = false }
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
