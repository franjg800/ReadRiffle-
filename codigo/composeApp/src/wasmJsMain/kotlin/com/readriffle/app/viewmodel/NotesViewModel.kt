package com.readriffle.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.readriffle.app.AppContainer
import com.readriffle.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Una entrada unificada para la pantalla de Notas: junta subrayados (con o sin comentario) y
 * notas sueltas de TODOS los libros en una sola lista cronológica -- lo que pidió el usuario
 * ("una sección nueva que serán estás notas y las frases resaltadas"). [quotedText] es el
 * fragmento citado del libro (solo para subrayados); [noteText] es el comentario del usuario
 * (el subrayado lo tiene si se anotó, la nota suelta siempre).
 */
data class NoteEntry(
    val id: String,
    val bookId: String,
    val bookTitle: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val jumpToWordIndex: Int,
    val pageNumber: Int,
    val totalPages: Int,
    val createdAtMillis: Long,
    val isHighlight: Boolean,
    val quotedText: String?,
    val noteText: String?,
    // Entrada de imagen generada con IA (ver [com.readriffle.app.data.model.GeneratedImageMeta]):
    // [quotedText] guarda la descripción/prompt usado (se muestra igual que la cita de un
    // subrayado) e [imagePath] el archivo ya guardado en el almacenamiento interno de la app.
    val isImage: Boolean = false,
    val imagePath: String? = null
)

data class NotesUiState(
    val isLoading: Boolean = true,
    val entries: List<NoteEntry> = emptyList()
)

/**
 * Agrega los subrayados y notas de todos los libros de la biblioteca. Se apoya únicamente en
 * [com.readriffle.app.data.model.BookMeta] -- nunca vuelve a parsear los EPUB -- gracias a que
 * cada [com.readriffle.app.data.model.HighlightMeta]/[com.readriffle.app.data.model.NoteMeta]
 * ya guarda una foto fija del título del capítulo y de la página en el momento de crearse.
 */
class NotesViewModel(private val repository: LibraryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val books = repository.loadLibrary()
            val entries = books.flatMap { meta ->
                val highlightEntries = meta.highlights.map { h ->
                    NoteEntry(
                        id = h.id,
                        bookId = meta.id,
                        bookTitle = meta.title,
                        chapterIndex = h.chapterIndex,
                        chapterTitle = h.chapterTitle,
                        jumpToWordIndex = h.startWordIndex,
                        pageNumber = h.pageNumber,
                        totalPages = h.totalPages,
                        createdAtMillis = h.createdAtMillis,
                        isHighlight = true,
                        quotedText = h.text,
                        noteText = h.note
                    )
                }
                val noteEntries = meta.notes.map { n ->
                    NoteEntry(
                        id = n.id,
                        bookId = meta.id,
                        bookTitle = meta.title,
                        chapterIndex = n.chapterIndex,
                        chapterTitle = n.chapterTitle,
                        jumpToWordIndex = n.wordIndex,
                        pageNumber = n.pageNumber,
                        totalPages = n.totalPages,
                        createdAtMillis = n.createdAtMillis,
                        isHighlight = false,
                        quotedText = null,
                        noteText = n.text
                    )
                }
                val imageEntries = meta.generatedImages.map { img ->
                    NoteEntry(
                        id = img.id,
                        bookId = meta.id,
                        bookTitle = meta.title,
                        chapterIndex = img.chapterIndex,
                        chapterTitle = img.chapterTitle,
                        jumpToWordIndex = img.wordIndex,
                        pageNumber = img.pageNumber,
                        totalPages = img.totalPages,
                        createdAtMillis = img.createdAtMillis,
                        isHighlight = false,
                        quotedText = img.prompt,
                        noteText = null,
                        isImage = true,
                        imagePath = img.imagePath
                    )
                }
                highlightEntries + noteEntries + imageEntries
            }.sortedByDescending { it.createdAtMillis }
            _uiState.update { it.copy(isLoading = false, entries = entries) }
        }
    }

    fun deleteEntry(entry: NoteEntry) {
        viewModelScope.launch {
            when {
                entry.isImage -> {
                    repository.removeGeneratedImage(entry.bookId, entry.id)
                    entry.imagePath?.let { repository.deleteGeneratedImageFile(it) }
                }
                entry.isHighlight -> repository.removeHighlight(entry.bookId, entry.id)
                else -> repository.removeNote(entry.bookId, entry.id)
            }
            refresh()
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { NotesViewModel(container.libraryRepository) }
        }
    }
}
