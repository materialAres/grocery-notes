package com.example.stickers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stickers.R
import com.example.stickers.data.NoteWithBlocks
import androidx.compose.foundation.layout.Row

/** A note in the bin: color chip, title or first line, and when it will be purged. */
@Composable
fun BinNoteRow(
    note: NoteWithBlocks,
    now: Long,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val days = daysUntilPurge(note.note.deletedAt ?: now, now)
    ListItem(
        modifier = modifier,
        leadingContent = {
            Box(
                Modifier
                    .size(32.dp)
                    .background(NotePalette.paper(note.note.color), RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
            )
        },
        headlineContent = {
            Text(
                note.label().ifEmpty { stringResource(R.string.untitled) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                if (days == 0) {
                    stringResource(R.string.bin_deletes_today)
                } else {
                    pluralStringResource(R.plurals.bin_deletes_in_days, days, days)
                },
            )
        },
        trailingContent = { Row(content = actions) },
    )
}
