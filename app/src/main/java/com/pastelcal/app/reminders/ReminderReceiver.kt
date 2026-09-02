package com.pastelcal.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pastelcal.app.MainActivity
import com.pastelcal.app.data.AppDatabase
import com.pastelcal.app.data.NotificationHistoryEntity
import com.pastelcal.app.data.OccurrenceCompletionEntity
import com.pastelcal.app.data.toModel
import com.pastelcal.app.model.ItemKind
import com.pastelcal.app.model.Recurrence
import com.pastelcal.app.widget.PastelCalWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        val occurrenceDay = intent.getLongExtra(EXTRA_OCCURRENCE_DAY, Long.MIN_VALUE)
        val reminderMinutes = intent.getIntExtra(EXTRA_REMINDER_MINUTES, -1)
        if (itemId <= 0L || occurrenceDay == Long.MIN_VALUE) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).calendarDao()
                val entity = dao.getById(itemId) ?: return@launch
                val item = entity.toModel()
                when (intent.action) {
                    ACTION_SNOOZE -> {
                        ReminderScheduler.snooze(context, itemId, occurrenceDay)
                        dao.insertNotificationHistory(history(itemId, item.title, occurrenceDay, reminderMinutes.takeIf { it >= 0 }, "SNOOZED"))
                        dao.pruneNotificationHistory()
                        NotificationManagerCompat.from(context).cancel(notificationId(itemId))
                    }
                    ACTION_DONE -> {
                        if (item.kind == ItemKind.TASK) {
                            if (item.recurrence == Recurrence.NONE) {
                                dao.setCompleted(itemId, true, System.currentTimeMillis())
                                ReminderScheduler.cancel(context, item)
                            } else {
                                dao.insertOccurrenceCompletion(OccurrenceCompletionEntity(itemId, occurrenceDay))
                                ReminderScheduler.cancel(context, item)
                                ReminderScheduler.scheduleFollowingOccurrence(
                                    context, item, occurrenceDay, dao.getCompletedOccurrenceDays(item.id).toSet()
                                )
                            }
                            dao.insertNotificationHistory(history(itemId, item.title, occurrenceDay, reminderMinutes.takeIf { it >= 0 }, "COMPLETED"))
                            dao.pruneNotificationHistory()
                            PastelCalWidgetProvider.updateAll(context)
                        }
                        NotificationManagerCompat.from(context).cancel(notificationId(itemId))
                    }
                    else -> {
                        val normalizedReminder = reminderMinutes.takeIf { it >= 0 }
                        dao.insertNotificationHistory(history(itemId, item.title, occurrenceDay, normalizedReminder, "FIRED"))
                        dao.pruneNotificationHistory()
                        showNotification(context, item, occurrenceDay, normalizedReminder)
                        if (normalizedReminder != null && normalizedReminder == item.allReminderMinutes.minOrNull()) {
                            ReminderScheduler.scheduleFollowingOccurrence(
                                context, item, occurrenceDay, dao.getCompletedOccurrenceDays(item.id).toSet()
                            )
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun history(itemId: Long, title: String, occurrenceDay: Long, minutes: Int?, action: String) =
        NotificationHistoryEntity(
            itemId = itemId,
            title = title,
            occurrenceEpochDay = occurrenceDay,
            reminderMinutes = minutes,
            action = action
        )

    private fun showNotification(context: Context, item: com.pastelcal.app.model.CalendarItem, occurrenceDay: Long, reminderMinutes: Int?) {
        createChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val occurrence = LocalDate.ofEpochDay(occurrenceDay)
        val openIntent = PendingIntent.getActivity(
            context,
            notificationId(item.id),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            notificationId(item.id) + 1,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_ITEM_ID, item.id)
                putExtra(EXTRA_OCCURRENCE_DAY, occurrenceDay)
                putExtra(EXTRA_REMINDER_MINUTES, reminderMinutes ?: -1)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val doneIntent = PendingIntent.getBroadcast(
            context,
            notificationId(item.id) + 2,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_DONE
                putExtra(EXTRA_ITEM_ID, item.id)
                putExtra(EXTRA_OCCURRENCE_DAY, occurrenceDay)
                putExtra(EXTRA_REMINDER_MINUTES, reminderMinutes ?: -1)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeText = item.startTime?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "Today"
        val reminderText = reminderMinutes?.let { if (it == 0) "At event time" else "$it min before" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(item.title)
            .setContentText(listOfNotNull("${occurrence.format(DateTimeFormatter.ofPattern("EEE, MMM d"))} · $timeText", reminderText).joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(buildString {
                append("${occurrence.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))} · $timeText")
                if (reminderText != null) append("\n$reminderText")
                if (item.location.isNotBlank()) append("\n${item.location}")
                if (item.notes.isNotBlank()) append("\n${item.notes}")
            }))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze 10m", snoozeIntent)
            .apply {
                if (item.kind == ItemKind.TASK) addAction(android.R.drawable.checkbox_on_background, "Done", doneIntent)
            }
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(item.id), notification)
    }

    companion object {
        const val ACTION_FIRE = "com.pastelcal.app.reminders.FIRE"
        const val ACTION_SNOOZE = "com.pastelcal.app.reminders.SNOOZE"
        const val ACTION_DONE = "com.pastelcal.app.reminders.DONE"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_OCCURRENCE_DAY = "occurrence_day"
        const val EXTRA_REMINDER_MINUTES = "reminder_minutes"
        private const val CHANNEL_ID = "pastelcal_reminders"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "Calendar reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Event, task, and reminder alerts from PastelCal"
                }
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }

        private fun notificationId(itemId: Long): Int = ((itemId xor (itemId ushr 32)).toInt() and 0x3fffffff) + 2000
    }
}
