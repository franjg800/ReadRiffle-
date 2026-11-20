package com.readriffle.app.ui.reading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import com.readriffle.app.R
import com.readriffle.app.theme.RsvpBackgroundMode
import com.readriffle.app.ui.common.formatRemainingTime
import com.readriffle.app.viewmodel.ReadingUiState
import com.readriffle.app.viewmodel.ReadingViewModel
import kotlin.math.roundToInt

/**
 * El propio modo RSVP: dos capas (el texto del capítulo fluyendo muy tenue de fondo, y la
 * palabra actual fija en el centro) y control casi 100% gestual.
 *
 * Gestos:
 *  - Modo "Automático" (por defecto): un toque pausa/reanuda; el texto de fondo fluye siempre
 *    que se está reproduciendo.
 *  - Modo "Solo al mantener pulsado": mantener pulsado hace avanzar la palabra (y el fondo con
 *    ella) mientras se sostiene, y soltar detiene la reproducción -- un control de "leer
 *    mientras aprietas", no una animación decorativa. (Corregido: antes este ajuste solo movía
 *    el texto de fondo sin afectar a la palabra RSVP real, que es justo lo contrario de lo que
 *    se pidió.)
 *  - Deslizar horizontal sobre la palabra, en pausa -> cambia de tipografía.
 *  - En pausa aparecen los botones flotantes, incluida la barra vertical de velocidad (ver
 *    [ReadingControls]) -- se ajusta ahí en vez de deslizando sobre la pantalla de lectura,
 *    porque ese gesto competía con el toque para pausar/reanudar y resultaba poco fiable.
 */
