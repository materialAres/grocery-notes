package com.example.stickers.ui.setup

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.stickers.R
import com.example.stickers.appContainer
import com.example.stickers.ui.editor.EditorActivity
import com.example.stickers.ui.theme.StickersTheme
import kotlinx.coroutines.launch

/**
 * Shown by the launcher when a note widget is placed (DESIGN.md §5.1).
 * Milestone 4 offers "New note"; "Restore deleted" comes with the bin in milestone 5.
 * Backing out leaves the result CANCELED, so the launcher drops the widget.
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
        lifecycleScope.launch {
            // The widget already shows a note (e.g. a pinned widget that was bound before
            // setup started): nothing to choose.
            if (appContainer.repository.getNoteForWidget(widgetId) != null) {
                finishWithWidget(widgetId)
                return@launch
            }
            setContent {
                StickersTheme {
                    SetupScreen(busy = busy, onNewNote = { createNote(widgetId) }, onCancel = ::finish)
                }
            }
        }
    }

    private fun createNote(widgetId: Int) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            val noteId = appContainer.repository.createNoteForWidget(widgetId)
            finishWithWidget(widgetId)
            startActivity(EditorActivity.intent(this@WidgetSetupActivity, noteId))
        }
    }

    private fun finishWithWidget(widgetId: Int) {
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@Composable
private fun SetupScreen(busy: Boolean, onNewNote: () -> Unit, onCancel: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.setup_body), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onNewNote, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.setup_new_note))
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
