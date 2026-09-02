package com.pastelcal.app.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RecurrenceEngineTest {
    @Test fun monthly31SkipsShortMonths() {
        val item = CalendarItem(title="Month end", kind=ItemKind.EVENT, date=LocalDate.of(2026,1,31), recurrence=Recurrence.MONTHLY)
        val dates = RecurrenceEngine.occurrencesBetween(item, LocalDate.of(2026,1,1), LocalDate.of(2026,4,30))
        assertEquals(listOf(LocalDate.of(2026,1,31), LocalDate.of(2026,3,31)), dates)
    }

    @Test fun recurrenceEndAndExclusionAreRespected() {
        val item = CalendarItem(
            title="Standup", kind=ItemKind.EVENT, date=LocalDate.of(2026,8,1), recurrence=Recurrence.DAILY,
            recurrenceEndDate=LocalDate.of(2026,8,5), excludedDates=setOf(LocalDate.of(2026,8,3))
        )
        val dates = RecurrenceEngine.occurrencesBetween(item, LocalDate.of(2026,8,1), LocalDate.of(2026,8,10))
        assertEquals(listOf(
            LocalDate.of(2026,8,1), LocalDate.of(2026,8,2), LocalDate.of(2026,8,4), LocalDate.of(2026,8,5)
        ), dates)
    }

    @Test fun conflictDetectorFindsTimedOverlap() {
        val day = LocalDate.of(2026,8,30)
        val existing = CalendarItem(id=1, title="Existing", kind=ItemKind.EVENT, date=day, startTime=LocalTime.of(10,0), endTime=LocalTime.of(11,0))
        val candidate = CalendarItem(id=2, title="Candidate", kind=ItemKind.EVENT, date=day, startTime=LocalTime.of(10,30), endTime=LocalTime.of(11,30))
        assertEquals(listOf(existing), ScheduleConflictDetector.conflicts(candidate, listOf(existing), day))
    }
}
