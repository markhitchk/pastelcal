package com.pastelcal.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pastelcal.app.data.*
import com.pastelcal.app.model.*
import com.pastelcal.app.reminders.ReminderScheduler
import com.pastelcal.app.system.CalendarProviderBridge
import com.pastelcal.app.widget.PastelCalWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val dao = AppDatabase.get(application).calendarDao()

    val items = dao.observeAll()
        .map { rows -> rows.map { it.toModel() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val completionRecords = dao.observeOccurrenceCompletions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val completedOccurrences = dao.observeOccurrenceCompletions()
        .map { rows -> rows.map { completionKey(it.itemId, it.occurrenceEpochDay) }.toSet() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val notificationHistory = dao.observeNotificationHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cycleEntries = dao.observeCycleEntries()
        .map { rows -> rows.map { it.toModel() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            dao.observeAll().first().map { it.toModel() }.forEach { scheduleItem(it) }
            PastelCalWidgetProvider.updateAll(app)
        }
    }

    fun save(item: CalendarItem) = viewModelScope.launch(Dispatchers.IO) {
        val saved = if (item.id == 0L) {
            val id = dao.insert(item.toEntity())
            dao.getById(id)?.toModel()
        } else {
            val existing = dao.getById(item.id)
            existing?.toModel()?.let { ReminderScheduler.cancel(app, it) }
            dao.update(item.toEntity(existingCreatedAt = existing?.createdAt))
            dao.getById(item.id)?.toModel()
        }
        saved?.let { scheduleItem(it) }
        PastelCalWidgetProvider.updateAll(app)
    }


    fun saveSeriesEdit(
        original: CalendarItem,
        editedOccurrence: CalendarItem,
        occurrenceDate: LocalDate,
        scope: SeriesEditScope
    ) = viewModelScope.launch(Dispatchers.IO) {
        if (original.recurrence == Recurrence.NONE) {
            save(editedOccurrence.copy(id = original.id)).join()
            return@launch
        }
        ReminderScheduler.cancel(app, original)
        when (scope) {
            SeriesEditScope.ENTIRE_SERIES -> {
                val existing = dao.getById(original.id)
                dao.update(editedOccurrence.copy(id = original.id, seriesParentId = original.seriesParentId).toEntity(existing?.createdAt))
            }
            SeriesEditScope.THIS_OCCURRENCE -> {
                val existing = dao.getById(original.id)
                val excluded = original.excludedDates + occurrenceDate
                dao.update(original.copy(excludedDates = excluded).toEntity(existing?.createdAt))
                val override = editedOccurrence.copy(
                    id = 0,
                    date = editedOccurrence.date,
                    recurrence = Recurrence.NONE,
                    recurrenceEndDate = null,
                    excludedDates = emptySet(),
                    seriesParentId = original.id,
                    systemEventId = null
                )
                dao.insert(override.toEntity())
            }
            SeriesEditScope.THIS_AND_FUTURE -> {
                val existing = dao.getById(original.id)
                val oldEnd = occurrenceDate.minusDays(1)
                if (existing != null) {
                    if (oldEnd.isBefore(original.date)) dao.delete(existing)
                    else dao.update(original.copy(recurrenceEndDate = oldEnd).toEntity(existing.createdAt))
                }
                val future = editedOccurrence.copy(
                    id = 0,
                    date = editedOccurrence.date,
                    recurrence = editedOccurrence.recurrence,
                    recurrenceEndDate = if (editedOccurrence.recurrence == Recurrence.NONE) null else editedOccurrence.recurrenceEndDate ?: original.recurrenceEndDate,
                    excludedDates = original.excludedDates.filter { !it.isBefore(occurrenceDate) }.toSet(),
                    seriesParentId = original.id,
                    systemEventId = null
                )
                dao.insert(future.toEntity())
            }
        }
        dao.observeAll().first().map { it.toModel() }.filter { it.id == original.id || it.seriesParentId == original.id }.forEach { scheduleItem(it) }
        PastelCalWidgetProvider.updateAll(app)
    }

    fun deleteSeriesEdit(original: CalendarItem, occurrenceDate: LocalDate, scope: SeriesEditScope) = viewModelScope.launch(Dispatchers.IO) {
        if (original.recurrence == Recurrence.NONE || scope == SeriesEditScope.ENTIRE_SERIES) {
            delete(original).join()
            return@launch
        }
        ReminderScheduler.cancel(app, original)
        val existing = dao.getById(original.id) ?: return@launch
        when (scope) {
            SeriesEditScope.THIS_OCCURRENCE -> {
                dao.update(original.copy(excludedDates = original.excludedDates + occurrenceDate).toEntity(existing.createdAt))
            }
            SeriesEditScope.THIS_AND_FUTURE -> {
                val end = occurrenceDate.minusDays(1)
                if (end.isBefore(original.date)) {
                    dao.delete(existing)
                    dao.clearOccurrenceCompletionsForItem(original.id)
                } else {
                    dao.update(original.copy(recurrenceEndDate = end).toEntity(existing.createdAt))
                }
            }
            SeriesEditScope.ENTIRE_SERIES -> Unit
        }
        dao.getById(original.id)?.toModel()?.let { scheduleItem(it) }
        PastelCalWidgetProvider.updateAll(app)
    }

    fun delete(item: CalendarItem) = viewModelScope.launch(Dispatchers.IO) {
        ReminderScheduler.cancel(app, item)
        dao.getById(item.id)?.let { dao.delete(it) }
        dao.clearOccurrenceCompletionsForItem(item.id)
        PastelCalWidgetProvider.updateAll(app)
    }

    fun toggleTask(item: CalendarItem) = viewModelScope.launch(Dispatchers.IO) {
        val complete = !item.completed
        dao.setCompleted(item.id, complete, System.currentTimeMillis())
        val updated = dao.getById(item.id)?.toModel()
        if (updated != null) {
            if (complete) ReminderScheduler.cancel(app, updated) else scheduleItem(updated)
        }
        PastelCalWidgetProvider.updateAll(app)
    }

    fun toggleTaskOccurrence(item: CalendarItem, occurrenceDate: LocalDate) = viewModelScope.launch(Dispatchers.IO) {
        if (item.recurrence == Recurrence.NONE) {
            val complete = !item.completed
            dao.setCompleted(item.id, complete, System.currentTimeMillis())
            val updated = dao.getById(item.id)?.toModel()
            if (updated != null) {
                if (complete) ReminderScheduler.cancel(app, updated) else scheduleItem(updated)
            }
        } else {
            val key = completionKey(item.id, occurrenceDate.toEpochDay())
            val wasCompleted = completedOccurrences.value.contains(key)
            if (wasCompleted) {
                dao.clearOccurrenceCompletion(item.id, occurrenceDate.toEpochDay())
                scheduleItem(item)
            } else {
                dao.insertOccurrenceCompletion(OccurrenceCompletionEntity(item.id, occurrenceDate.toEpochDay()))
                ReminderScheduler.cancel(app, item)
                ReminderScheduler.scheduleFollowingOccurrence(
                    app, item, occurrenceDate.toEpochDay(), dao.getCompletedOccurrenceDays(item.id).toSet()
                )
            }
        }
        PastelCalWidgetProvider.updateAll(app)
    }

    fun shiftItemTime(item: CalendarItem, deltaMinutes: Int) = viewModelScope.launch(Dispatchers.IO) {
        val start = item.startTime ?: return@launch
        val originalStart = LocalDateTime.of(item.date, start)
        val originalEnd = item.endTime?.let { LocalDateTime.of(item.date, it) }
        val duration = originalEnd?.let {
            var d = Duration.between(originalStart, it)
            if (d.isNegative || d.isZero) d = Duration.ofHours(1)
            d
        }
        val shiftedStart = originalStart.plusMinutes(deltaMinutes.toLong())
        val shiftedEnd = duration?.let { shiftedStart.plus(it) }
        save(
            item.copy(
                date = shiftedStart.toLocalDate(),
                startTime = shiftedStart.toLocalTime(),
                endTime = shiftedEnd?.toLocalTime()
            )
        ).join()
    }

    fun clearNotificationHistory() = viewModelScope.launch(Dispatchers.IO) {
        dao.clearNotificationHistory()
    }

    fun startPeriod(date: LocalDate) = viewModelScope.launch(Dispatchers.IO) {
        if (dao.getCycleEntryStartingOn(date.toEpochDay()) != null) return@launch
        if (dao.getOpenCycleEntry() != null) return@launch
        dao.insertCycleEntry(CycleEntry(startDate = date).toEntity())
    }

    fun endPeriod(entryId: Long, date: LocalDate) = viewModelScope.launch(Dispatchers.IO) {
        val existing = dao.getCycleEntryById(entryId) ?: return@launch
        val start = LocalDate.ofEpochDay(existing.startEpochDay)
        if (date.isBefore(start) || date.isAfter(start.plusDays(14))) return@launch
        dao.updateCycleEntry(existing.copy(endEpochDay = date.toEpochDay(), updatedAt = System.currentTimeMillis()))
    }

    fun deleteCycleEntry(entryId: Long) = viewModelScope.launch(Dispatchers.IO) {
        dao.getCycleEntryById(entryId)?.let { dao.deleteCycleEntry(it) }
    }

    fun clearCycleEntries() = viewModelScope.launch(Dispatchers.IO) {
        dao.clearCycleEntries()
    }

    suspend fun importItems(imported: List<CalendarItem>): Int = withContext(Dispatchers.IO) {
        var added = 0
        imported.forEach { source ->
            val item = source.copy(id = 0, systemEventId = null)
            val id = dao.insert(item.toEntity())
            dao.getById(id)?.toModel()?.let { scheduleItem(it) }
            added++
        }
        PastelCalWidgetProvider.updateAll(app)
        added
    }

    suspend fun replaceAllFromBackup(
        items: List<CalendarItem>,
        completions: List<OccurrenceCompletionEntity>,
        cycleEntries: List<CycleEntry> = emptyList()
    ) = withContext(Dispatchers.IO) {
        dao.observeAll().first().map { it.toModel() }.forEach { ReminderScheduler.cancel(app, it) }
        dao.clearAllOccurrenceCompletions()
        dao.clearNotificationHistory()
        dao.clearCalendarItems()
        dao.clearCycleEntries()
        items.forEach { dao.insert(it.toEntity()) }
        completions.forEach { dao.insertOccurrenceCompletion(it) }
        cycleEntries.forEach { dao.insertCycleEntry(it.copy(id = 0).toEntity()) }
        dao.observeAll().first().map { it.toModel() }.forEach { scheduleItem(it) }
        PastelCalWidgetProvider.updateAll(app)
    }

    suspend fun importDeviceCalendar(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val result = CalendarProviderBridge.importUpcoming(app)
        var added = 0
        var duplicates = 0
        result.imported.forEach { item ->
            val externalId = item.systemEventId
            if (externalId != null && dao.countBySystemEventId(externalId) > 0) {
                duplicates++
            } else {
                val id = dao.insert(item.toEntity())
                dao.getById(id)?.toModel()?.let { scheduleItem(it) }
                added++
            }
        }
        PastelCalWidgetProvider.updateAll(app)
        added to (duplicates + result.skipped)
    }

    private suspend fun scheduleItem(item: CalendarItem) {
        ReminderScheduler.schedule(app, item, dao.getCompletedOccurrenceDays(item.id).toSet())
    }


    companion object {
        fun completionKey(itemId: Long, occurrenceEpochDay: Long): String = "$itemId:$occurrenceEpochDay"
    }
}