@Composable
fun RsvpReaderContent(
    viewModel: ReadingViewModel,
    uiState: ReadingUiState,
    accentColor: Color,
    isDark: Boolean,
    pagination: ChapterPagination?,
    onExit: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val density = LocalDensity.current

    // El fondo simplemente sigue la reproducción real -- ya no tiene un modo "solo al
    // mantener pulsado" propio: mantener pulsado ahora controla la palabra RSVP (ver más abajo),
    // y el fondo la acompaña siempre que se está reproduciendo, sea como sea que se haya iniciado.
    val backgroundActive = uiState.isPlaying
    val backgroundAlpha = if (uiState.isPlaying) 0.16f else 0.08f
    val holdToRead = !uiState.backgroundMotionAuto

    val swipeThresholdPx = with(density) { 56.dp.toPx() }
    var forwardFlash by remember { mutableIntStateOf(0) }
    var backFlash by remember { mutableIntStateOf(0) }
    var wpmFlash by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Capa de fondo: elegible en Ajustes (ver RsvpBackgroundMode) -- el texto fluyendo de
        // siempre, pantalla negra con dos líneas marcando la palabra, o pantalla blanca (mismo
        // planteamiento, invertido).
        when (uiState.rsvpBackground) {
            RsvpBackgroundMode.FLOWING -> FlowingBackgroundText(
                text = remember(uiState.chapterIndex, uiState.chapters) {
                    com.readriffle.app.data.tokenizer.ImageMarker.strip(uiState.chapters.getOrNull(uiState.chapterIndex)?.text.orEmpty())
                },
                fontFamily = uiState.fontOption.fontFamily,
                active = backgroundActive,
                wpm = uiState.wpm,
                alpha = backgroundAlpha,
                modifier = Modifier.fillMaxSize()
            )
            RsvpBackgroundMode.BLACK_LINES -> BlackLinesBackground(
                lineColor = accentColor,
                textScale = uiState.textScale,
                modifier = Modifier.fillMaxSize()
            )
            RsvpBackgroundMode.WHITE_SCREEN -> WhiteBackground(
                lineColor = accentColor,
                textScale = uiState.textScale,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Capa de gestos. Tocar el tercio central pausa/reanuda (al instante, sin botón de play);
        // doble toque en el tercio derecho avanza palabras y en el izquierdo retrocede, con un
        // símbolo que aparece un momento; deslizar en vertical por los laterales cambia la
        // velocidad. En modo "Solo al mantener pulsado", mantener pulsado el CENTRO hace avanzar
        // la palabra (play() al presionar, pause() al soltar); los laterales funcionan igual en
        // ambos modos y nunca ponen el texto en marcha.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rsvpGestures(
                    holdToRead = holdToRead,
                    onCenterTap = { viewModel.togglePlayPause() },
                    onDoubleTapSide = { side ->
                        if (side > 0) { viewModel.skipWordsForward(); forwardFlash++ }
                        else { viewModel.skipWordsBackward(); backFlash++ }
                    },
                    onHoldingChange = { holding -> if (holding) viewModel.play() else viewModel.pause() },
                    currentWpm = { uiState.wpm },
                    onWpmDrag = { newWpm -> viewModel.setWpm(newWpm); wpmFlash++ }
                )
        )

        // Velocidad actual, visible un momento mientras se ajusta deslizando por los bordes.
        WpmFlash(trigger = wpmFlash, wpm = uiState.wpm, accentColor = accentColor, modifier = Modifier.align(Alignment.Center).offset(y = (-120).dp))

        // Símbolos momentáneos del doble toque.
        SkipFlash(trigger = backFlash, forward = false, accentColor = accentColor, modifier = Modifier.align(Alignment.CenterStart).padding(start = 36.dp))
        SkipFlash(trigger = forwardFlash, forward = true, accentColor = accentColor, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 36.dp))

        // Progreso del capítulo, discreto arriba. "Pantalla negra"/"Pantalla blanca" ya no
        // fuerzan negro/blanco puro (ver RsvpBackgrounds.kt) -- siguen el tema activo, así que
        // el color normal del tema ya contrasta correctamente en los tres fondos, sin casos
        // especiales.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.reading_chapter_progress_format,
                    uiState.chapterIndex + 1,
                    (uiState.chapterProgressFraction * 100).roundToInt()
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            // Tiempo restante de capítulo y de libro a la velocidad (PPM) actual -- recalculado
            // solo (sin volver a tokenizar nada) en cada composición, así que al arrastrar el
            // slider de velocidad del menú de pausa esto se actualiza en tiempo real, igual que
            // el "x2" de un audiolibro. Pedido explícito del usuario.
            Text(
                text = stringResource(
                    R.string.reading_time_remaining_chapter_book_format,
                    formatRemainingTime(uiState.chapterMinutesRemaining),
                    formatRemainingTime(uiState.bookMinutesRemaining)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        // Palabra RSVP central, fija; si está en pausa, deslizar horizontal cambia de tipografía.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .then(
                    if (!uiState.isPlaying) {
                        Modifier.pointerInputHorizontalDrag(
                            onDragEnd = { accum ->
                                if (kotlin.math.abs(accum) > swipeThresholdPx) {
                                    viewModel.cycleFont(forward = accum < 0)
                                }
                            }
                        )
                    } else Modifier
                )
        ) {
            // Solo en "Texto fluido": una franja opaca + las mismas dos líneas de marca que
            // usan los otros fondos, para que el texto fluyendo detrás no se vea justo bajo la
            // palabra fija ("marea un poco") -- pedido explícito del usuario.
            if (uiState.rsvpBackground == RsvpBackgroundMode.FLOWING) {
                FlowingWordFocusBackdrop(
                    lineColor = accentColor,
                    textScale = uiState.textScale,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                )
            }
            // OJO: sin este `.align(Alignment.Center)` explícito, la palabra se quedaba pegada
            // arriba del todo de este Box en vez de centrada entre las dos líneas ("la palabra
            // no está centrada, está como desplazada hacia arriba") -- un `Box` sin
            // `contentAlignment` propio alinea cada hijo a `TopStart` salvo que el hijo indique
            // lo contrario, y la franja de encima sí lo hacía (`Alignment.Center`) pero esta
            // palabra no.
            RsvpWordDisplay(
                word = uiState.currentWord,
                fontFamily = uiState.fontOption.fontFamily,
                accentColor = accentColor,
                textScale = uiState.textScale,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = !uiState.isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            ReadingControls(
                onExit = {
                    viewModel.pause()
                    onExit()
                },
                onLock = viewModel::lockCover,
                onOpenIndex = viewModel::openChapterIndex,
                onOpenPlayer = onOpenPlayer,
                textScale = uiState.textScale,
                zoomPresets = viewModel.zoomPresets,
                onSetTextScale = viewModel::setTextScale,
                fontOption = uiState.fontOption,
                onSelectFont = viewModel::setFont,
                themeMode = uiState.themeMode,
                themeVariant = uiState.themeVariant,
                onSetTheme = viewModel::setThemeChoice,
                isDark = isDark,
                rsvpBackground = uiState.rsvpBackground,
                onSetRsvpBackground = viewModel::setRsvpBackground,
                onAddNote = viewModel::openNoteComposer,
                accentColor = accentColor,
                readingMode = uiState.readingMode,
                onSelectMode = viewModel::setReadingMode,
                currentPage = pagination?.pageIndex ?: 0,
                totalPages = pagination?.totalPages ?: 1,
                onSeekPage = { page ->
                    pagination?.let { viewModel.setWordIndexInChapter(it.wordOffsetForPage(page)) }
                },
                showNextChapterButton = uiState.isChapterFinished && uiState.chapterIndex < uiState.chapters.lastIndex,
                onNextChapter = { viewModel.jumpToChapter(uiState.chapterIndex + 1) }
            )
        }

        if (uiState.showNoteComposer) {
            NoteComposerSheet(
                quotedText = uiState.noteComposerTargetHighlight?.text,
                initialText = uiState.noteComposerTargetHighlight?.note.orEmpty(),
                onSave = { text ->
                    viewModel.saveNoteComposer(
                        text = text,
                        currentPage = (pagination?.pageIndex ?: 0) + 1,
                        totalPages = pagination?.totalPages ?: 1
                    )
                },
                onDismiss = viewModel::closeNoteComposer
            )
        }
    }
}

