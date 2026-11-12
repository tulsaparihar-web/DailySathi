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
        if (dueAtMillis <= System.currentTimeMillis()) {
            // Do not leave an older scheduled alarm alive when a reminder is moved
            // into the past or a recovery pass encounters an already-due item.
            cancelAlarm(context, reminderId)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = pendingIntent(context, reminderId, dueAtMillis)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            dueAtMillis,
            alarmIntent
        )
    }

    private fun cancelAlarm(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Extras do not participate in PendingIntent identity; use any timestamp to
        // retrieve and cancel the existing reminder alarm.
        alarmManager.cancel(pendingIntent(context, reminderId, 0L))
    }

    fun cancel(context: Context, reminderId: Long) {
        cancelAlarm(context, reminderId)
        cancelNotification(context, reminderId)
    }

    fun cancelNotification(context: Context, reminderId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(reminderId.toInt())
    }

    private fun pendingIntent(context: Context, reminderId: Long, dueAtMillis: Long): PendingIntent {
        val intent = Intent(context, ReminderNotificationReceiver::class.java)
            .putExtra("reminder_id", reminderId)
            .putExtra("due_at_millis", dueAtMillis)
        return PendingIntent.getBroadcast(
            context, reminderId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showNotification(context: Context, title: String, note: String, reminderId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications for your DailySathi reminders"
            }
            )
        }
        val openApp = PendingIntent.getActivity(
            context, reminderId.toInt(), Intent(context, MainActivity::class.java)
                .putExtra("open_calendar", true)
                .putExtra("reminder_id", reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(note.ifBlank { "Your DailySathi reminder is due." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.ifBlank { "Your DailySathi reminder is due." }))
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
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
        val expectedDueAtMillis = intent.getLongExtra("due_at_millis", -1L)
        if (id <= 0L || expectedDueAtMillis <= 0L) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = DailySathiDatabase.getInstance(context).reminderDao()
                // Ignore stale alarms after an edit, completion, or deletion.
                val item = dao.getPendingAtDueTime(id, expectedDueAtMillis) ?: return@launch
                val anchorDay = if (item.recurrenceDayOfMonth in 1..31) {
                    item.recurrenceDayOfMonth
                } else {
                    java.util.Calendar.getInstance().apply { timeInMillis = expectedDueAtMillis }
                        .get(java.util.Calendar.DAY_OF_MONTH)
                }
                val nextDue = nextOccurrence(
                    expectedDueAtMillis,
                    item.recurrence,
                    recurrenceDayOfMonth = anchorDay
                )
                if (nextDue != null) {
                    // Advance only if this exact due date is still pending. A concurrent
                    // edit or completion wins and cannot be overwritten by this receiver.
                    val updated = dao.advanceRecurringIfUnchanged(
                        item.id, expectedDueAtMillis, nextDue, anchorDay
                    )
                    if (updated != 1) return@launch
                    ReminderAlarmScheduler.schedule(context, item.id, nextDue)
                }
                ReminderAlarmScheduler.showNotification(context, item.title, item.note, item.id)
            } finally {
                pending.finish()
            }
        }
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recoverableActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
        if (intent.action !in recoverableActions) return
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
    nowMillis: Long = System.currentTimeMillis(),
    recurrenceDayOfMonth: Int = 0
): Long? {
    if (dueAtMillis == null) return null
    val next = java.util.Calendar.getInstance().apply { timeInMillis = dueAtMillis }
    when (recurrence) {
        "DAILY", "WEEKLY" -> {
            val field = if (recurrence == "DAILY") java.util.Calendar.DAY_OF_YEAR
                else java.util.Calendar.WEEK_OF_YEAR
            // Catch up missed repeats while preserving the originally selected local time.
            while (next.timeInMillis <= nowMillis) next.add(field, 1)
        }
        "MONTHLY" -> {
            // Keep the original day (e.g. the 31st) when shorter months require clamping.
            // This avoids Calendar.add() rolling Jan 31 into March and permanently drifting.
            val anchorDay = recurrenceDayOfMonth.takeIf { it in 1..31 }
                ?: next.get(java.util.Calendar.DAY_OF_MONTH)
            while (next.timeInMillis <= nowMillis) {
                next.set(java.util.Calendar.DAY_OF_MONTH, 1)
                next.add(java.util.Calendar.MONTH, 1)
                next.set(
                    java.util.Calendar.DAY_OF_MONTH,
                    minOf(anchorDay, next.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
                )
            }
        }
        else -> return null
    }
    return next.timeInMillis
}
