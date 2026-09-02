package com.pastelcal.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pastelcal.app.model.*
import com.pastelcal.app.preferences.AppPreferences
import com.pastelcal.app.preferences.AppSettings
import com.pastelcal.app.preferences.AccentColor
import com.pastelcal.app.preferences.ThemeMode
import com.pastelcal.app.reminders.ReminderReceiver
import com.pastelcal.app.system.CalendarProviderBridge
import com.pastelcal.app.ui.theme.*
import com.pastelcal.app.widget.PastelCalWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderReceiver.createChannel(this)
        setContent {
            var settings by remember { mutableStateOf(AppPreferences.load(this@MainActivity)) }
            fun updateSettings(value: AppSettings) {
                AppPreferences.save(this@MainActivity, value)
                settings = value
                PastelCalWidgetProvider.updateAll(this@MainActivity)
            }
            PastelCalTheme(themeMode = settings.themeMode, dynamicColors = settings.dynamicColors, accentColor = settings.accentColor) {
                PastelCalApp(settings = settings, onSettingsChange = ::updateSettings)
            }
        }
    }
}

private enum class AppTab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Default.Today),
    CALENDAR("Calendar", Icons.Default.CalendarMonth),
    TASKS("Tasks", Icons.Default.CheckCircle),
    SEARCH("Search", Icons.Default.Search),
    SETTINGS("Settings", Icons.Default.Settings)
}

private data class CalendarOccurrence(val source: CalendarItem, val date: LocalDate, val completed: Boolean = false) {
    val display: CalendarItem get() = source.copy(date = date, completed = completed)
    val key: String get() = "${source.id}-${date.toEpochDay()}-${source.kind.name}"
}

private data class EditorTarget(val item: CalendarItem, val occurrenceDate: LocalDate)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastelCalApp(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    vm: CalendarViewModel = viewModel()
) {
    val context = LocalContext.current
    val allItems by vm.items.collectAsState()
    val completedOccurrences by vm.completedOccurrences.collectAsState()
    val cycleEntries by vm.cycleEntries.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(AppTab.TODAY) }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            snackbarHostState.showSnackbar(if (granted) "Reminder notifications enabled" else "Notifications are off; reminders stay scheduled but cannot be shown")
        }
    }

    fun create(kind: ItemKind, date: LocalDate = selectedDate) {
        val rounded = LocalTime.now().plusMinutes(30).withSecond(0).withNano(0)
        editorTarget = EditorTarget(CalendarItem(
            title = "",
            kind = kind,
            date = date,
            startTime = if (kind == ItemKind.EVENT || kind == ItemKind.REMINDER) rounded else null,
            endTime = if (kind == ItemKind.EVENT) rounded.plusHours(1) else null,
            category = when (kind) {
                ItemKind.EVENT -> PastelCategory.LAVENDER
                ItemKind.TASK -> PastelCategory.MINT
                ItemKind.REMINDER -> PastelCategory.PEACH
            },
            reminderMinutes = settings.defaultReminderMinutes
        ), date)
        showEditor = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("PastelCal", fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    IconButton(onClick = { selected = AppTab.SEARCH }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { create(if (selected == AppTab.TASKS) ItemKind.TASK else ItemKind.EVENT) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(if (selected == AppTab.TASKS) "New task" else "New") }
            )
        }
    ) { padding ->
        Box(
            Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(Modifier.fillMaxWidth().widthIn(max = 1100.dp)) {
            when (selected) {
                AppTab.TODAY -> TodayScreen(
                    items = allItems,
                    completedOccurrences = completedOccurrences,
                    onOpen = { item, occurrenceDate -> editorTarget = EditorTarget(item, occurrenceDate); showEditor = true },
                    onToggleTask = vm::toggleTaskOccurrence
                )
                AppTab.CALENDAR -> CalendarScreen(
                    items = allItems,
                    selectedDate = selectedDate,
                    onDateSelected = { selectedDate = it },
                    onOpen = { item, occurrenceDate -> editorTarget = EditorTarget(item, occurrenceDate); showEditor = true },
                    settings = settings,
                    completedOccurrences = completedOccurrences,
                    cycleEntries = cycleEntries,
                    onStartPeriod = vm::startPeriod,
                    onEndPeriod = vm::endPeriod,
                    onDeleteCycleEntry = vm::deleteCycleEntry,
                    onToggleTask = vm::toggleTaskOccurrence,
                    onShift = vm::shiftItemTime,
                    onMessage = { scope.launch { snackbarHostState.showSnackbar(it) } }
                )
                AppTab.TASKS -> TasksScreen(
                    tasks = allItems.filter { it.kind == ItemKind.TASK },
                    completedOccurrences = completedOccurrences,
                    onToggle = vm::toggleTaskOccurrence,
                    onOpen = { editorTarget = EditorTarget(it, it.date); showEditor = true }
                )
                AppTab.SEARCH -> SearchScreen(allItems) { editorTarget = EditorTarget(it, it.date); showEditor = true }
                AppTab.SETTINGS -> SettingsScreen(
                    vm = vm,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onMessage = { scope.launch { snackbarHostState.showSnackbar(it) } }
                )
            }
            }
        }
    }

    if (showEditor && editorTarget != null) {
        val target = editorTarget!!
        ItemEditorSheet(
            original = target.item,
            occurrenceDate = target.occurrenceDate,
            allItems = allItems,
            settings = settings,
            onDismiss = { showEditor = false },
            onSave = { edited, editScope ->
                if (target.item.recurrence == Recurrence.NONE) vm.save(edited.copy(id = target.item.id))
                else vm.saveSeriesEdit(target.item, edited, target.occurrenceDate, editScope)
                showEditor = false
                if (Build.VERSION.SDK_INT >= 33 && edited.allReminderMinutes.isNotEmpty() &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                scope.launch { snackbarHostState.showSnackbar("${edited.kind.label} saved") }
            },
            onDelete = { editScope ->
                if (target.item.recurrence == Recurrence.NONE) vm.delete(target.item)
                else vm.deleteSeriesEdit(target.item, target.occurrenceDate, editScope)
                showEditor = false
                scope.launch { snackbarHostState.showSnackbar("Deleted") }
            },
            onExported = { scope.launch { snackbarHostState.showSnackbar(it) } }
        )
    }
}

