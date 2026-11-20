package com.example.stickers.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class NoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NoteWidget()

    // TODO(milestone 5): onDeleted → repository.onWidgetsRemoved (move notes to the bin).
}
