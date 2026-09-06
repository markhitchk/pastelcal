package com.pastelcal.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pastelcal.app.model.CycleEntry
import com.pastelcal.app.model.CyclePredictionEngine
import com.pastelcal.app.preferences.AppSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodTrackerScreen(
    settings: AppSettings,
    entries: List<CycleEntry>,
    onSettingsChange: (AppSettings) -> Unit,
    onStartPeriod: (LocalDate) -> Unit,
    onEndPeriod: (Long, LocalDate) -> Unit,
    onDeleteCycleEntry: (Long) -> Unit,
    onMessage: (String) -> Unit
) {
    val today = LocalDate.now()
    val prediction = remember(entries) { CyclePredictionEngine.predict(entries) }
    val openRecord = remember(entries) { entries.filter { it.endDate == null }.maxByOrNull { it.startDate } }
    var selectedDate by remember { mutableStateOf(today) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CycleEntry?>(null) }
    val short = remember { DateTimeFormatter.ofPattern("MMM d") }
    val long = remember { DateTimeFormatter.ofPattern("MMMM d, yyyy") }

    val selectedRecord = entries.firstOrNull { record ->
        val end = record.endDate ?: record.startDate
        !selectedDate.isBefore(record.startDate) && !selectedDate.isAfter(end)
    }
    val canEndOpen = openRecord != null &&
        !selectedDate.isBefore(openRecord.startDate) &&
        !selectedDate.isAfter(openRecord.startDate.plusDays(14)) &&
        !selectedDate.isAfter(today)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Period Tracker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Private, local cycle history and calendar estimates.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TrackerSwitchRow(
                        title = "Period tracking",
                        subtitle = "Keep period dates on this device and use them for cycle estimates.",
                        checked = settings.cycleTrackingEnabled,
                        onCheckedChange = { onSettingsChange(settings.copy(cycleTrackingEnabled = it)) }
                    )
                    HorizontalDivider()
                    TrackerSwitchRow(
                        title = "Estimated fertile window",
                        subtitle = "Show date-only fertility estimates. This does not detect ovulation.",
                        checked = settings.showEstimatedFertileWindow,
                        enabled = settings.cycleTrackingEnabled,
                        onCheckedChange = { onSettingsChange(settings.copy(showEstimatedFertileWindow = it)) }
                    )
                }
            }
        }

        if (!settings.cycleTrackingEnabled) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Favorite, contentDescription = null)
                        Text("Your tracker is off", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Enable Period Tracker to log starts and ends, view your history, and get calendar-based estimates.")
                        Button(onClick = { onSettingsChange(settings.copy(cycleTrackingEnabled = true)) }) {
                            Text("Enable Period Tracker")
                        }
                    }
                }
            }
        } else {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .62f)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Insights, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Cycle overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            prediction?.let { AssistChip(onClick = {}, label = { Text(it.confidence.label) }) }
                        }
                        if (prediction == null) {
                            Text("Log your first period start to begin your cycle history.")
                        } else {
                            Text(
                                "Next period estimate · ${prediction.expectedStart.format(short)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Likely start ${prediction.likelyStartFrom.format(short)}–${prediction.likelyStartTo.format(short)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TrackerMetric("Cycle", "${prediction.estimatedCycleLengthDays} days", Modifier.weight(1f))
                                TrackerMetric("Period", "${prediction.estimatedPeriodLengthDays} days", Modifier.weight(1f))
                            }
                            Text(
                                if (prediction.completedCycleCount < 2) "Early estimate — more logged starts will personalize the range."
                                else "${prediction.completedCycleCount} recent cycle intervals used · variability ${String.format(Locale.US, "%.1f", prediction.variabilityDays)} days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .38f)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Log a period", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(selectedDate.format(long))
                        }

                        when {
                            openRecord != null && canEndOpen -> {
                                Button(
                                    onClick = {
                                        onEndPeriod(openRecord.id, selectedDate)
                                        onMessage("Period end saved for ${selectedDate.format(short)}")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("End period on this date") }
                            }
                            selectedRecord != null -> {
                                Text("This date is already part of a recorded period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = { pendingDelete = selectedRecord }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Remove this period record")
                                }
                            }
                            openRecord != null -> {
                                Text(
                                    "A period starting ${openRecord.startDate.format(short)} is still open. Choose an end date within 14 days of that start.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            selectedDate.isAfter(today) -> {
                                Text("Future dates are reserved for estimates. Log the start after it begins.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        onStartPeriod(selectedDate)
                                        onMessage("Period start saved for ${selectedDate.format(short)}")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Period started on this date") }
                            }
                        }
                    }
                }
            }

            if (prediction != null) {
                item {
                    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .32f)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Prediction details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Estimated period: ${prediction.expectedStart.format(short)}–${prediction.expectedPeriodEnd.format(short)}")
                            if (settings.showEstimatedFertileWindow && prediction.fertileWindowStart != null && prediction.fertileWindowEnd != null) {
                                Text("Estimated fertile window: ${prediction.fertileWindowStart.format(short)}–${prediction.fertileWindowEnd.format(short)}")
                            }
                            Text(
                                "Calendar estimates are based only on dates you log. They do not confirm ovulation, diagnose a condition, or provide reliable contraception.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Period history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }

            if (entries.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)) {
                        Text("No period history yet.", modifier = Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(entries.sortedByDescending { it.startDate }, key = { it.id }) { entry ->
                    val end = entry.endDate
                    val length = end?.let { it.toEpochDay() - entry.startDate.toEpochDay() + 1 }
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.startDate.format(long), fontWeight = FontWeight.SemiBold)
                                Text(
                                    when {
                                        end == null -> "In progress"
                                        length != null -> "Ended ${end.format(short)} · $length days"
                                        else -> "Ended ${end.format(short)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { pendingDelete = entry }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove period record")
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showDatePicker) {
        val initialMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Remove period record?") },
            text = { Text("This removes the period record starting ${record.startDate.format(short)} and recalculates your estimates.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteCycleEntry(record.id)
                    pendingDelete = null
                    onMessage("Period record removed")
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TrackerSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun TrackerMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .55f)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}