private fun occurrencesOn(items: List<CalendarItem>, date: LocalDate, completedOccurrences: Set<String>): List<CalendarOccurrence> =
    items.asSequence()
        .filter { RecurrenceEngine.occursOn(it, date) }
        .map { item ->
            val completed = if (item.kind == ItemKind.TASK) {
                if (item.recurrence == Recurrence.NONE) item.completed
                else CalendarViewModel.completionKey(item.id, date.toEpochDay()) in completedOccurrences
            } else false
            CalendarOccurrence(item, date, completed)
        }
        .sortedWith(compareBy<CalendarOccurrence> { it.source.startTime ?: LocalTime.MAX }.thenBy { it.source.title.lowercase() })
        .toList()

@Composable
private fun TodayScreen(
    items: List<CalendarItem>,
    completedOccurrences: Set<String>,
    onOpen: (CalendarItem, LocalDate) -> Unit,
    onToggleTask: (CalendarItem, LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val occurrences = remember(items, today, completedOccurrences) { occurrencesOn(items, today, completedOccurrences) }
    val events = occurrences.filter { it.source.kind != ItemKind.TASK }
    val tasks = occurrences.filter { it.source.kind == ItemKind.TASK }
    val next = events.firstOrNull { it.source.startTime == null || it.source.startTime >= LocalTime.now() } ?: events.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Your day, neatly organized.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SectionTitle("Next up") }
        if (next != null) item { PastelCard(next.display, large = true, onClick = { onOpen(next.source, next.date) }) }
        else item { EmptyPastelCard("Nothing else scheduled today") }
        item { SectionTitle("Today's schedule") }
        if (events.isEmpty()) item { EmptyPastelCard("No events yet") }
        items(events, key = { it.key }) { occurrence -> PastelCard(occurrence.display, onClick = { onOpen(occurrence.source, occurrence.date) }) }
        item { SectionTitle("Tasks") }
        if (tasks.isEmpty()) item { EmptyPastelCard("No tasks due today") }
        items(tasks, key = { it.key }) { occurrence ->
            TaskRow(occurrence.display, onToggle = { onToggleTask(occurrence.source, occurrence.date) }, onOpen = { onOpen(occurrence.source) })
        }
    }
}

