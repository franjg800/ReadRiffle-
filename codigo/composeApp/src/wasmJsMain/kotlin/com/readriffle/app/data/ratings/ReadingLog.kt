package com.readriffle.app.data.ratings

import com.readriffle.app.platform.System
import com.readriffle.app.data.covers.CoverSearch
import com.readriffle.app.data.model.BookMeta
import com.readriffle.app.data.model.ExternalReadBook
import com.readriffle.app.data.model.ReadingRecord
import com.readriffle.app.data.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Un libro de la sección Leídos, venga de la biblioteca ([libraryId]) o apuntado a mano / borrado
 * de la biblioteca después de leerlo ([externalId]).
 */
data class ReadEntry(
    val key: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val isbn: String?,
    val totalWordCount: Int,
    val record: ReadingRecord,
    val libraryBook: BookMeta? = null,
    val external: ExternalReadBook? = null
) {
    val isExternal: Boolean get() = external != null

    companion object {
        fun of(meta: BookMeta) = ReadEntry(
            key = "lib:${meta.id}", title = meta.title, author = meta.author, coverPath = meta.coverPath,
            isbn = meta.isbn, totalWordCount = meta.totalWordCount, record = meta.finished!!, libraryBook = meta
        )

        fun of(ext: ExternalReadBook) = ReadEntry(
            key = "ext:${ext.id}", title = ext.title, author = ext.author, coverPath = ext.coverPath,
            isbn = ext.isbn, totalWordCount = ext.totalWordCount, record = ext.record, external = ext
        )
    }
}

/**
 * Operaciones de la sección Leídos que siguen en marcha aunque se cambie de pantalla (buscar la
 * nota de los lectores y la portada de un libro apuntado a mano tarda unos segundos).
 */
class ReadingLog(private val repository: LibraryRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Todos los leídos, del más reciente al más antiguo. */
    suspend fun entries(): List<ReadEntry> {
        val lib = repository.loadLibrary().filter { it.isFinished }.map { ReadEntry.of(it) }
        val ext = repository.loadExternalReads().filter { it.record.active }.map { ReadEntry.of(it) }
        return (lib + ext).sortedByDescending { it.record.finishedAtMillis }
    }

    suspend fun entry(key: String): ReadEntry? {
        val id = key.substringAfter(':')
        return if (key.startsWith("lib:")) {
            repository.loadLibrary().find { it.id == id }?.takeIf { it.finished != null }?.let { ReadEntry.of(it) }
        } else {
            repository.loadExternalReads().find { it.id == id }?.let { ReadEntry.of(it) }
        }
    }

    /** Guarda la ficha (nueva o editada) y, si aún no tiene notas de lectores, las busca. */
    suspend fun save(key: String, record: ReadingRecord) {
        val id = key.substringAfter(':')
        if (key.startsWith("lib:")) repository.saveReadRecord(id, record)
        else repository.updateExternalRead(id) { it.copy(record = record) }
        if (record.ratingsFetchedAtMillis == 0L) refreshRatingsAsync(key)
    }

    suspend fun update(key: String, transform: (ReadingRecord) -> ReadingRecord) {
        val id = key.substringAfter(':')
        if (key.startsWith("lib:")) repository.updateReadRecord(id, transform)
        else repository.updateExternalRead(id) { it.copy(record = transform(it.record)) }
    }

    /** Quita el libro de Leídos (si es de la biblioteca, vuelve a "Leyendo"). */
    suspend fun remove(key: String) {
        val id = key.substringAfter(':')
        if (key.startsWith("lib:")) repository.unmarkFinished(id) else repository.removeExternalRead(id)
    }

    /** Apunta un libro leído fuera de la app; busca su portada y su nota en segundo plano. */
    suspend fun addExternal(title: String, author: String, record: ReadingRecord): String {
        val book = ExternalReadBook(title = title.trim(), author = author.trim(), record = record)
        repository.addExternalRead(book)
        val key = "ext:${book.id}"
        scope.launch {
            runCatching {
                val cover = CoverSearch().search(book.title, book.author).firstOrNull()
                val bytes = cover?.let { CoverSearch.downloadImage(it.fullUrl) ?: CoverSearch.downloadImage(it.thumbUrl) }
                val path = bytes?.let { repository.saveExternalCover(book.id, it) }
                if (path != null) repository.updateExternalRead(book.id) { it.copy(coverPath = path) }
            }
            refreshRatings(key)
        }
        return key
    }

    fun refreshRatingsAsync(key: String) {
        scope.launch { refreshRatings(key) }
    }

    /** Busca (o vuelve a buscar) la nota de los lectores y la guarda en la ficha. */
    suspend fun refreshRatings(key: String): Boolean {
        val e = entry(key) ?: return false
        val isbn = e.isbn ?: e.libraryBook?.let { runCatching { repository.isbnFor(it) }.getOrNull() }
        val ratings = runCatching { CommunityRatingsClient.fetch(e.title, e.author, isbn) }.getOrNull() ?: return false
        update(key) { it.copy(communityRatings = ratings, ratingsFetchedAtMillis = System.currentTimeMillis()) }
        return true
    }
}
