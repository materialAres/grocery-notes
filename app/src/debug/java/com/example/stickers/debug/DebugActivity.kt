package com.example.stickers.debug

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import com.example.stickers.appContainer
import com.example.stickers.data.BlockType
import com.example.stickers.data.NoteColor
import com.example.stickers.data.TextSize
import com.example.stickers.widget.NoteWidgetReceiver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Debug-only developer helper (not in release builds). Usage:
 *
 *   adb shell am start -n com.example.stickers/.debug.DebugActivity --es action pin
 *   adb shell am start -n com.example.stickers/.debug.DebugActivity --es action seed
 *
 * - pin:  asks the launcher to add a new note widget.
 * - seed: fills every placed widget that has no note yet with a sample note.
 */
class DebugActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getStringExtra("action")) {
            "pin" -> pin()
            "seed" -> {
                seed()
                return
            }
            else -> Log.w(TAG, "Unknown action; use pin or seed")
        }
        finish()
    }

    private fun pin() {
        val manager = AppWidgetManager.getInstance(this)
        val ok = manager.isRequestPinAppWidgetSupported &&
            manager.requestPinAppWidget(ComponentName(this, NoteWidgetReceiver::class.java), null, null)
        Log.i(TAG, "requestPinAppWidget → $ok")
    }

    private fun seed() = MainScope().launch {
        val repo = appContainer.repository
        val ids = AppWidgetManager.getInstance(this@DebugActivity)
            .getAppWidgetIds(ComponentName(this@DebugActivity, NoteWidgetReceiver::class.java))
        ids.sorted().forEachIndexed { index, widgetId ->
            if (repo.getNoteForWidget(widgetId) != null) return@forEachIndexed
            val noteId = repo.createNoteForWidget(widgetId)
            SAMPLES[index % SAMPLES.size].let { sample ->
                repo.updateTitle(noteId, sample.title)
                repo.updateColor(noteId, sample.color)
                repo.updateTextSize(noteId, sample.size)
                sample.blocks.forEach { (type, text) -> repo.addBlock(noteId, type, text) }
            }
            Log.i(TAG, "Seeded widget $widgetId with note $noteId")
        }
        finish()
    }

    private data class Sample(
        val title: String,
        val color: NoteColor,
        val size: TextSize,
        val blocks: List<Pair<BlockType, String>>,
    )

    private companion object {
        const val TAG = "StickersDebug"

        fun checks(vararg items: String) = items.map { BlockType.CHECK to it }

        val SAMPLES = listOf(
            Sample(
                "Groceries", NoteColor.YELLOW, TextSize.MEDIUM,
                listOf(BlockType.TEXT to "Remember the discount card") +
                    checks("Milk", "Eggs", "Bread", "Coffee", "Apples", "Pasta", "Tomatoes",
                        "Cheese", "Olive oil", "Rice", "Yogurt", "Bananas"),
            ),
            Sample(
                "", NoteColor.PINK, TextSize.LARGE,
                listOf(BlockType.TEXT to "No title on this one, large text") +
                    checks("Call the dentist", "Pay the electricity bill"),
            ),
            Sample(
                "A rather long title that should wrap onto a second line and then stop",
                NoteColor.BLUE, TextSize.SMALL,
                listOf(BlockType.TEXT to "Multi-line text block:\nsecond line\nthird line") +
                    checks("A checklist item with quite a lot of text so it has to wrap visually") +
                    listOf(BlockType.TEXT to "More text after the list"),
            ),
            Sample("", NoteColor.GREEN, TextSize.MEDIUM, emptyList()),
        )
    }
}
