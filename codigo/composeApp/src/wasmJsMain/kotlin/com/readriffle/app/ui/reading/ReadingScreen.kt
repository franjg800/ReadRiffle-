package com.readriffle.app.ui.reading

import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readriffle.app.data.model.ReadingMode
import com.readriffle.app.theme.ThemeMode
import com.readriffle.app.viewmodel.ReadingViewModel

/**
 * Punto de entrada de la pantalla de lectura: decide entre el modo RSVP (palabra a palabra,
 * [RsvpReaderContent]) y el modo normal paginado ([NormalReaderContent]). El selector de modo y
 * la barra de progreso arrastrable viven dentro de cada uno de esos dos modos, como parte de su
 * menú de pausa/controles -- no aquí -- para que solo aparezcan cuando se pausa la lectura (o se
 * tocan los controles en modo normal), no mientras se está leyendo activamente.
 *
 * La paginación del capítulo actual ([rememberChapterPagination]) se calcula aquí, una sola vez
 * para los dos modos -- así "cuántas páginas tiene el capítulo" es el mismo número se mire desde
 * el menú de pausa de RSVP o desde el modo normal, y la barra de progreso de ambos puede mostrar
 * "página X de Y" y saltar a una página exacta de forma consistente.
 */
@Composable
fun ReadingScreen(viewModel: ReadingViewModel, onExit: () -> Unit, onOpenPlayer: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()

    // Esta pantalla se recompone desde cero cada vez que se vuelve a ella (por ejemplo, al
    // cerrar el reproductor de audio), así que este efecto se dispara justo entonces -- el punto
    // en el que hay que recoger la posición más reciente por si se ha avanzado escuchando.
    LaunchedEffect(Unit) {
        viewModel.refreshPositionFromDisk()
    }

    // Mientras se lee, la pantalla no se apaga sola (Wake Lock del navegador, si lo admite).
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.readriffle.app.platform.keepScreenOn(true)
        val onHidden: () -> Unit = { viewModel.persistNow() }
        com.readriffle.app.platform.AppVisibility.add(onHidden)
        onDispose {
            com.readriffle.app.platform.keepScreenOn(false)
            com.readriffle.app.platform.AppVisibility.remove(onHidden)
            viewModel.persistNow()
        }
    }

    // Estadísticas: el tiempo de lectura solo cuenta mientras esta pantalla está visible.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> viewModel.setScreenActive(true)
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> viewModel.setScreenActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setScreenActive(false)
        }
    }

    BackHandler {
        viewModel.pause()
        onExit()
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val isDark = when (uiState.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accentColor = if (isDark) uiState.darkAccentOption.darkColor else uiState.lightAccentOption.lightColor

    val density = LocalDensity.current
    val shareScope = androidx.compose.runtime.rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val textColor = MaterialTheme.colorScheme.onBackground
    val fontSize = (19 * uiState.textScale).sp
    val lineHeight = (30 * uiState.textScale).sp
    val paginationStyle = TextStyle(fontFamily = uiState.fontOption.fontFamily, fontSize = fontSize, lineHeight = lineHeight, color = textColor)
    val chapterText = uiState.chapters.getOrNull(uiState.chapterIndex)?.text.orEmpty()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val maxWidthPx = with(density) { (maxWidth - 48.dp).roundToPx() }.coerceAtLeast(1)
        // La altura de página tiene que ser EXACTAMENTE el hueco real donde se pinta el texto en
        // NormalReaderContent: padding superior 88dp + inferior 86dp (antes aquí se descontaban
        // solo 150dp, así que cada página se llenaba ~24dp de más y la última línea se cortaba
        // por la mitad) más 8dp de margen de seguridad. El hueco entre párrafos también es el
        // real (16dp), no un porcentaje de la altura.
        val maxHeightPx = with(density) { (maxHeight - (88 + 86 + 8).dp).roundToPx() }.coerceAtLeast(1)
        val paragraphGapPx = with(density) { 16.dp.roundToPx() }

        val pagination = rememberChapterPagination(
            chapterText = chapterText,
            style = paginationStyle,
            textMeasurer = textMeasurer,
            maxWidthPx = maxWidthPx,
            maxHeightPx = maxHeightPx,
            paragraphGapPx = paragraphGapPx,
            wordIndex = uiState.wordIndex
        )

        when (uiState.readingMode) {
            ReadingMode.RSVP -> RsvpReaderContent(
                viewModel = viewModel,
                uiState = uiState,
                accentColor = accentColor,
                isDark = isDark,
                pagination = pagination,
                onExit = onExit,
                onOpenPlayer = onOpenPlayer
            )
            ReadingMode.NORMAL -> NormalReaderContent(
                viewModel = viewModel,
                uiState = uiState,
                accentColor = accentColor,
                pagination = pagination,
                pageGeometry = PageGeometry(paginationStyle, textMeasurer, maxWidthPx, maxHeightPx, paragraphGapPx),
                onExit = onExit,
                onOpenPlayer = onOpenPlayer
            )
        }
    }

    if (uiState.showChapterIndex) {
        ChapterIndexSheet(
            chapters = uiState.chapters,
            currentChapterIndex = uiState.chapterIndex,
            highlights = uiState.highlights,
            bookmarks = uiState.bookmarks,
            onSelectBookmark = viewModel::jumpToBookmark,
            onDeleteBookmark = viewModel::removeBookmark,
            onSelectChapter = viewModel::jumpToChapter,
            onSelectHighlight = viewModel::jumpToHighlight,
            onDeleteHighlight = viewModel::removeHighlight,
            onEditNote = viewModel::openNoteComposerForHighlight,
            onSearch = viewModel::search,
            onSelectHit = viewModel::jumpToSearchHit,
            onShareHighlight = { h ->
                shareScope.launch {
                    com.readriffle.app.data.share.QuoteCardRenderer.share(
                        h.text, uiState.bookTitle, uiState.bookAuthor, accentColor.toArgb()
                    )
                }
            },
            onDismiss = viewModel::closeChapterIndex
        )
    }
}
