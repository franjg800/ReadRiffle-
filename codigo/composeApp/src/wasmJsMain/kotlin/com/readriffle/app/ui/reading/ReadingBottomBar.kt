package com.readriffle.app.ui.reading

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.readriffle.app.data.model.ReadingMode

/** Compatibilidad: el panel inferior de la lectura vive ahora en [ReadingBottomPanel]. */
@Composable
fun ReadingBottomBar(
    readingMode: ReadingMode,
    onSelectMode: (ReadingMode) -> Unit,
    onOpenPlayer: () -> Unit,
    currentPage: Int,
    totalPages: Int,
    onSeekPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) = ReadingBottomPanel(readingMode, onSelectMode, onOpenPlayer, currentPage, totalPages, onSeekPage, modifier)
