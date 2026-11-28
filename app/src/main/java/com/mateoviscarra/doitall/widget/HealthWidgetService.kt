package com.mateoviscarra.doitall.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.mateoviscarra.doitall.R
import com.mateoviscarra.doitall.data.NutritionRepository
import com.mateoviscarra.doitall.data.PillLogEntry
import com.mateoviscarra.doitall.data.SupplementPill
import java.time.LocalDate

class HealthWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return HealthPillsRemoteViewsFactory(applicationContext)
    }
}

private class HealthPillsRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private data class PillRowItem(
        val pill: SupplementPill,
        val takenLog: PillLogEntry?
    )

    private var items: List<PillRowItem> = emptyList()

    override fun onCreate() {
        loadItems()
    }

    override fun onDataSetChanged() {
        loadItems()
    }

    private fun loadItems() {
        val repo = NutritionRepository(context.applicationContext)
        val today = LocalDate.now()
        val todayIso = today.toString()

        val logsByPillId = repo.pillLogs.value
            .filter { it.dateIso == todayIso }
            .associateBy { it.pillId }

        items = repo.supplements.value
            .filter { it.isScheduledForDate(today) }
            .sortedWith(
                compareBy<SupplementPill>(
                    { it.reminderTimes.minOrNull() ?: "99:99" },
                    { it.name.lowercase() }
                )
            )
            .map { pill ->
                PillRowItem(
                    pill = pill,
                    takenLog = logsByPillId[pill.id]
                )
            }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_pill_item)

        val pill = item.pill
        val isTaken = item.takenLog != null
        val views = RemoteViews(context.packageName, R.layout.widget_pill_item)

        views.setTextViewText(R.id.widget_pill_name, pill.name)

        val timePart = if (isTaken && item.takenLog != null) {
            "Taken at ${item.takenLog.takenTime}"
        } else {
            val nextTime = pill.reminderTimes.firstOrNull()
            if (nextTime != null) "Scheduled $nextTime" else "Scheduled today"
        }
        val subtitle = if (pill.dosageDescription.isNotBlank()) {
            "${pill.dosageDescription} • $timePart"
        } else {
            timePart
        }
        views.setTextViewText(R.id.widget_pill_subtitle, subtitle)

        if (isTaken) {
            views.setImageViewResource(R.id.widget_pill_circle, R.drawable.widget_check_circle_done)
            views.setViewVisibility(R.id.widget_pill_check_icon, View.VISIBLE)
            views.setTextViewText(R.id.widget_pill_action, "Taken")
            views.setInt(R.id.widget_pill_action, "setBackgroundResource", R.drawable.widget_badge_taken)
            views.setTextColor(
                R.id.widget_pill_action,
                context.getColor(R.color.widget_on_emerald_bg)
            )
        } else {
            views.setImageViewResource(R.id.widget_pill_circle, R.drawable.widget_check_circle_pending)
            views.setViewVisibility(R.id.widget_pill_check_icon, View.GONE)
            views.setTextViewText(R.id.widget_pill_action, "Take")
            views.setInt(R.id.widget_pill_action, "setBackgroundResource", R.drawable.widget_badge_pending)
            views.setTextColor(
                R.id.widget_pill_action,
                context.getColor(R.color.widget_on_primary_container)
            )
        }

        val fillInIntent = Intent().apply {
            putExtra(HealthWidgetProvider.EXTRA_PILL_ID, pill.id)
        }
        views.setOnClickFillInIntent(R.id.widget_pill_row, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.pill?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
