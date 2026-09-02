package com.pastelcal.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pastelcal.app.diagnostics.CrashReporter
import com.pastelcal.app.diagnostics.DiagnosticsReport
import com.pastelcal.app.ui.theme.Lavender
import com.pastelcal.app.ui.theme.Mint
import com.pastelcal.app.ui.theme.Pink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReleaseSettingsPanel(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPrivacy by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showCrash by remember { mutableStateOf(false) }
    var pendingDiagnostics by remember { mutableStateOf<String?>(null) }
    val hasCrash = remember(showCrash) { CrashReporter.readLastCrash(context) != null }

    val createDiagnostics = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = pendingDiagnostics
        pendingDiagnostics = null
        if (uri != null && text != null) {
            scope.launch(Dispatchers.IO) {
                val ok = runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) } }.isSuccess
                withContext(Dispatchers.Main) { onMessage(if (ok) "Diagnostics exported" else "Could not export diagnostics") }
            }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Release & privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))

        Surface(shape = RoundedCornerShape(20.dp), color = Mint.copy(alpha = .23f)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BugReport, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Diagnostics", fontWeight = FontWeight.SemiBold)
                        Text("Export device/build status without event titles, notes, locations, or account names.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val report = withContext(Dispatchers.IO) { DiagnosticsReport.build(context) }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("PastelCal diagnostics", report))
                            onMessage("Diagnostics copied")
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Copy") }
                    Button(onClick = {
                        scope.launch {
                            pendingDiagnostics = withContext(Dispatchers.IO) { DiagnosticsReport.build(context) }
                            createDiagnostics.launch("PastelCal-diagnostics.txt")
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Export") }
                }
                if (hasCrash) {
                    TextButton(onClick = { showCrash = true }) { Text("View last local crash") }
                }
            }
        }

        Surface(shape = RoundedCornerShape(20.dp), color = Pink.copy(alpha = .20f), onClick = { showPrivacy = true }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PrivacyTip, null)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Privacy", fontWeight = FontWeight.SemiBold)
                    Text("Local-first storage and explicit exports", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Surface(shape = RoundedCornerShape(20.dp), color = Lavender.copy(alpha = .20f), onClick = { showAbout = true }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("About PastelCal", fontWeight = FontWeight.SemiBold)
                    Text("Version 1.1.0 · Android 8.0+", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            icon = { Icon(Icons.Default.PrivacyTip, null) },
            title = { Text("PastelCal privacy") },
            text = { Text("PastelCal stores its calendar database locally on your device. It does not include analytics or an app account. Android Calendar access is optional and only used when you choose device-calendar import/export. ICS files, backups, and diagnostics are written only after you select a destination with Android's file picker. Manual diagnostics omit calendar content and account names.") },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Done") } }
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            icon = { Icon(Icons.Default.Info, null) },
            title = { Text("PastelCal 1.1.0") },
            text = { Text("A pastel, local-first Android calendar and planner built with Kotlin, Jetpack Compose, Room, Android Calendar Provider integration, native reminders, widgets, recurring events, tasks, ICS transfer, and local backups.") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Done") } }
        )
    }
    if (showCrash) {
        val crash = CrashReporter.readLastCrash(context).orEmpty()
        AlertDialog(
            onDismissRequest = { showCrash = false },
            title = { Text("Last local crash") },
            text = { Text(crash.take(5000), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { showCrash = false }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { CrashReporter.clear(context); showCrash = false; onMessage("Crash report cleared") }) { Text("Clear") } }
        )
    }
}
