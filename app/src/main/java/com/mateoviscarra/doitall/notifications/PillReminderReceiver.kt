package com.mateoviscarra.doitall.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mateoviscarra.doitall.MainActivity
import com.mateoviscarra.doitall.data.NutritionRepository
import java.time.LocalDate
import kotlin.math.abs

class PillReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pillId = intent.getStringExtra(PillReminderScheduler.EXTRA_PILL_ID) ?: return
        val repo = NutritionRepository(context.applicationContext)

        when (action) {
            PillReminderScheduler.ACTION_SHOW_PILL_REMINDER -> {
                val dayOfWeek = intent.getIntExtra(PillReminderScheduler.EXTRA_DAY_OF_WEEK, LocalDate.now().dayOfWeek.value)
                val timeStr = intent.getStringExtra(PillReminderScheduler.EXTRA_TIME_STR).orEmpty()

                val pill = repo.supplements.value.firstOrNull { it.id == pillId }
                if (pill != null && pill.notificationsEnabled) {
                    // Always reschedule next week's occurrence
                    if (timeStr.isNotBlank()) {
                        PillReminderScheduler.scheduleNextWeekOccurrence(context, pillId, dayOfWeek, timeStr)
                    }

                    val todayIso = LocalDate.now().toString()
                    val alreadyTakenToday = repo.pillLogs.value.any { it.pillId == pillId && it.dateIso == todayIso }
                    if (!alreadyTakenToday && canPostNotifications(context)) {
                        showReminderNotification(context, pill.id, pill.name, pill.dosageDescription, pill.notes)
                    }
                }
            }

            PillReminderScheduler.ACTION_MARK_PILL_TAKEN -> {
                val todayIso = LocalDate.now().toString()
                repo.markPillTakenById(pillId, todayIso)
                val notifId = intent.getIntExtra(PillReminderScheduler.EXTRA_NOTIFICATION_ID, abs(pillId.hashCode()))
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.cancel(notifId)
            }
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun showReminderNotification(
        context: Context,
        pillId: String,
        name: String,
        dosage: String,
        notes: String
    ) {
        PillReminderScheduler.createNotificationChannel(context)
        val notifId = abs(pillId.hashCode())

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPi = PendingIntent.getActivity(
            context,
            notifId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markTakenIntent = Intent(context, PillReminderReceiver::class.java).apply {
            action = PillReminderScheduler.ACTION_MARK_PILL_TAKEN
            putExtra(PillReminderScheduler.EXTRA_PILL_ID, pillId)
            putExtra(PillReminderScheduler.EXTRA_NOTIFICATION_ID, notifId)
        }
        val markTakenPi = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            markTakenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (dosage.isNotBlank()) "Time for $name ($dosage)" else "Time for $name"
        val body = notes.ifBlank { "Tap 'Mark Taken' to log it in today's nutrition tracker." }

        val notification = NotificationCompat.Builder(context, PillReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPi)
            .addAction(android.R.drawable.ic_menu_save, "Mark Taken", markTakenPi)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(notifId, notification)
    }
}
