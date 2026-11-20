package com.example.stickers.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Single entry point for every read and write. After each write that changes a note
 * bound to a widget, that widget is asked to refresh.
 */
class NoteRepository(
    private val db: AppDatabase,
    private val widgetUpdater: WidgetUpdater,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.noteDao()

    // ---------------------------------------------------------------- Reads

    fun observeNote(noteId: Long): Flow<NoteWithBlocks?> = dao.observeNoteWithBlocks(noteId)

    suspend fun getNote(noteId: Long): NoteWithBlocks? = dao.getNoteWithBlocks(noteId)

    suspend fun getNoteForWidget(appWidgetId: Int): NoteWithBlocks? =
        dao.getNoteWithBlocksByWidget(appWidgetId)

    fun observeBin(): Flow<List<NoteWithBlocks>> = dao.observeBin()

    fun observeBinCount(): Flow<Int> = dao.observeBinCount()

    suspend fun getBin(): List<NoteWithBlocks> = dao.getBin()

    // ---------------------------------------------------------------- Note lifecycle

    /** A new widget was placed and the user picked "New note". Returns the note id. */
    suspend fun createNoteForWidget(appWidgetId: Int): Long {
        val now = clock()
        val id = dao.insertNote(Note(appWidgetId = appWidgetId, createdAt = now, updatedAt = now))
        widgetUpdater.update(appWidgetId)
        return id
    }

    /**
     * Binds a note from the bin to a (new) widget. Returns false if the note is not in
     * the bin or the widget already shows another note.
     */
    suspend fun restoreToWidget(noteId: Long, appWidgetId: Int): Boolean {
        val restored = db.withTransaction {
            val note = dao.getNote(noteId)
            if (note == null || !note.isInBin || dao.getNoteByWidget(appWidgetId) != null) {
                false
            } else {
                dao.updateNote(note.copy(appWidgetId = appWidgetId, deletedAt = null, updatedAt = clock()))
                true
            }
        }
        if (restored) widgetUpdater.update(appWidgetId)
        return restored
    }

    /**
     * Widgets were removed from the home screen. Pending checked items are dropped first;
     * notes that are then empty are deleted outright, the rest move to the bin.
     */
    suspend fun onWidgetsRemoved(appWidgetIds: IntArray) {
        val now = clock()
        db.withTransaction {
            for (widgetId in appWidgetIds) {
                val note = dao.getNoteByWidget(widgetId) ?: continue
                dao.deleteCheckedBlocks(note.id)
                val withBlocks = dao.getNoteWithBlocks(note.id) ?: continue
                if (withBlocks.isEmpty) {
                    dao.deleteNote(note.id)
                } else {
                    dao.updateNote(note.copy(appWidgetId = null, deletedAt = now, updatedAt = now))
                }
            }
        }
    }

    suspend fun deleteForever(noteId: Long) {
        dao.deleteNote(noteId)
    }

    suspend fun emptyBin(): Int = dao.emptyBin()

    /** Deletes bin notes older than [BIN_RETENTION_MS]. Returns how many were removed. */
    suspend fun purgeExpiredBin(): Int = dao.deleteBinnedBefore(clock() - BIN_RETENTION_MS)

    // ---------------------------------------------------------------- Note properties

    suspend fun updateTitle(noteId: Long, title: String) =
        mutateNote(noteId) { it.copy(title = title) }

    suspend fun updateColor(noteId: Long, color: NoteColor) =
        mutateNote(noteId) { it.copy(color = color) }

    suspend fun updateTextSize(noteId: Long, size: TextSize) =
        mutateNote(noteId) { it.copy(textSize = size) }

    // ---------------------------------------------------------------- Blocks

    /** Appends a block at the bottom of the note. Returns the new block id. */
    suspend fun addBlock(noteId: Long, type: BlockType, text: String = ""): Long =
        mutate(noteId) {
            dao.insertBlock(Block(noteId = noteId, position = dao.maxPosition(noteId) + 1, type = type, text = text))
        }

    /** Inserts a block right below [afterBlockId] (e.g. pressing Enter on an item). */
    suspend fun addBlockAfter(afterBlockId: Long, type: BlockType, text: String = ""): Long? {
        val anchor = dao.getBlock(afterBlockId) ?: return null
        return mutate(anchor.noteId) {
            dao.shiftPositionsAfter(anchor.noteId, anchor.position)
            dao.insertBlock(Block(noteId = anchor.noteId, position = anchor.position + 1, type = type, text = text))
        }
    }

    suspend fun updateBlockText(blockId: Long, text: String) =
        mutateBlock(blockId) { dao.updateBlockText(blockId, text) }

    /** Switches a block between text and checklist item. Clears its checked state. */
    suspend fun setBlockType(blockId: Long, type: BlockType) =
        mutateBlock(blockId) { dao.updateBlockType(blockId, type) }

    /** Editor tick: marks the item checked (struck through) until [purgeCheckedBlocks]. */
    suspend fun setChecked(blockId: Long, checked: Boolean) =
        mutateBlock(blockId) { dao.updateBlockChecked(blockId, checked) }

    /** Deletes a block permanently. Used by the widget tick and by the editor. */
    suspend fun deleteBlock(blockId: Long) =
        mutateBlock(blockId) { dao.deleteBlock(blockId) }

    /** Called when the editor closes: permanently deletes all ticked items. */
    suspend fun purgeCheckedBlocks(noteId: Long) =
        mutate(noteId) { dao.deleteCheckedBlocks(noteId) }

    /** Rewrites block order. [orderedBlockIds] must list the note's blocks in the new order. */
    suspend fun reorderBlocks(noteId: Long, orderedBlockIds: List<Long>) =
        mutate(noteId) {
            orderedBlockIds.forEachIndexed { index, id -> dao.updateBlockPosition(noteId, id, index) }
        }

    // ---------------------------------------------------------------- Helpers

    /** Runs [block] in a transaction, bumps updatedAt, then refreshes the note's widget. */
    private suspend fun <T> mutate(noteId: Long, block: suspend () -> T): T {
        var widgetId: Int? = null
        val result = db.withTransaction {
            val r = block()
            dao.touch(noteId, clock())
            widgetId = dao.getNote(noteId)?.appWidgetId
            r
        }
        widgetId?.let { widgetUpdater.update(it) }
        return result
    }

    private suspend fun mutateNote(noteId: Long, change: (Note) -> Note) {
        mutate(noteId) {
            dao.getNote(noteId)?.let { dao.updateNote(change(it)) }
        }
    }

    private suspend fun mutateBlock(blockId: Long, change: suspend () -> Unit) {
        val block = dao.getBlock(blockId) ?: return
        mutate(block.noteId) { change() }
    }

    companion object {
        val BIN_RETENTION_MS: Long = TimeUnit.DAYS.toMillis(7)
    }
}
