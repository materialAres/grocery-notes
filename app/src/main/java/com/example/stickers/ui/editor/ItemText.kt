package com.example.stickers.ui.editor

/** Result of splitting multi-line input into single-line checklist items. */
internal data class ItemSplit(
    val lines: List<String>,
    /** Where the cursor goes inside the last line. */
    val cursorInLast: Int,
)

/**
 * A checklist item received text containing line breaks: Enter was pressed (one break at
 * the cursor) or several lines were pasted. Each line becomes its own item.
 *
 * The first and last lines are always kept, even if empty: "Milk|" + Enter gives "Milk"
 * and a new empty item. Blank lines in between (from a paste) are dropped.
 *
 * @param cursor the cursor position in [text] after the edit.
 */
internal fun splitIntoItems(text: String, cursor: Int): ItemSplit {
    val parts = text.split('\n')
    if (parts.size == 1) return ItemSplit(parts, cursor.coerceIn(0, text.length))
    val middle = parts.subList(1, parts.size - 1).filter { it.isNotBlank() }
    val lines = listOf(parts.first()) + middle + parts.last()
    val lastStart = text.lastIndexOf('\n') + 1
    return ItemSplit(lines, (cursor - lastStart).coerceIn(0, lines.last().length))
}

/**
 * A text block is turned into a checklist: each non-blank line becomes one item.
 * Always returns at least one (possibly empty) line.
 */
internal fun textToItems(text: String): List<String> =
    text.split('\n').filter { it.isNotBlank() }.ifEmpty { listOf("") }
