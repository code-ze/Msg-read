package com.example.smsspend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smsspend.model.Fire

@Composable
fun RetireScreen(vm: MainViewModel) {
    val holdings by vm.holdings.collectAsStateWithLifecycle()
    val portfolioValue = holdings.sumOf { it.marketValue() }

    var target by remember { mutableStateOf("500") }
    var ret by remember { mutableStateOf("7") }
    var invested by remember(portfolioValue) {
        mutableStateOf(if (portfolioValue > 0) Format.omr2(portfolioValue) else "")
    }
    var monthly by remember { mutableStateOf("200") }

    val targetV = target.toDoubleOrNull() ?: 0.0
    val retV = ret.toDoubleOrNull() ?: 0.0
    val investedV = invested.toDoubleOrNull() ?: 0.0
    val monthlyV = monthly.toDoubleOrNull() ?: 0.0
    val plan = Fire.plan(targetV, retV, investedV, monthlyV)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Work out how big an investment pot you need so its yearly return covers your living " +
                "costs — and how long it takes to get there.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your numbers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Num("Target monthly income (OMR)", target) { target = it }
                Num("Expected annual return / dividend yield (%)", ret) { ret = it }
                Num("Currently invested (OMR)", invested) { invested = it }
                Num("Monthly contribution (OMR)", monthly) { monthly = it }
                if (portfolioValue > 0) {
                    Text(
                        "Prefilled from your portfolio: ${Format.omr2(portfolioValue)} OMR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Target nest egg", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (plan.requiredNestEgg.isFinite()) "${Format.money(plan.requiredNestEgg)} OMR" else "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()
                Line("Time to reach", timeToReach(plan.monthsToReach))
                Line("Income your pot makes now", "${Format.omr2(plan.currentMonthlyIncome)} OMR/mo")
                Line("Target monthly income", "${Format.omr2(targetV)} OMR/mo")
            }
        }

        Text(
            "Rule of thumb: nest egg = yearly target ÷ return rate. At a $retV% yield, every " +
                "1 OMR/month of income needs about ${Format.omr2(if (retV > 0) 12.0 / (retV / 100) else 0.0)} OMR " +
                "invested. Returns aren't guaranteed — treat this as a planning guide.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun timeToReach(months: Int): String = when {
    months < 0 -> "Not reachable — raise your contribution"
    months == 0 -> "Already there 🎉"
    months < 12 -> "$months month${if (months == 1) "" else "s"}"
    else -> {
        val y = months / 12
        val m = months % 12
        "$y yr${if (y == 1) "" else "s"}" + if (m > 0) " $m mo" else ""
    }
}

@Composable
private fun Num(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
