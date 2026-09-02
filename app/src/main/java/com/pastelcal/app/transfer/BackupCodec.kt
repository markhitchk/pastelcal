package com.pastelcal.app.transfer

import com.pastelcal.app.data.OccurrenceCompletionEntity
import com.pastelcal.app.model.*
import com.pastelcal.app.preferences.AppSettings
import com.pastelcal.app.preferences.AccentColor
import com.pastelcal.app.preferences.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

data class BackupPayload(
    val items: List<CalendarItem>,
    val completions: List<OccurrenceCompletionEntity>,
    val cycleEntries: List<CycleEntry>,
    val settings: AppSettings
)

object BackupCodec {
    fun encode(
        items: List<CalendarItem>,
        completions: List<OccurrenceCompletionEntity>,
        cycleEntries: List<CycleEntry>,
        settings: AppSettings
    ): String {
        val root = JSONObject()
        root.put("schema", 3)
        root.put("app", "PastelCal")
        root.put("items", JSONArray().apply { items.forEach { put(itemToJson(it)) } })
        root.put("completions", JSONArray().apply {
            completions.forEach { row ->
                put(JSONObject().apply {
                    put("itemId", row.itemId)
                    put("occurrenceEpochDay", row.occurrenceEpochDay)
                    put("completedAt", row.completedAt)
                })
            }
        })
        root.put("cycleEntries", JSONArray().apply {
            cycleEntries.forEach { entry ->
                put(JSONObject().apply {
                    put("startDate", entry.startDate.toString())
                    put("endDate", entry.endDate?.toString())
                })
            }
        })
        root.put("settings", settingsToJson(settings))
        return root.toString(2)
    }

    fun decode(text: String): BackupPayload {
        require(text.length <= 20_000_000) { "Backup is too large" }
        val root = JSONObject(text)
        require(root.optInt("schema", 1) <= 3) { "Backup was created by a newer PastelCal version" }
        require(root.optString("app", "PastelCal") == "PastelCal") { "Not a PastelCal backup" }
        val itemsArray = root.optJSONArray("items") ?: JSONArray()
        require(itemsArray.length() <= 100_000) { "Backup contains too many items" }
        val items = buildList {
            for (i in 0 until itemsArray.length()) add(jsonToItem(itemsArray.getJSONObject(i)))
        }
        val completionsArray = root.optJSONArray("completions") ?: JSONArray()
        val completions = buildList {
            for (i in 0 until completionsArray.length()) {
                val obj = completionsArray.getJSONObject(i)
                add(
                    OccurrenceCompletionEntity(
                        itemId = obj.getLong("itemId"),
                        occurrenceEpochDay = obj.getLong("occurrenceEpochDay"),
                        completedAt = obj.optLong("completedAt", System.currentTimeMillis())
                    )
                )
            }
        }
        val cycleArray = root.optJSONArray("cycleEntries") ?: JSONArray()
        require(cycleArray.length() <= 10_000) { "Backup contains too many cycle records" }
        val cycleEntries = buildList {
            for (i in 0 until cycleArray.length()) {
                val obj = cycleArray.getJSONObject(i)
                val start = LocalDate.parse(obj.getString("startDate"))
                val end = obj.optString("endDate").takeIf { it.isNotBlank() && it != "null" }?.let(LocalDate::parse)
                add(CycleEntry(startDate = start, endDate = end))
            }
        }
        val settings = root.optJSONObject("settings")?.let(::jsonToSettings) ?: AppSettings()
        return BackupPayload(items, completions, cycleEntries, settings)
    }

