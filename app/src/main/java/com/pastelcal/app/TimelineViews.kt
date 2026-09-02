package com.pastelcal.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pastelcal.app.model.*
import com.pastelcal.app.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class CalendarViewMode(val label: String, val days: Int) {
    MONTH("Month", 0),
    DAY("Day", 1),
    THREE_DAY("3 Day", 3),
    WEEK("Week", 7)
}

private data class TimelineOccurrence(
    val item: CalendarItem,
    val date: LocalDate,
    val completed: Boolean
)

@Composable
fun TimelinePlanner(
    items: List<CalendarItem>,
    startDate: LocalDate,
    mode: CalendarViewMode,
    completedOccurrences: Set<String>,
    onOpen: (CalendarItem, LocalDate) -> Unit,
    onShift: (CalendarItem, Int) -> Unit,
    onToggleTask: (CalendarItem, LocalDate) -> Unit,
    onMessage: (String) -> Unit
) {
    val dates = remember(startDate, mode) { (0 until mode.days.coerceAtLeast(1)).map { startDate.plusDays(it.toLong()) } }
    val entries = remember(items, dates, completedOccurrences) {
        buildList {
            dates.forEach { date ->
                items.filter { RecurrenceEngine.occursOn(it, date) }
                    .sortedWith(compareBy<CalendarItem> { it.startTime ?: LocalTime.MIN }.thenBy { it.title.lowercase() })
                    .forEach { item ->
                        val completed = if (item.kind == ItemKind.TASK) {
                            if (item.recurrence == Recurrence.NONE) item.completed
                            else CalendarViewModel.completionKey(item.id, date.toEpochDay()) in completedOccurrences
                        } else false
                        add(TimelineOccurrence(item, date, completed))
                    }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        dates.forEach { date ->
            item(key = "header-${date.toEpochDay()}") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (date == LocalDate.now()) Sky.copy(alpha = .32f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(date.format(DateTimeFormatter.ofPattern("EEEE")), fontWeight = FontWeight.Bold)
                            Text(date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (date == LocalDate.now()) AssistChip(onClick = {}, label = { Text("Today") })
                    }
                }
            }

            val dayEntries = entries.filter { it.date == date }
            val allDay = dayEntries.filter { it.item.startTime == null }
            if (allDay.isNotEmpty()) {
                item(key = "all-day-${date.toEpochDay()}") { Text("All day", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(allDay, key = { "all-${it.item.id}-${date.toEpochDay()}" }) { occurrence ->
                    TimelineItemCard(occurrence, draggable = false, onOpen, onShift, onToggleTask, onMessage)
                }
            }

            val timed = dayEntries.filter { it.item.startTime != null }
            if (timed.isEmpty() && allDay.isEmpty()) {
                item(key = "empty-${date.toEpochDay()}") {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .3f)) {
                        Text("No scheduled items", Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val grouped = timed.groupBy { it.item.startTime!!.hour }
                grouped.keys.sorted().forEach { hour ->
                    item(key = "hour-${date.toEpochDay()}-$hour") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(
                                LocalTime.of(hour, 0).format(DateTimeFormatter.ofPattern("h a")),
                                modifier = Modifier.width(54.dp).padding(top = 15.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                grouped[hour].orEmpty().forEach { occurrence ->
                                    TimelineItemCard(occurrence, draggable = true, onOpen, onShift, onToggleTask, onMessage)
                                }
                            }
                        }
                    }
                }
            }
            item(key = "space-${date.toEpochDay()}") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun TimelineItemCard(
    occurrence: TimelineOccurrence,
    draggable: Boolean,
    onOpen: (CalendarItem, LocalDate) -> Unit,
    onShift: (CalendarItem, Int) -> Unit,
    onToggleTask: (CalendarItem, LocalDate) -> Unit,
    onMessage: (String) -> Unit
) {
    val density = LocalDensity.current
    var dragPx by remember(occurrence.item.id, occurrence.date) { mutableFloatStateOf(0f) }
    val color = timelineCategoryColor(occurrence.item.category)
    val dragModifier = if (!draggable) Modifier else Modifier.pointerInput(occurrence.item.id, occurrence.date) {
        detectDragGesturesAfterLongPress(
            onDragStart = { dragPx = 0f },
            onDrag = { change, amount ->
                change.consume()
                dragPx += amount.y
            },
            onDragCancel = { dragPx = 0f },
            onDragEnd = {
                val rawMinutes = with(density) { dragPx.toDp().value }.roundToInt()
                val snapped = ((rawMinutes / 15f).roundToInt() * 15).coerceIn(-720, 720)
                if (snapped != 0) {
                    onShift(occurrence.item, snapped)
                    onMessage(if (occurrence.item.recurrence == Recurrence.NONE) "Moved ${if (snapped > 0) "+" else ""}$snapped minutes" else "Repeating series moved ${if (snapped > 0) "+" else ""}$snapped minutes")
                }
                dragPx = 0f
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth().then(dragModifier),
        onClick = { onOpen(occurrence.item, occurrence.date) },
        shape = RoundedCornerShape(18.dp),
        color = color.copy(alpha = .42f)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (occurrence.item.kind == ItemKind.TASK) {
                Checkbox(
                    checked = occurrence.completed,
                    onCheckedChange = { onToggleTask(occurrence.item, occurrence.date) }
                )
            } else {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        occurrence.item.title,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (occurrence.completed) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f)
                    )
                    if (occurrence.item.recurrence != Recurrence.NONE) Icon(Icons.Default.Repeat, null, modifier = Modifier.size(15.dp))
                }
                val start = occurrence.item.startTime?.format(DateTimeFormatter.ofPattern("h:mm a"))
                val end = occurrence.item.endTime?.format(DateTimeFormatter.ofPattern("h:mm a"))
                Text(
                    listOfNotNull(start, end?.let { "– $it" }, occurrence.item.location.takeIf { it.isNotBlank() }).joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (draggable) Icon(Icons.Default.DragHandle, contentDescription = "Long press and drag to reschedule", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun timelineCategoryColor(category: PastelCategory): Color = when (category) {
    PastelCategory.LAVENDER -> Lavender
    PastelCategory.SKY -> Sky
    PastelCategory.MINT -> Mint
    PastelCategory.PEACH -> Peach
    PastelCategory.PINK -> Pink
    PastelCategory.BUTTER -> Butter
}
