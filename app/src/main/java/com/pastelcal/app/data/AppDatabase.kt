package com.pastelcal.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CalendarEntity::class,
        OccurrenceCompletionEntity::class,
        NotificationHistoryEntity::class,
        CycleEntryEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendar_items ADD COLUMN additionalReminderMinutesCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS occurrence_completions (
                        itemId INTEGER NOT NULL,
                        occurrenceEpochDay INTEGER NOT NULL,
                        completedAt INTEGER NOT NULL,
                        PRIMARY KEY(itemId, occurrenceEpochDay)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notification_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        itemId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        occurrenceEpochDay INTEGER NOT NULL,
                        reminderMinutes INTEGER,
                        action TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendar_items ADD COLUMN recurrenceEndEpochDay INTEGER")
                db.execSQL("ALTER TABLE calendar_items ADD COLUMN excludedEpochDaysCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE calendar_items ADD COLUMN seriesParentId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_items_dateEpochDay ON calendar_items(dateEpochDay)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_items_kind ON calendar_items(kind)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_items_systemEventId ON calendar_items(systemEventId)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cycle_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        startEpochDay INTEGER NOT NULL,
                        endEpochDay INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_cycle_entries_startEpochDay ON cycle_entries(startEpochDay)")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pastelcal.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}
