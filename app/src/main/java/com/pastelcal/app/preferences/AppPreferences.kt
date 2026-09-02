package com.pastelcal.app.preferences

import android.content.Context
import com.pastelcal.app.model.PastelCategory

enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark")
}

enum class AccentColor(val label: String, val argb: Long) {
    LAVENDER("Lavender", 0xFFC8B6FF),
    SKY("Sky", 0xFF79C7FF),
    MINT("Mint", 0xFF70D6A5),
    PEACH("Peach", 0xFFFFA979),
    PINK("Pink", 0xFFF28CB5),
    BUTTER("Butter", 0xFFE7C94F),
    BLUE("Blue", 0xFF5B8CFF),
    TEAL("Teal", 0xFF35BFC1)
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColors: Boolean = false,
    val accentColor: AccentColor = AccentColor.LAVENDER,
    val compactCalendar: Boolean = false,
    val weekNumbers: Boolean = false,
    val weekStartsMonday: Boolean = false,
    val cycleTrackingEnabled: Boolean = false,
    val showEstimatedFertileWindow: Boolean = false,
    val defaultReminderMinutes: Int? = 30,
    val selectedCalendarId: Long? = null,
    val widgetTransparencyPercent: Int = 16,
    val widgetTextSizeSp: Int = 14,
    val widgetPastel: PastelCategory = PastelCategory.LAVENDER,
    val widgetShowCompletedTasks: Boolean = false
)

object AppPreferences {
    private const val NAME = "pastelcal_preferences"

    fun load(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val reminder = if (prefs.getBoolean("default_reminder_enabled", true)) {
            prefs.getInt("default_reminder_minutes", 30)
        } else null
        val selectedId = prefs.getLong("selected_calendar_id", -1L).takeIf { it >= 0L }
        return AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name)!!) }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColors = prefs.getBoolean("dynamic_colors", false),
            accentColor = runCatching { AccentColor.valueOf(prefs.getString("accent_color", AccentColor.LAVENDER.name)!!) }.getOrDefault(AccentColor.LAVENDER),
            compactCalendar = prefs.getBoolean("compact_calendar", false),
            weekNumbers = prefs.getBoolean("week_numbers", false),
            weekStartsMonday = prefs.getBoolean("week_starts_monday", false),
            cycleTrackingEnabled = prefs.getBoolean("cycle_tracking_enabled", false),
            showEstimatedFertileWindow = prefs.getBoolean("show_estimated_fertile_window", false),
            defaultReminderMinutes = reminder,
            selectedCalendarId = selectedId,
            widgetTransparencyPercent = prefs.getInt("widget_transparency", 16).coerceIn(0, 85),
            widgetTextSizeSp = prefs.getInt("widget_text_size", 14).coerceIn(11, 20),
            widgetPastel = runCatching {
                PastelCategory.valueOf(prefs.getString("widget_pastel", PastelCategory.LAVENDER.name)!!)
            }.getOrDefault(PastelCategory.LAVENDER),
            widgetShowCompletedTasks = prefs.getBoolean("widget_show_completed", false)
        )
    }

    fun save(context: Context, settings: AppSettings) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("theme_mode", settings.themeMode.name)
            .putBoolean("dynamic_colors", settings.dynamicColors)
            .putString("accent_color", settings.accentColor.name)
            .putBoolean("compact_calendar", settings.compactCalendar)
            .putBoolean("week_numbers", settings.weekNumbers)
            .putBoolean("week_starts_monday", settings.weekStartsMonday)
            .putBoolean("cycle_tracking_enabled", settings.cycleTrackingEnabled)
            .putBoolean("show_estimated_fertile_window", settings.showEstimatedFertileWindow)
            .putBoolean("default_reminder_enabled", settings.defaultReminderMinutes != null)
            .putInt("default_reminder_minutes", settings.defaultReminderMinutes ?: 30)
            .putLong("selected_calendar_id", settings.selectedCalendarId ?: -1L)
            .putInt("widget_transparency", settings.widgetTransparencyPercent.coerceIn(0, 85))
            .putInt("widget_text_size", settings.widgetTextSizeSp.coerceIn(11, 20))
            .putString("widget_pastel", settings.widgetPastel.name)
            .putBoolean("widget_show_completed", settings.widgetShowCompletedTasks)
            .apply()
    }
}
