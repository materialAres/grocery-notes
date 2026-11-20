package com.example.stickers.ui.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stickers.R
import com.example.stickers.data.NoteWithBlocks
import com.example.stickers.ui.AppIcons
import com.example.stickers.ui.BinNoteRow
import com.example.stickers.ui.label

/** Something irreversible waiting for confirmation. */
private sealed interface Confirm {
    data object EmptyBin : Confirm
    data class DeleteForever(val note: NoteWithBlocks) : Confirm
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinScreen(viewModel: BinViewModel, onBack: () -> Unit) {
    val bin by viewModel.bin.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val now = remember(bin) { System.currentTimeMillis() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_recently_deleted)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { confirm = Confirm.EmptyBin }, enabled = !bin.isNullOrEmpty()) {
                        Text(stringResource(R.string.bin_empty_bin))
                    }
                },
            )
        },
    ) { padding ->
        val notes = bin
        when {
            notes == null -> Unit
            notes.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
                Text(
                    stringResource(R.string.bin_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(notes, key = { it.note.id }) { note ->
                    Column(Modifier.animateItem()) {
                        BinNoteRow(note, now)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            TextButton(onClick = { confirm = Confirm.DeleteForever(note) }) {
                                Text(stringResource(R.string.bin_delete_forever))
                            }
                            FilledTonalButton(onClick = { viewModel.restore(note.note.id) }) {
                                Text(stringResource(R.string.bin_restore))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    confirm?.let { pending ->
        val untitled = stringResource(R.string.untitled)
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = {
                Text(
                    when (pending) {
                        Confirm.EmptyBin -> stringResource(R.string.bin_empty_bin_confirm)
                        is Confirm.DeleteForever -> stringResource(
                            R.string.bin_delete_forever_confirm,
                            pending.note.label().ifEmpty { untitled },
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (pending) {
                            Confirm.EmptyBin -> viewModel.emptyBin()
                            is Confirm.DeleteForever -> viewModel.deleteForever(pending.note.note.id)
                        }
                        confirm = null
                    },
                ) {
                    Text(
                        stringResource(
                            if (pending == Confirm.EmptyBin) R.string.bin_empty_bin else R.string.bin_delete_forever,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
