package com.example.stickers.data

/**
 * Asks the home-screen widget showing a note to redraw itself.
 * The real Glance-backed implementation arrives with milestone 2.
 */
fun interface WidgetUpdater {
    suspend fun update(appWidgetId: Int)
}

object NoOpWidgetUpdater : WidgetUpdater {
    override suspend fun update(appWidgetId: Int) = Unit
}
