package com.readriffle.app.viewmodel

import com.readriffle.app.platform.System
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.readriffle.app.AppContainer
import com.readriffle.app.data.ai.GeminiImageClient
import com.readriffle.app.data.ai.GeminiImageException
import com.readriffle.app.data.model.BookmarkMeta
import com.readriffle.app.data.model.Chapter
import com.readriffle.app.data.model.PinnedPageMeta
import com.readriffle.app.data.model.GeneratedImageMeta
import com.readriffle.app.data.model.HighlightMeta
import com.readriffle.app.data.model.NoteMeta
import com.readriffle.app.data.model.ReadingMode
import com.readriffle.app.data.model.RsvpToken
import com.readriffle.app.data.preferences.UserPreferencesRepository
import com.readriffle.app.data.repository.LibraryRepository
import com.readriffle.app.data.tokenizer.WordTokenizer
import com.readriffle.app.theme.AccentColorOption
import com.readriffle.app.theme.ReaderFontOption
import com.readriffle.app.theme.RsvpBackgroundMode
import com.readriffle.app.theme.ThemeMode
import com.readriffle.app.theme.ThemeVariant
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

data class ReadingUiState(
    val isLoading: Boolean = true,
    val bookTitle: String = "",
    val bookAuthor: String = "",
    val chapters: List<Chapter> = emptyList(),
    val chapterWordCounts: List<Int> = emptyList(),
    val chapterIndex: Int = 0,
    val tokens: List<RsvpToken> = emptyList(),
    val wordIndex: Int = 0,
    val currentWord: String = "",
    val isPlaying: Boolean = false,
    val readingMode: ReadingMode = ReadingMode.NORMAL,
    val wpm: Int = 300,
    val wpmMin: Int = 150,
    val wpmMax: Int = 600,
    val slowdownIntensity: Float = 0.5f,
    val paragraphPauseMs: Int = 750,
    val commaPauseMs: Int = 120,
    val sentenceEndPauseMs: Int = 260,
    val backgroundMotionAuto: Boolean = true,
    val fontOption: ReaderFontOption = ReaderFontOption.LITERATA,
    val lightAccentOption: AccentColorOption = AccentColorOption.AMBER,
    val darkAccentOption: AccentColorOption = AccentColorOption.SKY,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeVariant: ThemeVariant = ThemeVariant.STANDARD,
    val rsvpBackground: RsvpBackgroundMode = RsvpBackgroundMode.FLOWING,
    val textScale: Float = 1f,
    /** Cuántas palabras saltan los botones de retroceder/adelantar en RSVP (ver Ajustes). */
    val wordSkipAmount: Int = 5,
    val showChapterIndex: Boolean = false,
    val highlights: List<HighlightMeta> = emptyList(),
    val notes: List<NoteMeta> = emptyList(),
    val bookmarks: List<BookmarkMeta> = emptyList(),
    val pinnedPages: List<PinnedPageMeta> = emptyList(),
    val showNoteComposer: Boolean = false,
    // Cuando la nota que se está redactando va a citar un subrayado concreto en vez de crearse
    // suelta (ver [ReadingViewModel.openNoteComposerForHighlight]).
    val noteComposerTargetHighlight: HighlightMeta? = null,
    /** API key de Gemini guardada en Ajustes (ver [GeminiImageClient]). Vacía = sin configurar. */
    val geminiApiKey: String = "",
    val isGeneratingImage: Boolean = false,
    val generatedImagePreview: GeneratedImagePreview? = null,
    val imageGenerationError: String? = null,
    /** Ya está en Leídos (no se vuelve a preguntar al llegar al final). */
    val alreadyFinished: Boolean = false,
    /** Mostrar la hoja "¿Lo has terminado?" (se llegó al final del libro). */
    val showFinishPrompt: Boolean = false
) {
    val chapterProgressFraction: Float
        get() = if (tokens.isEmpty()) 0f else (wordIndex.coerceAtMost(tokens.size)).toFloat() / tokens.size.toFloat()

    val chapterTitle: String
        get() = chapters.getOrNull(chapterIndex)?.title ?: ""

    /** Progreso del libro entero (no solo del capítulo actual), usado por la barra arrastrable. */
    val overallProgressFraction: Float
        get() {
            if (chapterWordCounts.isEmpty()) return 0f
            val total = chapterWordCounts.sum().coerceAtLeast(1)
            val before = chapterWordCounts.take(chapterIndex).sum()
            return ((before + wordIndex).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

    /**
     * True cuando la reproducción RSVP ha llegado al final del capítulo actual (se ha quedado
     * "bloqueada" ahí, sin poder avanzar palabra a palabra). Se usa para mostrar el botón de
     * "Siguiente capítulo" solo en ese momento, en vez de auto-avanzar de capítulo.
     */
    val isChapterFinished: Boolean
        get() = tokens.isNotEmpty() && wordIndex >= tokens.size

    /**
     * Palabras que quedan por leer del capítulo actual y del libro completo, a partir de
     * [chapterWordCounts] (ya calculado una vez al cargar el libro) y la posición actual --
     * pedido explícito: mostrar el tiempo restante de capítulo y de libro en la pantalla RSVP y
     * en la Biblioteca, igual que el "tiempo restante" de un audiolibro.
     */
    val chapterWordsRemaining: Int
        get() = (tokens.size - wordIndex).coerceAtLeast(0)

    val bookWordsRemaining: Int
        get() {
            if (chapterWordCounts.isEmpty()) return 0
            val remainingFutureChapters = chapterWordCounts.drop(chapterIndex + 1).sum()
            return remainingFutureChapters + chapterWordsRemaining
        }

    /**
     * Minutos restantes a la velocidad (WPM) ACTUAL -- se deriva de [wpm], que cambia en caliente
     * con cada gesto del slider (ver [ReadingViewModel.setWpm]), así que estos minutos se
     * recalculan solos en tiempo real al arrastrar, igual que el "x2" de un audiolibro.
     * `ceil` en vez de redondear para no mostrar nunca "0 min" mientras quede aunque sea una sola
     * palabra.
     */
    val chapterMinutesRemaining: Int
        get() = if (wpm <= 0 || chapterWordsRemaining == 0) 0 else ceil(chapterWordsRemaining.toFloat() / wpm.toFloat()).toInt()

    val bookMinutesRemaining: Int
        get() = if (wpm <= 0 || bookWordsRemaining == 0) 0 else ceil(bookWordsRemaining.toFloat() / wpm.toFloat()).toInt()
}

/**
 * Una imagen ya generada por Gemini pero todavía sin guardar: se muestra en un diálogo con
 * "Guardar"/"Descartar" (ver [ReadingViewModel.confirmSaveGeneratedImage]/[ReadingViewModel.discardGeneratedImage])
 * -- así no queda guardada en Notas si el usuario no la quiere, pero el archivo ya existe en
 * disco desde que Gemini responde (para no tener que volver a decodificar el base64 al guardar).
 */
data class GeneratedImagePreview(
    val imagePath: String,
    val prompt: String,
    val pageNumber: Int,
    val totalPages: Int
)

class ReadingViewModel(
    private val bookId: String,
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val geminiImageClient: GeminiImageClient = GeminiImageClient(),
    private val statsRepository: com.readriffle.app.data.stats.StatsRepository? = null,
    private val coverLock: com.readriffle.app.data.lock.CoverLockController? = null,
    // Posición de partida explícita (capítulo + palabra), cuando se abre el libro desde la
    // pantalla de Notas para saltar a un subrayado/nota concreto en vez de retomar por donde se
    // quedó la lectura la última vez. -1 = no hay salto pendiente, usar la última posición.
    private val initialChapterIndex: Int = -1,
    private val initialWordIndex: Int = -1,
    private val readingLog: com.readriffle.app.data.ratings.ReadingLog? = null
) : ViewModel() {

    /** Solo se pregunta una vez por sesión de lectura (si se dice "Ahora no", no insiste). */
    private var finishPromptShown = false

    /** Se ha llegado a la última página / última palabra del libro. */
    fun onReachedBookEnd() {
        val st = _uiState.value
        if (st.isLoading || st.alreadyFinished || finishPromptShown || readingLog == null) return
        finishPromptShown = true
        _uiState.update { it.copy(showFinishPrompt = true) }
    }

    fun dismissFinishPrompt() = _uiState.update { it.copy(showFinishPrompt = false) }

    /** Guarda el libro en Leídos con su nota y opinión. */
    fun markFinished(record: com.readriffle.app.data.model.ReadingRecord) {
        _uiState.update { it.copy(showFinishPrompt = false, alreadyFinished = true) }
        saveProgress()
        viewModelScope.launch { readingLog?.save("lib:$bookId", record) }
    }

    private val _uiState = MutableStateFlow(ReadingUiState())
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null
    private var wpmPersistJob: Job? = null
    private var wpmInitialized = false

    // --- Estadísticas de lectura -------------------------------------------------------------
    // El tiempo solo cuenta con la pantalla de lectura visible Y actividad reciente (RSVP en
    // marcha o un cambio de página en los últimos 90 s), para no inflarlo con el móvil sobre la mesa.
    private var screenActive = false
    private var lastInteractionMs = 0L
    private var pendingSeconds = 0
    private var pendingWords = 0
    private val statsScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    fun setScreenActive(active: Boolean) {
        screenActive = active
        if (active) lastInteractionMs = System.currentTimeMillis() else flushStats()
    }

    private fun flushStats() {
        val repo = statsRepository ?: return
        val s = pendingSeconds
        val w = pendingWords
        if (s == 0 && w == 0) return
        pendingSeconds = 0
        pendingWords = 0
        statsScope.launch { repo.add(s, w) }
    }

    private fun startStatsTicker() {
        if (statsRepository == null) return
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                val st = _uiState.value
                val recent = System.currentTimeMillis() - lastInteractionMs < 90_000
                if (screenActive && !st.isLoading && (st.isPlaying || recent)) pendingSeconds += 5
                if (pendingSeconds >= 30) flushStats()
            }
        }
    }

    override fun onCleared() {
        flushStats()
        super.onCleared()
    }

    /** La app pasa a segundo plano (web): guarda ya la posición y el tiempo leído. */
    fun persistNow() {
        if (_uiState.value.isPlaying) {
            playJob?.cancel()
            playJob = null
            _uiState.update { it.copy(isPlaying = false) }
        }
        val state = _uiState.value
        if (!state.isLoading && state.tokens.isNotEmpty()) {
            runCatching {
                libraryRepository.updateProgressNow(bookId, state.chapterIndex, state.wordIndex, state.overallProgressFraction)
            }
        }
        flushStats()
    }

    init {
        startStatsTicker()
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                _uiState.update { state ->
                    val resolvedWpm = if (!wpmInitialized) {
                        wpmInitialized = true
                        prefs.lastWpm.coerceIn(prefs.wpmMin, prefs.wpmMax)
                    } else {
                        state.wpm.coerceIn(prefs.wpmMin, prefs.wpmMax)
                    }
                    state.copy(
                        wpm = resolvedWpm,
                        wpmMin = prefs.wpmMin,
                        wpmMax = prefs.wpmMax,
                        slowdownIntensity = prefs.slowdownIntensity,
                        paragraphPauseMs = prefs.paragraphPauseMs,
                        commaPauseMs = prefs.commaPauseMs,
                        sentenceEndPauseMs = prefs.sentenceEndPauseMs,
                        backgroundMotionAuto = prefs.backgroundMotionAuto,
                        fontOption = ReaderFontOption.fromKey(prefs.fontKey),
                        lightAccentOption = AccentColorOption.fromKey(prefs.lightAccentKey),
                        darkAccentOption = AccentColorOption.fromKey(prefs.darkAccentKey),
                        themeMode = ThemeMode.fromKey(prefs.themeMode),
                        themeVariant = ThemeVariant.fromKey(prefs.themeVariantKey),
                        rsvpBackground = RsvpBackgroundMode.fromKey(prefs.rsvpBackgroundKey),
                        textScale = prefs.textScale,
                        wordSkipAmount = prefs.wordSkipAmount,
                        geminiApiKey = prefs.geminiApiKey
                    )
                }
            }
        }
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            val metaBefore = libraryRepository.loadLibrary().find { it.id == bookId } ?: return@launch
            val book = libraryRepository.loadBookContent(metaBefore)
            // Se vuelve a leer: cargar el libro puede haber trasladado la posición guardada.
            val meta = libraryRepository.loadLibrary().find { it.id == bookId } ?: metaBefore
            val hasExplicitJump = initialChapterIndex >= 0
            val startChapter = (if (hasExplicitJump) initialChapterIndex else meta.lastChapterIndex)
                .coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))
            val tokens = book.chapters.getOrNull(startChapter)?.text?.let(WordTokenizer::tokenize) ?: emptyList()
            val startWord = (if (hasExplicitJump) initialWordIndex else meta.lastWordIndex).coerceIn(0, tokens.size)
            val wordCounts = book.chapters.map { WordTokenizer.tokenize(it.text).size }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    bookTitle = book.title,
                    bookAuthor = book.author,
                    chapters = book.chapters,
                    chapterWordCounts = wordCounts,
                    chapterIndex = startChapter,
                    tokens = tokens,
                    wordIndex = startWord,
                    currentWord = tokens.getOrNull(startWord)?.display ?: tokens.firstOrNull()?.display.orEmpty(),
                    highlights = meta.highlights,
                    notes = meta.notes,
                    bookmarks = meta.bookmarks,
                    pinnedPages = meta.pinnedPages,
                    alreadyFinished = meta.isFinished
                )
            }
            // Al saltar aquí desde una nota/subrayado concreto de la pantalla de Notas, esa
            // posición pasa a ser también "por dónde vas" en el libro (igual que jumpToHighlight).
            if (hasExplicitJump) saveProgress()

            // Relleno perezoso de `BookMeta.totalWordCount` para libros importados antes de que
            // existiera ese campo -- ya se ha calculado `wordCounts` para el propio estado de
            // arriba, así que reutilizarlo aquí no cuesta nada extra (nada de volver a tokenizar).
            val total = wordCounts.sum()
            if (meta.totalWordCount != total) {
                libraryRepository.updateTotalWordCount(bookId, total)
            }
        }
    }

    /**
     * Vuelve a leer de disco la posición guardada y actualiza el estado si ha cambiado. Arregla
     * el fallo de sincronización de un solo sentido: si mientras tanto se ha escuchado el
     * audiolibro desde la pantalla del reproductor, este ViewModel -- que sigue vivo debajo, sin
     * recrearse, mientras esa pantalla está encima -- nunca se enteraba de que la posición había
     * avanzado ("si avanzas en el audio, el RSVP... no avanza. Se desincroniza"). Se llama al
     * volver a esta pantalla. No hace nada si RSVP está reproduciéndose, para no interrumpir una
     * lectura en curso ni pisarla con una posición antigua.
     */
    fun refreshPositionFromDisk() {
        if (_uiState.value.isPlaying) return
        viewModelScope.launch {
            val meta = libraryRepository.loadLibrary().find { it.id == bookId } ?: return@launch
            val state = _uiState.value
            if (meta.lastChapterIndex == state.chapterIndex && meta.lastWordIndex == state.wordIndex) return@launch

            val sameChapter = meta.lastChapterIndex == state.chapterIndex
            val tokens = if (sameChapter) {
                state.tokens
            } else {
                state.chapters.getOrNull(meta.lastChapterIndex)?.text?.let(WordTokenizer::tokenize) ?: return@launch
            }
            val targetWord = meta.lastWordIndex.coerceIn(0, (tokens.size - 1).coerceAtLeast(0))
            _uiState.update {
                it.copy(
                    chapterIndex = meta.lastChapterIndex,
                    tokens = tokens,
                    wordIndex = targetWord,
                    currentWord = tokens.getOrNull(targetWord)?.display.orEmpty(),
                    highlights = meta.highlights
                )
            }
        }
    }

    /** Cubre la app con la portada de este libro (bloqueo con portada). Pausa el RSVP si estaba en marcha. */
    fun lockCover() {
        pause()
        viewModelScope.launch {
            libraryRepository.loadLibrary().find { it.id == bookId }?.let { coverLock?.lock(it) }
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) pause() else play()
    }

    fun play() {
        val state = _uiState.value
        if (state.tokens.isEmpty() || state.wordIndex >= state.tokens.size) return

        _uiState.update { it.copy(isPlaying = true) }
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (isActive) {
                val current = _uiState.value
                val token = current.tokens.getOrNull(current.wordIndex) ?: break
                _uiState.update { it.copy(currentWord = token.display) }

                delay(
                    RsvpTiming.delayMillis(
                        token = token,
                        wpm = current.wpm,
                        intensity = current.slowdownIntensity,
                        paragraphPauseMs = current.paragraphPauseMs,
                        commaPauseMs = current.commaPauseMs,
                        sentenceEndPauseMs = current.sentenceEndPauseMs
                    )
                )

                val nextIndex = current.wordIndex + 1
                if (nextIndex >= current.tokens.size) {
                    _uiState.update { it.copy(wordIndex = current.tokens.size, isPlaying = false) }
                    saveProgress()
                    break
                } else {
                    _uiState.update { it.copy(wordIndex = nextIndex) }
                    pendingWords++
                    if (nextIndex % 15 == 0) saveProgress()
                }
            }
        }
    }

    fun pause() {
        playJob?.cancel()
        playJob = null
        if (_uiState.value.isPlaying) {
            _uiState.update { it.copy(isPlaying = false) }
        }
        saveProgress()
    }

    fun setWpm(newWpm: Int) {
        val state = _uiState.value
        val clamped = newWpm.coerceIn(state.wpmMin, state.wpmMax)
        if (clamped == state.wpm) return
        _uiState.update { it.copy(wpm = clamped) }

        wpmPersistJob?.cancel()
        wpmPersistJob = viewModelScope.launch {
            delay(400)
            preferencesRepository.setLastWpm(clamped)
        }
    }

    fun openChapterIndex() = _uiState.update { it.copy(showChapterIndex = true) }
    fun closeChapterIndex() = _uiState.update { it.copy(showChapterIndex = false) }

    fun jumpToChapter(index: Int) {
        val state = _uiState.value
        val chapter = state.chapters.getOrNull(index) ?: return
        playJob?.cancel()
        playJob = null

        val tokens = WordTokenizer.tokenize(chapter.text)
        _uiState.update {
            it.copy(
                chapterIndex = index,
                tokens = tokens,
                wordIndex = 0,
                currentWord = tokens.firstOrNull()?.display.orEmpty(),
                isPlaying = false,
                showChapterIndex = false
            )
        }
        saveProgress()
    }

    /**
     * Salta a una fracción del libro completo (0f..1f), usado por la barra de progreso
     * arrastrable: recorre el recuento de palabras por capítulo para encontrar a qué
     * capítulo y palabra corresponde esa posición.
     */
    fun seekToOverallFraction(fraction: Float) {
        val state = _uiState.value
        if (state.chapterWordCounts.isEmpty()) return
        val total = state.chapterWordCounts.sum().coerceAtLeast(1)
        val targetOverall = (fraction.coerceIn(0f, 1f) * total).roundToInt().coerceIn(0, total - 1)

        var remaining = targetOverall
        var targetChapter = 0
        for ((idx, count) in state.chapterWordCounts.withIndex()) {
            if (remaining < count || idx == state.chapterWordCounts.lastIndex) {
                targetChapter = idx
                break
            }
            remaining -= count
        }

        playJob?.cancel()
        playJob = null

        val tokens = state.chapters.getOrNull(targetChapter)?.text?.let(WordTokenizer::tokenize) ?: emptyList()
        val targetWordIndex = remaining.coerceIn(0, (tokens.size - 1).coerceAtLeast(0))

        _uiState.update {
            it.copy(
                chapterIndex = targetChapter,
                tokens = tokens,
                wordIndex = targetWordIndex,
                currentWord = tokens.getOrNull(targetWordIndex)?.display.orEmpty(),
                isPlaying = false
            )
        }
        saveProgress()
    }

    /**
     * Actualiza la palabra actual dentro del capítulo en curso, sin cambiar de capítulo ni
     * re-tokenizar -- mantiene sincronizados los modos RSVP y normal: al pasar de página en
     * modo normal se informa aquí la palabra donde ha quedado el usuario, y ese mismo
     * [ReadingUiState.wordIndex] es lo que el modo normal usa para saber en qué página
     * mostrarse al entrar (o al volver desde RSVP).
     */
    fun setWordIndexInChapter(wordIndex: Int) {
        val state = _uiState.value
        val clamped = wordIndex.coerceIn(0, (state.tokens.size - 1).coerceAtLeast(0))
        if (clamped == state.wordIndex) return
        // Estadísticas: avanzar unas palabras (pasar de página, saltar 10 palabras) cuenta como leído.
        val delta = clamped - state.wordIndex
        if (delta in 1..1500) pendingWords += delta
        lastInteractionMs = System.currentTimeMillis()
        playJob?.cancel()
        playJob = null
        _uiState.update {
            it.copy(
                wordIndex = clamped,
                currentWord = it.tokens.getOrNull(clamped)?.display.orEmpty(),
                isPlaying = false
            )
        }
        saveProgress()
    }

    /**
     * Retroceder/adelantar un número fijo de palabras en RSVP -- "lo típico del vídeo de sumar
     * o retroceder 10 segundos, pero con palabras", con la cantidad ajustable en Ajustes (ver
     * [ReadingUiState.wordSkipAmount]). Reutiliza [setWordIndexInChapter], que ya se encarga de
     * acotar el resultado a los límites del capítulo actual (no cruza a capítulos vecinos) y de
     * guardar el progreso.
     */
    fun skipWordsForward() {
        val state = _uiState.value
        setWordIndexInChapter(state.wordIndex + state.wordSkipAmount)
    }

    fun skipWordsBackward() {
        val state = _uiState.value
        setWordIndexInChapter(state.wordIndex - state.wordSkipAmount)
    }

    /**
     * Salta a [wordIndex] dentro del capítulo actual, cambia a modo RSVP y arranca la
     * reproducción de inmediato -- la "nueva habilidad" pedida para no tener que releer desde
     * el principio de la página al cambiar de modo a mitad de lectura: se selecciona una palabra
     * en modo normal (ver [com.readriffle.app.ui.reading.NormalReaderContent]) y se retoma en
     * RSVP justo ahí.
     */
    fun startRsvpFromWord(wordIndex: Int) {
        val state = _uiState.value
        val clamped = wordIndex.coerceIn(0, (state.tokens.size - 1).coerceAtLeast(0))
        playJob?.cancel()
        playJob = null
        _uiState.update {
            it.copy(
                wordIndex = clamped,
                currentWord = it.tokens.getOrNull(clamped)?.display.orEmpty(),
                readingMode = ReadingMode.RSVP,
                isPlaying = false
            )
        }
        saveProgress()
        play()
    }

    /** Resalta el rango de palabras [startWordIndex]..[endWordIndex] (inclusive) del capítulo actual. */
    fun addHighlight(startWordIndex: Int, endWordIndex: Int, text: String, pageNumber: Int = 0, totalPages: Int = 0) {
        val state = _uiState.value
        val highlight = HighlightMeta(
            chapterIndex = state.chapterIndex,
            chapterTitle = state.chapterTitle,
            startWordIndex = startWordIndex,
            endWordIndex = endWordIndex,
            text = text,
            pageNumber = pageNumber,
            totalPages = totalPages
        )
        _uiState.update { it.copy(highlights = it.highlights + highlight) }
        viewModelScope.launch { libraryRepository.addHighlight(bookId, highlight) }
    }

    fun removeHighlight(highlightId: String) {
        _uiState.update { it.copy(highlights = it.highlights.filterNot { h -> h.id == highlightId }) }
        viewModelScope.launch { libraryRepository.removeHighlight(bookId, highlightId) }
    }

    /** Abre el redactor de notas para crear una nota SUELTA, ligada a la posición actual. */
    fun openNoteComposer() {
        _uiState.update { it.copy(showNoteComposer = true, noteComposerTargetHighlight = null) }
    }

    /** Abre el redactor de notas para comentar un subrayado concreto (desde "Subrayados"/Notas). */
    fun openNoteComposerForHighlight(highlight: HighlightMeta) {
        // Se cierra el índice de capítulos primero -- si veníamos de la pestaña "Subrayados",
        // no tiene sentido dejar dos hojas inferiores abiertas a la vez.
        _uiState.update {
            it.copy(showChapterIndex = false, showNoteComposer = true, noteComposerTargetHighlight = highlight)
        }
    }

    fun closeNoteComposer() {
        _uiState.update { it.copy(showNoteComposer = false, noteComposerTargetHighlight = null) }
    }

    /**
     * Guarda lo que se esté redactando: si el redactor se abrió para un subrayado concreto, el
     * texto se guarda como comentario de ESE subrayado; si no, se crea una nota suelta ligada a
     * la posición actual (capítulo + palabra + página, la misma referencia que pide el usuario:
     * "las notas deben ir referidas a página, capítulo, libro y fecha").
     */
    fun saveNoteComposer(text: String, currentPage: Int, totalPages: Int) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            closeNoteComposer()
            return
        }
        val state = _uiState.value
        val targetHighlight = state.noteComposerTargetHighlight
        if (targetHighlight != null) {
            _uiState.update {
                it.copy(
                    highlights = it.highlights.map { h -> if (h.id == targetHighlight.id) h.copy(note = trimmed) else h }
                )
            }
            viewModelScope.launch { libraryRepository.setHighlightNote(bookId, targetHighlight.id, trimmed) }
        } else {
            val note = NoteMeta(
                chapterIndex = state.chapterIndex,
                chapterTitle = state.chapterTitle,
                wordIndex = state.wordIndex,
                pageNumber = currentPage,
                totalPages = totalPages,
                text = trimmed
            )
            _uiState.update { it.copy(notes = it.notes + note) }
            viewModelScope.launch { libraryRepository.addNote(bookId, note) }
        }
        closeNoteComposer()
    }

    fun removeNote(noteId: String) {
        _uiState.update { it.copy(notes = it.notes.filterNot { n -> n.id == noteId }) }
        viewModelScope.launch { libraryRepository.removeNote(bookId, noteId) }
    }

    /** Salta a la posición de un subrayado guardado, desde la pestaña "Subrayados" del índice. */
    fun jumpToHighlight(highlight: HighlightMeta) = jumpToPosition(highlight.chapterIndex, highlight.startWordIndex)

    /** Salta a la página de un marcador guardado, desde la pestaña "Marcadores" del índice. */
    fun jumpToBookmark(bookmark: BookmarkMeta) = jumpToPosition(bookmark.chapterIndex, bookmark.wordIndex)

    /** Busca [query] en todo el libro (sin tildes ni mayúsculas). */
    suspend fun search(query: String): List<com.readriffle.app.data.search.SearchHit> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.readriffle.app.data.search.BookSearch.search(_uiState.value.chapters, query)
        }

    fun jumpToSearchHit(hit: com.readriffle.app.data.search.SearchHit) = jumpToPosition(hit.chapterIndex, hit.wordIndex)

    private fun jumpToPosition(chapterIndex: Int, wordIndex: Int) {
        val state = _uiState.value
        val chapter = state.chapters.getOrNull(chapterIndex) ?: return
        playJob?.cancel()
        playJob = null

        val tokens = if (chapterIndex == state.chapterIndex) state.tokens else WordTokenizer.tokenize(chapter.text)
        val targetWord = wordIndex.coerceIn(0, (tokens.size - 1).coerceAtLeast(0))
        _uiState.update {
            it.copy(
                chapterIndex = chapterIndex,
                tokens = tokens,
                wordIndex = targetWord,
                currentWord = tokens.getOrNull(targetWord)?.display.orEmpty(),
                isPlaying = false,
                showChapterIndex = false
            )
        }
        saveProgress()
    }

    /**
     * Marca o desmarca la página actual. [pageStartWord]..[pageEndWord] (fin exclusivo) delimitan
     * las palabras de la página: si ya hay un marcador dentro, se quita; si no, se crea uno.
     */
    fun toggleBookmark(pageStartWord: Int, pageEndWord: Int, pageNumber: Int, totalPages: Int) {
        val state = _uiState.value
        val existing = state.bookmarks.filter { it.chapterIndex == state.chapterIndex && it.wordIndex in pageStartWord until pageEndWord }
        if (existing.isNotEmpty()) {
            val ids = existing.map { it.id }.toSet()
            _uiState.update { it.copy(bookmarks = it.bookmarks.filterNot { b -> b.id in ids }) }
            viewModelScope.launch { libraryRepository.removeBookmarks(bookId, ids) }
        } else {
            val bookmark = BookmarkMeta(
                chapterIndex = state.chapterIndex,
                chapterTitle = state.chapterTitle,
                wordIndex = pageStartWord,
                pageNumber = pageNumber,
                totalPages = totalPages
            )
            _uiState.update { it.copy(bookmarks = it.bookmarks + bookmark) }
            viewModelScope.launch { libraryRepository.addBookmark(bookId, bookmark) }
        }
    }

    fun removeBookmark(id: String) {
        _uiState.update { it.copy(bookmarks = it.bookmarks.filterNot { b -> b.id == id }) }
        viewModelScope.launch { libraryRepository.removeBookmarks(bookId, setOf(id)) }
    }

    /** Fija la página actual como página de consulta (ver [PinnedPageMeta]). */
    fun pinCurrentPage(label: String, pageStartWord: Int, pageNumber: Int, totalPages: Int) {
        val state = _uiState.value
        val page = PinnedPageMeta(
            label = label.trim(),
            chapterIndex = state.chapterIndex,
            chapterTitle = state.chapterTitle,
            wordIndex = pageStartWord,
            pageNumber = pageNumber,
            totalPages = totalPages
        )
        _uiState.update { it.copy(pinnedPages = it.pinnedPages + page) }
        viewModelScope.launch { libraryRepository.addPinnedPage(bookId, page) }
    }

    /** Añade una imagen del usuario (mapa, esquema...) como página de consulta. */
    fun addReferenceImage(imageBytes: ByteArray, label: String) {
        viewModelScope.launch {
            val path = runCatching { libraryRepository.saveReferenceImage(imageBytes) }.getOrNull() ?: return@launch
            val page = PinnedPageMeta(label = label.trim(), chapterIndex = 0, wordIndex = 0, imagePath = path)
            _uiState.update { it.copy(pinnedPages = it.pinnedPages + page) }
            libraryRepository.addPinnedPage(bookId, page)
        }
    }

    fun removePinnedPage(id: String) {
        _uiState.update { it.copy(pinnedPages = it.pinnedPages.filterNot { p -> p.id == id }) }
        viewModelScope.launch { libraryRepository.removePinnedPage(bookId, id) }
    }

    fun cycleFont(forward: Boolean) {
        val options = ReaderFontOption.entries
        val currentIndex = options.indexOf(_uiState.value.fontOption)
        val nextIndex = (currentIndex + (if (forward) 1 else -1) + options.size) % options.size
        setFont(options[nextIndex])
    }

    /** Elegir una tipografía directamente (menú de selección), en vez de ciclar de una en una. */
    fun setFont(option: ReaderFontOption) {
        _uiState.update { it.copy(fontOption = option) }
        viewModelScope.launch { preferencesRepository.setFontKey(option.name) }
    }

    /** Los 5 niveles de zoom del menú de tamaño de texto -- del más pequeño al más grande. */
    val zoomPresets = listOf(0.8f, 1.0f, 1.2f, 1.4f, 1.6f)

    fun cycleTextScale() {
        val current = _uiState.value.textScale
        val currentIdx = zoomPresets.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }.let { if (it == -1) 1 else it }
        setTextScale(zoomPresets[(currentIdx + 1) % zoomPresets.size])
    }

    /** Elegir un nivel de zoom directamente del menú, en vez de ciclar de uno en uno. */
    fun setTextScale(scale: Float) {
        _uiState.update { it.copy(textScale = scale) }
        viewModelScope.launch { preferencesRepository.setTextScale(scale) }
    }

    /** Fondo del modo RSVP, elegido desde Ajustes (ver [RsvpBackgroundMode]). */
    fun setRsvpBackground(mode: RsvpBackgroundMode) {
        _uiState.update { it.copy(rsvpBackground = mode) }
        viewModelScope.launch { preferencesRepository.setRsvpBackgroundKey(mode.name) }
    }

    fun setReadingMode(mode: ReadingMode) {
        if (_uiState.value.readingMode == mode) return
        // Ya no se recuerda el modo: cada libro se abre siempre en lectura normal.
        _uiState.update { it.copy(readingMode = mode) }
    }

    /** Alterna directamente entre claro y oscuro (sin pasar por "Sistema"), para el botón rápido. */
    fun toggleTheme(currentlyDark: Boolean) {
        val next = if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
        setThemeChoice(next, _uiState.value.themeVariant)
    }

    /**
     * Elige tema + variante a la vez (menú de 4 opciones: Claro / Claro cálido / Oscuro /
     * Oscuro profundo) -- sustituye al simple alternar claro/oscuro de antes. Como el propio
     * alternar de antes, fuerza LIGHT o DARK directamente, saltándose "Sistema". Accesible tanto
     * desde el modo RSVP como desde el modo de lectura normal (ver [ThemeMenuButton] en
     * `ReadingControls`/`NormalReaderContent`) -- pedido explícito: "la edición de modo claro y
     * oscuro... tiene que ser completa desde la parte de RSVP y la parte de lectura normal".
     *
     * Los fondos RSVP "Pantalla negra"/"Pantalla blanca" son deliberadamente independientes del
     * tema (ver [RsvpBackgroundMode]), pero el usuario pidió que, al cambiar aquí entre claro y
     * oscuro, ese fondo "limpio" los siga: si estás en "Pantalla negra" y pasas a un tema claro,
     * cambia sola a "Pantalla blanca" (y viceversa) -- así nunca te quedas leyendo con, por
     * ejemplo, letras oscuras de un tema claro sobre una pantalla que sigue siendo negra.
     * "Texto fluido" no se toca: ya sigue los colores del tema por sí solo.
     */
    fun setThemeChoice(mode: ThemeMode, variant: ThemeVariant) {
        val goingLight = mode == ThemeMode.LIGHT
        val goingDark = mode == ThemeMode.DARK
        val currentBackground = _uiState.value.rsvpBackground
        val matchedBackground = when {
            goingLight && currentBackground == RsvpBackgroundMode.BLACK_LINES -> RsvpBackgroundMode.WHITE_SCREEN
            goingDark && currentBackground == RsvpBackgroundMode.WHITE_SCREEN -> RsvpBackgroundMode.BLACK_LINES
            else -> null
        }
        _uiState.update {
            it.copy(
                themeMode = mode,
                themeVariant = variant,
                rsvpBackground = matchedBackground ?: it.rsvpBackground
            )
        }
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode.name)
            preferencesRepository.setThemeVariantKey(variant.name)
            matchedBackground?.let { preferencesRepository.setRsvpBackgroundKey(it.name) }
        }
    }

    /**
     * Manda el texto seleccionado a Gemini como descripción de un personaje o paisaje del libro,
     * y pide una imagen de vuelta -- pedido explícito del usuario. La imagen ya se escribe en
     * disco en cuanto llega (ver [GeminiImageClient]), pero solo se liga a este libro (para que
     * aparezca en Notas) si el usuario la confirma con [confirmSaveGeneratedImage]; si la
     * descarta, el archivo se borra sin dejar rastro.
     */
    fun generateImage(prompt: String, pageNumber: Int, totalPages: Int) {
        if (_uiState.value.isGeneratingImage) return
        val apiKey = _uiState.value.geminiApiKey
        _uiState.update { it.copy(isGeneratingImage = true, imageGenerationError = null) }
        viewModelScope.launch {
            runCatching {
                val bytes = geminiImageClient.generateImage(apiKey, prompt)
                libraryRepository.saveGeneratedImageFile(bytes)
            }.onSuccess { path ->
                _uiState.update {
                    it.copy(
                        isGeneratingImage = false,
                        generatedImagePreview = GeneratedImagePreview(path, prompt, pageNumber, totalPages)
                    )
                }
            }.onFailure { error ->
                val message = (error as? GeminiImageException)?.message
                    ?: "No se pudo generar la imagen: ${error.message}"
                _uiState.update { it.copy(isGeneratingImage = false, imageGenerationError = message) }
            }
        }
    }

    /** Guarda la imagen previsualizada en Notas, ligada a este libro y a la posición actual. */
    fun confirmSaveGeneratedImage() {
        val preview = _uiState.value.generatedImagePreview ?: return
        val state = _uiState.value
        val image = GeneratedImageMeta(
            chapterIndex = state.chapterIndex,
            chapterTitle = state.chapterTitle,
            wordIndex = state.wordIndex,
            prompt = preview.prompt,
            imagePath = preview.imagePath,
            pageNumber = preview.pageNumber,
            totalPages = preview.totalPages
        )
        _uiState.update { it.copy(generatedImagePreview = null) }
        viewModelScope.launch(NonCancellable) { libraryRepository.addGeneratedImage(bookId, image) }
    }

    /** Descarta la imagen previsualizada sin guardarla, borrando el archivo del disco. */
    fun discardGeneratedImage() {
        val preview = _uiState.value.generatedImagePreview ?: return
        _uiState.update { it.copy(generatedImagePreview = null) }
        viewModelScope.launch { libraryRepository.deleteGeneratedImageFile(preview.imagePath) }
    }

    fun dismissImageGenerationError() {
        _uiState.update { it.copy(imageGenerationError = null) }
    }

    private fun saveProgress() {
        val state = _uiState.value
        viewModelScope.launch {
            libraryRepository.updateProgress(
                bookId = bookId,
                chapterIndex = state.chapterIndex,
                wordIndex = state.wordIndex,
                // OJO: esto guardaba `chapterProgressFraction` (progreso solo del capítulo
                // actual), así que la Biblioteca mostraba "42%" queriendo decir "42% de este
                // capítulo" -- sin sentido para el usuario, que espera el progreso del LIBRO
                // completo. `overallProgressFraction` ya existía para esto (la barra de
                // progreso arrastrable ya lo usaba) pero nunca se había usado aquí.
                progressFraction = state.overallProgressFraction
            )
        }
    }

    companion object {
        fun factory(
            bookId: String,
            container: AppContainer,
            initialChapterIndex: Int = -1,
            initialWordIndex: Int = -1
        ) = viewModelFactory {
            initializer {
                ReadingViewModel(
                    bookId = bookId,
                    libraryRepository = container.libraryRepository,
                    preferencesRepository = container.userPreferencesRepository,
                    geminiImageClient = container.geminiImageClient,
                    statsRepository = container.statsRepository,
                    readingLog = container.readingLog,
                    coverLock = container.coverLock,
                    initialChapterIndex = initialChapterIndex,
                    initialWordIndex = initialWordIndex
                )
            }
        }
    }
}
