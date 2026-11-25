package com.mateoviscarra.doitall.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mateoviscarra.doitall.data.SupplementPill
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

private const val TAG = "PillReminderScheduler"
private const val PREFS_NAME = "pill_alarm_prefs"
private const val KEY_ACTIVE_CODES = "active_request_codes"

object PillReminderScheduler {
    const val CHANNEL_ID = "pill_reminders"
    const val ACTION_SHOW_PILL_REMINDER = "com.mateoviscarra.doitall.ACTION_SHOW_PILL_REMINDER"
    const val ACTION_MARK_PILL_TAKEN = "com.mateoviscarra.doitall.ACTION_MARK_PILL_TAKEN"
    const val EXTRA_PILL_ID = "extra_pill_id"
    const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
    const val EXTRA_TIME_STR = "extra_time_str"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pill & Supplement Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scheduled reminders to take your daily pills and supplements."
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun scheduleAll(context: Context, pills: List<SupplementPill>) {
        createNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Cancel previously scheduled alarms
        val oldCodes = prefs.getStringSet(KEY_ACTIVE_CODES, emptySet()).orEmpty()
        for (codeStr in oldCodes) {
            val code = codeStr.toIntOrNull() ?: continue
            val cancelIntent = Intent(context, PillReminderReceiver::class.java).apply {
                action = ACTION_SHOW_PILL_REMINDER
            }
            val pi = PendingIntent.getBroadcast(
                context,
                code,
                cancelIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }

        val newCodes = mutableSetOf<String>()
        val now = ZonedDateTime.now()

        for (pill in pills) {
            if (!pill.notificationsEnabled) continue
            val days = if (pill.scheduledDaysOfWeek.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else pill.scheduledDaysOfWeek
            for (timeStr in pill.reminderTimes) {
                val parsedTime = parseTime(timeStr) ?: continue
                for (dayVal in days) {
                    val targetDow = DayOfWeek.of(dayVal.coerceIn(1, 7))
                    val triggerZdt = nextOccurrence(now, targetDow, parsedTime)
                    val requestCode = buildRequestCode(pill.id, dayVal, timeStr)
                    scheduleAlarmAt(context, alarmManager, triggerZdt, pill.id, dayVal, timeStr, requestCode)
                    newCodes.add(requestCode.toString())
                }
            }
        }

        prefs.edit().putStringSet(KEY_ACTIVE_CODES, newCodes).apply()
    }

    fun scheduleNextWeekOccurrence(
        context: Context,
        pillId: String,
        dayOfWeekValue: Int,
        timeStr: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val parsedTime = parseTime(timeStr) ?: return
        val targetDow = DayOfWeek.of(dayOfWeekValue.coerceIn(1, 7))
        val nextWeek = nextOccurrence(ZonedDateTime.now().plusMinutes(1), targetDow, parsedTime)
        val requestCode = buildRequestCode(pillId, dayOfWeekValue, timeStr)
        scheduleAlarmAt(context, alarmManager, nextWeek, pillId, dayOfWeekValue, timeStr, requestCode)
    }

    private fun scheduleAlarmAt(
        context: Context,
        alarmManager: AlarmManager,
        triggerZdt: ZonedDateTime,
        pillId: String,
        dayOfWeekValue: Int,
        timeStr: String,
        requestCode: Int
    ) {
        val intent = Intent(context, PillReminderReceiver::class.java).apply {
            action = ACTION_SHOW_PILL_REMINDER
            putExtra(EXTRA_PILL_ID, pillId)
            putExtra(EXTRA_DAY_OF_WEEK, dayOfWeekValue)
            putExtra(EXTRA_TIME_STR, timeStr)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerMillis = triggerZdt.toInstant().toEpochMilli()
        try {
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm not permitted, falling back to inexact alarm", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        }
    }

    private fun nextOccurrence(now: ZonedDateTime, targetDow: DayOfWeek, time: LocalTime): ZonedDateTime {
        val todayAtTime = LocalDate.now(now.zone).atTime(time).atZone(now.zone)
        return if (now.dayOfWeek == targetDow && todayAtTime.isAfter(now)) {
            todayAtTime
        } else {
            val nextDate = LocalDate.now(now.zone).with(TemporalAdjusters.next(targetDow))
            nextDate.atTime(time).atZone(now.zone)
        }
    }

    private fun parseTime(timeStr: String): LocalTime? {
        return try {
            val parts = timeStr.trim().split(":")
            if (parts.size != 2) return null
            LocalTime.of(parts[0].toInt().coerceIn(0, 23), parts[1].toInt().coerceIn(0, 59))
        } catch (_: Exception) {
            null
        }
    }

    private fun buildRequestCode(pillId: String, dayVal: Int, timeStr: String): Int =
        abs("${pillId}_${dayVal}_$timeStr".hashCode())
}
