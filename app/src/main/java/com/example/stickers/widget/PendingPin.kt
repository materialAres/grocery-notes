package com.example.stickers.widget

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers the latest "pin a widget" request made from the app (DESIGN.md §5.5).
 *
 * After the user confirms the launcher's dialog, two things may happen in either order:
 * the pin callback ([PinWidgetCallbackReceiver]) and, on some launchers, the setup screen
 * for the new widget. The setup screen uses this record to recognize a pinned widget and
 * bind the requested note instead of asking the user again.
 */
class PendingPin(context: Context, private val clock: () -> Long = System::currentTimeMillis) {

    private val prefs = context.applicationContext.getSharedPreferences("pending_pin", Context.MODE_PRIVATE)

    /** What a pinned widget should show. */
    sealed interface Target {
        data object NewNote : Target
        data class Restore(val noteId: Long) : Target
    }

    /** Called just before asking the launcher to pin. */
    @Synchronized
    fun record(target: Target) = prefs.edit {
        putLong(KEY_NOTE_ID, (target as? Target.Restore)?.noteId ?: NEW_NOTE)
        putLong(KEY_AT, clock())
        remove(KEY_WIDGET_ID)
    }

    /** The pin callback reports which widget the request produced. */
    @Synchronized
    fun markBound(appWidgetId: Int) {
        if (prefs.contains(KEY_AT)) prefs.edit { putInt(KEY_WIDGET_ID, appWidgetId) }
    }

    /**
     * For the setup screen: returns (and forgets) a recent request that belongs to
     * [appWidgetId] — one already bound to that widget, or not bound to any widget yet.
     */
    @Synchronized
    fun take(appWidgetId: Int): Target? {
        if (!prefs.contains(KEY_AT)) return null
        val fresh = clock() - prefs.getLong(KEY_AT, 0) in 0..MAX_AGE_MS
        val boundTo = if (prefs.contains(KEY_WIDGET_ID)) prefs.getInt(KEY_WIDGET_ID, 0) else null
        if (!fresh || (boundTo != null && boundTo != appWidgetId)) return null
        val noteId = prefs.getLong(KEY_NOTE_ID, NEW_NOTE)
        prefs.edit { clear() }
        return if (noteId == NEW_NOTE) Target.NewNote else Target.Restore(noteId)
    }

    private companion object {
        const val KEY_NOTE_ID = "noteId"
        const val KEY_AT = "requestedAt"
        const val KEY_WIDGET_ID = "widgetId"
        const val NEW_NOTE = -1L
        const val MAX_AGE_MS = 2 * 60_000L
    }
}
