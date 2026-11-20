package com.example.stickers.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: NoteRepository
    private val updatedWidgets = mutableListOf<Int>()
    private var now = 1_000_000_000L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = NoteRepository(db, { updatedWidgets += it }, clock = { now })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun texts(noteId: Long) = repo.getNote(noteId)!!.blocks.map { it.text }

    // ------------------------------------------------------------ creation

    @Test
    fun createNoteForWidget_createsEmptyBoundNote_andRefreshesWidget() = runTest {
        val id = repo.createNoteForWidget(appWidgetId = 42)

        val note = repo.getNoteForWidget(42)!!
        assertEquals(id, note.note.id)
        assertEquals("", note.note.title)
        assertEquals(NoteColor.YELLOW, note.note.color)
        assertEquals(TextSize.MEDIUM, note.note.textSize)
        assertTrue(note.isEmpty)
        assertEquals(listOf(42), updatedWidgets)
    }

    @Test
    fun noteProperties_areUpdated_andWidgetRefreshed() = runTest {
        val id = repo.createNoteForWidget(1)
        updatedWidgets.clear()
        now += 10

        repo.updateTitle(id, "Groceries")
        repo.updateColor(id, NoteColor.BLUE)
        repo.updateTextSize(id, TextSize.LARGE)

        val note = repo.getNote(id)!!.note
        assertEquals("Groceries", note.title)
        assertEquals(NoteColor.BLUE, note.color)
        assertEquals(TextSize.LARGE, note.textSize)
        assertEquals(now, note.updatedAt)
        assertEquals(listOf(1, 1, 1), updatedWidgets)
    }

    // ------------------------------------------------------------ blocks

    @Test
    fun addBlock_appendsAtBottom() = runTest {
        val id = repo.createNoteForWidget(1)
        repo.addBlock(id, BlockType.TEXT, "intro")
        repo.addBlock(id, BlockType.CHECK, "milk")
        repo.addBlock(id, BlockType.CHECK, "eggs")

        assertEquals(listOf("intro", "milk", "eggs"), texts(id))
    }

    @Test
    fun addBlockAfter_insertsBelowAnchor() = runTest {
        val id = repo.createNoteForWidget(1)
        val milk = repo.addBlock(id, BlockType.CHECK, "milk")
        repo.addBlock(id, BlockType.CHECK, "bread")

        repo.addBlockAfter(milk, BlockType.CHECK, "eggs")

        assertEquals(listOf("milk", "eggs", "bread"), texts(id))
    }

    @Test
    fun deleteBlock_removesPermanently_andRefreshesWidget() = runTest {
        val id = repo.createNoteForWidget(7)
        val milk = repo.addBlock(id, BlockType.CHECK, "milk")
        repo.addBlock(id, BlockType.CHECK, "eggs")
        updatedWidgets.clear()

        repo.deleteBlock(milk)

        assertEquals(listOf("eggs"), texts(id))
        assertNull(db.noteDao().getBlock(milk))
        assertEquals(listOf(7), updatedWidgets)
    }

    @Test
    fun editorCheck_hidesFromWidget_untilPurged() = runTest {
        val id = repo.createNoteForWidget(1)
        val milk = repo.addBlock(id, BlockType.CHECK, "milk")
        val eggs = repo.addBlock(id, BlockType.CHECK, "eggs")

        repo.setChecked(milk, true)
        repo.setChecked(eggs, true)
        repo.setChecked(eggs, false) // user changed their mind

        val beforePurge = repo.getNote(id)!!
        assertEquals(listOf("milk", "eggs"), beforePurge.blocks.map { it.text })
        assertEquals(listOf("eggs"), beforePurge.visibleBlocks.map { it.text })

        repo.purgeCheckedBlocks(id)

        assertEquals(listOf("eggs"), texts(id))
    }

    @Test
    fun setChecked_isIgnoredForTextBlocks() = runTest {
        val id = repo.createNoteForWidget(1)
        val text = repo.addBlock(id, BlockType.TEXT, "paragraph")

        repo.setChecked(text, true)

        assertFalse(repo.getNote(id)!!.blocks.single().checked)
    }

    @Test
    fun setBlockType_switchesType_andClearsChecked() = runTest {
        val id = repo.createNoteForWidget(1)
        val item = repo.addBlock(id, BlockType.CHECK, "milk")
        repo.setChecked(item, true)

        repo.setBlockType(item, BlockType.TEXT)

        val block = repo.getNote(id)!!.blocks.single()
        assertEquals(BlockType.TEXT, block.type)
        assertFalse(block.checked)
    }

    @Test
    fun updateBlockText_changesText() = runTest {
        val id = repo.createNoteForWidget(1)
        val item = repo.addBlock(id, BlockType.CHECK, "mlk")

        repo.updateBlockText(item, "milk")

        assertEquals(listOf("milk"), texts(id))
    }

    @Test
    fun reorderBlocks_appliesNewOrder() = runTest {
        val id = repo.createNoteForWidget(1)
        val a = repo.addBlock(id, BlockType.TEXT, "a")
        val b = repo.addBlock(id, BlockType.CHECK, "b")
        val c = repo.addBlock(id, BlockType.CHECK, "c")

        repo.reorderBlocks(id, listOf(a, c, b))

        assertEquals(listOf("a", "c", "b"), texts(id))
    }

    // ------------------------------------------------------------ widget removal → bin

    @Test
    fun widgetRemoved_nonEmptyNote_goesToBin() = runTest {
        val id = repo.createNoteForWidget(5)
        repo.updateTitle(id, "Keep me")
        now += 100

        repo.onWidgetsRemoved(intArrayOf(5))

        val note = repo.getNote(id)!!.note
        assertNull(note.appWidgetId)
        assertEquals(now, note.deletedAt)
        assertEquals(listOf(id), repo.getBin().map { it.note.id })
        assertEquals(1, repo.observeBinCount().first())
        assertNull(repo.getNoteForWidget(5))
    }

    @Test
    fun widgetRemoved_emptyNote_isDeletedOutright() = runTest {
        val id = repo.createNoteForWidget(5)
        repo.addBlock(id, BlockType.CHECK, "   ") // blank item counts as empty

        repo.onWidgetsRemoved(intArrayOf(5))

        assertNull(repo.getNote(id))
        assertTrue(repo.getBin().isEmpty())
    }

    @Test
    fun widgetRemoved_dropsPendingCheckedItems_first() = runTest {
        val onlyChecked = repo.createNoteForWidget(1)
        repo.setChecked(repo.addBlock(onlyChecked, BlockType.CHECK, "done"), true)
        val mixed = repo.createNoteForWidget(2)
        repo.setChecked(repo.addBlock(mixed, BlockType.CHECK, "done"), true)
        repo.addBlock(mixed, BlockType.CHECK, "todo")

        repo.onWidgetsRemoved(intArrayOf(1, 2, 999)) // 999 = unknown widget, ignored

        assertNull(repo.getNote(onlyChecked))
        assertEquals(listOf("todo"), texts(mixed))
        assertTrue(repo.getNote(mixed)!!.note.isInBin)
    }

    // ------------------------------------------------------------ restore

    @Test
    fun restoreToWidget_bindsNoteAndLeavesBin() = runTest {
        val id = repo.createNoteForWidget(5)
        repo.updateTitle(id, "Back again")
        repo.onWidgetsRemoved(intArrayOf(5))
        updatedWidgets.clear()

        assertTrue(repo.restoreToWidget(id, appWidgetId = 6))

        val note = repo.getNoteForWidget(6)!!.note
        assertEquals(id, note.id)
        assertNull(note.deletedAt)
        assertTrue(repo.getBin().isEmpty())
        assertEquals(listOf(6), updatedWidgets)
    }

    @Test
    fun restoreToWidget_failsForActiveNote_orOccupiedWidget() = runTest {
        val active = repo.createNoteForWidget(1)
        assertFalse(repo.restoreToWidget(active, appWidgetId = 2))

        val binned = repo.createNoteForWidget(3)
        repo.updateTitle(binned, "x")
        repo.onWidgetsRemoved(intArrayOf(3))
        assertFalse(repo.restoreToWidget(binned, appWidgetId = 1)) // widget 1 already shows a note
        assertTrue(repo.getNote(binned)!!.note.isInBin)
    }

    // ------------------------------------------------------------ bin maintenance

    @Test
    fun purgeExpiredBin_deletesOnlyNotesOlderThan7Days() = runTest {
        val old = repo.createNoteForWidget(1).also { repo.updateTitle(it, "old") }
        val recent = repo.createNoteForWidget(2).also { repo.updateTitle(it, "recent") }
        repo.onWidgetsRemoved(intArrayOf(1))
        now += NoteRepository.BIN_RETENTION_MS / 2
        repo.onWidgetsRemoved(intArrayOf(2))
        now += NoteRepository.BIN_RETENTION_MS / 2 + 1

        assertEquals(1, repo.purgeExpiredBin())

        assertNull(repo.getNote(old))
        assertNotNull(repo.getNote(recent))
    }

    @Test
    fun emptyBin_deletesOnlyBinnedNotes() = runTest {
        val active = repo.createNoteForWidget(1).also { repo.updateTitle(it, "active") }
        val binned = repo.createNoteForWidget(2).also { repo.updateTitle(it, "binned") }
        repo.onWidgetsRemoved(intArrayOf(2))

        assertEquals(1, repo.emptyBin())

        assertNotNull(repo.getNote(active))
        assertNull(repo.getNote(binned))
    }

    @Test
    fun deleteForever_cascadesToBlocks() = runTest {
        val id = repo.createNoteForWidget(1)
        val block = repo.addBlock(id, BlockType.CHECK, "milk")
        repo.onWidgetsRemoved(intArrayOf(1))

        repo.deleteForever(id)

        assertNull(repo.getNote(id))
        assertNull(db.noteDao().getBlock(block))
    }

    @Test
    fun observeNote_emitsCurrentState() = runTest {
        val id = repo.createNoteForWidget(1)
        repo.addBlock(id, BlockType.CHECK, "milk")

        val observed = repo.observeNote(id).first()!!

        assertEquals(listOf("milk"), observed.blocks.map { it.text })
    }
}
