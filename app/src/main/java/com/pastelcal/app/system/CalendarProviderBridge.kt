package com.pastelcal.app.system

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.pastelcal.app.model.*
import java.time.*

object CalendarProviderBridge {
    data class ImportResult(val imported: List<CalendarItem>, val skipped: Int)
    data class DeviceCalendar(val id: Long, val displayName: String, val accountName: String, val accessLevel: Int)

    fun hasCalendarPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun availableCalendars(context: Context): List<DeviceCalendar> {
        if (!hasCalendarPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            buildList {
                val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val accessIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
                while (cursor.moveToNext()) {
                    add(DeviceCalendar(
                        id = cursor.getLong(idIndex),
                        displayName = cursor.getString(nameIndex).orEmpty().ifBlank { "Calendar" },
                        accountName = cursor.getString(accountIndex).orEmpty(),
                        accessLevel = cursor.getInt(accessIndex)
                    ))
                }
            }
        } ?: emptyList()
    }

    fun exportEvent(context: Context, item: CalendarItem, calendarId: Long): Long? {
        if (!hasCalendarPermission(context) || item.kind == ItemKind.TASK) return null
        val timed = item.startTime != null
        val localZone = ZoneId.systemDefault()
        val eventZone = if (timed) localZone else ZoneOffset.UTC
        val start = item.startTime ?: LocalTime.MIDNIGHT
        val end = item.endTime ?: if (timed) start.plusHours(1) else LocalTime.MIDNIGHT
        val beginMillis = item.date.atTime(start).atZone(eventZone).toInstant().toEpochMilli()
        val endMillis = if (timed) {
            item.date.atTime(end).atZone(eventZone).toInstant().toEpochMilli()
        } else {
            item.date.plusDays(1).atStartOfDay(eventZone).toInstant().toEpochMilli()
        }
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, beginMillis)
            put(CalendarContract.Events.TITLE, item.title)
            put(CalendarContract.Events.DESCRIPTION, item.notes)
            put(CalendarContract.Events.EVENT_LOCATION, item.location)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, eventZone.id)
            put(CalendarContract.Events.ALL_DAY, if (timed) 0 else 1)
            val rule = recurrenceRule(item, eventZone)
            if (rule == null) {
                put(CalendarContract.Events.DTEND, endMillis)
            } else {
                val durationMinutes = if (timed) java.time.Duration.between(start, end).toMinutes().coerceAtLeast(1) else 1440L
                put(CalendarContract.Events.DURATION, "PT${durationMinutes}M")
                put(CalendarContract.Events.RRULE, rule)
                recurrenceExceptionDates(item, eventZone)?.let { put(CalendarContract.Events.EXDATE, it) }
            }
        }
        val eventId = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let(ContentUris::parseId) ?: return null
        item.allReminderMinutes.forEach { minutes ->
            val reminder = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder)
        }
        return eventId
    }

    fun importUpcoming(context: Context, days: Long = 90): ImportResult {
        if (!hasCalendarPermission(context)) return ImportResult(emptyList(), 0)
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.now().plusDays(days).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.ALL_DAY
        )
        val imported = mutableListOf<CalendarItem>()
        var skipped = 0
        context.contentResolver.query(uri, projection, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cursor ->
            val idI = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleI = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginI = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endI = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val locationI = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            val descriptionI = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
            val allDayI = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            while (cursor.moveToNext()) {
                val title = cursor.getString(titleI)?.trim().orEmpty()
                if (title.isBlank()) { skipped++; continue }
                val allDay = cursor.getInt(allDayI) != 0
                val beginInstant = Instant.ofEpochMilli(cursor.getLong(beginI))
                val finishInstant = Instant.ofEpochMilli(cursor.getLong(endI))
                val begin = beginInstant.atZone(if (allDay) ZoneOffset.UTC else zone)
                val finish = finishInstant.atZone(if (allDay) ZoneOffset.UTC else zone)
                imported += CalendarItem(
                    title = title,
                    kind = ItemKind.EVENT,
                    date = begin.toLocalDate(),
                    startTime = if (allDay) null else begin.toLocalTime().withSecond(0).withNano(0),
                    endTime = if (allDay) null else finish.toLocalTime().withSecond(0).withNano(0),
                    category = PastelCategory.SKY,
                    location = cursor.getString(locationI).orEmpty(),
                    notes = cursor.getString(descriptionI).orEmpty(),
                    reminderMinutes = null,
                    systemEventId = systemInstanceKey(cursor.getLong(idI), cursor.getLong(beginI))
                )
            }
        }
        return ImportResult(imported, skipped)
    }

    private fun recurrenceRule(item: CalendarItem, zone: ZoneId): String? {
        val base = when (item.recurrence) {
            Recurrence.NONE -> return null
            Recurrence.DAILY -> "FREQ=DAILY"
            Recurrence.WEEKLY -> "FREQ=WEEKLY"
            Recurrence.MONTHLY -> "FREQ=MONTHLY"
            Recurrence.YEARLY -> "FREQ=YEARLY"
        }
        val until = item.recurrenceEndDate?.let { date ->
            val instant = date.plusDays(1).atStartOfDay(zone).minusNanos(1).toInstant()
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(instant)
        }
        return if (until == null) base else "$base;UNTIL=$until"
    }

    private fun systemInstanceKey(eventId: Long, beginMillis: Long): Long =
        (eventId * 1_000_003L) xor beginMillis

    private fun recurrenceExceptionDates(item: CalendarItem, zone: ZoneId): String? {
        if (item.excludedDates.isEmpty()) return null
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
        val time = item.startTime ?: LocalTime.MIDNIGHT
        return item.excludedDates.sorted().joinToString(",") { date ->
            formatter.format(date.atTime(time).atZone(zone).toInstant())
        }
    }
}
