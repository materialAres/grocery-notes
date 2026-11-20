package com.example.stickers.ui.editor

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stickers.R
import com.example.stickers.data.BlockType
import com.example.stickers.data.NoteColor
import com.example.stickers.data.TextSize
import com.example.stickers.ui.NotePalette
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Height of one line of a block row; the checkbox and drag handle are centered in it. */
private val RowHeight = 40.dp

private const val TITLE_KEY = "title"
private const val TAIL_KEY = "tail"

@Composable
fun EditorScreen(viewModel: EditorViewModel, onClose: () -> Unit) {
    val state = viewModel.state

    LaunchedEffect(state.missing) { if (state.missing) onClose() }

    val paper = NotePalette.paper(state.color)
    if (!state.loaded) {
        Box(Modifier.fillMaxSize().background(paper))
        return
    }

    val fontSize = state.textSize.sp.sp
    val textStyle = TextStyle(color = NotePalette.ink, fontSize = fontSize, lineHeight = fontSize * 1.4f)

    val listState = rememberLazyListState()
    val titleFocus = remember { FocusRequester() }
    val blockFocus = remember { mutableMapOf<Long, FocusRequester>() }
    var focusedBlockId by remember { mutableStateOf<Long?>(null) }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        // Only blocks move; the title and the tail are not reorderable items.
        val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
        val toId = to.key as? Long ?: return@rememberReorderableLazyListState
        viewModel.moveBlock(fromId, toId)
    }

    LaunchedEffect(state.focus) {
        val request = state.focus ?: return@LaunchedEffect
        val index = if (request.blockId == null) 0 else 1 + state.blocks.indexOfFirst { it.id == request.blockId }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.scrollToItem(index)
        }
        withFrameNanos { } // let a newly added block compose and attach its FocusRequester
        val requester = request.blockId?.let { blockFocus.getOrPut(it) { FocusRequester() } } ?: titleFocus
        runCatching { requester.requestFocus() }
        viewModel.onFocusHandled(request.seq)
    }

    val focusedType = focusedBlockId?.let { id -> state.blocks.find { it.id == id }?.type }

    Scaffold(
        containerColor = paper,
        contentColor = NotePalette.ink,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.editor_done), color = NotePalette.ink)
                }
            }
        },
        bottomBar = {
            EditorToolbar(
                focusedType = focusedType,
                color = state.color,
                textSize = state.textSize,
                onAddItem = { viewModel.addBlock(BlockType.CHECK) },
                onAddText = { viewModel.addBlock(BlockType.TEXT) },
                onToggleType = { focusedBlockId?.let(viewModel::toggleType) },
                onColor = viewModel::setColor,
                onTextSize = viewModel::setTextSize,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(key = TITLE_KEY) {
                TitleField(
                    value = state.title,
                    style = textStyle.copy(fontWeight = FontWeight.Bold),
                    focusRequester = titleFocus,
                    onValueChange = viewModel::onTitleChange,
                    onNext = viewModel::onTitleNext,
                )
            }

            items(state.blocks, key = { it.id }) { block ->
                ReorderableItem(reorderState, key = block.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "drag")
                    Surface(color = paper, contentColor = NotePalette.ink, shadowElevation = elevation) {
                        val focusRequester = remember(block.id) { blockFocus.getOrPut(block.id) { FocusRequester() } }
                        BlockRow(
                            block = block,
                            style = textStyle,
                            focusRequester = focusRequester,
                            dragHandle = {
                                DragHandle(Modifier.longPressDraggableHandle(onDragStopped = viewModel::commitOrder))
                            },
                            onValueChange = { viewModel.onBlockChange(block.id, it) },
                            onChecked = { viewModel.setChecked(block.id, it) },
                            onBackspaceInEmpty = { viewModel.onBackspaceInEmpty(block.id) },
                            onFocusChanged = { focused ->
                                if (focused) focusedBlockId = block.id
                                else if (focusedBlockId == block.id) focusedBlockId = null
                            },
                        )
                    }
                }
            }

            item(key = TAIL_KEY) {
                // Empty space below the blocks: tap to keep writing.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clickable(interactionSource = null, indication = null, onClick = viewModel::onTapBelow),
                ) {
                    if (state.blocks.isEmpty()) {
                        Text(
                            stringResource(R.string.editor_empty_hint),
                            style = textStyle.copy(color = NotePalette.inkFaded),
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleField(
    value: TextFieldValue,
    style: TextStyle,
    focusRequester: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    onNext: () -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(NotePalette.ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).focusRequester(focusRequester),
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty()) {
                    Text(stringResource(R.string.editor_title_hint), style = style.copy(color = NotePalette.inkFaded))
                }
                inner()
            }
        },
    )
}

