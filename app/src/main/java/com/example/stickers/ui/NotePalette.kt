package com.example.stickers.ui

import androidx.compose.ui.graphics.Color
import com.example.stickers.data.NoteColor

/**
 * Sticky-note paper colors, shared by the widget and (later) the editor.
 * They are light paper tones and do not change in dark mode, so text is always [ink].
 */
object NotePalette {
    val ink = Color(0xFF202124)
    val inkFaded = Color(0x99202124)

    fun paper(color: NoteColor): Color = when (color) {
        NoteColor.YELLOW -> Color(0xFFFFF59D)
        NoteColor.PINK -> Color(0xFFF8BBD0)
        NoteColor.ORANGE -> Color(0xFFFFCC80)
        NoteColor.GREEN -> Color(0xFFC5E1A5)
        NoteColor.BLUE -> Color(0xFFB3E5FC)
        NoteColor.PURPLE -> Color(0xFFD1C4E9)
        NoteColor.GRAY -> Color(0xFFE0E0E0)
        NoteColor.WHITE -> Color(0xFFFAFAFA)
    }
}
