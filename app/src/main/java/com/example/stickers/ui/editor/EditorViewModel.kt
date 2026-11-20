package com.example.stickers.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stickers.data.BlockType
import com.example.stickers.data.NoteColor
import com.example.stickers.data.NoteRepository
import com.example.stickers.data.TextSize
import com.example.stickers.data.WriteQueue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class EditorBlock(
    val id: Long,
    val type: BlockType,
    val value: TextFieldValue,
    val checked: Boolean = false,
)

/** Ask the screen to focus the title ([blockId] == null) or a block. [seq] makes each request unique. */
data class FocusRequest(val blockId: Long?, val seq: Int)

data class EditorState(
    val loaded: Boolean = false,
    /** The note no longer exists; the editor should close. */
    val missing: Boolean = false,
    val title: TextFieldValue = TextFieldValue(),
    val color: NoteColor = NoteColor.YELLOW,
    val textSize: TextSize = TextSize.MEDIUM,
    val blocks: List<EditorBlock> = emptyList(),
    val focus: FocusRequest? = null,
)

/**
 * Editor for one note (DESIGN.md §5.3).
 *
 * The screen state lives here and is the source for the UI while the editor is open, so
 * typing is never disturbed by database round-trips. Every change is written through
 * [writes] (ordered, survives the editor closing):
 * - text and title edits are debounced by [SAVE_DEBOUNCE_MS];
 * - structural changes (add, delete, convert, tick, reorder, color, size) are written at once.
 *
 * Ticked items stay (struck through) until the editor is left, then they are deleted.
 */
