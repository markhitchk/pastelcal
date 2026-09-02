package com.pastelcal.app.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object RecurrenceEngine {
    fun occursOn(item: CalendarItem, date: LocalDate): Boolean {
        if (date.isBefore(item.date)) return false
        if (item.recurrenceEndDate?.let { date.isAfter(it) } == true) return false
        if (date in item.excludedDates) return false
        return when (item.recurrence) {
            Recurrence.NONE -> date == item.date
            Recurrence.DAILY -> true
            Recurrence.WEEKLY -> ChronoUnit.DAYS.between(item.date, date) % 7L == 0L
            Recurrence.MONTHLY -> {
                val months = ChronoUnit.MONTHS.between(YearMonth.from(item.date), YearMonth.from(date))
                months >= 0 && date.dayOfMonth == item.date.dayOfMonth
            }
            Recurrence.YEARLY -> date.month == item.date.month && date.dayOfMonth == item.date.dayOfMonth
        }
    }

    fun occurrencesBetween(item: CalendarItem, start: LocalDate, endInclusive: LocalDate): List<LocalDate> {
        if (endInclusive.isBefore(start) || endInclusive.isBefore(item.date)) return emptyList()
        val boundedEnd = item.recurrenceEndDate?.let { minOf(it, endInclusive) } ?: endInclusive
        if (boundedEnd.isBefore(start)) return emptyList()
        val first = if (start.isBefore(item.date)) item.date else start
        val raw = when (item.recurrence) {
            Recurrence.NONE -> if (!item.date.isBefore(first) && !item.date.isAfter(boundedEnd)) listOf(item.date) else emptyList()
            Recurrence.DAILY -> generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(boundedEnd) }.toList()
            Recurrence.WEEKLY -> {
                val offset = Math.floorMod(ChronoUnit.DAYS.between(item.date, first), 7L)
                val candidate = if (offset == 0L) first else first.plusDays(7L - offset)
                generateSequence(candidate) { it.plusWeeks(1) }.takeWhile { !it.isAfter(boundedEnd) }.toList()
            }
            Recurrence.MONTHLY -> monthlyOccurrences(item.date, first, boundedEnd)
            Recurrence.YEARLY -> yearlyOccurrences(item.date, first, boundedEnd)
        }
        return raw.filterNot { it in item.excludedDates }
    }

    fun nextOccurrence(item: CalendarItem, fromDate: LocalDate): LocalDate? {
        val start = if (fromDate.isBefore(item.date)) item.date else fromDate
        if (item.recurrenceEndDate?.let { start.isAfter(it) } == true) return null
        val searchEnd = item.recurrenceEndDate ?: when (item.recurrence) {
            Recurrence.NONE -> item.date
            Recurrence.DAILY, Recurrence.WEEKLY -> start.plusYears(2)
            Recurrence.MONTHLY -> start.plusYears(10)
            Recurrence.YEARLY -> start.plusYears(20)
        }
        return occurrencesBetween(item, start, searchEnd).firstOrNull()
    }

    private fun monthlyOccurrences(anchor: LocalDate, start: LocalDate, end: LocalDate): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var month = YearMonth.from(if (start.isBefore(anchor)) anchor else start)
        val lastMonth = YearMonth.from(end)
        while (!month.isAfter(lastMonth)) {
            if (anchor.dayOfMonth <= month.lengthOfMonth()) {
                val candidate = month.atDay(anchor.dayOfMonth)
                if (!candidate.isBefore(anchor) && !candidate.isBefore(start) && !candidate.isAfter(end)) result += candidate
            }
            month = month.plusMonths(1)
        }
        return result
    }

    private fun yearlyOccurrences(anchor: LocalDate, start: LocalDate, end: LocalDate): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        for (year in maxOf(anchor.year, start.year)..end.year) {
            val candidate = runCatching { LocalDate.of(year, anchor.month, anchor.dayOfMonth) }.getOrNull() ?: continue
            if (!candidate.isBefore(anchor) && !candidate.isBefore(start) && !candidate.isAfter(end)) result += candidate
        }
        return result
    }
}
