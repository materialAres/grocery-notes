package com.example.stickers.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Sticky-note paper colors. Notes keep these colors in dark mode too. */
enum class NoteColor { YELLOW, PINK, ORANGE, GREEN, BLUE, PURPLE, GRAY, WHITE }

/** Per-note text size. Title and body always share the same size. */
enum class TextSize(val sp: Int) { SMALL(14), MEDIUM(16), LARGE(18) }

/**
 * A sticky note. One note is shown by exactly one home-screen widget.
 *
 * - Active note: [appWidgetId] != null and [deletedAt] == null
 * - In the recently-deleted bin: [appWidgetId] == null and [deletedAt] != null
 */
@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["appWidgetId"], unique = true),
        Index(value = ["deletedAt"]),
    ],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appWidgetId: Int?,
    /** Empty string = no title line shown. */
    val title: String = "",
    val color: NoteColor = NoteColor.YELLOW,
    val textSize: TextSize = TextSize.MEDIUM,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    val isInBin: Boolean get() = deletedAt != null
}
