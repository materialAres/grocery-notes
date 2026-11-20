package com.example.stickers.data

/**
 * Asks the home-screen widget showing a note to redraw itself.
 * The app uses the Glance-backed `GlanceWidgetUpdater`; tests pass a fake.
 */
fun interface WidgetUpdater {
    suspend fun update(appWidgetId: Int)
}

object NoOpWidgetUpdater : WidgetUpdater {
    override suspend fun update(appWidgetId: Int) = Unit
}
