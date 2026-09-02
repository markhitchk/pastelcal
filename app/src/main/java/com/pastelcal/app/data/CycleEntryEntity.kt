package com.pastelcal.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pastelcal.app.model.CycleEntry
import java.time.LocalDate

@Entity(
    tableName = "cycle_entries",
    indices = [Index(value = ["startEpochDay"], unique = true)]
)
data class CycleEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun CycleEntryEntity.toModel(): CycleEntry = CycleEntry(
    id = id,
    startDate = LocalDate.ofEpochDay(startEpochDay),
    endDate = endEpochDay?.let(LocalDate::ofEpochDay)
)

fun CycleEntry.toEntity(existingCreatedAt: Long? = null): CycleEntryEntity = CycleEntryEntity(
    id = id,
    startEpochDay = startDate.toEpochDay(),
    endEpochDay = endDate?.toEpochDay(),
    createdAt = existingCreatedAt ?: System.currentTimeMillis(),
    updatedAt = System.currentTimeMillis()
)
