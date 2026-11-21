package com.readriffle.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.readriffle.app.AppContainer
import com.readriffle.app.data.model.ReadingRecord
import com.readriffle.app.data.ratings.ReadEntry
import com.readriffle.app.data.ratings.ReadingLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReadBookUiState(
    val isLoading: Boolean = true,
    val entry: ReadEntry? = null,
    val loadingRatings: Boolean = false,
    /** Subrayados del libro, para elegir la frase favorita al editar. */
    val quotes: List<String> = emptyList(),
    val removed: Boolean = false
)

/** Ficha de un libro leído: tu nota y opinión frente a la de los lectores. */
class ReadBookViewModel(private val key: String, private val log: ReadingLog) : ViewModel() {
    private val _uiState = MutableStateFlow(ReadBookUiState())
    val uiState: StateFlow<ReadBookUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            reload()
            val e = _uiState.value.entry
            if (e != null && e.record.ratingsFetchedAtMillis == 0L) refreshRatings()
        }
    }

    private suspend fun reload() {
        val e = log.entry(key)
        _uiState.update {
            it.copy(
                isLoading = false, entry = e,
                quotes = e?.libraryBook?.highlights?.sortedBy { h -> h.createdAtMillis }?.map { h -> h.text.trim() }?.filter { q -> q.isNotEmpty() }.orEmpty()
            )
        }
    }

    fun refreshRatings() {
        if (_uiState.value.loadingRatings) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingRatings = true) }
            log.refreshRatings(key)
            reload()
            _uiState.update { it.copy(loadingRatings = false) }
        }
    }

    fun setRating(halfStars: Int) = edit { it.copy(rating = halfStars) }

    fun save(record: ReadingRecord) = edit { record }

    private fun edit(transform: (ReadingRecord) -> ReadingRecord) {
        viewModelScope.launch {
            log.update(key, transform)
            reload()
        }
    }

    /** Fecha de fin: se mueve de un día en un día desde la ficha. */
    fun shiftFinishedDate(days: Int) = edit { it.copy(finishedAtMillis = it.finishedAtMillis + days * 86_400_000L) }

    fun remove() {
        viewModelScope.launch {
            log.remove(key)
            _uiState.update { it.copy(removed = true) }
        }
    }

    companion object {
        fun factory(key: String, container: AppContainer) = viewModelFactory {
            initializer { ReadBookViewModel(key, container.readingLog) }
        }
    }
}
