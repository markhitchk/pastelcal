package com.pastelcal.app.transfer

import com.pastelcal.app.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

object IcsTransfer {
    private val dateFmt = DateTimeFormatter.BASIC_ISO_DATE
    private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val utcStampFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun export(items: List<CalendarItem>): String = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//PastelCal//Android//EN")
        appendLine("CALSCALE:GREGORIAN")
        items.forEach { item ->
            appendLine("BEGIN:VEVENT")
            appendLine("UID:${item.id.takeIf { it > 0 } ?: UUID.randomUUID()}@pastelcal")
            appendLine("DTSTAMP:${ZonedDateTime.now(ZoneOffset.UTC).format(utcStampFmt)}")
            appendLine("SUMMARY:${escape(item.title)}")
            if (item.startTime == null) {
                appendLine("DTSTART;VALUE=DATE:${item.date.format(dateFmt)}")
            } else {
                appendLine("DTSTART:${item.date.atTime(item.startTime).format(dateTimeFmt)}")
                item.endTime?.let { appendLine("DTEND:${item.date.atTime(it).format(dateTimeFmt)}") }
            }
            if (item.location.isNotBlank()) appendLine("LOCATION:${escape(item.location)}")
            if (item.notes.isNotBlank()) appendLine("DESCRIPTION:${escape(item.notes)}")
            appendLine("X-PASTELCAL-KIND:${item.kind.name}")
            appendLine("X-PASTELCAL-CATEGORY:${item.category.name}")
            if (item.recurrence != Recurrence.NONE) {
                val until = item.recurrenceEndDate?.format(dateFmt)?.let { ";UNTIL=${it}T235959" }.orEmpty()
                appendLine("RRULE:FREQ=${item.recurrence.name}$until")
                if (item.excludedDates.isNotEmpty()) {
                    if (item.startTime == null) {
                        appendLine("EXDATE;VALUE=DATE:${item.excludedDates.sorted().joinToString(",") { it.format(dateFmt) }}")
                    } else {
                        appendLine("EXDATE:${item.excludedDates.sorted().joinToString(",") { it.atTime(item.startTime).format(dateTimeFmt) }}")
                    }
                }
            }
            item.allReminderMinutes.forEach { minutes ->
                appendLine("BEGIN:VALARM")
                appendLine("ACTION:DISPLAY")
                appendLine("DESCRIPTION:${escape(item.title)}")
                appendLine("TRIGGER:${triggerValue(minutes)}")
                appendLine("END:VALARM")
            }
            appendLine("END:VEVENT")
        }
        appendLine("END:VCALENDAR")
    }

    fun import(text: String): List<CalendarItem> {
        require(text.length <= 20_000_000) { "ICS file is too large" }
        val normalized = text.replace("\r\n", "\n")
            .replace(Regex("\n[ \\t]"), "")
        val blocks = Regex("BEGIN:VEVENT\\n(.*?)\\nEND:VEVENT", RegexOption.DOT_MATCHES_ALL)
            .findAll(normalized)
            .map { it.groupValues[1] }
            .take(100_001)
            .toList()
        require(blocks.size <= 100_000) { "ICS file contains too many events" }

        return blocks.mapNotNull { parseEvent(it) }
    }

    private fun parseEvent(block: String): CalendarItem? {
        val lines = block.lines()
        fun values(name: String): List<String> = lines.mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            val key = line.substring(0, colon).substringBefore(';')
            if (key.equals(name, true)) line.substring(colon + 1) else null
        }
        fun value(name: String): String? = values(name).firstOrNull()

        val title = value("SUMMARY")?.let(::unescape)?.takeIf { it.isNotBlank() } ?: return null
        val dtStartLine = lines.firstOrNull { it.substringBefore(':').substringBefore(';').equals("DTSTART", true) } ?: return null
        val dtStart = dtStartLine.substringAfter(':')
        val allDay = dtStartLine.contains("VALUE=DATE", true) || dtStart.length == 8
        val startDateTime = parseDateTime(dtStart) ?: return null
        val endDateTime = value("DTEND")?.let(::parseDateTime)
        val rrule = value("RRULE")
        val recurrence = rrule
            ?.substringAfter("FREQ=", "")
            ?.substringBefore(';')
            ?.uppercase()
            ?.let { runCatching { Recurrence.valueOf(it) }.getOrNull() }
            ?: Recurrence.NONE
        val recurrenceEndDate = rrule
            ?.split(';')
            ?.firstOrNull { it.startsWith("UNTIL=", true) }
            ?.substringAfter('=')
            ?.let { raw -> parseDateTime(raw)?.toLocalDate() ?: runCatching { LocalDate.parse(raw.take(8), dateFmt) }.getOrNull() }
        val excludedDates = values("EXDATE")
            .flatMap { it.split(',') }
            .mapNotNull { parseDateTime(it)?.toLocalDate() }
            .toSet()
        val kind = value("X-PASTELCAL-KIND")?.uppercase()?.let { runCatching { ItemKind.valueOf(it) }.getOrNull() } ?: ItemKind.EVENT
        val category = value("X-PASTELCAL-CATEGORY")?.uppercase()?.let { runCatching { PastelCategory.valueOf(it) }.getOrNull() } ?: PastelCategory.LAVENDER
        val reminderOffsets = values("TRIGGER").mapNotNull(::parseTrigger).distinct().sortedDescending()

        return CalendarItem(
            title = title,
            kind = kind,
            date = startDateTime.toLocalDate(),
            startTime = if (allDay) null else startDateTime.toLocalTime(),
            endTime = if (allDay) null else endDateTime?.toLocalTime(),
            category = category,
            notes = value("DESCRIPTION")?.let(::unescape).orEmpty(),
            location = value("LOCATION")?.let(::unescape).orEmpty(),
            recurrence = recurrence,
            recurrenceEndDate = recurrenceEndDate,
            excludedDates = excludedDates,
            reminderMinutes = reminderOffsets.firstOrNull(),
            additionalReminderMinutes = reminderOffsets.drop(1)
        )
    }

    private fun parseDateTime(raw: String): LocalDateTime? {
        val clean = raw.trim()
        return when {
            clean.length == 8 -> runCatching { LocalDate.parse(clean, dateFmt).atStartOfDay() }.getOrNull()
            clean.endsWith("Z") -> runCatching {
                Instant.from(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX").parse(clean))
                    .atZone(ZoneId.systemDefault()).toLocalDateTime()
            }.getOrNull()
            else -> runCatching { LocalDateTime.parse(clean.take(15), dateTimeFmt) }.getOrNull()
        }
    }

    private fun triggerValue(minutes: Int): String = when {
        minutes == 0 -> "PT0M"
        minutes % 1440 == 0 -> "-P${minutes / 1440}D"
        minutes % 60 == 0 -> "-PT${minutes / 60}H"
        else -> "-PT${minutes}M"
    }

    private fun parseTrigger(value: String): Int? {
        val v = value.trim().uppercase()
        if (v == "PT0M" || v == "-PT0M") return 0
        Regex("-P(\\d+)D").matchEntire(v)?.let { return it.groupValues[1].toInt() * 1440 }
        Regex("-PT(\\d+)H").matchEntire(v)?.let { return it.groupValues[1].toInt() * 60 }
        Regex("-PT(\\d+)M").matchEntire(v)?.let { return it.groupValues[1].toInt() }
        return null
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")

    private fun unescape(value: String): String = value
        .replace("\\n", "\n", true)
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
}
