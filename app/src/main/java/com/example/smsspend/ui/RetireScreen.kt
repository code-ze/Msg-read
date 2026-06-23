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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smsspend.model.Fire

@Composable
fun RetireScreen(vm: MainViewModel) {
    val holdings by vm.holdings.collectAsStateWithLifecycle()
    val surplus by vm.monthlyNetEstimate.collectAsStateWithLifecycle()
    val portfolioValue = holdings.sumOf { it.marketValue() }

    var target by remember { mutableStateOf("500") }
    var ret by remember { mutableStateOf("7") }
    var invested by remember(portfolioValue) {
        mutableStateOf(if (portfolioValue > 0) Format.omr2(portfolioValue) else "")
    }
    var horizon by remember { mutableIntStateOf(10) }
    var contribution by remember(surplus) {
        mutableStateOf(if (surplus > 0) Format.omr2(surplus) else "200")
    }

    val targetV = target.toDoubleOrNull() ?: 0.0
    val retV = ret.toDoubleOrNull() ?: 0.0
    val investedV = invested.toDoubleOrNull() ?: 0.0
    val contribV = contribution.toDoubleOrNull() ?: 0.0

    val nestEgg = Fire.requiredNestEgg(targetV, retV)
    val progress = if (nestEgg.isFinite() && nestEgg > 0) (investedV / nestEgg).toFloat() else 0f
    val requiredForHorizon =
        if (nestEgg.isFinite()) Fire.requiredMonthlyContribution(investedV, nestEgg, retV, horizon * 12) else Double.POSITIVE_INFINITY
    val monthsAtPace = if (nestEgg.isFinite()) Fire.monthsToReach(investedV, contribV, nestEgg, retV) else -1
    val projectedAtHorizon = Fire.futureValue(investedV, contribV, retV, horizon * 12)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Plan your financial independence: how big a pot you need to live off its returns, " +
                "and exactly what to invest each month to get there by your target date.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ---- the headline question ----
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("I want to retire in…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(5, 10, 15, 20, 25)) { y ->
                        FilterChip(
                            selected = horizon == y,
                            onClick = { horizon = y },
                            label = { Text("$y yrs") }
                        )
                    }
                }
                HorizontalDivider()
                Text("To get there, invest", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (requiredForHorizon.isFinite()) "${Format.money(requiredForHorizon)} OMR/mo" else "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(advice(requiredForHorizon, surplus, horizon),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (surplus > 0) {
                    Text("Your estimated monthly surplus right now: ${Format.omr2(surplus)} OMR " +
                        "(income − spending, last 90 days).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ---- the goal ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Num("Monthly income you want in retirement (OMR)", target) { target = it }
                Num("Expected annual return / dividend yield (%)", ret) { ret = it }
                Num("Currently invested (OMR)", invested) { invested = it }

                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Target nest egg", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (nestEgg.isFinite()) "${Format.money(nestEgg)} OMR" else "—",
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                ProgressBar(progress)
                Text(
                    if (nestEgg.isFinite())
                        "${(progress * 100).toInt().coerceIn(0, 100)}% of the way there · " +
                            "your pot makes ${Format.omr2(Fire.monthlyIncome(investedV, retV))} OMR/mo today"
                    else "Enter a return rate above 0 to compute your target.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- forward mode: your own pace ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Or set your own pace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Num("Monthly contribution (OMR)", contribution) { contribution = it }
                Line("Reaches your goal in", timeToReach(monthsAtPace))
                Line("Pot value in $horizon yrs", "${Format.money(projectedAtHorizon)} OMR")
                Line("Income then", "${Format.omr2(Fire.monthlyIncome(projectedAtHorizon, retV))} OMR/mo")
            }
        }

        Text(
            "Rule of thumb: nest egg = yearly target ÷ return rate. At $retV%, each 1 OMR/month of " +
                "future income needs about ${Format.omr2(if (retV > 0) 12.0 / (retV / 100) else 0.0)} OMR invested. " +
                "Returns aren't guaranteed — use this as a planning guide, and trim spending in Insights to grow your surplus.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun advice(required: Double, surplus: Double, horizon: Int): String = when {
    !required.isFinite() -> "Set an expected return above 0% to plan."
    required <= 0.0 -> "You're already there — your current pot reaches the goal in time. 🎉"
    surplus <= 0.0 -> "Aim to invest ${Format.money(required)} OMR/month. Free up cash by trimming spending (see Insights)."
    required <= surplus -> "Within reach — your ~${Format.omr2(surplus)} OMR/month surplus already covers this. Automate it and stay consistent."
    else -> "That's about ${Format.money(required - surplus)} OMR/month more than your current surplus. " +
        "Options: extend the horizon, cut spending to raise your surplus, or invest for a higher yield."
}

private fun timeToReach(months: Int): String = when {
    months < 0 -> "Not reachable at this pace"
    months == 0 -> "Already there 🎉"
    months < 12 -> "$months month${if (months == 1) "" else "s"}"
    else -> {
        val y = months / 12; val m = months % 12
        "$y yr${if (y == 1) "" else "s"}" + if (m > 0) " $m mo" else ""
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(10.dp)
                .clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary)
        )
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
