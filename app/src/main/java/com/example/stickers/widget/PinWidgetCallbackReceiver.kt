package com.example.stickers.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.stickers.appContainer
import kotlinx.coroutines.launch

/**
 * Called by the launcher once a widget requested by [WidgetPinner] has been placed:
 * binds the new widget to a new note or to the restored bin note.
 *
 * The setup screen may bind the same widget too; both paths are idempotent.
 */
class PinWidgetCallbackReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val restoreNoteId = intent.restoreNoteIdOrNull(EXTRA_RESTORE_NOTE_ID)
        val container = context.appContainer
        container.pendingPin.markBound(widgetId)

        val pending = goAsync()
        container.appScope.launch {
            try {
                if (restoreNoteId == null) {
                    container.repository.createNoteForWidget(widgetId)
                } else if (container.repository.getNoteForWidget(widgetId) == null) {
                    container.repository.restoreToWidget(restoreNoteId, widgetId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_RESTORE_NOTE_ID = "restoreNoteId"

        fun intent(context: Context, restoreNoteId: Long?): Intent =
            Intent(context, PinWidgetCallbackReceiver::class.java)
                .putExtra(EXTRA_RESTORE_NOTE_ID, restoreNoteId ?: -1L)
    }
}