/**
 * Gestos de la capa de lectura. Detector propio (en vez de `detectTapGestures`, que retrasa el
 * toque simple ~300ms para esperar un posible doble toque): el centro responde al instante y el
 * doble toque solo se vigila en los laterales, donde un toque simple no hace nada.
 * [onDoubleTapSide] recibe -1 (izquierda) o +1 (derecha).
 */
private fun Modifier.rsvpGestures(
    holdToRead: Boolean,
    onCenterTap: () -> Unit,
    onDoubleTapSide: (Int) -> Unit,
    onHoldingChange: (Boolean) -> Unit,
    currentWpm: () -> Int,
    onWpmDrag: (Int) -> Unit
): Modifier = this.then(
    Modifier.pointerInput(holdToRead) {
        val doubleTapWindowMs = 300L
        // Deslizar vertical por los laterales: cada 10dp = 5 ppm (arriba sube, abajo baja).
        val stepPx = 10.dp.toPx()
        var lastSideTapTime = 0L
        var lastSideTapSide = 0
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val side = when {
                down.position.x < size.width / 3f -> -1
                down.position.x > size.width * 2f / 3f -> 1
                else -> 0
            }
            // "Mantener pulsado para leer" solo vale en el tercio central: los laterales son para
            // el doble toque y el deslizamiento de velocidad, y no deben poner el texto en marcha
            // (si no, el menú desaparecía y la lectura seguía avanzando al ajustar).
            val holding = holdToRead && side == 0
            if (holding) onHoldingChange(true)
            var dragging = false
            var startWpm = 0
            var up: androidx.compose.ui.input.pointer.PointerInputChange? = null
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!dragging && change.isConsumed) break
                    if (!change.pressed) { up = change; break }
                    val dy = change.position.y - down.position.y
                    val dx = change.position.x - down.position.x
                    if (!dragging && side != 0 && kotlin.math.abs(dy) > viewConfiguration.touchSlop && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                        dragging = true
                        startWpm = currentWpm()
                    }
                    if (dragging) {
                        change.consume()
                        val steps = (-dy / stepPx).toInt()
                        onWpmDrag(startWpm + steps * 5)
                    }
                }
            } finally {
                if (holding) onHoldingChange(false)
            }
            if (dragging) { lastSideTapTime = 0L; return@awaitEachGesture }
            if (up == null || holding) return@awaitEachGesture
            if (side == 0) {
                lastSideTapTime = 0L
                onCenterTap()
            } else if (side == lastSideTapSide && up.uptimeMillis - lastSideTapTime <= doubleTapWindowMs) {
                lastSideTapTime = 0L
                onDoubleTapSide(side)
            } else {
                lastSideTapTime = up.uptimeMillis
                lastSideTapSide = side
            }
        }
    }
)

/** Número de ppm que aparece al ajustar la velocidad y se desvanece poco después de soltar. */
@Composable
private fun WpmFlash(trigger: Int, wpm: Int, accentColor: Color, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        visible = true
        delay(900)
        visible = false
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(80)), exit = fadeOut(tween(300)), modifier = modifier) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = wpm.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = accentColor)
            Text(text = "ppm", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/** Símbolo de avance/retroceso que aparece ~0,6 s tras un doble toque y se desvanece. */
@Composable
private fun SkipFlash(trigger: Int, forward: Boolean, accentColor: Color, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        visible = true
        delay(600)
        visible = false
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(80)), exit = fadeOut(tween(260)), modifier = modifier) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(if (forward) R.drawable.ic_skip_forward_words else R.drawable.ic_skip_back_words),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun Modifier.pointerInputHorizontalDrag(onDragEnd: (Float) -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        var accum = 0f
        detectHorizontalDragGestures(
            onDragStart = { accum = 0f },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                accum += dragAmount
            },
            onDragEnd = { onDragEnd(accum) },
            onDragCancel = { accum = 0f }
        )
    }
)
