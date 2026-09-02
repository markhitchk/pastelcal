package com.pastelcal.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pastelcal.app.model.CalendarItem
import com.pastelcal.app.model.ItemKind
import com.pastelcal.app.model.Recurrence
import com.pastelcal.app.model.RecurrenceEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ReminderScheduler {
    private val standardOffsets = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 10080)

    fun schedule(context: Context, item: CalendarItem, completedOccurrenceDays: Set<Long> = emptySet()) {
        cancelLegacy(context, item.id)
        cancel(context, item)
        if (!canSchedule(item)) return
        scheduleFirstAvailableOccurrence(context, item, LocalDateTime.now(), completedOccurrenceDays)
    }

    fun scheduleFollowingOccurrence(
        context: Context,
        item: CalendarItem,
        afterOccurrenceEpochDay: Long,
        completedOccurrenceDays: Set<Long> = emptySet()
    ) {
        if (!canSchedule(item) || item.recurrence == Recurrence.NONE) return
        var candidate = RecurrenceEngine.nextOccurrence(item, LocalDate.ofEpochDay(afterOccurrenceEpochDay).plusDays(1)) ?: return
        repeat(400) {
            if (!(item.kind == ItemKind.TASK && candidate.toEpochDay() in completedOccurrenceDays)) {
                if (scheduleOccurrence(context, item, candidate, LocalDateTime.now())) return
            }
            candidate = RecurrenceEngine.nextOccurrence(item, candidate.plusDays(1)) ?: return
        }
    }

    fun snooze(context: Context, itemId: Long, occurrenceEpochDay: Long, minutes: Int = 10) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val trigger = System.currentTimeMillis() + minutes * 60_000L
        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_ITEM_ID, itemId)
            putExtra(ReminderReceiver.EXTRA_OCCURRENCE_DAY, occurrenceEpochDay)
            putExtra(ReminderReceiver.EXTRA_REMINDER_MINUTES, -1)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode(itemId, SNOOZE_OFFSET), snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(alarmManager, trigger, pendingIntent)
    }

    private fun cancelLegacy(context: Context, itemId: Long) {
        if (itemId == 0L) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            legacyRequestCode(itemId),
            Intent(context, ReminderReceiver::class.java).apply { action = ReminderReceiver.ACTION_FIRE },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun cancel(context: Context, item: CalendarItem) {
        if (item.id == 0L) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        (standardOffsets + item.allReminderMinutes + SNOOZE_OFFSET).distinct().forEach { offset ->
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode(item.id, offset),
                Intent(context, ReminderReceiver::class.java).apply { action = ReminderReceiver.ACTION_FIRE },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun canSchedule(item: CalendarItem): Boolean =
        item.id != 0L && item.allReminderMinutes.isNotEmpty() && !(item.kind == ItemKind.TASK && item.completed)

    private fun scheduleFirstAvailableOccurrence(
        context: Context,
        item: CalendarItem,
        now: LocalDateTime,
        completedOccurrenceDays: Set<Long>
    ) {
        var candidate = RecurrenceEngine.nextOccurrence(item, now.toLocalDate()) ?: return
        repeat(400) {
            val completedOccurrence = item.kind == ItemKind.TASK && item.recurrence != Recurrence.NONE && candidate.toEpochDay() in completedOccurrenceDays
            if (!completedOccurrence && scheduleOccurrence(context, item, candidate, now)) return
            if (item.recurrence == Recurrence.NONE) return
            candidate = RecurrenceEngine.nextOccurrence(item, candidate.plusDays(1)) ?: return
        }
    }

    /** Returns true when at least one alarm for this occurrence was placed. */
    private fun scheduleOccurrence(context: Context, item: CalendarItem, occurrence: LocalDate, now: LocalDateTime): Boolean {
        val startTime = item.startTime ?: LocalTime.of(9, 0)
        val eventTime = occurrence.atTime(startTime)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        var scheduled = false
        item.allReminderMinutes.forEach { offset ->
            val trigger = eventTime.minusMinutes(offset.toLong())
            if (trigger.isAfter(now)) {
                val triggerMillis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                setAlarm(alarmManager, triggerMillis, firePendingIntent(context, item.id, occurrence.toEpochDay(), offset))
                scheduled = true
            }
        }
        return scheduled
    }

    private fun setAlarm(alarmManager: AlarmManager, triggerMillis: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    private fun firePendingIntent(context: Context, itemId: Long, occurrenceEpochDay: Long, reminderMinutes: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_ITEM_ID, itemId)
            putExtra(ReminderReceiver.EXTRA_OCCURRENCE_DAY, occurrenceEpochDay)
            putExtra(ReminderReceiver.EXTRA_REMINDER_MINUTES, reminderMinutes)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(itemId, reminderMinutes),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun legacyRequestCode(itemId: Long): Int = (itemId xor (itemId ushr 32)).toInt() and 0x7fffffff

    private fun requestCode(itemId: Long, reminderMinutes: Int): Int {
        val base = (itemId xor (itemId ushr 32)).toInt() and 0x3fffffff
        return (base * 31 + reminderMinutes * 17) and 0x7fffffff
    }

    private const val SNOOZE_OFFSET = 999_991
}
