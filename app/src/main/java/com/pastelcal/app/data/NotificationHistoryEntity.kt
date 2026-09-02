package com.pastelcal.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_history")
data class NotificationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val title: String,
    val occurrenceEpochDay: Long,
    val reminderMinutes: Int?,
    val action: String,
    val createdAt: Long = System.currentTimeMillis()
)
