package com.readriffle.app.viewmodel

import com.readriffle.app.platform.PickedFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.readriffle.app.AppContainer
import com.readriffle.app.data.model.BookMeta
import com.readriffle.app.data.preferences.UserPreferencesRepository
import com.readriffle.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isLoading: Boolean = true,
    val books: List<BookMeta> = emptyList(),
    val isImporting: Boolean = false,
    val importError: String? = null,
    // Última velocidad (PPM) usada en RSVP -- pedido explícito: mostrar en la Biblioteca el
    // tiempo restante de cada libro "a la velocidad... que haya puesto cuando me he salido del
    // libro". Es un único valor global (no por libro), igual que el resto del ajuste de
    // velocidad en toda la app.
    val lastWpm: Int = 300,
    val stats: com.readriffle.app.data.stats.StatsSummary = com.readriffle.app.data.stats.StatsSummary(),
    /** Estante seleccionado (null = todos). */
    val selectedShelf: String? = null,
    val folderImportMessage: String? = null,
    val importFolderUri: String = "",
    val isScanning: Boolean = false,
    val shelfNames: List<String> = emptyList(),
    /** Pestaña de la Biblioteca: libros que se están leyendo o sección Leídos. */
    val tab: LibraryTab = LibraryTab.READING,
    /** Libros terminados (de la biblioteca y apuntados a mano), del más reciente al más antiguo. */
    val readEntries: List<com.readriffle.app.data.ratings.ReadEntry> = emptyList()
) {
    /** Libros de la pestaña "Leyendo": los que no están marcados como leídos. */
    val readingBooks: List<BookMeta> get() = books.filterNot { it.isFinished }

    /**
     * Secciones de la biblioteca: primero las que crea el usuario (aunque estén vacías) y después
     * las sagas detectadas solas en los EPUB (una por cada saga).
     */
    val sections: List<LibrarySection>
        get() {
            val custom = (shelfNames + books.flatMap { it.shelves }).distinct()
            val customSections = custom.map { name -> LibrarySection(name, books.filter { name in it.shelves }, isCustom = true) }
            val seriesSections = books.mapNotNull { it.series }.distinct()
                .filter { s -> s !in custom }
                .map { s -> LibrarySection(s, books.filter { it.series == s }, isCustom = false) }
                .sortedBy { it.name.lowercase() }
            return customSections + seriesSections
        }

    /** Todos los estantes: los propios del usuario + las sagas detectadas en los EPUB. */
    val shelves: List<String>
        get() = (books.flatMap { it.shelves } + books.mapNotNull { it.series }).distinct().sortedBy { it.lowercase() }

    val visibleBooks: List<BookMeta>
        get() = selectedShelf?.let { s -> books.filter { s in it.shelves || it.series == s } } ?: books

    /** El subrayado más reciente de toda la biblioteca, para la tarjeta de "Hoy". */
    val latestHighlight: Pair<BookMeta, com.readriffle.app.data.model.HighlightMeta>?
        get() = books.flatMap { b -> b.highlights.map { b to it } }.maxByOrNull { it.second.createdAtMillis }

    /**
     * El libro que se está leyendo ahora mismo -- el más recientemente avanzado (ver
     * `BookMeta.lastOpenedAtMillis`, actualizado en cada `updateProgress`), no el añadido más
     * reciente. Se destaca arriba de todo en la Biblioteca; null si aún no se ha abierto ninguno.
     */
    val currentBook: BookMeta?
        get() = books.filter { it.lastOpenedAtMillis > 0L && !it.isFinished }.maxByOrNull { it.lastOpenedAtMillis }
}

enum class LibraryTab { READING, READ }

/** Una sección de la biblioteca: nombre + libros. [isCustom] = creada por el usuario (editable); si no, es una saga automática. */
data class LibrarySection(val name: String, val books: List<BookMeta>, val isCustom: Boolean)

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val statsRepository: com.readriffle.app.data.stats.StatsRepository? = null,
    private val readingLog: com.readriffle.app.data.ratings.ReadingLog? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                _uiState.update { it.copy(lastWpm = prefs.lastWpm, importFolderUri = prefs.importFolderUri) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val books = repository.loadLibrary().sortedByDescending { it.addedAtMillis }
            val stats = statsRepository?.summary() ?: com.readriffle.app.data.stats.StatsSummary()
            val shelfNames = repository.loadShelfNames()
            val reads = readingLog?.entries().orEmpty()
            _uiState.update { it.copy(isLoading = false, books = books, stats = stats, shelfNames = shelfNames, readEntries = reads) }
        }
    }

    /**
     * Importa uno o varios EPUB elegidos con el selector de archivos (en el iPhone, la app
     * Archivos). Los que ya estaban (mismo nombre y tamaño) no se duplican.
     */
    fun importFiles(files: List<PickedFile>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importError = null) }
            val known = repository.loadLibrary().mapNotNull { it.sourceKey }.toMutableSet()
            var failed = 0
            var imported = 0
            var skipped = 0
            for (f in files) {
                val key = "${f.name}|${f.bytes.size}"
                if (key in known) { skipped++; continue }
                runCatching { repository.importBookFromBytes(f.bytes, key) }
                    .onSuccess { imported++; known += key }
                    .onFailure { failed++ }
                refresh()
            }
            val message = when {
                failed > 0 && imported == 0 -> if (files.size == 1) "No se pudo importar el EPUB" else "No se pudieron importar $failed archivos"
                failed > 0 -> "Importados $imported libros ($failed con error)"
                skipped > 0 && imported == 0 -> "Ese libro ya está en tu biblioteca"
                else -> null
            }
            _uiState.update { it.copy(isImporting = false, importError = if (failed > 0 && imported == 0) message else null, folderImportMessage = if (failed > 0 && imported == 0) null else message) }
            refresh()
        }
    }

    fun dismissFolderMessage() = _uiState.update { it.copy(folderImportMessage = null) }

    fun createSection(name: String) {
        viewModelScope.launch { repository.createShelf(name); refresh() }
    }

    fun renameSection(old: String, new: String) {
        viewModelScope.launch { repository.renameShelf(old, new); refresh() }
    }

    fun deleteSection(name: String) {
        viewModelScope.launch { repository.deleteShelf(name); refresh() }
    }

    fun selectShelf(shelf: String?) = _uiState.update { it.copy(selectedShelf = shelf) }

    fun setBookShelves(bookId: String, shelves: List<String>) {
        viewModelScope.launch {
            repository.setBookShelves(bookId, shelves)
            refresh()
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            repository.deleteBook(bookId)
            refresh()
        }
    }

    fun dismissError() = _uiState.update { it.copy(importError = null) }

    fun selectTab(tab: LibraryTab) = _uiState.update { it.copy(tab = tab) }

    /** Apunta en Leídos un libro leído fuera de la app (portada y nota de lectores se buscan solas). */
    fun addExternalRead(title: String, author: String, record: com.readriffle.app.data.model.ReadingRecord) {
        val log = readingLog ?: return
        viewModelScope.launch {
            log.addExternal(title, author, record)
            refresh()
            // La portada y la nota llegan unos segundos después: se refresca otra vez.
            kotlinx.coroutines.delay(6_000)
            refresh()
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { LibraryViewModel(container.libraryRepository, container.userPreferencesRepository, container.statsRepository, container.readingLog) }
        }
    }
}
