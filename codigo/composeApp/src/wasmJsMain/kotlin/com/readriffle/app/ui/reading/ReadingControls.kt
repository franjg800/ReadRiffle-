package com.readriffle.app.ui.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readriffle.app.R
import com.readriffle.app.data.model.ReadingMode
import com.readriffle.app.theme.ReaderFontOption
import com.readriffle.app.theme.RsvpBackgroundMode
import com.readriffle.app.theme.ThemeMode
import com.readriffle.app.theme.ThemeVariant

/**
 * Controles que aparecen al pausar la lectura en modo RSVP: cápsula de salir (arriba-izquierda),
 * cápsula con índice / "Aa" / más opciones (arriba-derecha) y el panel inferior con progreso y
 * modos. Play y saltos de palabras no son botones, son gestos (ver RsvpReaderContent). La hoja
 * "Aa" reúne tamaño, tipografía, tema y fondo; el menú de los tres puntos, nota y bloqueo.
 *
 * El contenedor respeta las barras del sistema para que nada quede recortado por el notch.
 */
@Composable
fun ReadingControls(
    onExit: () -> Unit,
    onLock: () -> Unit,
    onOpenIndex: () -> Unit,
    onOpenPlayer: () -> Unit,
    textScale: Float,
    zoomPresets: List<Float>,
    onSetTextScale: (Float) -> Unit,
    fontOption: ReaderFontOption,
    onSelectFont: (ReaderFontOption) -> Unit,
    themeMode: ThemeMode,
    themeVariant: ThemeVariant,
    onSetTheme: (ThemeMode, ThemeVariant) -> Unit,
    isDark: Boolean,
    rsvpBackground: RsvpBackgroundMode,
    onSetRsvpBackground: (RsvpBackgroundMode) -> Unit,
    onAddNote: () -> Unit,
    accentColor: Color,
    readingMode: ReadingMode,
    onSelectMode: (ReadingMode) -> Unit,
    currentPage: Int,
    totalPages: Int,
    onSeekPage: (Int) -> Unit,
    showNextChapterButton: Boolean,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSettings by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ReadingTopBar(
            isBookmarked = null,
            onExit = onExit,
            onToggleBookmark = {},
            onOpenIndex = onOpenIndex,
            onOpenSettings = { showSettings = true },
            menuActions = listOf(
                ChromeMenuAction(R.drawable.ic_note_simple, stringResource(R.string.reading_add_note), onAddNote),
                ChromeMenuAction(R.drawable.ic_lock, "Bloquear con la portada", onLock)
            ),
            modifier = Modifier.align(Alignment.TopCenter)
        )
        if (showNextChapterButton) {
            ChapterEndButton(
                onClick = onNextChapter,
                accentColor = accentColor,
                modifier = Modifier.align(Alignment.Center).padding(top = 72.dp)
            )
        }
        ReadingBottomPanel(
            readingMode = readingMode,
            onSelectMode = onSelectMode,
            onOpenPlayer = onOpenPlayer,
            currentPage = currentPage,
            totalPages = totalPages,
            onSeekPage = onSeekPage,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showSettings) {
        ReadingSettingsSheet(
            textScale = textScale, zoomPresets = zoomPresets, onSetTextScale = onSetTextScale,
            fontOption = fontOption, onSelectFont = onSelectFont,
            themeMode = themeMode, themeVariant = themeVariant, onSetTheme = onSetTheme,
            isDark = isDark,
            rsvpBackground = rsvpBackground, onSetRsvpBackground = onSetRsvpBackground,
            onDismiss = { showSettings = false }
        )
    }
}

/**
 * Botón que solo aparece cuando el capítulo actual ha terminado en modo RSVP (no se auto-avanza
 * de capítulo: el usuario pidió un botón manual). Píldora con el acento.
 */
@Composable
private fun ChapterEndButton(onClick: () -> Unit, accentColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .glass(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.reading_next_chapter),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            painter = painterResource(R.drawable.ic_next_chapter),
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}
