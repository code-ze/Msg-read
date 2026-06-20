package com.example.smsspend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smsspend.data.TxnEntity
import com.example.smsspend.parser.Categorizer
import com.example.smsspend.parser.TxnType

private val categoryColors = mapOf(
    "Food Delivery" to Color(0xFFEF4444),
    "Cafes & Tea" to Color(0xFFB45309),
    "Restaurants" to Color(0xFFF97316),
    "Groceries" to Color(0xFF22C55E),
    "Fuel & Transport" to Color(0xFF3B82F6),
    "Pharmacy & Health" to Color(0xFF14B8A6),
    "Telecom" to Color(0xFF8B5CF6),
    "Utilities" to Color(0xFF06B6D4),
    "Subscriptions" to Color(0xFFEC4899),
    "Online Shopping" to Color(0xFFF59E0B),
    "Charity" to Color(0xFF10B981),
    Categorizer.TRANSFERS to Color(0xFF6B7280),
    Categorizer.INVESTMENTS to Color(0xFF7C3AED),
    Categorizer.DIVIDENDS to Color(0xFF0EA5E9),
    Categorizer.INCOME to Color(0xFF16A34A),
    Categorizer.OTHER to Color(0xFF94A3B8)
)

fun categoryColor(category: String): Color = categoryColors[category] ?: Color(0xFF94A3B8)

@Composable
fun CategoryDot(category: String, size: Int = 12) {
    Box(
        Modifier
            .size(size.dp)
            .background(categoryColor(category), CircleShape)
    )
}

@Composable
fun TxnRow(txn: TxnEntity, onClick: () -> Unit) {
    val type = runCatching { TxnType.valueOf(txn.type) }.getOrDefault(TxnType.DEBIT)
    val positive = type.isIncome
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        CategoryDot(txn.category, 14)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                txn.merchantClean,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                "${txn.category} · ${Format.dayTime(txn.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            Format.omrSigned(txn.amount, positive),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (positive) categoryColor(Categorizer.INCOME) else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

/** Picker that learns a category for the whole merchant (retroactive re-categorization). */
@Composable
fun RecategorizeDialog(
    merchant: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorize $merchant") },
        text = {
            Column {
                Text(
                    "Applies to every transaction from this merchant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    Modifier
                        .padding(top = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Categorizer.allCategories.forEach { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(cat) }
                                .padding(vertical = 10.dp)
                        ) {
                            CategoryDot(cat)
                            Text(cat, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
