package com.example.stickers.ui.setup

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.stickers.R
import com.example.stickers.appContainer
import com.example.stickers.data.NoteWithBlocks
import com.example.stickers.ui.BinNoteRow
import com.example.stickers.ui.editor.EditorActivity
import com.example.stickers.ui.theme.StickersTheme
import com.example.stickers.widget.PendingPin
import kotlinx.coroutines.launch

/**
 * The widget's configure screen (DESIGN.md §5.1): New note, or restore a note from the bin.
 *
 * - Placed from the widget picker: the widget is empty; backing out cancels the placement.
 * - Pinned from the app: the request recorded in [PendingPin] is applied without asking.
 * - Reconfigured from the widget's long-press menu: the widget already has a note, which
 *   moves to the bin when another one is chosen; backing out changes nothing.
 */
class WidgetSetupActivity : ComponentActivity() {

    private var busy by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        val container = appContainer
        val repo = container.repository
        lifecycleScope.launch {
            // Only on first launch: after a rotation the request has already been applied.
            val pinned = if (savedInstanceState == null) container.pendingPin.take(widgetId) else null
            if (pinned != null) {
                applyPinned(widgetId, pinned)
                return@launch
            }
            val replacing = repo.getNoteForWidget(widgetId) != null
            repo.purgeExpiredBin()
            setContent {
                StickersTheme {
                    val bin by remember { repo.observeBin() }.collectAsState(initial = emptyList())
                    SetupScreen(
                        replacing = replacing,
                        bin = bin,
                        busy = busy,
                        onNewNote = { newNote(widgetId, replacing) },
                        onRestore = { noteId -> restore(widgetId, noteId, replacing) },
                        onCancel = ::finish,
                    )
                }
            }
        }
    }

    /** A widget pinned from the app: bind what was requested (the pin callback may have done it already). */
    private suspend fun applyPinned(widgetId: Int, target: PendingPin.Target) {
        val repo = appContainer.repository
        when (target) {
            PendingPin.Target.NewNote -> {
                val noteId = repo.createNoteForWidget(widgetId)
                finishWithWidget(widgetId)
                startActivity(EditorActivity.intent(this, noteId))
            }
            is PendingPin.Target.Restore -> {
                repo.restoreToWidget(target.noteId, widgetId)
                finishWithWidget(widgetId)
            }
        }
    }

    private fun newNote(widgetId: Int, replacing: Boolean) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            val noteId = appContainer.repository.createNoteForWidget(widgetId, replaceCurrent = replacing)
            finishWithWidget(widgetId)
            startActivity(EditorActivity.intent(this@WidgetSetupActivity, noteId))
        }
    }

    private fun restore(widgetId: Int, noteId: Long, replacing: Boolean) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            if (appContainer.repository.restoreToWidget(noteId, widgetId, replaceCurrent = replacing)) {
                finishWithWidget(widgetId)
            } else {
                busy = false // the note was purged meanwhile; the list has already updated
            }
        }
    }

    private fun finishWithWidget(widgetId: Int) {
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@Composable
private fun SetupScreen(
    replacing: Boolean,
    bin: List<NoteWithBlocks>,
    busy: Boolean,
    onNewNote: () -> Unit,
    onRestore: (noteId: Long) -> Unit,
    onCancel: () -> Unit,
) {
    val now = remember(bin) { System.currentTimeMillis() }
    Scaffold { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(if (replacing) R.string.setup_replace_title else R.string.setup_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(if (replacing) R.string.setup_replace_body else R.string.setup_body),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = onNewNote, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.setup_new_note))
                    }
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
            // Hidden when the bin is empty (DESIGN.md §5.1).
            if (bin.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.setup_restore_header),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                items(bin, key = { it.note.id }) { note ->
                    BinNoteRow(
                        note = note,
                        now = now,
                        modifier = Modifier.clickable(enabled = !busy) { onRestore(note.note.id) },
                    ) {
                        TextButton(onClick = { onRestore(note.note.id) }, enabled = !busy) {
                            Text(stringResource(R.string.bin_restore))
                        }
                    }
                }
            }
        }
    }
}
