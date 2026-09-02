package com.pastelcal.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar_items ORDER BY dateEpochDay ASC, COALESCE(startMinutes, 1440) ASC, updatedAt DESC")
    fun observeAll(): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM occurrence_completions")
    fun observeOccurrenceCompletions(): Flow<List<OccurrenceCompletionEntity>>

    @Query("SELECT * FROM notification_history ORDER BY createdAt DESC LIMIT 100")
    fun observeNotificationHistory(): Flow<List<NotificationHistoryEntity>>

    @Query("SELECT * FROM cycle_entries ORDER BY startEpochDay ASC")
    fun observeCycleEntries(): Flow<List<CycleEntryEntity>>

    @Query("SELECT * FROM cycle_entries WHERE id = :id LIMIT 1")
    suspend fun getCycleEntryById(id: Long): CycleEntryEntity?

    @Query("SELECT * FROM cycle_entries WHERE startEpochDay = :startEpochDay LIMIT 1")
    suspend fun getCycleEntryStartingOn(startEpochDay: Long): CycleEntryEntity?

    @Query("SELECT * FROM cycle_entries WHERE endEpochDay IS NULL ORDER BY startEpochDay DESC LIMIT 1")
    suspend fun getOpenCycleEntry(): CycleEntryEntity?

    @Query("SELECT * FROM calendar_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CalendarEntity?

    @Query("SELECT COUNT(*) FROM calendar_items")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM calendar_items WHERE systemEventId = :systemEventId")
    suspend fun countBySystemEventId(systemEventId: Long): Int

    @Query("SELECT occurrenceEpochDay FROM occurrence_completions WHERE itemId = :itemId")
    suspend fun getCompletedOccurrenceDays(itemId: Long): List<Long>

    @Insert
    suspend fun insert(item: CalendarEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOccurrenceCompletion(item: OccurrenceCompletionEntity)

    @Insert
    suspend fun insertNotificationHistory(item: NotificationHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCycleEntry(item: CycleEntryEntity): Long

    @Update
    suspend fun updateCycleEntry(item: CycleEntryEntity)

    @Delete
    suspend fun deleteCycleEntry(item: CycleEntryEntity)

    @Update
    suspend fun update(item: CalendarEntity)

    @Delete
    suspend fun delete(item: CalendarEntity)

    @Query("UPDATE calendar_items SET completed = :completed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, updatedAt: Long)

    @Query("DELETE FROM occurrence_completions WHERE itemId = :itemId AND occurrenceEpochDay = :occurrenceEpochDay")
    suspend fun clearOccurrenceCompletion(itemId: Long, occurrenceEpochDay: Long)

    @Query("DELETE FROM occurrence_completions WHERE itemId = :itemId")
    suspend fun clearOccurrenceCompletionsForItem(itemId: Long)

    @Query("DELETE FROM occurrence_completions")
    suspend fun clearAllOccurrenceCompletions()

    @Query("DELETE FROM notification_history WHERE id NOT IN (SELECT id FROM notification_history ORDER BY createdAt DESC LIMIT 100)")
    suspend fun pruneNotificationHistory()

    @Query("DELETE FROM notification_history")
    suspend fun clearNotificationHistory()

    @Query("DELETE FROM calendar_items")
    suspend fun clearCalendarItems()

    @Query("DELETE FROM cycle_entries")
    suspend fun clearCycleEntries()
}