class EditorViewModel(
    private val noteId: Long,
    private val repo: NoteRepository,
    private val writes: WriteQueue,
) : ViewModel() {

    var state by mutableStateOf(EditorState())
        private set

    private var needsReload = false
    private var dirtyTitle: String? = null
    private val dirtyTexts = LinkedHashMap<Long, String>()
    private var saveJob: Job? = null
    private var focusSeq = 0

    // ------------------------------------------------------------------ Lifecycle

    /** Activity onStart: first load, or reload after the editor was left (the widget may have changed the note). */
    fun onStart() {
        if (state.loaded && !needsReload) return
        val firstLoad = !state.loaded
        needsReload = false
        viewModelScope.launch {
            val data = writes.run {
                // Leftovers from an editor session that was killed before it could purge.
                if (firstLoad) repo.purgeCheckedBlocks(noteId)
                repo.getNote(noteId)
            }
            if (data == null) {
                state = state.copy(missing = true)
                return@launch
            }
            val title = data.note.title
            state = EditorState(
                loaded = true,
                title = TextFieldValue(title, TextRange(title.length)),
                color = data.note.color,
                textSize = data.note.textSize,
                blocks = data.blocks.map {
                    EditorBlock(it.id, it.type, TextFieldValue(it.text, TextRange(it.text.length)), it.checked)
                },
                // A brand-new note: start typing the title straight away.
                focus = if (firstLoad && data.isEmpty) nextFocus(null) else null,
            )
        }
    }

    /**
     * Activity onStop (back, home, app switch): save pending text and delete ticked items.
     * A configuration change (rotation) only saves, so ticks survive it.
     */
    fun onStop(changingConfigurations: Boolean) {
        flush()
        if (changingConfigurations) return
        writes.launch { repo.purgeCheckedBlocks(noteId) }
        state = state.copy(blocks = state.blocks.filterNot { it.checked })
        needsReload = true
    }

    override fun onCleared() {
        flush()
    }

    // ------------------------------------------------------------------ Text

    fun onTitleChange(value: TextFieldValue) {
        // Single line: a pasted line break becomes a space.
        val clean = if ('\n' in value.text) value.copy(text = value.text.replace('\n', ' ')) else value
        val changed = clean.text != state.title.text
        state = state.copy(title = clean)
        if (changed) {
            dirtyTitle = clean.text
            scheduleSave()
        }
    }

    /** Enter in the title: go to the first block, or start the body if there is none. */
    fun onTitleNext() {
        val first = state.blocks.firstOrNull()
        if (first == null) addBlock(BlockType.TEXT) else requestFocus(first.id)
    }

    fun onBlockChange(blockId: Long, value: TextFieldValue) {
        val block = block(blockId) ?: return
        if (block.type == BlockType.CHECK && '\n' in value.text) {
            splitItem(block, value)
            return
        }
        updateBlock(blockId) { it.copy(value = value) }
        if (value.text != block.value.text) {
            dirtyTexts[blockId] = value.text
            scheduleSave()
        }
    }

    /** Enter (or a multi-line paste) in a checklist item: the text after each break becomes a new item. */
    private fun splitItem(block: EditorBlock, value: TextFieldValue) {
        val split = splitIntoItems(value.text, value.selection.start)
        val first = split.lines.first()
        val rest = split.lines.drop(1)
        dirtyTexts.remove(block.id)
        updateBlock(block.id) { it.copy(value = TextFieldValue(first, TextRange(first.length))) }
        viewModelScope.launch {
            val newIds = writes.run {
                repo.updateBlockText(block.id, first)
                insertItemsAfter(block.id, rest)
            }
            insertLocally(block.id, rest.zip(newIds) { text, id -> EditorBlock(id, BlockType.CHECK, TextFieldValue(text)) })
            newIds.lastOrNull()?.let { requestFocus(it, cursor = split.cursorInLast) }
        }
    }

    /** Backspace in an empty block deletes it and moves to the end of the block above (or the title). */
    fun onBackspaceInEmpty(blockId: Long) {
        val index = state.blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        dirtyTexts.remove(blockId)
        val above = state.blocks.getOrNull(index - 1)
        state = state.copy(blocks = state.blocks.filterNot { it.id == blockId })
        requestFocus(above?.id)
        writes.launch { repo.deleteBlock(blockId) }
    }

    /** Tap on the empty area below the blocks. */
    fun onTapBelow() {
        val last = state.blocks.lastOrNull()
        if (last == null) addBlock(BlockType.TEXT) else requestFocus(last.id)
    }

    // ------------------------------------------------------------------ Structure

    /** Toolbar "+ Item" / "+ Text": new block at the bottom, focused. */
    fun addBlock(type: BlockType) {
        viewModelScope.launch {
            val id = writes.run { repo.addBlock(noteId, type) }
            state = state.copy(blocks = state.blocks + EditorBlock(id, type, TextFieldValue()))
            requestFocus(id)
        }
    }

    /** Toolbar convert: item → text block, or text block → one item per non-blank line. */
    fun toggleType(blockId: Long) {
        val block = block(blockId) ?: return
        dirtyTexts.remove(blockId)
        val text = block.value.text

        if (block.type == BlockType.CHECK) {
            updateBlock(blockId) { it.copy(type = BlockType.TEXT, checked = false) }
            writes.launch {
                repo.setBlockType(blockId, BlockType.TEXT)
                repo.updateBlockText(blockId, text)
            }
            return
        }

        val lines = textToItems(text)
        val first = lines.first()
        val rest = lines.drop(1)
        updateBlock(blockId) {
            it.copy(type = BlockType.CHECK, value = TextFieldValue(first, TextRange(first.length)))
        }
        viewModelScope.launch {
            val newIds = writes.run {
                repo.setBlockType(blockId, BlockType.CHECK)
                repo.updateBlockText(blockId, first)
                insertItemsAfter(blockId, rest)
            }
            insertLocally(blockId, rest.zip(newIds) { line, id -> EditorBlock(id, BlockType.CHECK, TextFieldValue(line)) })
        }
    }

    fun setChecked(blockId: Long, checked: Boolean) {
        updateBlock(blockId) { it.copy(checked = checked) }
        writes.launch { repo.setChecked(blockId, checked) }
    }

    /** Live move while dragging; persisted by [commitOrder] when the drag ends. */
    fun moveBlock(fromId: Long, toId: Long) {
        val list = state.blocks.toMutableList()
        val from = list.indexOfFirst { it.id == fromId }
        val to = list.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0) return
        list.add(to, list.removeAt(from))
        state = state.copy(blocks = list)
    }

    fun commitOrder() {
        val ids = state.blocks.map { it.id }
        writes.launch { repo.reorderBlocks(noteId, ids) }
    }

    fun setColor(color: NoteColor) {
        state = state.copy(color = color)
        writes.launch { repo.updateColor(noteId, color) }
    }

    fun setTextSize(size: TextSize) {
        state = state.copy(textSize = size)
        writes.launch { repo.updateTextSize(noteId, size) }
    }

    // ------------------------------------------------------------------ Focus

    fun onFocusHandled(seq: Int) {
        if (state.focus?.seq == seq) state = state.copy(focus = null)
    }

    /** Focuses the title or a block, with the cursor at [cursor] (null = end of text). */
    private fun requestFocus(blockId: Long?, cursor: Int? = null) {
        fun TextFieldValue.withCursor() = copy(selection = TextRange((cursor ?: text.length).coerceIn(0, text.length)))
        state = if (blockId == null) {
            state.copy(title = state.title.withCursor(), focus = nextFocus(null))
        } else {
            state.copy(
                blocks = state.blocks.map { if (it.id == blockId) it.copy(value = it.value.withCursor()) else it },
                focus = nextFocus(blockId),
            )
        }
    }

    private fun nextFocus(blockId: Long?) = FocusRequest(blockId, ++focusSeq)

    // ------------------------------------------------------------------ Helpers

    private fun block(id: Long) = state.blocks.find { it.id == id }

    private fun updateBlock(id: Long, change: (EditorBlock) -> EditorBlock) {
        state = state.copy(blocks = state.blocks.map { if (it.id == id) change(it) else it })
    }

    private fun insertLocally(afterId: Long, newBlocks: List<EditorBlock>) {
        if (newBlocks.isEmpty()) return
        val list = state.blocks.toMutableList()
        val index = list.indexOfFirst { it.id == afterId }
        list.addAll(if (index < 0) list.size else index + 1, newBlocks)
        state = state.copy(blocks = list)
    }

    /** Inserts checklist items one below the other after [anchorId]. Call inside [writes]. */
    private suspend fun insertItemsAfter(anchorId: Long, lines: List<String>): List<Long> {
        val ids = mutableListOf<Long>()
        var anchor = anchorId
        for (line in lines) {
            anchor = repo.addBlockAfter(anchor, BlockType.CHECK, line) ?: break
            ids += anchor
        }
        return ids
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            flush()
        }
    }

    /** Writes pending title and text edits now. */
    private fun flush() {
        saveJob?.cancel()
        saveJob = null
        val title = dirtyTitle
        val texts = dirtyTexts.toMap()
        dirtyTitle = null
        dirtyTexts.clear()
        if (title == null && texts.isEmpty()) return
        writes.launch {
            title?.let { repo.updateTitle(noteId, it) }
            texts.forEach { (id, text) -> repo.updateBlockText(id, text) }
        }
    }

    companion object {
        const val SAVE_DEBOUNCE_MS = 300L
    }
}
