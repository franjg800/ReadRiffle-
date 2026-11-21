package com.readriffle.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.readriffle.app.AppContainer
import com.readriffle.app.data.covers.CoverCandidate
import com.readriffle.app.data.covers.CoverSearch
import com.readriffle.app.data.model.BookMeta
import com.readriffle.app.data.preferences.UserPreferencesRepository
import com.readriffle.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookDetailUiState(
    val isLoading: Boolean = true,
    val book: BookMeta? = null,
    val wpm: Int = 300
)

/** Estado de la galería "Cambiar portada". */
data class CoverPickerState(
    val query: String = "",
    val loading: Boolean = false,
    val searched: Boolean = false,
    val results: List<CoverCandidate> = emptyList(),
    val applying: Boolean = false,
    val error: String? = null
)

/** Estado de la ficha de un libro: sus metadatos al día y la velocidad de lectura habitual. */
class BookDetailViewModel(
    private val bookId: String,
    private val repository: LibraryRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val readingLog: com.readriffle.app.data.ratings.ReadingLog? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private val coverSearch = CoverSearch()
    private val _picker = MutableStateFlow<CoverPickerState?>(null)
    /** null = la galería de portadas está cerrada. */
    val picker: StateFlow<CoverPickerState?> = _picker.asStateFlow()
    private var searchJob: kotlinx.coroutines.Job? = null

    init {
        refresh()
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                _uiState.update { it.copy(wpm = prefs.lastWpm) }
            }
        }
    }

    /** Vuelve a leer de disco: se llama al volver desde la lectura para ver el progreso nuevo. */
    /** Marca el libro como leído con su nota y opinión (y busca la nota de los lectores). */
    fun markRead(record: com.readriffle.app.data.model.ReadingRecord) {
        viewModelScope.launch {
            readingLog?.save("lib:$bookId", record)
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val book = repository.loadLibrary().find { it.id == bookId }
            _uiState.update { it.copy(isLoading = false, book = book) }
        }
    }

    fun openCoverPicker() {
        val book = _uiState.value.book ?: return
        _picker.value = CoverPickerState(query = book.title)
        searchCovers(book.title, useAuthor = true)
    }

    fun closeCoverPicker() { searchJob?.cancel(); _picker.value = null }

    fun setCoverQuery(q: String) = _picker.update { it?.copy(query = q) }

    /** Busca con el texto del cuadro; el autor solo se añade si no se ha tocado el título. */
    fun searchCovers(query: String, useAuthor: Boolean = false) {
        val book = _uiState.value.book ?: return
        val q = query.trim().ifBlank { return }
        searchJob?.cancel()
        _picker.update { it?.copy(loading = true, searched = false, results = emptyList(), error = null) }
        searchJob = viewModelScope.launch {
            val withAuthor = useAuthor || q.equals(book.title.trim(), ignoreCase = true)
            val found = runCatching { coverSearch.search(q, if (withAuthor) book.author else "") }.getOrDefault(emptyList())
            val original = repository.originalCoverPath(book.id)?.let {
                CoverCandidate(thumbUrl = it, fullUrl = it, source = "Portada del EPUB", localPath = it)
            }
            _picker.update { it?.copy(loading = false, searched = true, results = listOfNotNull(original) + found) }
        }
    }

    /** Guarda la portada elegida (descarga la grande; si falla, la miniatura) y cierra la galería. */
    fun applyCover(candidate: CoverCandidate) {
        val book = _uiState.value.book ?: return
        _picker.update { it?.copy(applying = true, error = null) }
        viewModelScope.launch {
            val ok = if (candidate.localPath != null) {
                repository.useOriginalCover(book.id); true
            } else {
                val bytes = CoverSearch.downloadImage(candidate.fullUrl) ?: CoverSearch.downloadImage(candidate.thumbUrl)
                bytes != null && repository.setCustomCover(book.id, bytes)
            }
            if (ok) {
                refresh()
                _picker.value = null
            } else {
                _picker.update { it?.copy(applying = false, error = "No se pudo descargar esa portada. Prueba con otra.") }
            }
        }
    }

    fun editBook(title: String, author: String) {
        viewModelScope.launch {
            repository.updateBookInfo(bookId, title, author)
            refresh()
        }
    }

    fun deleteBook(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteBook(bookId)
            onDone()
        }
    }

    companion object {
        fun factory(bookId: String, container: AppContainer) = viewModelFactory {
            initializer { BookDetailViewModel(bookId, container.libraryRepository, container.userPreferencesRepository, container.readingLog) }
        }
    }
}
