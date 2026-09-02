package com.pastelcal.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.pastelcal.app.CalendarViewModel
import com.pastelcal.app.MainActivity
import com.pastelcal.app.R
import com.pastelcal.app.data.AppDatabase
import com.pastelcal.app.data.toModel
import com.pastelcal.app.model.CalendarItem
import com.pastelcal.app.model.ItemKind
import com.pastelcal.app.model.PastelCategory
import com.pastelcal.app.model.Recurrence
import com.pastelcal.app.model.RecurrenceEngine
import com.pastelcal.app.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class PastelCalWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).calendarDao()
                val items = dao.observeAll().first().map { it.toModel() }
                val completed = dao.observeOccurrenceCompletions().first()
                    .map { CalendarViewModel.completionKey(it.itemId, it.occurrenceEpochDay) }
                    .toSet()
                appWidgetIds.forEach { updateWidget(context, appWidgetManager, it, items, completed) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) updateAll(context)
    }

    companion object {
        private const val ACTION_REFRESH = "com.pastelcal.app.widget.REFRESH"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, PastelCalWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                context.sendBroadcast(Intent(context, PastelCalWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                })
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            items: List<CalendarItem>,
            completedOccurrences: Set<String>
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_agenda)
            val settings = AppPreferences.load(context)
            val today = LocalDate.now()
            views.setTextViewText(R.id.widget_date, today.format(DateTimeFormatter.ofPattern("EEE, MMM d")))

            val pastel = pastelColor(settings.widgetPastel)
            val alpha = ((100 - settings.widgetTransparencyPercent) / 100f * 255).toInt().coerceIn(38, 255)
            views.setInt(R.id.widget_root, "setBackgroundColor", Color.argb(alpha, Color.red(pastel), Color.green(pastel), Color.blue(pastel)))
            views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, (settings.widgetTextSizeSp + 4).toFloat())
            views.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_SP, (settings.widgetTextSizeSp - 1).coerceAtLeast(10).toFloat())

            val upcoming = buildList {
                for (offset in 0L..14L) {
                    val date = today.plusDays(offset)
                    items.forEach { item ->
                        if (!RecurrenceEngine.occursOn(item, date)) return@forEach
                        val occurrenceCompleted = if (item.kind == ItemKind.TASK) {
                            if (item.recurrence == Recurrence.NONE) item.completed
                            else CalendarViewModel.completionKey(item.id, date.toEpochDay()) in completedOccurrences
                        } else false
                        if (!occurrenceCompleted || settings.widgetShowCompletedTasks) add(item.copy(date = date, completed = occurrenceCompleted))
                    }
                }
            }.filter {
                it.date > today || it.startTime == null || it.startTime >= LocalTime.now().minusMinutes(1)
            }.sortedWith(compareBy<CalendarItem> { it.date }.thenBy { it.startTime ?: LocalTime.MAX }).take(3)

            val lineIds = intArrayOf(R.id.widget_item_1, R.id.widget_item_2, R.id.widget_item_3)
            lineIds.forEachIndexed { index, viewId ->
                views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, settings.widgetTextSizeSp.toFloat())
                val item = upcoming.getOrNull(index)
                if (item == null) {
                    views.setViewVisibility(viewId, View.GONE)
                } else {
                    views.setViewVisibility(viewId, View.VISIBLE)
                    val prefix = if (item.date == today) {
                        item.startTime?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "Today"
                    } else item.date.format(DateTimeFormatter.ofPattern("EEE d"))
                    val completedMark = if (item.kind == ItemKind.TASK && item.completed) "✓ " else ""
                    views.setTextViewText(viewId, "$prefix  •  $completedMark${item.title}")
                }
            }
            views.setViewVisibility(R.id.widget_empty, if (upcoming.isEmpty()) View.VISIBLE else View.GONE)
            views.setTextViewTextSize(R.id.widget_empty, TypedValue.COMPLEX_UNIT_SP, settings.widgetTextSizeSp.toFloat())

            val open = PendingIntent.getActivity(
                context,
                widgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            val refresh = PendingIntent.getBroadcast(
                context,
                widgetId + 10_000,
                Intent(context, PastelCalWidgetProvider::class.java).apply { action = ACTION_REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refresh)
            manager.updateAppWidget(widgetId, views)
        }

        private fun pastelColor(category: PastelCategory): Int = when (category) {
            PastelCategory.LAVENDER -> Color.rgb(0xC8, 0xB6, 0xFF)
            PastelCategory.SKY -> Color.rgb(0xA8, 0xD8, 0xFF)
            PastelCategory.MINT -> Color.rgb(0xB8, 0xE8, 0xD0)
            PastelCategory.PEACH -> Color.rgb(0xFF, 0xD0, 0xB5)
            PastelCategory.PINK -> Color.rgb(0xF7, 0xBF, 0xD4)
            PastelCategory.BUTTER -> Color.rgb(0xF6, 0xE6, 0xA8)
        }
    }
}
