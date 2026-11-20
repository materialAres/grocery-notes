package com.example.stickers.ui

import com.example.stickers.data.Block
import com.example.stickers.data.BlockType
import com.example.stickers.data.Note
import com.example.stickers.data.NoteWithBlocks
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class NoteSummaryTest {

    private fun note(title: String, vararg blocks: Pair<Int, String>) = NoteWithBlocks(
        Note(id = 1, appWidgetId = null, title = title, createdAt = 0, updatedAt = 0, deletedAt = 0),
        blocks.map { (position, text) -> Block(noteId = 1, position = position, type = BlockType.TEXT, text = text) },
    )

    @Test
    fun label_isTitle_whenPresent() {
        assertEquals("Groceries", note(" Groceries ", 0 to "milk").label())
    }

    @Test
    fun label_isFirstNonBlankLine_inBlockOrder() {
        assertEquals("second line", note("", 1 to "later", 0 to "  \n second line \nthird").label())
    }

    @Test
    fun label_isEmpty_forEmptyNote() {
        assertEquals("", note("", 0 to "  ").label())
    }

    @Test
    fun daysUntilPurge_roundsUp() {
        val day = TimeUnit.DAYS.toMillis(1)
        assertEquals(7, daysUntilPurge(deletedAt = 0, now = 0))
        assertEquals(7, daysUntilPurge(deletedAt = 0, now = 1))
        assertEquals(6, daysUntilPurge(deletedAt = 0, now = day))
        assertEquals(1, daysUntilPurge(deletedAt = 0, now = 7 * day - 1))
        assertEquals(0, daysUntilPurge(deletedAt = 0, now = 7 * day))
        assertEquals(0, daysUntilPurge(deletedAt = 0, now = 8 * day))
    }
}
