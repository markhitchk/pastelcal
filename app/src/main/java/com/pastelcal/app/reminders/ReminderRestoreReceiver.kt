package com.pastelcal.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pastelcal.app.data.AppDatabase
import com.pastelcal.app.data.toModel
import com.pastelcal.app.widget.PastelCalWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).calendarDao()
                val items = dao.observeAll().first().map { it.toModel() }
                items.forEach { item ->
                    ReminderScheduler.schedule(context, item, dao.getCompletedOccurrenceDays(item.id).toSet())
                }
                PastelCalWidgetProvider.updateAll(context)
            } finally {
                result.finish()
            }
        }
    }
}
