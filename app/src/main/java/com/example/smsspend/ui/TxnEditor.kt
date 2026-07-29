package com.example.smsspend.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.smsspend.data.TxnEntity
import com.example.smsspend.parser.Categorizer
import kotlin.math.abs

/** What the editor is being opened for; decides the title, defaults and save behaviour. */
sealed interface EditorMode {
    /** A brand-new transaction the bank never messaged about. */
    data object Add : EditorMode
    /** Correcting an existing row. */
    data class Edit(val txn: TxnEntity) : EditorMode
    /** Money coming back on an existing charge — prefilled from it, amount still adjustable. */
    data class Refund(val txn: TxnEntity) : EditorMode
}

/** Expense or refund. Refunds are stored as a negative amount so they net out of every total. */
private enum class Direction(val label: String) { OUT("Expense"), IN("Refund / income") }

/**
 * One sheet for adding, editing and refunding. A partial refund is the common case — the card
 * statement shows money back that never arrived as an SMS — so the amount is always editable
 * rather than assumed to match the original charge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxnEditorSheet(
    mode: EditorMode,
    categories: List<String>,
    onSave: (amount: Double, merchant: String, category: String, date: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val source = when (mode) {
        is EditorMode.Edit -> mode.txn
        is EditorMode.Refund -> mode.txn
        EditorMode.Add -> null
    }

    var direction by remember {
        mutableStateOf(
            when {
                mode is EditorMode.Refund -> Direction.IN
                source != null && source.amount < 0 -> Direction.IN
                else -> Direction.OUT
            }
        )
    }
    var amountText by remember {
        mutableStateOf(source?.let { Format.omr(abs(it.amount)) } ?: "")
    }
    var merchant by remember { mutableStateOf(source?.merchantClean ?: "") }
    var category by remember {
        mutableStateOf(source?.category ?: Categorizer.OTHER)
    }
    var date by remember { mutableStateOf(source?.date ?: System.currentTimeMillis()) }
    var showPicker by remember { mutableStateOf(false) }

    val title = when (mode) {
        EditorMode.Add -> "Add transaction"
        is EditorMode.Edit -> "Edit transaction"
        is EditorMode.Refund -> "Record a refund"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        if (mode is EditorMode.Refund) {
            Text(
                "Refunding ${mode.txn.merchantClean} — originally ${Format.omr(abs(mode.txn.amount))} OMR. " +
                    "Enter the amount you actually got back; a partial refund is fine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Direction.values().forEach { d ->
                FilterChip(
                    selected = direction == d,
                    onClick = { direction = d },
                    label = { Text(d.label) }
                )
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Amount (OMR)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text("Merchant / description") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Category", style = MaterialTheme.typography.labelLarge)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Column(Modifier.weight(1f)) {}
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Date: ${Format.day(date)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { showPicker = true }) { Text("Change") }
        }

        val amount = amountText.toDoubleOrNull() ?: 0.0
        val valid = amount > 0.0 && merchant.isNotBlank()

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = {
                    val signed = if (direction == Direction.IN) -amount else amount
                    onSave(signed, merchant.trim(), category, date)
                },
                enabled = valid,
                modifier = Modifier.weight(1f)
            ) { Text("Save") }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = it }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }
}

/**
 * Shown when a transaction row is tapped: what it is, and the things you can do to it.
 * Imported rows can be corrected but keep their SMS provenance.
 */
@Composable
fun TxnActionSheet(
    txn: TxnEntity,
    onEdit: () -> Unit,
    onRefund: () -> Unit,
    onDelete: () -> Unit,
    onViewMerchant: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(txn.merchantClean, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            Format.omrSigned(abs(txn.amount), txn.isRefund) + " OMR · ${Format.dayTime(txn.date)}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (txn.isRefund) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
        )
        Text(
            buildString {
                append(txn.category)
                if (txn.subcategory.isNotBlank()) append(" › ${txn.subcategory}")
                if (txn.isForeign) append(" · billed ${txn.currency} ${Format.omr2(txn.originalAmount)}")
                if (txn.manual) append(" · added by you")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SheetAction("Edit") { onEdit() }
            // Only an outgoing charge can be refunded; refunding a refund makes no sense.
            if (!txn.isRefund) SheetAction("Record a refund") { onRefund() }
            SheetAction("See all from this merchant") { onViewMerchant() }
            SheetAction("Delete", destructive = true) { onDelete() }
        }
    }
}

@Composable
private fun SheetAction(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    )
}
