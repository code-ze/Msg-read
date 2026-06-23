package com.example.smsspend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.smsspend.model.Period

/**
 * Top control showing the active period, with ◀ ▶ to step through cycles/months/years and
 * a tap-to-open sheet of presets + a custom date-range picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodBar(
    period: Period,
    canStep: Boolean,
    anchorDay: Int,
    salaryDetected: Boolean,
    onStep: (Int) -> Unit,
    onSelect: (PeriodReq) -> Unit,
    modifier: Modifier = Modifier
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var rangeOpen by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = { onStep(-1) }, enabled = canStep) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
            }
            TextButton(
                onClick = { sheetOpen = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    period.label,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(Icons.Default.ExpandMore, contentDescription = "Change period")
            }
            IconButton(onClick = { onStep(1) }, enabled = canStep) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next")
            }
        }
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Choose period", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (salaryDetected)
                        "Pay cycle follows your detected salary deposits (the real landing date each month)."
                    else
                        "No salary detected yet — pay cycle uses day $anchorDay as a fallback (set in Settings).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                fun pick(req: PeriodReq) { onSelect(req); sheetOpen = false }

                PresetRow(
                    "This pay cycle" to { pick(PeriodReq.Salary(0)) },
                    "Last pay cycle" to { pick(PeriodReq.Salary(-1)) }
                )
                PresetRow(
                    "This month" to { pick(PeriodReq.Month(0)) },
                    "Last month" to { pick(PeriodReq.Month(-1)) }
                )
                PresetRow(
                    "This year" to { pick(PeriodReq.Year(0)) },
                    "Last year" to { pick(PeriodReq.Year(-1)) }
                )
                Button(
                    onClick = { sheetOpen = false; rangeOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Text("  Custom range…")
                }
            }
        }
    }

    if (rangeOpen) {
        val state = rememberDateRangePickerState()
        Dialog(
            onDismissRequest = { rangeOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f)
            ) {
                Column {
                    DateRangePicker(state = state, modifier = Modifier.weight(1f))
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { rangeOpen = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val s = state.selectedStartDateMillis
                                val e = state.selectedEndDateMillis ?: s
                                if (s != null && e != null) {
                                    // picker returns UTC midnight; nudge to local midday to avoid off-by-one
                                    onSelect(PeriodReq.Custom(s + 12 * 3600_000L, e + 12 * 3600_000L))
                                }
                                rangeOpen = false
                            },
                            enabled = state.selectedStartDateMillis != null
                        ) { Text("Apply") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetRow(left: Pair<String, () -> Unit>, right: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = left.second, modifier = Modifier.weight(1f)) { Text(left.first) }
        FilledTonalButton(onClick = right.second, modifier = Modifier.weight(1f)) { Text(right.first) }
    }
}
