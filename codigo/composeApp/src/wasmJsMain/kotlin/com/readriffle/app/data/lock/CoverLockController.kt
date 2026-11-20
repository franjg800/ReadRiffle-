package com.readriffle.app.data.lock

import com.readriffle.app.platform.SimplePrefs
import com.readriffle.app.data.model.BookMeta
import com.readriffle.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado del "bloqueo con portada": cuando hay un libro aquí, la app se cubre con una capa con su
 * portada. El libro bloqueado se recuerda en disco, así que si el sistema cierra la app (o se sale
 * y se vuelve a entrar) la capa sigue ahí hasta que se desbloquee.
 */
class CoverLockController {
    private val prefs = SimplePrefs("cover_lock")
    private val _locked = MutableStateFlow<BookMeta?>(null)
    val locked: StateFlow<BookMeta?> = _locked.asStateFlow()

    fun lock(book: BookMeta) {
        prefs.putString("book_id", book.id)
        _locked.value = book
    }

    fun unlock() {
        prefs.remove("book_id")
        _locked.value = null
    }

    /** Al arrancar la app: si se quedó bloqueada, vuelve a poner la capa. */
    suspend fun restore(repository: LibraryRepository) {
        if (_locked.value != null) return
        val id = prefs.getString("book_id", null) ?: return
        val meta = repository.loadLibrary().find { it.id == id }
        if (meta == null) prefs.remove("book_id") else _locked.value = meta
    }
}
