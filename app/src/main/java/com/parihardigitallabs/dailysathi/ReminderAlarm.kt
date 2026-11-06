package com.parihardigitallabs.dailysathi

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.parihardigitallabs.dailysathi.data.DailySathiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ReminderAlarmScheduler {
    private const val CHANNEL_ID = "dailysathi_reminders"

    fun schedule(context: Context, reminderId: Long, dueAtMillis: Long) {
        if (dueAtMillis <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            dueAtMillis,
            pendingIntent(context, reminderId)
        )
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, reminderId))
    }

    private fun pendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderNotificationReceiver::class.java)
            .putExtra("reminder_id", reminderId)
        return PendingIntent.getBroadcast(
            context, reminderId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showNotification(context: Context, title: String, note: String, reminderId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(note.ifBlank { "Your DailySathi reminder is due." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.ifBlank { "Your DailySathi reminder is due." }))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(reminderId.toInt(), notification)
        } catch (_: SecurityException) {
            // Notifications remain safely suppressed when permission has not been granted.
        }
    }
}

class ReminderNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("reminder_id", -1L)
        if (id <= 0L) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val item = DailySathiDatabase.getInstance(context).reminderDao().getById(id)
                if (item != null && !item.isCompleted) {
                    ReminderAlarmScheduler.showNotification(context, item.title, item.note, item.id)
                    val nextDue = nextOccurrence(item.dueAtMillis, item.recurrence)
                    if (nextDue != null) {
                        DailySathiDatabase.getInstance(context).reminderDao()
                            .update(item.copy(dueAtMillis = nextDue))
                        ReminderAlarmScheduler.schedule(context, item.id, nextDue)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val items = DailySathiDatabase.getInstance(context).reminderDao().getPendingWithDueDates()
                items.forEach { item ->
                    item.dueAtMillis?.let { ReminderAlarmScheduler.schedule(context, item.id, it) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

internal fun nextOccurrence(
    dueAtMillis: Long?,
    recurrence: String,
    nowMillis: Long = System.currentTimeMillis()
): Long? {
    if (dueAtMillis == null) return null
    val calendarField = when (recurrence) {
        "DAILY" -> java.util.Calendar.DAY_OF_YEAR
        "WEEKLY" -> java.util.Calendar.WEEK_OF_YEAR
        "MONTHLY" -> java.util.Calendar.MONTH
        else -> return null
    }
    val next = java.util.Calendar.getInstance().apply { timeInMillis = dueAtMillis }
    // Catch up missed repeats after the device has been off or the app was unused.
    // Always return the next future occurrence so the reminder is not silently lost.
    while (next.timeInMillis <= nowMillis) next.add(calendarField, 1)
    return next.timeInMillis
}
