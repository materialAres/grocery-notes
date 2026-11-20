package com.example.stickers.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // ---- Notes ----

    @Insert
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: Long): Note?

    @Query("SELECT * FROM notes WHERE appWidgetId = :appWidgetId")
    suspend fun getNoteByWidget(appWidgetId: Int): Note?

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteWithBlocks(id: Long): NoteWithBlocks?

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNoteWithBlocks(id: Long): Flow<NoteWithBlocks?>

    @Transaction
    @Query("SELECT * FROM notes WHERE appWidgetId = :appWidgetId")
    suspend fun getNoteWithBlocksByWidget(appWidgetId: Int): NoteWithBlocks?

    @Query("UPDATE notes SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long): Int

    // ---- Bin ----

    @Transaction
    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeBin(): Flow<List<NoteWithBlocks>>

    @Transaction
    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun getBin(): List<NoteWithBlocks>

    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NOT NULL")
    fun observeBinCount(): Flow<Int>

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL")
    suspend fun emptyBin(): Int

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun deleteBinnedBefore(cutoff: Long): Int

    // ---- Blocks ----

    @Insert
    suspend fun insertBlock(block: Block): Long

    @Query("SELECT * FROM blocks WHERE id = :id")
    suspend fun getBlock(id: Long): Block?

    @Query("SELECT COALESCE(MAX(position), -1) FROM blocks WHERE noteId = :noteId")
    suspend fun maxPosition(noteId: Long): Int

    @Query("UPDATE blocks SET position = position + 1 WHERE noteId = :noteId AND position > :after")
    suspend fun shiftPositionsAfter(noteId: Long, after: Int)

    @Query("UPDATE blocks SET position = :position WHERE id = :id AND noteId = :noteId")
    suspend fun updateBlockPosition(noteId: Long, id: Long, position: Int)

    @Query("UPDATE blocks SET text = :text WHERE id = :id")
    suspend fun updateBlockText(id: Long, text: String)

    @Query("UPDATE blocks SET type = :type, checked = 0 WHERE id = :id")
    suspend fun updateBlockType(id: Long, type: BlockType)

    @Query("UPDATE blocks SET checked = :checked WHERE id = :id AND type = 'CHECK'")
    suspend fun updateBlockChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM blocks WHERE id = :id")
    suspend fun deleteBlock(id: Long): Int

    @Query("DELETE FROM blocks WHERE noteId = :noteId AND checked = 1")
    suspend fun deleteCheckedBlocks(noteId: Long): Int
}
