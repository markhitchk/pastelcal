package com.pastelcal.app.reminders

import android.content.Context

object ReminderStateStore {
    private const val NAME = "pastelcal_reminder_state"

    fun lastFiredOccurrence(context: Context, itemId: Long): Long? =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getLong("fired_$itemId", Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }

    fun markFired(context: Context, itemId: Long, occurrenceEpochDay: Long) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putLong("fired_$itemId", occurrenceEpochDay).apply()
    }

    fun clear(context: Context, itemId: Long) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().remove("fired_$itemId").apply()
    }
}
