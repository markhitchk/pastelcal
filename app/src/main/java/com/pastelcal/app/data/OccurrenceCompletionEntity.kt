package com.pastelcal.app.data

import androidx.room.Entity

@Entity(
    tableName = "occurrence_completions",
    primaryKeys = ["itemId", "occurrenceEpochDay"]
)
data class OccurrenceCompletionEntity(
    val itemId: Long,
    val occurrenceEpochDay: Long,
    val completedAt: Long = System.currentTimeMillis()
)
