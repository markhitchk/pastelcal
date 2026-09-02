package com.pastelcal.app.model

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class CycleEntry(
    val id: Long = 0,
    val startDate: LocalDate,
    val endDate: LocalDate? = null
)

enum class CycleConfidence(val label: String) {
    LOW("Low confidence"),
    MEDIUM("Medium confidence"),
    HIGH("High confidence")
}

data class CyclePrediction(
    val expectedStart: LocalDate,
    val likelyStartFrom: LocalDate,
    val likelyStartTo: LocalDate,
    val expectedPeriodEnd: LocalDate,
    val estimatedCycleLengthDays: Int,
    val estimatedPeriodLengthDays: Int,
    val confidence: CycleConfidence,
    val completedCycleCount: Int,
    val variabilityDays: Double,
    val estimatedOvulationDate: LocalDate?,
    val fertileWindowStart: LocalDate?,
    val fertileWindowEnd: LocalDate?
)

enum class CycleDayKind {
    RECORDED_PERIOD,
    PREDICTED_PERIOD,
    ESTIMATED_FERTILE
}

/**
 * Local-only date prediction helper. It intentionally returns ranges/confidence rather than
 * presenting a calendar estimate as a medical measurement.
 */
object CyclePredictionEngine {
    private const val DEFAULT_CYCLE_DAYS = 28
    private const val DEFAULT_PERIOD_DAYS = 5
    private const val MIN_CYCLE_DAYS = 15
    private const val MAX_CYCLE_DAYS = 120

    fun predict(entries: List<CycleEntry>): CyclePrediction? {
        val ordered = entries
            .distinctBy { it.startDate }
            .sortedBy { it.startDate }
        if (ordered.isEmpty()) return null

        val rawLengths = ordered.zipWithNext { a, b -> (b.startDate.toEpochDay() - a.startDate.toEpochDay()).toInt() }
            .filter { it in MIN_CYCLE_DAYS..MAX_CYCLE_DAYS }
            .takeLast(12)
        val robustLengths = rejectOutliers(rawLengths)
        val estimatedCycle = if (robustLengths.isEmpty()) DEFAULT_CYCLE_DAYS else weightedAverage(robustLengths)
            .roundToInt().coerceIn(MIN_CYCLE_DAYS, MAX_CYCLE_DAYS)

        val variability = if (robustLengths.size >= 2) weightedStdDev(robustLengths, estimatedCycle.toDouble()) else 6.0
        val uncertainty = when {
            robustLengths.size < 2 -> 5
            robustLengths.size < 4 -> ceil(max(3.0, variability * 1.25)).toInt()
            else -> ceil(max(2.0, variability * 1.15)).toInt()
        }.coerceIn(2, 21)

        val periodDurations = ordered.mapNotNull { entry ->
            entry.endDate?.let { end -> (end.toEpochDay() - entry.startDate.toEpochDay() + 1).toInt() }
        }.filter { it in 1..14 }.takeLast(12)
        val estimatedPeriod = if (periodDurations.isEmpty()) DEFAULT_PERIOD_DAYS else weightedAverage(periodDurations)
            .roundToInt().coerceIn(1, 14)

        val lastStart = ordered.last().startDate
        val expectedStart = lastStart.plusDays(estimatedCycle.toLong())
        val expectedEnd = expectedStart.plusDays((estimatedPeriod - 1).toLong())
        val confidence = when {
            robustLengths.size >= 6 && variability <= 2.5 -> CycleConfidence.HIGH
            robustLengths.size >= 3 && variability <= 5.0 -> CycleConfidence.MEDIUM
            else -> CycleConfidence.LOW
        }

        // Calendar-only ovulation estimate. Date-only history cannot detect actual ovulation.
        val ovulation = if (robustLengths.size >= 2) expectedStart.minusDays(14) else null
        val fertileStart = ovulation?.minusDays(5)
        val fertileEnd = ovulation?.plusDays(1)

        return CyclePrediction(
            expectedStart = expectedStart,
            likelyStartFrom = expectedStart.minusDays(uncertainty.toLong()),
            likelyStartTo = expectedStart.plusDays(uncertainty.toLong()),
            expectedPeriodEnd = expectedEnd,
            estimatedCycleLengthDays = estimatedCycle,
            estimatedPeriodLengthDays = estimatedPeriod,
            confidence = confidence,
            completedCycleCount = robustLengths.size,
            variabilityDays = variability,
            estimatedOvulationDate = ovulation,
            fertileWindowStart = fertileStart,
            fertileWindowEnd = fertileEnd
        )
    }

    fun dayKinds(
        entries: List<CycleEntry>,
        prediction: CyclePrediction?,
        showFertileWindow: Boolean
    ): Map<LocalDate, CycleDayKind> {
        val result = linkedMapOf<LocalDate, CycleDayKind>()
        entries.forEach { entry ->
            val end = entry.endDate ?: entry.startDate
            datesBetween(entry.startDate, end).forEach { result[it] = CycleDayKind.RECORDED_PERIOD }
        }
        prediction?.let { p ->
            datesBetween(p.expectedStart, p.expectedPeriodEnd).forEach { date ->
                result.putIfAbsent(date, CycleDayKind.PREDICTED_PERIOD)
            }
            if (showFertileWindow && p.fertileWindowStart != null && p.fertileWindowEnd != null) {
                datesBetween(p.fertileWindowStart, p.fertileWindowEnd).forEach { date ->
                    result.putIfAbsent(date, CycleDayKind.ESTIMATED_FERTILE)
                }
            }
        }
        return result
    }

    private fun rejectOutliers(values: List<Int>): List<Int> {
        if (values.size < 4) return values
        val median = median(values)
        val deviations = values.map { kotlin.math.abs(it - median) }
        val mad = medianDouble(deviations).coerceAtLeast(1.0)
        val threshold = max(4.0, mad * 2.5)
        val filtered = values.filter { kotlin.math.abs(it - median) <= threshold }
        return if (filtered.size >= 2) filtered else values
    }

    private fun weightedAverage(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0
        var weighted = 0.0
        var totalWeight = 0.0
        values.forEachIndexed { index, value ->
            val weight = (index + 1).toDouble()
            weighted += value * weight
            totalWeight += weight
        }
        return weighted / totalWeight
    }

    private fun weightedStdDev(values: List<Int>, center: Double): Double {
        if (values.size < 2) return 0.0
        var sum = 0.0
        var totalWeight = 0.0
        values.forEachIndexed { index, value ->
            val weight = (index + 1).toDouble()
            val diff = value - center
            sum += weight * diff * diff
            totalWeight += weight
        }
        return sqrt(sum / totalWeight)
    }

    private fun median(values: List<Int>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid].toDouble()
    }

    private fun medianDouble(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun datesBetween(start: LocalDate, end: LocalDate): Sequence<LocalDate> = sequence {
        if (end.isBefore(start)) return@sequence
        var date = start
        while (!date.isAfter(end)) {
            yield(date)
            date = date.plusDays(1)
        }
    }
}
