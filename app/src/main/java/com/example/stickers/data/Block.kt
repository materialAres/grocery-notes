package com.example.stickers.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BlockType {
    /** Free text paragraph (may span multiple lines). */
    TEXT,

    /** Single-line checklist item. */
    CHECK,
}

/**
 * One piece of a note's body: either a text paragraph or a checklist item.
 * Blocks are ordered by [position] within their note (gaps are allowed).
 */
@Entity(
    tableName = "blocks",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["noteId", "position"])],
)
data class Block(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val position: Int,
    val type: BlockType,
    val text: String = "",
    /**
     * Only used by the editor: a ticked item stays visible (struck through) until the
     * editor is closed, then it is deleted. Widgets never show checked blocks, and a
     * tick on the widget deletes the block straight away.
     */
    val checked: Boolean = false,
)
