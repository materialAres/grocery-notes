package com.example.stickers.ui.editor

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stickers.appContainer
import com.example.stickers.ui.theme.StickersTheme

/** Full editor for one note, opened from its widget or right after creating it. */
class EditorActivity : ComponentActivity() {

    private val noteId: Long by lazy { intent.getLongExtra(EXTRA_NOTE_ID, -1L) }

    private val viewModel: EditorViewModel by viewModels {
        viewModelFactory {
            initializer { EditorViewModel(noteId, appContainer.repository, appContainer.editorWrites) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (noteId < 0) {
            finish()
            return
        }
        // The note paper behind the status bar is always light → dark status icons.
        // The bottom toolbar follows the app theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            StickersTheme {
                EditorScreen(viewModel, onClose = ::finish)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (noteId >= 0) viewModel.onStart()
    }

    override fun onStop() {
        super.onStop()
        if (noteId >= 0) viewModel.onStop(isChangingConfigurations)
    }

    companion object {
        private const val EXTRA_NOTE_ID = "noteId"

        /**
         * Opens the editor for [noteId] in a fresh task, replacing any editor already open
         * (that one is stopped first, so its ticked items are purged as usual).
         */
        fun intent(context: Context, noteId: Long): Intent =
            Intent(context, EditorActivity::class.java)
                .putExtra(EXTRA_NOTE_ID, noteId)
                // Distinct per note, so widgets' PendingIntents never collapse into one.
                .setIdentifier("note-$noteId")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
