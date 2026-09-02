package com.pastelcal.app.model

import java.time.LocalDate
import java.time.LocalTime

object ScheduleConflictDetector {
    fun conflicts(candidate: CalendarItem, allItems: List<CalendarItem>, onDate: LocalDate = candidate.date): List<CalendarItem> {
        val candidateStart = candidate.startTime ?: return emptyList()
        val candidateEnd = normalizedEnd(candidateStart, candidate.endTime)
        return allItems.asSequence()
            .filter { it.id != candidate.id }
            .filter { candidate.seriesParentId == null || it.id != candidate.seriesParentId }
            .filter { RecurrenceEngine.occursOn(it, onDate) }
            .filter { it.startTime != null }
            .filter { other ->
                val otherStart = other.startTime!!
                val otherEnd = normalizedEnd(otherStart, other.endTime)
                candidateStart < otherEnd && otherStart < candidateEnd
            }
            .sortedBy { it.startTime }
            .toList()
    }

    private fun normalizedEnd(start: LocalTime, end: LocalTime?): LocalTime {
        val candidate = end ?: start.plusHours(1)
        return if (candidate > start) candidate else LocalTime.MAX
    }
}
