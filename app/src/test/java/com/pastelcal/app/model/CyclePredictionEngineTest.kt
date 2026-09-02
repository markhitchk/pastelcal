package com.pastelcal.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CyclePredictionEngineTest {
    @Test
    fun predictsFromRecentWeightedHistory() {
        val entries = listOf(
            "2026-01-01", "2026-01-29", "2026-02-26", "2026-03-26", "2026-04-24", "2026-05-22"
        ).map { CycleEntry(startDate = LocalDate.parse(it), endDate = LocalDate.parse(it).plusDays(4)) }

        val prediction = assertNotNull(CyclePredictionEngine.predict(entries))
        assertEquals(LocalDate.parse("2026-06-19"), prediction!!.expectedStart)
        assertEquals(28, prediction.estimatedCycleLengthDays)
        assertEquals(5, prediction.estimatedPeriodLengthDays)
        assertTrue(prediction.likelyStartFrom.isBefore(prediction.expectedStart))
    }

    @Test
    fun ignoresExtremeCycleOutlierWhenEnoughHistoryExists() {
        val entries = listOf(
            "2026-01-01", "2026-01-29", "2026-02-26", "2026-03-26", "2026-05-20", "2026-06-17", "2026-07-15"
        ).map { CycleEntry(startDate = LocalDate.parse(it)) }

        val prediction = assertNotNull(CyclePredictionEngine.predict(entries))
        assertTrue(prediction!!.estimatedCycleLengthDays in 27..30)
    }

    @Test
    fun oneRecordUsesLowConfidenceFallback() {
        val prediction = assertNotNull(CyclePredictionEngine.predict(listOf(CycleEntry(startDate = LocalDate.parse("2026-08-01")))))
        assertEquals(CycleConfidence.LOW, prediction!!.confidence)
        assertEquals(28, prediction.estimatedCycleLengthDays)
    }
}
