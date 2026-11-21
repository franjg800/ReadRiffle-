package com.readriffle.app.ui.reading

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readriffle.app.R
import com.readriffle.app.viewmodel.ReadingUiState
import com.readriffle.app.viewmodel.ReadingViewModel
import kotlin.math.roundToInt

/**
 * Modo de lectura normal: el capítulo paginado como un libro corriente, con un pequeño efecto
 * de deslizamiento al pasar página (no pretende ser un "page curl" realista, solo dar un toque
 * más agradable que un corte seco).
 *
 * Gestos: tocar el tercio izquierdo retrocede una página, el tercio derecho avanza, y el
 * tercio central muestra/oculta los controles (salir arriba-izq; índice de capítulos y
 * tipografía arriba-der; zoom abajo-izq). Al llegar al final/principio de un capítulo, pasar de
 * página salta al siguiente/anterior capítulo.
 *
 * **Selección de palabras**: mantener pulsado sobre una palabra la selecciona (y arrastrar sin
 * soltar extiende la selección a una frase, ver [SelectableParagraphText]); al soltar aparece
 * una barra flotante ([WordSelectionActionBar]) para saltar al modo RSVP retomando justo desde
 * ahí, o para resaltar el texto elegido -- dos habilidades distintas que comparten el mismo
 * gesto de entrada.
 *
 * **Sincronizado con el modo RSVP**: la página mostrada no es un número local independiente,
 * sino que se deriva de [ReadingUiState.wordIndex] (la misma posición que usa el motor RSVP) a
 * través de [pagination] -- calculada una sola vez en [ReadingScreen] y compartida con RSVP, para
 * que "cuántas páginas tiene el capítulo" sea el mismo número se mire desde donde se mire. Al
 * pasar de página se actualiza esa misma palabra en el ViewModel (`setWordIndexInChapter`), así
 * que si el usuario cambia a RSVP retoma justo donde se quedó, y viceversa.
 */
