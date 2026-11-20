package com.example.stickers.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Asks the launcher to add a note widget to the home screen ("Add to home screen" dialog). */
class WidgetPinner(context: Context, private val pendingPin: PendingPin) {

    private val context = context.applicationContext
    private val manager = AppWidgetManager.getInstance(this.context)

    /** False if the launcher can't pin widgets; the user must then add one by hand. */
    val isSupported: Boolean get() = manager.isRequestPinAppWidgetSupported

    /**
     * Requests a widget showing a new note, or the bin note [restoreNoteId].
     * Returns false if the launcher doesn't support pinning.
     */
    fun pin(restoreNoteId: Long? = null): Boolean {
        if (!isSupported) return false
        pendingPin.record(restoreNoteId?.let { PendingPin.Target.Restore(it) } ?: PendingPin.Target.NewNote)
        // Mutable: the launcher adds EXTRA_APPWIDGET_ID to it.
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            PinWidgetCallbackReceiver.intent(context, restoreNoteId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return manager.requestPinAppWidget(ComponentName(context, NoteWidgetReceiver::class.java), null, callback)
    }
}

internal fun Intent.restoreNoteIdOrNull(key: String): Long? = getLongExtra(key, -1L).takeIf { it >= 0 }
