package com.example.smsspend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.smsspend.data.CategoryDef
import com.example.smsspend.parser.Categorizer
import com.example.smsspend.parser.TxnType
import kotlin.math.abs

// Cohesive, muted palette (teals / blues / purples) tuned for dark mode.
private val categoryColors = mapOf(
    "Food Delivery" to Color(0xFFE57373),
    "Cafes & Tea" to Color(0xFFC9A36A),
    "Restaurants" to Color(0xFFE0A45B),
    "Groceries" to Color(0xFF5FB99A),
    "Fuel & Transport" to Color(0xFF5B8DEF),
    "Pharmacy & Health" to Color(0xFF4DB6AC),
    "Telecom" to Color(0xFF9B8CFF),
    "Utilities" to Color(0xFF4FC3E0),
    "Rent" to Color(0xFFB57EDC),
    "Subscriptions" to Color(0xFFE08AB0),
    "Online Shopping" to Color(0xFFD9A441),
    "Charity" to Color(0xFF6BC9A0),
    Categorizer.TRANSFERS to Color(0xFF8A93A0),
    Categorizer.INVESTMENTS to Color(0xFF8B7BD8),
    Categorizer.DIVIDENDS to Color(0xFF4FA3D1),
    Categorizer.INCOME to Color(0xFF4DD0B1),
    Categorizer.OTHER to Color(0xFF8A93A0)
)

/** The grouped "Other" slice color for donuts (top-N + rest). */
val otherSliceColor = Color(0xFF8A93A0)

/** Stable palette so custom categories / sub-categories get a consistent, distinct color. */
private val fallbackPalette = listOf(
    Color(0xFF4DB6AC), Color(0xFF5B8DEF), Color(0xFF9B8CFF), Color(0xFF5FB99A),
    Color(0xFF4FC3E0), Color(0xFFB57EDC), Color(0xFFE08AB0), Color(0xFFD9A441),
    Color(0xFF6BC9A0), Color(0xFF7FB0E6), Color(0xFF4FA3D1), Color(0xFFC9A36A)
)

fun categoryColor(name: String): Color =
    categoryColors[name] ?: fallbackPalette[abs(name.hashCode()) % fallbackPalette.size]

@Composable
fun CategoryDot(category: String, size: Int = 12) {
    Box(
        Modifier
            .size(size.dp)
            .background(categoryColor(category), CircleShape)
    )
}

@Composable
fun TxnRow(txn: com.example.smsspend.data.TxnEntity, onClick: () -> Unit) {
    val type = runCatching { TxnType.valueOf(txn.type) }.getOrDefault(TxnType.DEBIT)
    val positive = type.isIncome
    val tag = if (txn.subcategory.isNotBlank()) "${txn.category} › ${txn.subcategory}" else txn.category
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        CategoryDot(if (txn.subcategory.isNotBlank()) txn.subcategory else txn.category, 14)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                txn.merchantClean,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                "$tag · ${Format.dayTime(txn.date)}",
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

/**
 * Re-categorize sheet that learns a category **and** an optional sub-category for the whole
 * merchant (retroactive). You can also create a brand-new category or sub-category inline.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecategorizeSheet(
    merchant: String,
    categories: List<CategoryDef>,
    initialCategory: String,
    initialSubcategory: String,
    onApply: (category: String, subcategory: String) -> Unit,
    onAddCategory: (name: String, parent: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(initialCategory) }
    var selectedSub by remember { mutableStateOf(initialSubcategory) }
    var newCat by remember { mutableStateOf("") }
    var newSub by remember { mutableStateOf("") }

    val topLevel = categories.filter { it.parent.isBlank() }
        .map { it.name }
        .ifEmpty { Categorizer.allCategories }
    val subs = categories.filter { it.parent == selected }.map { it.name }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Categorize $merchant", style = MaterialTheme.typography.titleLarge)
            Text(
                "Applies to every transaction from this merchant, past and future.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topLevel.forEach { cat ->
                    Chip(label = cat, selected = cat == selected, dotColor = categoryColor(cat)) {
                        selected = cat; selectedSub = ""
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newCat, onValueChange = { newCat = it },
                    label = { Text("New category") }, singleLine = true, modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onAddCategory(newCat, ""); selected = newCat.trim(); newCat = "" },
                    enabled = newCat.isNotBlank()
                ) { Text("Add") }
            }

            if (selected.isNotBlank()) {
                Text("Sub-category (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(label = "None", selected = selectedSub.isBlank(), dotColor = null) { selectedSub = "" }
                    subs.forEach { sub ->
                        Chip(label = sub, selected = sub == selectedSub, dotColor = categoryColor(sub)) {
                            selectedSub = sub
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newSub, onValueChange = { newSub = it },
                        label = { Text("New sub-category") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { onAddCategory(newSub, selected); selectedSub = newSub.trim(); newSub = "" },
                        enabled = newSub.isNotBlank()
                    ) { Text("Add") }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { onApply(selected, selectedSub) },
                    enabled = selected.isNotBlank()
                ) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, dotColor: Color?, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (dotColor != null && !selected) {
            Box(Modifier.size(10.dp).background(dotColor, CircleShape))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}
