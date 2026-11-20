package com.example.stickers.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.stickers.appContainer
import com.example.stickers.data.Block
import com.example.stickers.data.BlockType
import com.example.stickers.data.NoteColor
import com.example.stickers.data.NoteWithBlocks
import com.example.stickers.data.TextSize
import com.example.stickers.ui.NotePalette
import com.example.stickers.ui.editor.EditorActivity

/**
 * Home-screen sticky note. Layout is the same at every size (DESIGN.md §6):
 * pinned bold title, then a scrolling list of text blocks and checklist items.
 * Ticking a checkbox deletes the item; any other tap opens the editor.
 */
class NoteWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val repository = context.appContainer.repository
        val initial = repository.getNoteForWidget(appWidgetId)

        provideContent {
            // Live while the Glance session runs; afterwards WidgetUpdater restarts it.
            val note by repository.observeNoteForWidget(appWidgetId).collectAsState(initial)
            NoteWidgetContent(note)
        }
    }
}

/** Item id of the clickable spacer after the last block (block ids are positive). */
private const val TAIL_ITEM_ID = -1L

@Composable
private fun NoteWidgetContent(data: NoteWithBlocks?) {
    val paper = NotePalette.paper(data?.note?.color ?: NoteColor.YELLOW)
    val fontSize = (data?.note?.textSize ?: TextSize.MEDIUM).sp.sp
    val ink = ColorProvider(NotePalette.ink)
    val bodyStyle = TextStyle(color = ink, fontSize = fontSize)

    val context = LocalContext.current
    val openEditor = data?.let { actionStartActivity(EditorActivity.intent(context, it.note.id)) }
    fun GlanceModifier.opensEditor() = if (openEditor != null) clickable(openEditor) else this

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(paper)
            .cornerRadius(16.dp)
            .opensEditor()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (data == null) {
            // Only if the widget was placed without going through setup.
            Placeholder("Note not set up", fontSize)
            return@Column
        }

        val title = data.note.title
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = bodyStyle.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                modifier = GlanceModifier.fillMaxWidth().opensEditor(),
            )
            Spacer(GlanceModifier.height(4.dp))
        }

        val blocks = data.visibleBlocks
        if (blocks.isEmpty()) {
            if (title.isBlank()) Placeholder("Tap to write…", fontSize)
        } else {
            // List rows can't fall through to the root's click, so each row (and a tail
            // spacer for the empty area below) opens the editor itself.
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(blocks, itemId = { it.id }) { block -> BlockRow(block, bodyStyle, openEditor!!) }
                item(itemId = TAIL_ITEM_ID) {
                    Box(GlanceModifier.fillMaxWidth().height(48.dp).opensEditor()) {}
                }
            }
        }
    }
}

@Composable
private fun BlockRow(block: Block, style: TextStyle, openEditor: Action) {
    val rowModifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
    when (block.type) {
        BlockType.TEXT -> Text(text = block.text, style = style, modifier = rowModifier.clickable(openEditor))
        BlockType.CHECK -> Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
            // The box alone ticks (and deletes); the label opens the editor, so a stray tap
            // on the text can't delete an item.
            CheckBox(
                // Always drawn unticked: a tick deletes the item, so it never shows as checked.
                checked = false,
                onCheckedChange = CheckItemAction.forBlock(block.id),
                text = "",
                colors = CheckboxDefaults.colors(
                    checkedColor = NotePalette.ink,
                    uncheckedColor = NotePalette.ink,
                ),
            )
            Text(
                text = block.text,
                style = style,
                modifier = GlanceModifier.defaultWeight().clickable(openEditor),
            )
        }
    }
}

@Composable
private fun Placeholder(text: String, fontSize: TextUnit) {
    Text(text = text, style = TextStyle(color = ColorProvider(NotePalette.inkFaded), fontSize = fontSize))
}