@Composable
fun NormalReaderContent(
    viewModel: ReadingViewModel,
    uiState: ReadingUiState,
    accentColor: Color,
    pagination: ChapterPagination?,
    pageGeometry: PageGeometry,
    onExit: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    var controlsVisible by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // Capítulo para el que, en cuanto se calculen sus páginas, hay que aterrizar en la última
    // (al retroceder desde el principio del capítulo siguiente) en vez de en la primera.
    var landOnLastPageForChapter by remember { mutableStateOf<Int?>(null) }
    var forward by remember { mutableStateOf(true) }
    // Selección de palabra/frase activa (índices de palabra dentro del CAPÍTULO, no de la
    // página): se calcula ya en coordenadas de capítulo en el propio callback de selección, así
    // esta barra no depende de en qué página se originó y sobrevive a un cambio de página.
    var activeSelection by remember { mutableStateOf<ActiveWordSelection?>(null) }
    var dictionaryWord by remember { mutableStateOf<String?>(null) }
    var zoomImagePath by remember { mutableStateOf<String?>(null) }
    var showReferenceSheet by remember { mutableStateOf(false) }
    var openReferenceId by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val swipeThresholdPx = with(density) { 64.dp.toPx() }

    if (pagination == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val safePages = pagination.pages
    val pageIndex = pagination.pageIndex
    val pageStartWord = pagination.pageWordOffsets[pageIndex]
    val pageEndWord = pagination.pageWordOffsets.getOrElse(pageIndex + 1) { Int.MAX_VALUE }

    // Leídos: la última página del último capítulo cuenta como "libro terminado".
    LaunchedEffect(pageIndex, safePages.size, uiState.chapterIndex, uiState.chapters.size) {
        if (safePages.isNotEmpty() && pageIndex == safePages.lastIndex && uiState.chapterIndex == uiState.chapters.lastIndex) {
            viewModel.onReachedBookEnd()
        }
    }
    val isBookmarked = uiState.bookmarks.any { it.chapterIndex == uiState.chapterIndex && it.wordIndex in pageStartWord until pageEndWord }

    // Si veníamos de retroceder desde el capítulo siguiente, en cuanto este capítulo tenga
    // sus páginas calculadas, aterrizamos en la última en vez de en la primera.
    LaunchedEffect(safePages, landOnLastPageForChapter, uiState.chapterIndex) {
        if (landOnLastPageForChapter == uiState.chapterIndex && safePages.isNotEmpty()) {
            landOnLastPageForChapter = null
            viewModel.setWordIndexInChapter(pagination.wordOffsetForPage(safePages.lastIndex))
        }
    }

    fun goNext() {
        when {
            pageIndex < safePages.lastIndex -> {
                forward = true
                viewModel.setWordIndexInChapter(pagination.wordOffsetForPage(pageIndex + 1))
            }
            uiState.chapterIndex < uiState.chapters.lastIndex -> {
                forward = true
                viewModel.jumpToChapter(uiState.chapterIndex + 1)
            }
        }
    }

    fun goPrev() {
        when {
            pageIndex > 0 -> {
                forward = false
                viewModel.setWordIndexInChapter(pagination.wordOffsetForPage(pageIndex - 1))
            }
            uiState.chapterIndex > 0 -> {
                forward = false
                landOnLastPageForChapter = uiState.chapterIndex - 1
                viewModel.jumpToChapter(uiState.chapterIndex - 1)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // OJO: la clave de pointerInput no puede ser Unit aquí. goNext()/goPrev() cierran
            // sobre pageIndex/safePages/uiState.chapterIndex, valores locales que cambian en
            // cada recomposición -- con clave Unit, el detector de gestos se instala una sola
            // vez y sigue usando esa closure "congelada" para siempre, dejando de avanzar tras
            // las primeras páginas. Al incluir estos valores como claves, el detector se
            // reinstala con datos frescos cada vez que cambian.
            .pointerInput(pageIndex, safePages, uiState.chapterIndex, uiState.chapters.size, activeSelection != null) {
                detectTapGestures { offset ->
                    // Con una selección activa, tocar fuera solo la cancela (como en cualquier
                    // selección de texto): no pasa de página ni abre los controles.
                    if (activeSelection != null) { activeSelection = null; return@detectTapGestures }
                    val third = size.width / 3f
                    when {
                        offset.x < third -> goPrev()
                        offset.x > third * 2f -> goNext()
                        else -> controlsVisible = !controlsVisible
                    }
                }
            }
            // Deslizar horizontalmente también pasa página -- pedido explícito, además del toque
            // en los laterales de siempre (que se deja intacto). Un detector de gesto aparte
            // (igual que el de mantener pulsado + arrastrar en RSVP): solo se dispara al superar
            // el umbral de deslizamiento real, así que no compite con el toque simple de arriba.
            .pointerInput(pageIndex, safePages, uiState.chapterIndex, uiState.chapters.size) {
                var accum = 0f
                val threshold = swipeThresholdPx
                detectHorizontalDragGestures(
                    onDragStart = { accum = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accum += dragAmount
                    },
                    onDragEnd = {
                        // Derecha a izquierda (accum negativo) -> avanza página; izquierda a
                        // derecha (accum positivo) -> retrocede -- igual que pasar las páginas
                        // de un libro de verdad.
                        if (accum <= -threshold) goNext() else if (accum >= threshold) goPrev()
                    },
                    onDragCancel = { accum = 0f }
                )
            }
    ) {
        AnimatedContent(
            targetState = uiState.chapterIndex to pageIndex,
            transitionSpec = {
                if (forward) {
                    (slideInHorizontally(tween(220)) { w -> w } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { w -> -w } + fadeOut(tween(220)))
                } else {
                    (slideInHorizontally(tween(220)) { w -> -w } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { w -> w } + fadeOut(tween(220)))
                }
            },
            label = "page-turn",
            modifier = Modifier.fillMaxSize()
        ) { (_, pIdx) ->
            val pageParagraphs = safePages.getOrNull(pIdx).orEmpty()
            val fontSize = (19 * uiState.textScale).sp
            val lineHeight = (30 * uiState.textScale).sp
            val style = TextStyle(
                fontFamily = uiState.fontOption.fontFamily,
                fontSize = fontSize,
                lineHeight = lineHeight,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Recuento de palabras por párrafo de ESTA página, y dónde empieza cada uno dentro
            // del capítulo -- necesario para traducir un toque local (párrafo + palabra dentro
            // de él) al índice de palabra global del capítulo que usa el motor RSVP, y para
            // saber qué trozo de los subrayados ya guardados cae dentro de cada párrafo.
            val paragraphWordCounts = remember(pageParagraphs) { pageParagraphs.map { wordSpans(it).size } }
            val paragraphWordStarts = remember(paragraphWordCounts) {
                val offsets = IntArray(paragraphWordCounts.size)
                var running = 0
                for (i in paragraphWordCounts.indices) {
                    offsets[i] = running
                    running += paragraphWordCounts[i]
                }
                offsets
            }
            val pageStartWordIndex = pagination.wordOffsetForPage(pIdx)
            val chapterHighlights = remember(uiState.highlights, uiState.chapterIndex) {
                uiState.highlights.filter { it.chapterIndex == uiState.chapterIndex }
            }

            // El texto del capítulo empezaba demasiado arriba y se pisaba con la etiqueta de
            // "Capítulo X · Y%" de encima -- pedido explícito: "se pisa en el modo de lectura
            // normal se pisa con las letras el texto, entonces simplemente súbelo un poco para
            // arriba". Se sube la etiqueta (menos padding propio, más cerca de la barra de
            // estado) y se baja el inicio del texto, para que quede claramente separado en vez
            // de depender de que ambos "encajen" por poco en cada dispositivo.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 88.dp, bottom = 86.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                pageParagraphs.forEachIndexed { paraIdx, paragraph ->
                    val imagePath = com.readriffle.app.data.tokenizer.ImageMarker.pathOf(paragraph)
                    if (imagePath != null) {
                        PageImageBlock(imagePath, Modifier.weight(1f).fillMaxWidth()) { zoomImagePath = imagePath }
                        return@forEachIndexed
                    }
                    val paraStart = pageStartWordIndex + paragraphWordStarts[paraIdx]
                    val paraEnd = paraStart + paragraphWordCounts[paraIdx]
                    val highlightedRanges = chapterHighlights.mapNotNull { h ->
                        val s = maxOf(h.startWordIndex, paraStart)
                        val e = minOf(h.endWordIndex + 1, paraEnd)
                        if (s < e) (s - paraStart)..(e - paraStart - 1) else null
                    }
                    // "Dónde nos hemos quedado": la palabra en uiState.wordIndex, si cae en este
                    // párrafo -- pedido explícito para que al pasar de RSVP a modo normal se vea
                    // enseguida, resaltada, la palabra exacta por la que se iba, con el mismo
                    // color de acento que resalta la palabra en RSVP.
                    val currentPositionWordIndex = uiState.wordIndex
                        .takeIf { it >= paraStart && it < paraEnd }
                        ?.let { it - paraStart }
                    // La selección ya hecha (mientras se elige qué hacer con ella) sigue marcada.
                    val pending = activeSelection?.let { sel ->
                        val s0 = maxOf(sel.startWordIndex, paraStart)
                        val e0 = minOf(sel.endWordIndex, paraEnd - 1)
                        if (s0 <= e0) (s0 - paraStart)..(e0 - paraStart) else null
                    }
                    SelectableParagraphText(
                        paragraph = paragraph,
                        style = style,
                        currentPositionWordIndex = currentPositionWordIndex,
                        accentColor = accentColor,
                        highlightedRanges = highlightedRanges,
                        pendingRange = pending,
                        onSelectionFinished = { start, end, text, anchor ->
                            activeSelection = ActiveWordSelection(
                                startWordIndex = paraStart + start,
                                endWordIndex = paraStart + end,
                                text = text,
                                anchor = anchor
                            )
                        }
                    )
                }
            }
        }

        val pageFraction = if (safePages.size <= 1) 1f else (pageIndex + 1).toFloat() / safePages.size.toFloat()
        androidx.compose.animation.AnimatedVisibility(
            visible = !controlsVisible,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) { Text(
            text = stringResource(
                R.string.reading_chapter_progress_format,
                uiState.chapterIndex + 1,
                (pageFraction * 100).roundToInt()
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 6.dp)
        ) }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                ReadingTopBar(
                    isBookmarked = isBookmarked,
                    onExit = { viewModel.pause(); onExit() },
                    onToggleBookmark = {
                        viewModel.toggleBookmark(pageStartWord, pageEndWord, pageIndex + 1, safePages.size)
                    },
                    onOpenIndex = viewModel::openChapterIndex,
                    onOpenSettings = { showSettings = true },
                    menuActions = listOf(
                        ChromeMenuAction(R.drawable.ic_note_simple, stringResource(R.string.reading_add_note), viewModel::openNoteComposer),
                        ChromeMenuAction(R.drawable.ic_reference_page, stringResource(R.string.reference_open)) { showReferenceSheet = true },
                        ChromeMenuAction(R.drawable.ic_lock, "Bloquear con la portada") { viewModel.lockCover() }
                    ),
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                // Selector de modo (Libro/RSVP/Escuchar) + barra de progreso en páginas: solo
                // visibles aquí, al tocar el centro para revelar los controles.
                ReadingBottomPanel(
                    readingMode = uiState.readingMode,
                    onSelectMode = viewModel::setReadingMode,
                    onOpenPlayer = onOpenPlayer,
                    currentPage = pageIndex,
                    totalPages = pagination.totalPages,
                    onSeekPage = { page -> viewModel.setWordIndexInChapter(pagination.wordOffsetForPage(page)) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        if (showSettings) {
            val isDarkNow = uiState.themeMode == com.readriffle.app.theme.ThemeMode.DARK ||
                (uiState.themeMode == com.readriffle.app.theme.ThemeMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme())
            ReadingSettingsSheet(
                textScale = uiState.textScale, zoomPresets = viewModel.zoomPresets, onSetTextScale = viewModel::setTextScale,
                fontOption = uiState.fontOption, onSelectFont = viewModel::setFont,
                themeMode = uiState.themeMode, themeVariant = uiState.themeVariant, onSetTheme = viewModel::setThemeChoice,
                isDark = isDarkNow,
                rsvpBackground = null, onSetRsvpBackground = {},
                onDismiss = { showSettings = false }
            )
        }

        AnimatedVisibility(
            visible = activeSelection != null,
            enter = fadeIn(tween(140)) + androidx.compose.animation.scaleIn(tween(160), initialScale = 0.92f),
            exit = fadeOut(tween(100)),
            modifier = Modifier.fillMaxSize()
        ) {
            val selection = activeSelection
            if (selection != null) {
                WordSelectionActionBar(
                    anchor = selection.anchor,
                    onReadFromHere = {
                        viewModel.startRsvpFromWord(selection.startWordIndex)
                        activeSelection = null
                    },
                    onHighlight = {
                        viewModel.addHighlight(
                            selection.startWordIndex,
                            selection.endWordIndex,
                            selection.text,
                            pageNumber = pageIndex + 1,
                            totalPages = safePages.size
                        )
                        activeSelection = null
                    },
                    onGenerateImage = {
                        viewModel.generateImage(
                            prompt = selection.text,
                            pageNumber = pageIndex + 1,
                            totalPages = safePages.size
                        )
                        activeSelection = null
                    },
                    onDictionary = {
                        dictionaryWord = selection.text
                        activeSelection = null
                    },
                    onDismiss = { activeSelection = null }
                )
            }
        }

        if (isBookmarked && !controlsVisible) {
            Icon(
                painter = painterResource(R.drawable.ic_bookmark_filled),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 22.dp).size(28.dp)
            )
        }

        if (showReferenceSheet) {
            ReferencePagesSheet(
                bookTitle = uiState.bookTitle,
                currentPage = pageIndex + 1,
                totalPages = safePages.size,
                pages = uiState.pinnedPages,
                onPinCurrent = { name ->
                    viewModel.pinCurrentPage(name, pageStartWord, pageIndex + 1, safePages.size)
                },
                onOpen = { page ->
                    openReferenceId = page.id
                    showReferenceSheet = false
                },
                onAddImage = { name, bytes -> viewModel.addReferenceImage(bytes, name) },
                onRemove = viewModel::removePinnedPage,
                onDismiss = { showReferenceSheet = false }
            )
        }
        openReferenceId?.let { id ->
            ReferencePageWindow(
                pages = uiState.pinnedPages,
                initialId = id,
                chapters = uiState.chapters,
                geometry = pageGeometry,
                onDismiss = { openReferenceId = null }
            )
        }

        zoomImagePath?.let { ImageZoomDialog(it) { zoomImagePath = null } }

        dictionaryWord?.let { DictionaryDialog(word = it, onDismiss = { dictionaryWord = null }) }

        // Generar imagen con IA (Gemini) a partir de la descripción seleccionada -- pedido
        // explícito del usuario. Tres estados posibles, mutuamente excluyentes: cargando,
        // resultado para confirmar/descartar, o error.
        if (uiState.isGeneratingImage) {
            ImageGenerationLoadingDialog()
        }
        uiState.generatedImagePreview?.let { preview ->
            GeneratedImageResultDialog(
                preview = preview,
                onSave = viewModel::confirmSaveGeneratedImage,
                onDiscard = viewModel::discardGeneratedImage
            )
        }
        uiState.imageGenerationError?.let { message ->
            ImageGenerationErrorDialog(
                message = message,
                onDismiss = viewModel::dismissImageGenerationError
            )
        }

        if (uiState.showNoteComposer) {
            NoteComposerSheet(
                quotedText = uiState.noteComposerTargetHighlight?.text,
                initialText = uiState.noteComposerTargetHighlight?.note.orEmpty(),
                onSave = { text ->
                    viewModel.saveNoteComposer(
                        text = text,
                        currentPage = pageIndex + 1,
                        totalPages = safePages.size
                    )
                },
                onDismiss = viewModel::closeNoteComposer
            )
        }
    }
}

/** Selección de palabra/frase activa, en índices de palabra del capítulo (no de la página). */
private data class ActiveWordSelection(
    val startWordIndex: Int,
    val endWordIndex: Int,
    val text: String,
    val anchor: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect.Zero
)
