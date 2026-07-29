package com.example.smsspend.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smsspend.data.TxnEntity
import com.example.smsspend.parser.Categorizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(vm: MainViewModel) {
    val period by vm.period.collectAsStateWithLifecycle()
    val anchor by vm.anchorDay.collectAsStateWithLifecycle()
    val salaryDates by vm.salaryDates.collectAsStateWithLifecycle()
    val txns by vm.recentTxns.collectAsStateWithLifecycle()
    val categoryDefs by vm.categories.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf<String?>(null) }

    // Sheet state: which transaction is selected, and which editor (if any) is open.
    var selected by remember { mutableStateOf<TxnEntity?>(null) }
    var editor by remember { mutableStateOf<EditorMode?>(null) }

    val categories = remember(txns) { txns.map { it.category }.distinct().sorted() }
    val pickerCategories = remember(categoryDefs) {
        categoryDefs.filter { it.parent.isBlank() }.map { it.name }
            .ifEmpty { Categorizer.allCategories }
    }
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

    Box(Modifier.fillMaxSize()) {
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
                        // Tapping opens the actions sheet — editing lives here rather than on the
                        // dashboard, where a tap should still jump straight to the merchant.
                        TxnRow(t) { selected = t }
                        HorizontalDivider(Modifier.padding(start = 42.dp))
                    }
                    item { Box(Modifier.padding(bottom = 88.dp)) {} }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { editor = EditorMode.Add },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Add") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }

    // Actions for a tapped row.
    selected?.let { txn ->
        if (editor == null) {
            ModalBottomSheet(onDismissRequest = { selected = null }) {
                TxnActionSheet(
                    txn = txn,
                    onEdit = { editor = EditorMode.Edit(txn) },
                    onRefund = { editor = EditorMode.Refund(txn) },
                    onDelete = {
                        vm.deleteTxn(txn)
                        selected = null
                    },
                    onViewMerchant = {
                        selected = null
                        vm.navigate(Screen.Merchant(txn.merchantClean))
                    }
                )
            }
        }
    }

    // Add / edit / refund form.
    editor?.let { mode ->
        ModalBottomSheet(onDismissRequest = { editor = null; selected = null }) {
            TxnEditorSheet(
                mode = mode,
                categories = pickerCategories,
                onSave = { amount, merchant, category, date ->
                    when (mode) {
                        is EditorMode.Edit -> vm.updateTxn(
                            mode.txn.copy(
                                amount = amount,
                                merchantClean = merchant,
                                category = category,
                                date = date
                            )
                        )
                        // A refund is its own row rather than an edit of the charge, so the
                        // original spend stays visible and the two net out.
                        is EditorMode.Refund -> vm.addManualTxn(
                            amount = amount,
                            merchant = merchant,
                            category = category,
                            date = date
                        )
                        EditorMode.Add -> vm.addManualTxn(
                            amount = amount,
                            merchant = merchant,
                            category = category,
                            date = date
                        )
                    }
                    editor = null
                    selected = null
                },
                onDismiss = { editor = null; selected = null }
            )
        }
    }
}
