package com.example.stickers.data

import androidx.room.Embedded
import androidx.room.Relation

data class NoteWithBlocks(
    @Embedded val note: Note,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val unsortedBlocks: List<Block>,
) {
    /** All blocks in display order, including checked ones (for the editor). */
    val blocks: List<Block> get() = unsortedBlocks.sortedBy { it.position }

    /** Blocks the widget shows: checked items are never displayed there. */
    val visibleBlocks: List<Block> get() = blocks.filterNot { it.checked }

    /** No title and no non-blank content. Empty notes skip the bin when their widget is removed. */
    val isEmpty: Boolean
        get() = note.title.isBlank() && unsortedBlocks.all { it.text.isBlank() }
}
