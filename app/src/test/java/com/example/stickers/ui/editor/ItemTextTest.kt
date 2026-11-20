package com.example.stickers.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemTextTest {

    @Test
    fun enterAtEnd_createsEmptyItem_withCursorAtStart() {
        assertEquals(ItemSplit(listOf("Milk", ""), 0), splitIntoItems("Milk\n", cursor = 5))
    }

    @Test
    fun enterInMiddle_movesRestOfLineToNewItem() {
        assertEquals(ItemSplit(listOf("Mi", "lk"), 0), splitIntoItems("Mi\nlk", cursor = 3))
    }

    @Test
    fun enterAtStart_leavesEmptyItemAbove() {
        assertEquals(ItemSplit(listOf("", "Milk"), 0), splitIntoItems("\nMilk", cursor = 1))
    }

    @Test
    fun paste_dropsBlankMiddleLines_andKeepsCursorAfterPaste() {
        // "Eggs|" then paste "Milk\n\nBread\nJam" → cursor after "Jam", before " tail".
        val text = "EggsMilk\n\nBread\nJam tail"
        val cursor = text.indexOf(" tail")
        assertEquals(ItemSplit(listOf("EggsMilk", "Bread", "Jam tail"), 3), splitIntoItems(text, cursor))
    }

    @Test
    fun noLineBreak_isSingleItem() {
        assertEquals(ItemSplit(listOf("Milk"), 2), splitIntoItems("Milk", cursor = 2))
    }

    @Test
    fun textToItems_keepsNonBlankLines() {
        assertEquals(listOf("a", "b"), textToItems("a\n\n  \nb\n"))
    }

    @Test
    fun textToItems_blankText_givesOneEmptyItem() {
        assertEquals(listOf(""), textToItems(" \n"))
    }
}