@Composable
private fun BlockRow(
    block: EditorBlock,
    style: TextStyle,
    focusRequester: FocusRequester,
    dragHandle: @Composable () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onChecked: (Boolean) -> Unit,
    onBackspaceInEmpty: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    val textPadding = ((RowHeight - lineHeight) / 2).coerceAtLeast(0.dp)
    val isItem = block.type == BlockType.CHECK

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (isItem) {
            dragHandle()
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides RowHeight) {
                Checkbox(
                    checked = block.checked,
                    onCheckedChange = onChecked,
                    colors = CheckboxDefaults.colors(
                        checkedColor = NotePalette.ink,
                        uncheckedColor = NotePalette.ink,
                        checkmarkColor = NotePalette.paper(NoteColor.WHITE),
                    ),
                )
            }
        }
        BasicTextField(
            value = block.value,
            onValueChange = onValueChange,
            textStyle = if (block.checked) {
                style.copy(color = NotePalette.inkFaded, textDecoration = TextDecoration.LineThrough)
            } else {
                style
            },
            cursorBrush = SolidColor(NotePalette.ink),
            // Items are single-line: Enter inserts '\n', which the ViewModel turns into a new item.
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = textPadding)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .onPreviewKeyEvent { event ->
                    val deleteOnEmpty = event.type == KeyEventType.KeyDown &&
                        event.key == Key.Backspace && block.value.text.isEmpty()
                    if (deleteOnEmpty) onBackspaceInEmpty()
                    deleteOnEmpty
                },
        )
    }
}

/** Six-dot grip; long-press and drag it to move the item. */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.editor_reorder)
    Canvas(
        modifier
            .width(20.dp)
            .height(RowHeight)
            .semantics { contentDescription = description },
    ) {
        val radius = 1.75.dp.toPx()
        val dx = 3.5.dp.toPx()
        val dy = 5.dp.toPx()
        for (col in listOf(-dx, dx)) {
            for (row in listOf(-dy, 0f, dy)) {
                drawCircle(NotePalette.inkFaded, radius, center + Offset(col, row))
            }
        }
    }
}

@Composable
private fun EditorToolbar(
    focusedType: BlockType?,
    color: NoteColor,
    textSize: TextSize,
    onAddItem: () -> Unit,
    onAddText: () -> Unit,
    onToggleType: () -> Unit,
    onColor: (NoteColor) -> Unit,
    onTextSize: (TextSize) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Sits above the keyboard when it is open, above the nav bar otherwise.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onAddItem) { Text(stringResource(R.string.editor_add_item)) }
            TextButton(onClick = onAddText) { Text(stringResource(R.string.editor_add_text)) }
            TextButton(onClick = onToggleType, enabled = focusedType != null) {
                Text(
                    stringResource(
                        if (focusedType == BlockType.CHECK) R.string.editor_to_text else R.string.editor_to_list,
                    ),
                )
            }
            Spacer(Modifier.weight(1f))
            ColorPicker(color, onColor)
            TextSizePicker(textSize, onTextSize)
        }
    }
}

@Composable
private fun ColorPicker(selected: NoteColor, onColor: (NoteColor) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.editor_color)
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = description }) {
            Swatch(selected, isSelected = false, size = 24)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            NoteColor.entries.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { color ->
                        val name = stringResource(color.label)
                        Swatch(
                            color = color,
                            isSelected = color == selected,
                            size = 36,
                            modifier = Modifier
                                .clickable { onColor(color); open = false }
                                .semantics { contentDescription = name },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: NoteColor, isSelected: Boolean, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size.dp)
            .background(NotePalette.paper(color), CircleShape)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            ),
    )
}

@Composable
private fun TextSizePicker(selected: TextSize, onTextSize: (TextSize) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.editor_text_size)
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = description }) {
            Text("Aa", fontSize = selected.sp.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TextSize.entries.forEach { size ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(size.label),
                            fontSize = size.sp.sp,
                            fontWeight = if (size == selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = { onTextSize(size); open = false },
                )
            }
        }
    }
}

private val NoteColor.label: Int
    get() = when (this) {
        NoteColor.YELLOW -> R.string.color_yellow
        NoteColor.PINK -> R.string.color_pink
        NoteColor.ORANGE -> R.string.color_orange
        NoteColor.GREEN -> R.string.color_green
        NoteColor.BLUE -> R.string.color_blue
        NoteColor.PURPLE -> R.string.color_purple
        NoteColor.GRAY -> R.string.color_gray
        NoteColor.WHITE -> R.string.color_white
    }

private val TextSize.label: Int
    get() = when (this) {
        TextSize.SMALL -> R.string.size_small
        TextSize.MEDIUM -> R.string.size_medium
        TextSize.LARGE -> R.string.size_large
    }
