package com.example.stickers.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.stickers.work.WidgetsRemovedWorker

class NoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NoteWidget()

    /** Widget removed from the home screen → its note goes to the bin. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetsRemovedWorker.enqueue(context, appWidgetIds)
    }
}
