package com.pastelcal.app.diagnostics

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.pastelcal.app.data.AppDatabase
import com.pastelcal.app.preferences.AppPreferences
import com.pastelcal.app.system.CalendarProviderBridge
import java.time.Instant

object DiagnosticsReport {
    suspend fun build(context: Context): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        val settings = AppPreferences.load(context)
        val memoryClass = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        val notifications = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val calendar = CalendarProviderBridge.hasCalendarPermission(context)
        val count = AppDatabase.get(context).calendarDao().count()
        val crash = CrashReporter.readLastCrash(context)

        return buildString {
            appendLine("PastelCal diagnostics")
            appendLine("Generated: ${Instant.now()}")
            appendLine("Version: ${packageInfo.versionName} ($versionCode)")
            appendLine("Android: ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Memory class: ${memoryClass} MB")
            appendLine("Calendar items: $count")
            appendLine("Notification permission: $notifications")
            appendLine("Exact alarms: $exact")
            appendLine("Calendar provider access: $calendar")
            appendLine("Theme: ${settings.themeMode}")
            appendLine("Dynamic colors: ${settings.dynamicColors}")
            appendLine("Week starts Monday: ${settings.weekStartsMonday}")
            appendLine("Widget text size: ${settings.widgetTextSizeSp}sp")
            appendLine()
            appendLine("Privacy: event titles, notes, locations, calendar account names, and reminder contents are intentionally omitted from this report.")
            if (crash != null) {
                appendLine()
                appendLine("--- Last local crash ---")
                appendLine(crash)
            }
        }
    }
}
