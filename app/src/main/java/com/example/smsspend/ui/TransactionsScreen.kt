package com.example.smsspend.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TransactionsScreen(vm: MainViewModel) {
    val period by vm.period.collectAsStateWithLifecycle()
    val anchor by vm.anchorDay.collectAsStateWithLifecycle()
    val salaryDates by vm.salaryDates.collectAsStateWithLifecycle()
    val txns by vm.recentTxns.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf<String?>(null) }

    val categories = remember(txns) { txns.map { it.category }.distinct().sorted() }
    val filtered = remember(txns, query, selectedCat) {
        txns.filter { t ->
            (selectedCat == null || t.category == selectedCat) &&
                (query.isBlank() ||
                    t.merchantClean.contains(query, ignoreCase = true) ||
                    t.category.contains(query, ignoreCase = true) ||
                    t.subcategory.contains(query, ignoreCase = true))
        }
    }
    val total = filtered.sumOf { it.amount }

    Column(Modifier.fillMaxSize()) {
        PeriodBar(
            period = period,
            canStep = vm.canStep,
            anchorDay = anchor,
            salaryDetected = salaryDates.isNotEmpty(),
            onStep = { vm.stepPeriod(it) },
            onSelect = { vm.setPeriod(it) }
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search merchant or category") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedCat == null,
                    onClick = { selectedCat = null },
                    label = { Text("All") },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCat == cat,
                    onClick = { selectedCat = if (selectedCat == cat) null else cat },
                    label = { Text(cat) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        Text(
            "${Format.omr2(total)} OMR · ${filtered.size} item${if (filtered.size == 1) "" else "s"}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        if (filtered.isEmpty()) {
            Text(
                "No matching transactions.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(filtered, key = { it.key }) { t ->
                    TxnRow(t) { vm.navigate(Screen.Merchant(t.merchantClean)) }
                    HorizontalDivider(Modifier.padding(start = 42.dp))
                }
            }
        }
    }
}
