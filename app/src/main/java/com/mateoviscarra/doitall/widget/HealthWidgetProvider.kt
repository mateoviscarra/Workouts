package com.mateoviscarra.doitall.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.mateoviscarra.doitall.MainActivity
import com.mateoviscarra.doitall.R
import com.mateoviscarra.doitall.data.NutritionRepository
import com.mateoviscarra.doitall.data.persist.WeightUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class HealthWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val repo = NutritionRepository(context.applicationContext)
        val today = LocalDate.now()
        val todayIso = today.toString()

        val allWeights = repo.weightLogs.value
        val todayWeight = allWeights.firstOrNull { it.dateIso == todayIso }
        val latestWeight = allWeights.firstOrNull()

        val scheduledPills = repo.supplements.value.filter { it.isScheduledForDate(today) }
        val todayTakenIds = repo.pillLogs.value
            .asSequence()
            .filter { it.dateIso == todayIso }
            .map { it.pillId }
            .toSet()
        val takenCount = scheduledPills.count { it.id in todayTakenIds }
        val totalCount = scheduledPills.size

        val dateLabel = today.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_health)

            // Header
            views.setTextViewText(R.id.widget_date, "Today • $dateLabel")
            views.setTextViewText(R.id.widget_pills_summary, "$takenCount/$totalCount pills")

            val allPillsTaken = totalCount > 0 && takenCount == totalCount
            views.setInt(
                R.id.widget_pills_summary,
                "setBackgroundResource",
                if (allPillsTaken) R.drawable.widget_badge_taken else R.drawable.widget_badge_pending
            )
            views.setTextColor(
                R.id.widget_pills_summary,
                context.getColor(
                    if (allPillsTaken) R.color.widget_on_emerald_bg else R.color.widget_on_primary_container
                )
            )

            // Open app on header tap
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPi = PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 1,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, openAppPi)

            // Weight card
            val displayEntry = todayWeight ?: latestWeight
            if (displayEntry != null) {
                val unitLabel = if (displayEntry.unit == WeightUnit.LBS) "lb" else "kg"
                views.setTextViewText(
                    R.id.widget_weight_value,
                    "${formatWeight(displayEntry.weight)} $unitLabel"
                )
                if (todayWeight != null) {
                    views.setTextViewText(R.id.widget_weight_subtitle, "Logged today")
                    views.setTextColor(
                        R.id.widget_weight_subtitle,
                        context.getColor(R.color.widget_emerald)
                    )
                    views.setTextViewText(R.id.widget_weight_log_btn, "Edit")
                } else {
                    val lastDate = runCatching {
                        LocalDate.parse(displayEntry.dateIso)
                            .format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                    }.getOrDefault(displayEntry.dateIso)
                    views.setTextViewText(R.id.widget_weight_subtitle, "Last: $lastDate")
                    views.setTextColor(
                        R.id.widget_weight_subtitle,
                        context.getColor(R.color.widget_text_secondary)
                    )
                    views.setTextViewText(R.id.widget_weight_log_btn, "Log")
                }
            } else {
                views.setTextViewText(R.id.widget_weight_value, "— kg")
                views.setTextViewText(R.id.widget_weight_subtitle, "Tap to log weight")
                views.setTextColor(
                    R.id.widget_weight_subtitle,
                    context.getColor(R.color.widget_text_secondary)
                )
                views.setTextViewText(R.id.widget_weight_log_btn, "Log")
            }

            // Weight quick-dialog trigger
            val quickWeightIntent = Intent(context, QuickWeightDialogActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val quickWeightPi = PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 2,
                quickWeightIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_weight_info, quickWeightPi)
            views.setOnClickPendingIntent(R.id.widget_weight_log_btn, quickWeightPi)

            // Step weight buttons (-0.1 / +0.1)
            views.setOnClickPendingIntent(
                R.id.widget_weight_minus,
                buildStepWeightPendingIntent(context, appWidgetId * 10 + 3, -0.1f)
            )
            views.setOnClickPendingIntent(
                R.id.widget_weight_plus,
                buildStepWeightPendingIntent(context, appWidgetId * 10 + 4, +0.1f)
            )

            // Pills ListView adapter
            val serviceIntent = Intent(context, HealthWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.widget_pills_list, serviceIntent)
            views.setEmptyView(R.id.widget_pills_list, R.id.widget_pills_empty)
            views.setOnClickPendingIntent(R.id.widget_pills_empty, openAppPi)

            // Visibility hint for empty view is handled automatically by setEmptyView,
            // but ensure list is visible when pills exist.
            views.setViewVisibility(
                R.id.widget_pills_list,
                if (scheduledPills.isEmpty()) View.GONE else View.VISIBLE
            )
            views.setViewVisibility(
                R.id.widget_pills_empty,
                if (scheduledPills.isEmpty()) View.VISIBLE else View.GONE
            )

            // Pill row click template
            val toggleTemplateIntent = Intent(context, HealthWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_PILL
            }
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val toggleTemplatePi = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 5,
                toggleTemplateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
            )
            views.setPendingIntentTemplate(R.id.widget_pills_list, toggleTemplatePi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_pills_list)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_PILL -> {
                val pillId = intent.getStringExtra(EXTRA_PILL_ID) ?: return
                val repo = NutritionRepository(context.applicationContext)
                val todayIso = LocalDate.now().toString()
                val pill = repo.supplements.value.firstOrNull { it.id == pillId } ?: return
                repo.togglePillTaken(pill, todayIso)
                refreshAll(context)
            }

            ACTION_STEP_WEIGHT -> {
                val delta = intent.getFloatExtra(EXTRA_WEIGHT_DELTA, 0f)
                if (delta == 0f) return
                val repo = NutritionRepository(context.applicationContext)
                val todayIso = LocalDate.now().toString()
                val baseEntry = repo.weightLogs.value.firstOrNull { it.dateIso == todayIso }
                    ?: repo.weightLogs.value.firstOrNull()
                val baseWeight = baseEntry?.weight ?: 70.0f
                val unit = baseEntry?.unit ?: WeightUnit.KG
                val nextWeight = (((baseWeight + delta) * 10f).roundToInt() / 10f).coerceAtLeast(1f)
                repo.logWeight(
                    dateIso = todayIso,
                    weight = nextWeight,
                    unit = unit,
                    note = baseEntry?.takeIf { it.dateIso == todayIso }?.note.orEmpty()
                )
                refreshAll(context)
            }

            ACTION_REFRESH -> refreshAll(context)
        }
    }

    private fun buildStepWeightPendingIntent(
        context: Context,
        requestCode: Int,
        delta: Float
    ): PendingIntent {
        val intent = Intent(context, HealthWidgetProvider::class.java).apply {
            action = ACTION_STEP_WEIGHT
            putExtra(EXTRA_WEIGHT_DELTA, delta)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_TOGGLE_PILL = "com.mateoviscarra.doitall.widget.ACTION_TOGGLE_PILL"
        const val ACTION_STEP_WEIGHT = "com.mateoviscarra.doitall.widget.ACTION_STEP_WEIGHT"
        const val ACTION_REFRESH = "com.mateoviscarra.doitall.widget.ACTION_REFRESH"
        const val EXTRA_PILL_ID = "extra_pill_id"
        const val EXTRA_WEIGHT_DELTA = "extra_weight_delta"

        fun formatWeight(weight: Float): String {
            val rounded2 = (weight * 100f).roundToInt()
            return if (rounded2 % 10 == 0) {
                String.format(Locale.US, "%.1f", weight)
            } else {
                String.format(Locale.US, "%.2f", weight)
            }
        }

        fun refreshAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext) ?: return
            val component = ComponentName(appContext, HealthWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            val updateIntent = Intent(appContext, HealthWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            appContext.sendBroadcast(updateIntent)
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_pills_list)
        }
    }
}
