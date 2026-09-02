package com.pastelcal.app.model

import java.time.LocalDate
import java.time.LocalTime

enum class ItemKind(val label: String) {
    EVENT("Event"),
    TASK("Task"),
    REMINDER("Reminder")
}

enum class PastelCategory(val label: String) {
    LAVENDER("Lavender"),
    SKY("Sky"),
    MINT("Mint"),
    PEACH("Peach"),
    PINK("Pink"),
    BUTTER("Butter")
}

enum class Recurrence(val label: String) {
    NONE("Does not repeat"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

enum class SeriesEditScope(val label: String) {
    THIS_OCCURRENCE("This occurrence"),
    THIS_AND_FUTURE("This and future"),
    ENTIRE_SERIES("Entire series")
}

data class CalendarItem(
    val id: Long = 0,
    val title: String,
    val kind: ItemKind,
    val date: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val category: PastelCategory = PastelCategory.LAVENDER,
    val completed: Boolean = false,
    val notes: String = "",
    val location: String = "",
    val recurrence: Recurrence = Recurrence.NONE,
    val recurrenceEndDate: LocalDate? = null,
    val excludedDates: Set<LocalDate> = emptySet(),
    val seriesParentId: Long? = null,
    val reminderMinutes: Int? = 30,
    val additionalReminderMinutes: List<Int> = emptyList(),
    val systemEventId: Long? = null
) {
    val allReminderMinutes: List<Int>
        get() = (listOfNotNull(reminderMinutes) + additionalReminderMinutes)
            .filter { it >= 0 }
            .distinct()
            .sortedDescending()

    val isSeries: Boolean get() = recurrence != Recurrence.NONE
}
