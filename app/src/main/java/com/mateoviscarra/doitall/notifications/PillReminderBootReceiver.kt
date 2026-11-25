package com.mateoviscarra.doitall.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mateoviscarra.doitall.data.NutritionRepository

class PillReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repo = NutritionRepository(context.applicationContext)
            PillReminderScheduler.scheduleAll(context.applicationContext, repo.supplements.value)
        }
    }
}