@Composable
private fun CalendarScreen(
    items: List<CalendarItem>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onOpen: (CalendarItem, LocalDate) -> Unit,
    settings: AppSettings,
    completedOccurrences: Set<String>,
    cycleEntries: List<CycleEntry>,
    onStartPeriod: (LocalDate) -> Unit,
    onEndPeriod: (Long, LocalDate) -> Unit,
    onDeleteCycleEntry: (Long) -> Unit,
    onToggleTask: (CalendarItem, LocalDate) -> Unit,
    onShift: (CalendarItem, Int) -> Unit,
    onMessage: (String) -> Unit
) {
    var visibleMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    val cyclePrediction = remember(cycleEntries) { CyclePredictionEngine.predict(cycleEntries) }
    val cycleDays = remember(cycleEntries, cyclePrediction, settings.showEstimatedFertileWindow) {
        if (settings.cycleTrackingEnabled) CyclePredictionEngine.dayKinds(cycleEntries, cyclePrediction, settings.showEstimatedFertileWindow) else emptyMap()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CalendarViewMode.entries.forEach { mode ->
                FilterChip(
                    selected = viewMode == mode,
                    onClick = { viewMode = mode },
                    label = { Text(mode.label, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (viewMode == CalendarViewMode.MONTH) {
            val dayItems = remember(items, selectedDate, completedOccurrences) { occurrencesOn(items, selectedDate, completedOccurrences) }
            val datesWithItems = remember(items, visibleMonth) {
                val start = visibleMonth.atDay(1)
                val end = visibleMonth.atEndOfMonth()
                items.flatMap { RecurrenceEngine.occurrencesBetween(it, start, end) }.toSet()
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (settings.compactCalendar) 7.dp else 12.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, "Previous month") }
                        Text(visibleMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { visibleMonth = YearMonth.now(); onDateSelected(LocalDate.now()) }) { Text("Today") }
                        IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, "Next month") }
                    }
                }
                item {
                    MonthGrid(
                        month = visibleMonth, selectedDate = selectedDate, datesWithItems = datesWithItems, onDateSelected = onDateSelected,
                        compact = settings.compactCalendar, weekNumbers = settings.weekNumbers, weekStartsMonday = settings.weekStartsMonday,
                        cycleDays = cycleDays
                    )
                }
                if (settings.cycleTrackingEnabled) {
                    item {
                        CycleTrackingCard(
                            selectedDate = selectedDate,
                            entries = cycleEntries,
                            prediction = cyclePrediction,
                            showFertileWindow = settings.showEstimatedFertileWindow,
                            onStartPeriod = onStartPeriod,
                            onEndPeriod = onEndPeriod,
                            onDeleteCycleEntry = onDeleteCycleEntry,
                            onMessage = onMessage
                        )
                    }
                }
                item { Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                if (dayItems.isEmpty()) item { EmptyPastelCard("Nothing planned for this day") }
                items(dayItems, key = { it.key }) { occurrence ->
                    if (occurrence.source.kind == ItemKind.TASK) {
                        TaskRow(occurrence.display, onToggle = { onToggleTask(occurrence.source, occurrence.date) }, onOpen = { onOpen(occurrence.source, occurrence.date) })
                    } else PastelCard(occurrence.display, onClick = { onOpen(occurrence.source, occurrence.date) })
                }
            }
        } else {
            val step = viewMode.days.toLong()
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDateSelected(selectedDate.minusDays(step)) }) { Icon(Icons.Default.ChevronLeft, "Previous range") }
                Text(
                    if (viewMode == CalendarViewMode.DAY) selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
                    else "${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))} – ${selectedDate.plusDays(step - 1).format(DateTimeFormatter.ofPattern("MMM d"))}",
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { onDateSelected(LocalDate.now()) }) { Text("Today") }
                IconButton(onClick = { onDateSelected(selectedDate.plusDays(step)) }) { Icon(Icons.Default.ChevronRight, "Next range") }
            }
            Text(
                "Long-press a timed item and drag vertically to move it in 15-minute steps.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            TimelinePlanner(
                items = items, startDate = selectedDate, mode = viewMode, completedOccurrences = completedOccurrences,
                onOpen = onOpen, onShift = onShift, onToggleTask = onToggleTask, onMessage = onMessage
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    datesWithItems: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    compact: Boolean,
    weekNumbers: Boolean,
    weekStartsMonday: Boolean,
    cycleDays: Map<LocalDate, CycleDayKind> = emptyMap()
) {
    val first = month.atDay(1)
    val firstOffset = if (weekStartsMonday) first.dayOfWeek.value - 1 else first.dayOfWeek.value % 7
    val days = month.lengthOfMonth()
    val headers = if (weekStartsMonday) listOf("M", "T", "W", "T", "F", "S", "S") else listOf("S", "M", "T", "W", "T", "F", "S")
    val spacing = if (compact) 2.dp else 5.dp
    val cellPadding = if (compact) 1.dp else 2.dp
    val corner = if (compact) 10.dp else 14.dp

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (weekNumbers) Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Text("#", style = MaterialTheme.typography.labelSmall) }
            headers.forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (weekNumbers) {
                    val rowStart = first.minusDays(firstOffset.toLong()).plusDays((week * 7).toLong())
                    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                        Text(rowStart.get(WeekFields.ISO.weekOfWeekBasedYear()).toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                repeat(7) { weekday ->
                    val cell = week * 7 + weekday
                    val day = cell - firstOffset + 1
                    if (day in 1..days) {
                        val date = month.atDay(day)
                        val selected = date == selectedDate
                        val today = date == LocalDate.now()
                        Surface(
                            modifier = Modifier.weight(1f).aspectRatio(1f).padding(cellPadding).clickable { onDateSelected(date) },
                            shape = RoundedCornerShape(corner),
                            color = when {
                                selected -> Lavender.copy(alpha = .72f)
                                cycleDays[date] == CycleDayKind.RECORDED_PERIOD -> Pink.copy(alpha = .50f)
                                cycleDays[date] == CycleDayKind.PREDICTED_PERIOD -> Pink.copy(alpha = .22f)
                                cycleDays[date] == CycleDayKind.ESTIMATED_FERTILE -> Mint.copy(alpha = .24f)
                                today -> Sky.copy(alpha = .32f)
                                else -> Color.Transparent
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(day.toString(), fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal, fontSize = if (compact) 13.sp else 14.sp)
                                cycleDays[date]?.let { kind ->
                                    val markerColor = when (kind) {
                                        CycleDayKind.RECORDED_PERIOD -> Pink
                                        CycleDayKind.PREDICTED_PERIOD -> MaterialTheme.colorScheme.primary
                                        CycleDayKind.ESTIMATED_FERTILE -> Mint
                                    }
                                    Box(
                                        Modifier.align(Alignment.TopEnd)
                                            .padding(top = if (compact) 3.dp else 5.dp, end = if (compact) 3.dp else 5.dp)
                                            .size(if (compact) 5.dp else 6.dp)
                                            .background(markerColor, CircleShape)
                                    )
                                }
                                if (date in datesWithItems) {
                                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = if (compact) 3.dp else 5.dp).size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                }
                            }
                        }
                    } else Spacer(Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun CycleTrackingCard(
    selectedDate: LocalDate,
    entries: List<CycleEntry>,
    prediction: CyclePrediction?,
    showFertileWindow: Boolean,
    onStartPeriod: (LocalDate) -> Unit,
    onEndPeriod: (Long, LocalDate) -> Unit,
    onDeleteCycleEntry: (Long) -> Unit,
    onMessage: (String) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<CycleEntry?>(null) }
    val completedRecord = entries.firstOrNull { entry ->
        val end = entry.endDate ?: entry.startDate
        !selectedDate.isBefore(entry.startDate) && !selectedDate.isAfter(end)
    }
    val openRecord = entries.filter { it.endDate == null }.maxByOrNull { it.startDate }
    val today = LocalDate.now()
    val canEndOpen = openRecord != null &&
        !selectedDate.isBefore(openRecord.startDate) &&
        !selectedDate.isAfter(openRecord.startDate.plusDays(14)) &&
        !selectedDate.isAfter(today)
    val dayKind = CyclePredictionEngine.dayKinds(entries, prediction, showFertileWindow)[selectedDate]
    val short = DateTimeFormatter.ofPattern("MMM d")

    Surface(shape = RoundedCornerShape(22.dp), color = Pink.copy(alpha = .20f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Cycle tracking", fontWeight = FontWeight.SemiBold)
                    if (prediction == null) {
                        Text("Log a period start to begin building your personal cycle history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(
                            "${if (prediction.expectedStart.isBefore(today)) "Expected around" else "Next period estimate"} · ${prediction.expectedStart.format(short)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Likely start ${prediction.likelyStartFrom.format(short)}–${prediction.likelyStartTo.format(short)} · ${prediction.estimatedCycleLengthDays}-day cycle · ${prediction.estimatedPeriodLengthDays}-day period",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (prediction != null) {
                    AssistChip(onClick = {}, label = { Text(prediction.confidence.label) })
                }
            }

            val selectedLabel = when (dayKind) {
                CycleDayKind.RECORDED_PERIOD -> "Recorded period day"
                CycleDayKind.PREDICTED_PERIOD -> "Predicted period day"
                CycleDayKind.ESTIMATED_FERTILE -> "Estimated fertile-window day"
                null -> "No cycle marker on this date"
            }
            Text("${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))} · $selectedLabel", style = MaterialTheme.typography.bodySmall)

            if (prediction != null && prediction.completedCycleCount < 2) {
                Text("Early estimate: add at least 3 period starts for a more personalized range.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (prediction != null) {
                Text(
                    "${prediction.completedCycleCount} recent cycle intervals used · variability ${String.format(Locale.US, "%.1f", prediction.variabilityDays)} days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showFertileWindow) {
                Text(
                    "Fertile-window dates are calendar estimates only; they do not confirm ovulation or provide reliable contraception.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when {
                    openRecord != null && canEndOpen -> {
                        Button(
                            onClick = {
                                onEndPeriod(openRecord.id, selectedDate)
                                onMessage("Period end saved for ${selectedDate.format(short)}")
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("End period here") }
                        OutlinedButton(onClick = { pendingDelete = openRecord }, modifier = Modifier.weight(1f)) { Text("Remove record") }
                    }
                    completedRecord != null -> {
                        OutlinedButton(onClick = { pendingDelete = completedRecord }, modifier = Modifier.fillMaxWidth()) { Text("Remove period record") }
                    }
                    openRecord != null -> {
                        Text(
                            "A period starting ${openRecord.startDate.format(short)} is still open. Select a date within 14 days of that start to mark its end.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    selectedDate.isAfter(today) -> {
                        Text(
                            "Future dates are prediction space. Log the period start once it actually begins.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        Button(
                            onClick = {
                                onStartPeriod(selectedDate)
                                onMessage("Period start saved for ${selectedDate.format(short)}")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Period started here") }
                    }
                }
            }
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Remove period record?") },
            text = { Text("This removes the cycle record that starts ${record.startDate.format(short)} and immediately recalculates predictions.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteCycleEntry(record.id)
                    pendingDelete = null
                    onMessage("Cycle record removed")
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TasksScreen(
    tasks: List<CalendarItem>,
    completedOccurrences: Set<String>,
    onToggle: (CalendarItem, LocalDate) -> Unit,
    onOpen: (CalendarItem) -> Unit
) {
    val today = LocalDate.now()
    val occurrences = remember(tasks, completedOccurrences, today) {
        tasks.mapNotNull { task ->
            val date = if (task.recurrence == Recurrence.NONE) task.date else RecurrenceEngine.nextOccurrence(task, today) ?: return@mapNotNull null
            val completed = if (task.recurrence == Recurrence.NONE) task.completed
            else CalendarViewModel.completionKey(task.id, date.toEpochDay()) in completedOccurrences
            CalendarOccurrence(task, date, completed)
        }.sortedWith(compareBy<CalendarOccurrence> { it.completed }.thenBy { it.date }.thenBy { it.source.startTime ?: LocalTime.MAX })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Tasks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Text("${occurrences.count { !it.completed }} remaining · ${occurrences.count { it.completed }} completed", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (occurrences.isEmpty()) item { EmptyPastelCard("No tasks yet") }
        items(occurrences, key = { it.key }) { occurrence ->
            TaskRow(
                occurrence.display,
                onToggle = { onToggle(occurrence.source, occurrence.date) },
                onOpen = { onOpen(occurrence.source, occurrence.date) }
            )
        }
    }
}

@Composable
private fun SearchScreen(items: List<CalendarItem>, onOpen: (CalendarItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<ItemKind?>(null) }
    val filtered = items.filter {
        (query.isBlank() || listOf(it.title, it.notes, it.location).any { value -> value.contains(query, true) }) &&
            (filter == null || it.kind == filter)
    }.sortedWith(itemComparator())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search events, tasks, notes, places") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(18.dp),
                singleLine = true
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                ItemKind.entries.forEach { kind -> FilterChip(selected = filter == kind, onClick = { filter = kind }, label = { Text(kind.label) }) }
            }
        }
        if (filtered.isEmpty()) item { EmptyPastelCard("No matching items") }
        items(filtered, key = { it.id }) { item ->
            if (item.kind == ItemKind.TASK) TaskRow(item, onToggle = {}, onOpen = onOpen)
            else PastelCard(item, onClick = { onOpen(item) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    vm: CalendarViewModel,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var calendarGranted by remember { mutableStateOf(CalendarProviderBridge.hasCalendarPermission(context)) }
    var notificationGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    var exactAlarmGranted by remember { mutableStateOf(canUseExactAlarms(context)) }
    var calendars by remember { mutableStateOf<List<CalendarProviderBridge.DeviceCalendar>>(emptyList()) }
    var calendarExpanded by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }
    var reminderExpanded by remember { mutableStateOf(false) }
    var showClearCycleConfirm by remember { mutableStateOf(false) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        calendarGranted = result[Manifest.permission.READ_CALENDAR] == true && result[Manifest.permission.WRITE_CALENDAR] == true
        onMessage(if (calendarGranted) "Device calendar access enabled" else "Calendar permission was not granted")
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationGranted = granted
        onMessage(if (granted) "Reminder notifications enabled" else "Notification permission was not granted")
    }

    LaunchedEffect(calendarGranted) {
        calendars = if (calendarGranted) withContext(Dispatchers.IO) { CalendarProviderBridge.availableCalendars(context) } else emptyList()
        if (calendarGranted && settings.selectedCalendarId == null && calendars.isNotEmpty()) {
            onSettingsChange(settings.copy(selectedCalendarId = calendars.first().id))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmGranted = canUseExactAlarms(context)
                notificationGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                calendarGranted = CalendarProviderBridge.hasCalendarPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selectedCalendar = calendars.firstOrNull { it.id == settings.selectedCalendarId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Make PastelCal yours", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Choose the theme and accent used across navigation, buttons, and controls.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { SectionTitle("Appearance") }
        item {
            ExposedDropdownMenuBox(expanded = themeExpanded, onExpandedChange = { themeExpanded = !themeExpanded }) {
                OutlinedTextField(
                    value = settings.themeMode.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Theme") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(themeExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                )
                ExposedDropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                    ThemeMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(mode.label) }, onClick = { onSettingsChange(settings.copy(themeMode = mode)); themeExpanded = false }) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Accent color", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.dynamicColors) "Material You is currently overriding the custom accent." else "${settings.accentColor.label} is used for app controls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp)
                ) {
                    items(AccentColor.entries) { accent ->
                        val selectedAccent = !settings.dynamicColors && settings.accentColor == accent
                        Surface(
                            modifier = Modifier
                                .size(if (selectedAccent) 42.dp else 36.dp)
                                .clickable { onSettingsChange(settings.copy(accentColor = accent, dynamicColors = false)) }
                                .semantics { contentDescription = "${accent.label} accent" },
                            shape = CircleShape,
                            color = Color(accent.argb),
                            border = if (selectedAccent) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null
                        ) {
                            if (selectedAccent) Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF202027))
                            }
                        }
                    }
                }
            }
        }
        item { SettingsToggle("Compact calendar", "Fit more information on screen", settings.compactCalendar) { onSettingsChange(settings.copy(compactCalendar = it)) } }
        item { SettingsToggle("Material You colors", "Use your wallpaper colors. Turn this off to use your selected PastelCal accent.", settings.dynamicColors) { onSettingsChange(settings.copy(dynamicColors = it)) } }
        item { SettingsToggle("Week numbers", "Show ISO week numbers beside month rows", settings.weekNumbers) { onSettingsChange(settings.copy(weekNumbers = it)) } }
        item { SettingsToggle("Week starts Monday", "Use Monday instead of Sunday as the first column", settings.weekStartsMonday) { onSettingsChange(settings.copy(weekStartsMonday = it)) } }

        item { SectionTitle("Cycle tracking") }
        item {
            SettingsToggle(
                "Cycle tracking",
                "Log period dates and show personalized predictions on your calendar. Cycle records stay in PastelCal's local database.",
                settings.cycleTrackingEnabled
            ) { enabled ->
                onSettingsChange(settings.copy(
                    cycleTrackingEnabled = enabled,
                    showEstimatedFertileWindow = if (enabled) settings.showEstimatedFertileWindow else false
                ))
            }
        }
        if (settings.cycleTrackingEnabled) {
            item {
                SettingsToggle(
                    "Estimated fertile window",
                    "Optionally show a date-only fertile-window estimate. It cannot detect actual ovulation and should not be used as birth control.",
                    settings.showEstimatedFertileWindow
                ) { onSettingsChange(settings.copy(showEstimatedFertileWindow = it)) }
            }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = Pink.copy(alpha = .20f)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Predictions improve as you log more cycles. PastelCal shows a likely range and confidence instead of claiming an exact medical measurement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showClearCycleConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear cycle history")
                }
            }
        }

        item { SectionTitle("Planning") }
        item {
            ExposedDropdownMenuBox(expanded = reminderExpanded, onExpandedChange = { reminderExpanded = !reminderExpanded }) {
                OutlinedTextField(
                    value = reminderLabel(settings.defaultReminderMinutes),
                    onValueChange = {}, readOnly = true, label = { Text("Default reminder") },
                    leadingIcon = { Icon(Icons.Default.Notifications, null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(reminderExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(18.dp)
                )
                ExposedDropdownMenu(expanded = reminderExpanded, onDismissRequest = { reminderExpanded = false }) {
                    listOf<Int?>(null, 0, 5, 15, 30, 60, 1440).forEach { minutes ->
                        DropdownMenuItem(text = { Text(reminderLabel(minutes)) }, onClick = { onSettingsChange(settings.copy(defaultReminderMinutes = minutes)); reminderExpanded = false })
                    }
                }
            }
        }

        item { SectionTitle("PastelCal reminders") }
        item {
            PermissionCard(
                icon = Icons.Default.Notifications,
                title = "Notification permission",
                description = if (notificationGranted) "Enabled. PastelCal can show scheduled reminders." else "Required on Android 13+ to display reminder notifications.",
                enabled = notificationGranted,
                actionLabel = if (Build.VERSION.SDK_INT >= 33 && !notificationGranted) "Enable notifications" else null,
                onAction = { if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            )
        }
        item {
            PermissionCard(
                icon = Icons.Default.Alarm,
                title = "Precise reminder timing",
                description = if (exactAlarmGranted) "Exact alarms are available." else "PastelCal falls back to Android's inexact alarm timing until precise alarms are allowed.",
                enabled = exactAlarmGranted,
                actionLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmGranted) "Allow precise alarms" else null,
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }
            )
        }

        item { SectionTitle("Device calendar") }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = Sky.copy(alpha = .25f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Android Calendar Provider", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (calendarGranted) "Access enabled. Choose where PastelCal exports events." else "Optional access for importing and exporting calendar events.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!calendarGranted) {
                        Button(onClick = { calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }) { Text("Enable calendar access") }
                    } else {
                        ExposedDropdownMenuBox(expanded = calendarExpanded, onExpandedChange = { calendarExpanded = !calendarExpanded }) {
                            OutlinedTextField(
                                value = selectedCalendar?.let { calendarLabel(it) } ?: "Choose calendar",
                                onValueChange = {}, readOnly = true, label = { Text("Export calendar") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(calendarExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(16.dp)
                            )
                            ExposedDropdownMenu(expanded = calendarExpanded, onDismissRequest = { calendarExpanded = false }) {
                                calendars.forEach { calendar -> DropdownMenuItem(text = { Text(calendarLabel(calendar)) }, onClick = { onSettingsChange(settings.copy(selectedCalendarId = calendar.id)); calendarExpanded = false }) }
                            }
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val (added, skipped) = vm.importDeviceCalendar()
                                onMessage("Imported $added events${if (skipped > 0) " · skipped $skipped duplicates/invalid" else ""}")
                            }
                        }) { Text("Import next 90 days") }
                    }
                }
            }
        }

        item { SectionTitle("Home screen") }
        item {
            PermissionCard(
                icon = Icons.Default.Widgets,
                title = "Agenda widget",
                description = "Add PastelCal from your launcher's Widgets screen to see your next three items. It refreshes automatically and can be refreshed manually.",
                enabled = true,
                actionLabel = null,
                onAction = {}
            )
        }

        item {
            DataToolsPanel(vm = vm, settings = settings, onSettingsChange = onSettingsChange, onMessage = onMessage)
        }

        item { ReleaseSettingsPanel(onMessage = onMessage) }

        item {
            Text(
                "PastelCal 1.1.0 · Calendar and optional cycle data stay local unless you explicitly import, export, or back up data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }

    if (showClearCycleConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCycleConfirm = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text("Clear cycle history?") },
            text = { Text("This permanently deletes period records and resets cycle predictions. Calendar events and tasks are not affected.") },
            confirmButton = {
                Button(onClick = {
                    vm.clearCycleEntries()
                    showClearCycleConfirm = false
                    onMessage("Cycle history cleared")
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearCycleConfirm = false }) { Text("Cancel") } }
        )
    }
}

private fun canUseExactAlarms(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

private fun calendarLabel(calendar: CalendarProviderBridge.DeviceCalendar): String =
    if (calendar.accountName.isBlank() || calendar.accountName == calendar.displayName) calendar.displayName else "${calendar.displayName} · ${calendar.accountName}"

@Composable
private fun PermissionCard(icon: ImageVector, title: String, description: String, enabled: Boolean, actionLabel: String?, onAction: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = if (enabled) Mint.copy(alpha = .28f) else Peach.copy(alpha = .28f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (enabled) Icons.Default.CheckCircle else Icons.Default.Info, null)
            }
            if (actionLabel != null) Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun PastelCard(item: CalendarItem, large: Boolean = false, onClick: () -> Unit) {
    val color = categoryColor(item.category)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = color.copy(alpha = if (large) .68f else .42f)
    ) {
        Row(Modifier.padding(if (large) 20.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(if (large) 14.dp else 10.dp).background(color, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = if (large) 20.sp else 16.sp, modifier = Modifier.weight(1f))
                    if (item.recurrence != Recurrence.NONE) Icon(Icons.Default.Repeat, null, modifier = Modifier.size(16.dp))
                }
                Text(itemSubtitle(item), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.location.isNotBlank()) Text(item.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun TaskRow(item: CalendarItem, onToggle: (CalendarItem) -> Unit, onOpen: (CalendarItem) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(item) },
        shape = RoundedCornerShape(18.dp),
        color = categoryColor(item.category).copy(alpha = .32f)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.completed, onCheckedChange = { onToggle(item) })
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.recurrence != Recurrence.NONE) Icon(Icons.Default.Repeat, null, modifier = Modifier.size(15.dp))
                }
                Text(
                    item.date.format(DateTimeFormatter.ofPattern("MMM d")) + (item.startTime?.let { " · ${formatTime(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun EmptyPastelCard(text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
        Text(text, Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun categoryColor(category: PastelCategory): Color = when (category) {
    PastelCategory.LAVENDER -> Lavender
    PastelCategory.SKY -> Sky
    PastelCategory.MINT -> Mint
    PastelCategory.PEACH -> Peach
    PastelCategory.PINK -> Pink
    PastelCategory.BUTTER -> Butter
}

private fun itemSubtitle(item: CalendarItem): String {
    val date = item.date.format(DateTimeFormatter.ofPattern("MMM d"))
    val time = item.startTime?.let(::formatTime)
    val end = item.endTime?.let(::formatTime)
    val base = when {
        time != null && end != null -> "$date · $time–$end"
        time != null -> "$date · $time"
        else -> "$date · ${item.kind.label}"
    }
    return if (item.recurrence == Recurrence.NONE) base else "$base · ${item.recurrence.label}"
}

private fun formatTime(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern("h:mm a"))
private fun itemComparator() = compareBy<CalendarItem> { it.date }.thenBy { it.startTime ?: LocalTime.MAX }.thenBy { it.title.lowercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemEditorSheet(
    original: CalendarItem,
    occurrenceDate: LocalDate,
    allItems: List<CalendarItem>,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (CalendarItem, SeriesEditScope) -> Unit,
    onDelete: (SeriesEditScope) -> Unit,
    onExported: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val occurrenceInitial = remember(original, occurrenceDate) {
        if (original.id != 0L && original.recurrence != Recurrence.NONE) original.copy(date = occurrenceDate) else original
    }
    var title by remember(original.id, occurrenceDate) { mutableStateOf(occurrenceInitial.title) }
    var kind by remember(original.id, occurrenceDate) { mutableStateOf(occurrenceInitial.kind) }
    var date by remember(original.id, occurrenceDate) { mutableStateOf(occurrenceInitial.date) }
    var startTime by remember(original.id, occurrenceDate) { mutableStateOf(occurrenceInitial.startTime) }
    var endTime by remember(original.id, occurrenceDate) { mutableStateOf(occurrenceInitial.endTime) }
    var category by remember(original.id, occurrenceDate) { mutableStateOf(occurrenceInitial.category) }
    var notes by remember(original.id, occurrenceDate) { mutableStateOf(occurrenceInitial.notes) }
    var location by remember(original.id, occurrenceDate) { mutableStateOf(occurrenceInitial.location) }
    var recurrence by remember(original.id, occurrenceDate) { mutableStateOf(occurrenceInitial.recurrence) }
    var recurrenceEndDate by remember(original.id, occurrenceDate) { mutableStateOf(occurrenceInitial.recurrenceEndDate) }
    var reminderMinutes by remember(original.id, occurrenceDate) { mutableStateOf(occurrenceInitial.reminderMinutes) }
    var additionalReminderMinutes by remember(original.id, occurrenceDate) { mutableStateOf(occurrenceInitial.additionalReminderMinutes) }
    var editScope by remember(original.id, occurrenceDate) {
        mutableStateOf(
            if (original.recurrence != Recurrence.NONE && occurrenceDate != original.date) SeriesEditScope.THIS_OCCURRENCE
            else SeriesEditScope.ENTIRE_SERIES
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var pickingStart by remember { mutableStateOf(true) }
    var showTimePicker by remember { mutableStateOf(false) }
    var recurrenceExpanded by remember { mutableStateOf(false) }
    var reminderExpanded by remember { mutableStateOf(false) }
    val canExport = CalendarProviderBridge.hasCalendarPermission(context) && kind != ItemKind.TASK
    val isExistingSeries = original.id != 0L && original.recurrence != Recurrence.NONE

    fun current() = original.copy(
        title = title.trim(),
        kind = kind,
        date = date,
        startTime = startTime,
        endTime = if (kind == ItemKind.EVENT) endTime else null,
        category = category,
        notes = notes.trim(),
        location = location.trim(),
        recurrence = recurrence,
        recurrenceEndDate = recurrenceEndDate.takeIf { recurrence != Recurrence.NONE },
        reminderMinutes = reminderMinutes,
        additionalReminderMinutes = additionalReminderMinutes.filter { it != reminderMinutes }.distinct()
    )

    val candidate = current()
    val conflicts = remember(candidate, allItems) { ScheduleConflictDetector.conflicts(candidate, allItems, candidate.date) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            original.id == 0L -> "New ${kind.label.lowercase()}"
                            isExistingSeries && editScope == SeriesEditScope.THIS_OCCURRENCE -> "Edit occurrence"
                            else -> "Edit ${kind.label.lowercase()}"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (original.id != 0L) IconButton(onClick = { onDelete(editScope) }) { Icon(Icons.Default.DeleteOutline, "Delete") }
                }
            }
            if (isExistingSeries) {
                item {
                    Text("Apply changes to", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SeriesEditScope.entries.forEach { option ->
                            FilterChip(
                                selected = editScope == option,
                                onClick = { editScope = option },
                                label = { Text(option.label) },
                                leadingIcon = if (editScope == option) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ItemKind.entries.forEach { option -> FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option.label) }) }
                }
            }
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true
                )
            }
            item { EditorActionRow(Icons.Default.DateRange, "Date", date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))) { showDatePicker = true } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { pickingStart = true; showTimePicker = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(6.dp)); Text(startTime?.let(::formatTime) ?: "Add time")
                    }
                    if (kind == ItemKind.EVENT) {
                        OutlinedButton(onClick = { pickingStart = false; showTimePicker = true }, modifier = Modifier.weight(1f)) { Text(endTime?.let(::formatTime) ?: "End time") }
                    }
                }
            }
            if (conflicts.isNotEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(18.dp), color = Butter.copy(alpha = .45f)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Schedule overlap", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Overlaps ${conflicts.take(2).joinToString { it.title }}${if (conflicts.size > 2) " and ${conflicts.size - 2} more" else ""}. You can still save it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text("Color", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PastelCategory.entries.forEach { option ->
                        Surface(
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = "${option.label} color" }
                                .clickable { category = option },
                            shape = CircleShape,
                            color = categoryColor(option),
                            border = if (category == option) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null
                        ) {}
                    }
                }
            }
            item {
                ExposedDropdownMenuBox(expanded = recurrenceExpanded, onExpandedChange = { recurrenceExpanded = !recurrenceExpanded }) {
                    OutlinedTextField(
                        value = recurrence.label,
                        onValueChange = {}, readOnly = true, label = { Text("Repeat") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(recurrenceExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(18.dp)
                    )
                    ExposedDropdownMenu(expanded = recurrenceExpanded, onDismissRequest = { recurrenceExpanded = false }) {
                        Recurrence.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                recurrence = option
                                if (option == Recurrence.NONE) recurrenceEndDate = null
                                recurrenceExpanded = false
                            })
                        }
                    }
                }
            }
            if (recurrence != Recurrence.NONE) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.EventAvailable, null)
                            Spacer(Modifier.width(6.dp))
                            Text(recurrenceEndDate?.let { "Until ${it.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}" } ?: "No end date")
                        }
                        if (recurrenceEndDate != null) TextButton(onClick = { recurrenceEndDate = null }) { Text("Clear") }
                    }
                }
            }
            item {
                ExposedDropdownMenuBox(expanded = reminderExpanded, onExpandedChange = { reminderExpanded = !reminderExpanded }) {
                    OutlinedTextField(
                        value = reminderLabel(reminderMinutes),
                        onValueChange = {}, readOnly = true, label = { Text("Reminder") },
                        leadingIcon = { Icon(Icons.Default.Notifications, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(reminderExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(18.dp)
                    )
                    ExposedDropdownMenu(expanded = reminderExpanded, onDismissRequest = { reminderExpanded = false }) {
                        listOf<Int?>(null, 0, 5, 15, 30, 60, 1440).forEach { minutes ->
                            DropdownMenuItem(text = { Text(reminderLabel(minutes)) }, onClick = { reminderMinutes = minutes; additionalReminderMinutes = additionalReminderMinutes.filter { it != minutes }; reminderExpanded = false })
                        }
                    }
                }
            }
            item {
                Text("Additional reminders", fontWeight = FontWeight.Medium)
                Text("Add extra alerts for the same item.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 15, 30, 60, 120, 1440).chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { minutes ->
                                if (minutes != reminderMinutes) {
                                    FilterChip(
                                        selected = minutes in additionalReminderMinutes,
                                        onClick = {
                                            additionalReminderMinutes = if (minutes in additionalReminderMinutes) additionalReminderMinutes - minutes
                                            else (additionalReminderMinutes + minutes).distinct().sortedDescending()
                                        },
                                        label = { Text(shortReminderLabel(minutes)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = location, onValueChange = { location = it }, label = { Text("Location") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)
                )
            }
            item {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it }, label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(18.dp)
                )
            }
            if (canExport && original.id != 0L) {
                item {
                    OutlinedButton(
                        onClick = {
                            val item = current()
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    val calendars = CalendarProviderBridge.availableCalendars(context)
                                    val calendar = calendars.firstOrNull { it.id == settings.selectedCalendarId } ?: calendars.firstOrNull()
                                    if (calendar == null) null else CalendarProviderBridge.exportEvent(context, item, calendar.id) to calendar.displayName
                                }
                                if (result?.first != null) onExported("Exported to ${result.second}") else onExported("No writable device calendar found")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("Export to device calendar")
                    }
                }
            }
            item {
                Button(
                    onClick = { if (title.isNotBlank()) onSave(current(), editScope) },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) { Text(if (original.id == 0L) "Create ${kind.label}" else "Save changes") }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }

    if (showDatePicker) {
        DatePickerModal(date, onDismiss = { showDatePicker = false }) { date = it; showDatePicker = false }
    }
    if (showEndDatePicker) {
        DatePickerModal(recurrenceEndDate ?: date.plusMonths(1), onDismiss = { showEndDatePicker = false }) {
            recurrenceEndDate = if (it.isBefore(date)) date else it
            showEndDatePicker = false
        }
    }
    if (showTimePicker) {
        TimePickerModal(
            initial = if (pickingStart) startTime ?: LocalTime.of(9, 0) else endTime ?: (startTime?.plusHours(1) ?: LocalTime.of(10, 0)),
            onDismiss = { showTimePicker = false }
        ) {
            if (pickingStart) {
                startTime = it
                if (kind == ItemKind.EVENT && (endTime == null || !it.isBefore(endTime))) endTime = it.plusHours(1)
            } else endTime = it
            showTimePicker = false
        }
    }
}

@Composable
private fun EditorActionRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(initial: LocalDate, onDismiss: () -> Unit, onSelected: (LocalDate) -> Unit) {
    val initialMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onSelected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerModal(initial: LocalTime, onDismiss: () -> Unit, onSelected: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSelected(LocalTime.of(state.hour, state.minute)) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) }
    )
}

private fun shortReminderLabel(minutes: Int): String = when (minutes) {
    0 -> "At time"
    60 -> "1h"
    120 -> "2h"
    1440 -> "1d"
    else -> "${minutes}m"
}

private fun reminderLabel(minutes: Int?): String = when (minutes) {
    null -> "No reminder"
    0 -> "At time of event"
    5 -> "5 minutes before"
    15 -> "15 minutes before"
    30 -> "30 minutes before"
    60 -> "1 hour before"
    1440 -> "1 day before"
    else -> "$minutes minutes before"
}
