package com.example.stickers.ui

import com.example.stickers.data.NoteRepository
import com.example.stickers.data.NoteWithBlocks
import java.util.concurrent.TimeUnit

/** What a bin entry is called: the title, else the first non-blank line of the content. */
fun NoteWithBlocks.label(): String {
    if (note.title.isNotBlank()) return note.title.trim()
    return blocks.asSequence()
        .flatMap { it.text.lineSequence() }
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
}

/** Whole days left before a bin note is purged, rounded up (0 = goes today). */
fun daysUntilPurge(deletedAt: Long, now: Long): Int {
    val left = deletedAt + NoteRepository.BIN_RETENTION_MS - now
    if (left <= 0) return 0
    val day = TimeUnit.DAYS.toMillis(1)
    return ((left + day - 1) / day).toInt()
}
