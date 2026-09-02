package com.pastelcal.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pastelcal.app.model.*
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "calendar_items",
    indices = [
        Index(value = ["dateEpochDay"]),
        Index(value = ["kind"]),
        Index(value = ["systemEventId"])
    ]
)
data class CalendarEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: String,
    val dateEpochDay: Long,
    val startMinutes: Int?,
    val endMinutes: Int?,
    val category: String,
    val completed: Boolean,
    val notes: String,
    val location: String,
    val recurrence: String,
    val recurrenceEndEpochDay: Long? = null,
    val excludedEpochDaysCsv: String = "",
    val seriesParentId: Long? = null,
    val reminderMinutes: Int?,
    val additionalReminderMinutesCsv: String = "",
    val systemEventId: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

fun CalendarEntity.toModel() = CalendarItem(
    id = id,
    title = title,
    kind = enumValueOrDefault(kind, ItemKind.EVENT),
    date = LocalDate.ofEpochDay(dateEpochDay),
    startTime = startMinutes?.let { LocalTime.of(it / 60, it % 60) },
    endTime = endMinutes?.let { LocalTime.of(it / 60, it % 60) },
    category = enumValueOrDefault(category, PastelCategory.LAVENDER),
    completed = completed,
    notes = notes,
    location = location,
    recurrence = enumValueOrDefault(recurrence, Recurrence.NONE),
    recurrenceEndDate = recurrenceEndEpochDay?.let(LocalDate::ofEpochDay),
    excludedDates = excludedEpochDaysCsv
        .split(',')
        .mapNotNull { it.trim().toLongOrNull() }
        .map(LocalDate::ofEpochDay)
        .toSet(),
    seriesParentId = seriesParentId,
    reminderMinutes = reminderMinutes,
    additionalReminderMinutes = additionalReminderMinutesCsv
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it >= 0 }
        .distinct(),
    systemEventId = systemEventId
)

fun CalendarItem.toEntity(existingCreatedAt: Long? = null): CalendarEntity {
    val now = System.currentTimeMillis()
    return CalendarEntity(
        id = id,
        title = title.trim(),
        kind = kind.name,
        dateEpochDay = date.toEpochDay(),
        startMinutes = startTime?.let { it.hour * 60 + it.minute },
        endMinutes = endTime?.let { it.hour * 60 + it.minute },
        category = category.name,
        completed = completed,
        notes = notes.trim(),
        location = location.trim(),
        recurrence = recurrence.name,
        recurrenceEndEpochDay = recurrenceEndDate?.toEpochDay(),
        excludedEpochDaysCsv = excludedDates.map(LocalDate::toEpochDay).sorted().joinToString(","),
        seriesParentId = seriesParentId,
        reminderMinutes = reminderMinutes,
        additionalReminderMinutesCsv = additionalReminderMinutes
            .filter { it >= 0 && it != reminderMinutes }
            .distinct()
            .joinToString(","),
        systemEventId = systemEventId,
        createdAt = existingCreatedAt ?: now,
        updatedAt = now
    )
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
