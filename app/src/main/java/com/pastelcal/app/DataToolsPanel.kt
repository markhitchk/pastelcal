package com.pastelcal.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pastelcal.app.model.PastelCategory
import com.pastelcal.app.preferences.AppSettings
import com.pastelcal.app.transfer.BackupCodec
import com.pastelcal.app.transfer.BackupPayload
import com.pastelcal.app.transfer.IcsTransfer
import com.pastelcal.app.ui.theme.*
import com.pastelcal.app.widget.PastelCalWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DataToolsPanel(
    vm: CalendarViewModel,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allItems by vm.items.collectAsState()
    val completionRecords by vm.completionRecords.collectAsState()
    val cycleEntries by vm.cycleEntries.collectAsState()
    val history by vm.notificationHistory.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<BackupPayload?>(null) }
    var pendingIcsText by remember { mutableStateOf<String?>(null) }
    var pendingBackupText by remember { mutableStateOf<String?>(null) }

    fun update(value: AppSettings) {
        onSettingsChange(value)
        PastelCalWidgetProvider.updateAll(context)
    }

    val createIcs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        val text = pendingIcsText
        pendingIcsText = null
        if (uri != null && text != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching {
                    val stream = context.contentResolver.openOutputStream(uri) ?: error("Unable to open destination")
                    stream.bufferedWriter().use { it.write(text) }
                    true
                }.getOrDefault(false) }
            onMessage(if (ok) "ICS calendar exported" else "Could not write ICS file")
        }
    }
    val openIcs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Empty file")
                    IcsTransfer.import(text)
                }
            }
            if (result.isSuccess) {
                val imported = result.getOrThrow()
                val added = vm.importItems(imported)
                onMessage("Imported $added ICS items")
            } else {
                val error = result.exceptionOrNull()
                onMessage("ICS import failed: ${error?.message ?: "invalid file"}")
            }
        }
    }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = pendingBackupText
        pendingBackupText = null
        if (uri != null && text != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching {
                    val stream = context.contentResolver.openOutputStream(uri) ?: error("Unable to open destination")
                    stream.bufferedWriter().use { it.write(text) }
                    true
                }.getOrDefault(false) }
            onMessage(if (ok) "PastelCal backup saved" else "Could not write backup")
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Empty file")
                    BackupCodec.decode(text)
                }
            }
            result.onSuccess { pendingRestore = it }
                .onFailure { onMessage("Backup could not be read: ${it.message ?: "invalid backup"}") }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Data & transfer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = Lavender.copy(alpha = .23f)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SwapVert, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ICS import / export", fontWeight = FontWeight.SemiBold)
                        Text("Move calendar data through standard .ics files using Android's file picker.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openIcs.launch(arrayOf("text/calendar", "text/*", "application/octet-stream")) }, modifier = Modifier.weight(1f)) { Text("Import ICS") }
                    Button(onClick = {
                        pendingIcsText = IcsTransfer.export(allItems)
                        createIcs.launch("PastelCal-${LocalDate.now()}.ics")
                    }, modifier = Modifier.weight(1f)) { Text("Export ICS") }
                }
            }
        }

        Surface(shape = RoundedCornerShape(20.dp), color = Mint.copy(alpha = .24f)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Backup, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Local backup", fontWeight = FontWeight.SemiBold)
                        Text("Back up events, cycle history, recurring-task completion state, and PastelCal preferences.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f)) { Text("Restore") }
                    Button(onClick = {
                        pendingBackupText = BackupCodec.encode(allItems, completionRecords, cycleEntries, settings)
                        createBackup.launch("PastelCal-backup-${LocalDate.now()}.json")
                    }, modifier = Modifier.weight(1f)) { Text("Back up") }
                }
            }
        }

        Surface(shape = RoundedCornerShape(20.dp), color = Peach.copy(alpha = .24f)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Notification history", fontWeight = FontWeight.SemiBold)
                        Text("Keeps the latest 100 reminder actions inside PastelCal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AssistChip(onClick = { showHistory = true }, label = { Text(history.size.toString()) })
                }
                OutlinedButton(onClick = { showHistory = true }, modifier = Modifier.fillMaxWidth()) { Text("View history") }
            }
        }

        Text("Widget customization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = Sky.copy(alpha = .22f)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pastel background", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PastelCategory.entries.forEach { option ->
                        Surface(
                            onClick = { update(settings.copy(widgetPastel = option)) },
                            shape = CircleShape,
                            color = settingCategoryColor(option),
                            border = if (settings.widgetPastel == option) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "${option.label} widget color" }
                        ) {}
                    }
                }
                Text("Transparency · ${settings.widgetTransparencyPercent}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.widgetTransparencyPercent.toFloat(),
                    onValueChange = { update(settings.copy(widgetTransparencyPercent = it.toInt())) },
                    valueRange = 0f..85f,
                    steps = 16
                )
                Text("Text size · ${settings.widgetTextSizeSp}sp", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.widgetTextSizeSp.toFloat(),
                    onValueChange = { update(settings.copy(widgetTextSizeSp = it.toInt())) },
                    valueRange = 11f..20f,
                    steps = 8
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show completed tasks", fontWeight = FontWeight.Medium)
                        Text("Include completed task occurrences in the agenda widget.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.widgetShowCompletedTasks, onCheckedChange = { update(settings.copy(widgetShowCompletedTasks = it)) })
                }
            }
        }
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Reminder history") },
            text = {
                if (history.isEmpty()) Text("No reminders have fired yet.")
                else LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history, key = { it.id }) { row ->
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(row.title, fontWeight = FontWeight.Medium)
                                val whenText = Instant.ofEpochMilli(row.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d · h:mm a"))
                                Text("${row.action.lowercase().replaceFirstChar { it.uppercase() }} · $whenText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Done") } },
            dismissButton = {
                if (history.isNotEmpty()) TextButton(onClick = { vm.clearNotificationHistory(); onMessage("Notification history cleared") }) { Text("Clear") }
            }
        )
    }

    pendingRestore?.let { payload ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            icon = { Icon(Icons.Default.Restore, null) },
            title = { Text("Replace current PastelCal data?") },
            text = { Text("This backup contains ${payload.items.size} calendar items and ${payload.cycleEntries.size} cycle records. Restoring replaces the current local PastelCal calendar, cycle history, and occurrence completion state.") },
            confirmButton = {
                Button(onClick = {
                    pendingRestore = null
                    scope.launch {
                        vm.replaceAllFromBackup(payload.items, payload.completions, payload.cycleEntries)
                        onSettingsChange(payload.settings)
                        PastelCalWidgetProvider.updateAll(context)
                        onMessage("Backup restored")
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun settingCategoryColor(category: PastelCategory): Color = when (category) {
    PastelCategory.LAVENDER -> Lavender
    PastelCategory.SKY -> Sky
    PastelCategory.MINT -> Mint
    PastelCategory.PEACH -> Peach
    PastelCategory.PINK -> Pink
    PastelCategory.BUTTER -> Butter
}
