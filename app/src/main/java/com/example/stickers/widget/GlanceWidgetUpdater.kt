package com.example.stickers.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.example.stickers.data.WidgetUpdater

/** Redraws the Glance widget bound to an appWidgetId after a data change. */
class GlanceWidgetUpdater(context: Context) : WidgetUpdater {
    private val context = context.applicationContext

    override suspend fun update(appWidgetId: Int) {
        try {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            NoteWidget().update(context, glanceId)
        } catch (e: IllegalArgumentException) {
            // The widget no longer exists on the home screen; nothing to redraw.
            Log.w("GlanceWidgetUpdater", "No widget for id $appWidgetId", e)
        }
    }
}