    private fun itemToJson(item: CalendarItem) = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("kind", item.kind.name)
        put("date", item.date.toString())
        put("startTime", item.startTime?.toString())
        put("endTime", item.endTime?.toString())
        put("category", item.category.name)
        put("completed", item.completed)
        put("notes", item.notes)
        put("location", item.location)
        put("recurrence", item.recurrence.name)
        put("recurrenceEndDate", item.recurrenceEndDate?.toString())
        put("excludedDates", JSONArray(item.excludedDates.map { it.toString() }.sorted()))
        put("seriesParentId", item.seriesParentId)
        put("reminderMinutes", item.reminderMinutes)
        put("additionalReminderMinutes", JSONArray(item.additionalReminderMinutes))
        put("systemEventId", item.systemEventId)
    }

    private fun jsonToItem(obj: JSONObject): CalendarItem {
        val additional = obj.optJSONArray("additionalReminderMinutes") ?: JSONArray()
        return CalendarItem(
            id = obj.optLong("id", 0L),
            title = obj.getString("title"),
            kind = enumOr(obj.optString("kind"), ItemKind.EVENT),
            date = LocalDate.parse(obj.getString("date")),
            startTime = obj.optString("startTime").takeIf { it.isNotBlank() && it != "null" }?.let(LocalTime::parse),
            endTime = obj.optString("endTime").takeIf { it.isNotBlank() && it != "null" }?.let(LocalTime::parse),
            category = enumOr(obj.optString("category"), PastelCategory.LAVENDER),
            completed = obj.optBoolean("completed", false),
            notes = obj.optString("notes", ""),
            location = obj.optString("location", ""),
            recurrence = enumOr(obj.optString("recurrence"), Recurrence.NONE),
            recurrenceEndDate = obj.optString("recurrenceEndDate").takeIf { it.isNotBlank() && it != "null" }?.let(LocalDate::parse),
            excludedDates = (obj.optJSONArray("excludedDates") ?: JSONArray()).let { array ->
                buildSet { for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let { add(LocalDate.parse(it)) } }
            },
            seriesParentId = if (obj.isNull("seriesParentId")) null else obj.optLong("seriesParentId"),
            reminderMinutes = if (obj.isNull("reminderMinutes")) null else obj.optInt("reminderMinutes"),
            additionalReminderMinutes = buildList { for (i in 0 until additional.length()) add(additional.optInt(i)) },
            systemEventId = if (obj.isNull("systemEventId")) null else obj.optLong("systemEventId")
        )
    }

    private fun settingsToJson(settings: AppSettings) = JSONObject().apply {
        put("themeMode", settings.themeMode.name)
        put("dynamicColors", settings.dynamicColors)
        put("accentColor", settings.accentColor.name)
        put("compactCalendar", settings.compactCalendar)
        put("weekNumbers", settings.weekNumbers)
        put("weekStartsMonday", settings.weekStartsMonday)
        put("cycleTrackingEnabled", settings.cycleTrackingEnabled)
        put("showEstimatedFertileWindow", settings.showEstimatedFertileWindow)
        put("defaultReminderMinutes", settings.defaultReminderMinutes)
        put("selectedCalendarId", settings.selectedCalendarId)
        put("widgetTransparencyPercent", settings.widgetTransparencyPercent)
        put("widgetTextSizeSp", settings.widgetTextSizeSp)
        put("widgetPastel", settings.widgetPastel.name)
        put("widgetShowCompletedTasks", settings.widgetShowCompletedTasks)
    }

    private fun jsonToSettings(obj: JSONObject) = AppSettings(
        themeMode = enumOr(obj.optString("themeMode"), ThemeMode.SYSTEM),
        dynamicColors = obj.optBoolean("dynamicColors", false),
        accentColor = enumOr(obj.optString("accentColor"), AccentColor.LAVENDER),
        compactCalendar = obj.optBoolean("compactCalendar", false),
        weekNumbers = obj.optBoolean("weekNumbers", false),
        weekStartsMonday = obj.optBoolean("weekStartsMonday", false),
        cycleTrackingEnabled = obj.optBoolean("cycleTrackingEnabled", false),
        showEstimatedFertileWindow = obj.optBoolean("showEstimatedFertileWindow", false),
        defaultReminderMinutes = if (obj.isNull("defaultReminderMinutes")) null else obj.optInt("defaultReminderMinutes", 30),
        selectedCalendarId = if (obj.isNull("selectedCalendarId")) null else obj.optLong("selectedCalendarId"),
        widgetTransparencyPercent = obj.optInt("widgetTransparencyPercent", 16).coerceIn(0, 85),
        widgetTextSizeSp = obj.optInt("widgetTextSizeSp", 14).coerceIn(11, 20),
        widgetPastel = enumOr(obj.optString("widgetPastel"), PastelCategory.LAVENDER),
        widgetShowCompletedTasks = obj.optBoolean("widgetShowCompletedTasks", false)
    )

    private inline fun <reified T : Enum<T>> enumOr(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
