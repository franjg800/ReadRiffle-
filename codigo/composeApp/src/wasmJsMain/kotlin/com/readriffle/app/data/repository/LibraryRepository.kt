package com.readriffle.app.data.repository

import com.readriffle.app.platform.System
import com.readriffle.app.data.epub.EpubParser
import com.readriffle.app.data.model.Book
import com.readriffle.app.data.model.BookmarkMeta
import com.readriffle.app.data.model.PinnedPageMeta
import com.readriffle.app.data.model.BookMeta
import com.readriffle.app.data.model.DeletedBookMeta
import com.readriffle.app.data.model.GeneratedImageMeta
import com.readriffle.app.data.model.HighlightMeta
import com.readriffle.app.data.model.LibraryIndex
import com.readriffle.app.data.model.NoteMeta
import com.readriffle.app.data.tokenizer.WordTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.readriffle.app.platform.File
import com.readriffle.app.platform.ImageTools
import com.readriffle.app.platform.Vfs
import com.readriffle.app.platform.appFilesDir
import com.readriffle.app.platform.UUID

/**
 * Gestiona la biblioteca local: copia cada EPUB importado al almacenamiento interno de la app
 * (100% local, sin depender de que el permiso sobre la Uri original siga vivo), extrae portada
 * y metadatos, y persiste el índice de la biblioteca como JSON.
 *
 * Bug corregido -- las notas (y en general cualquier escritura lanzada justo antes de salir de
 * la pantalla de lectura) podían perderse en silencio, dos causas a la vez:
 * 1) Cada escritura hacía su propio "leer todo el índice -> modificar -> volver a escribirlo
 *    entero" sin ninguna exclusión mutua entre sí; si dos escrituras se solapaban (p. ej.
 *    guardar el progreso de lectura y añadir una nota casi a la vez), la que terminaba de
 *    escribir la última podía pisar por completo el resultado de la otra, basada en una lectura
 *    más antigua ("lost update"). [writeMutex] serializa TODAS las lecturas+escrituras del
 *    índice entre sí para que esto ya no pueda pasar.
 * 2) `ReadingViewModel` lanza estas escrituras en su `viewModelScope`; si el usuario añadía una
 *    nota y salía de la pantalla de lectura casi inmediatamente después (el flujo más natural:
 *    anotas algo y cierras el libro), ese `viewModelScope` se cancelaba al destruirse la
 *    pantalla, cortando la escritura en pleno vuelo, con la nota nunca llegando a guardarse en
 *    disco -- "cada vez que ponemos una nota no se guarda en ningún lado". Envolver el cuerpo de
 *    cada escritura en `NonCancellable` hace que, una vez lanzada, termine siempre de
 *    escribirse aunque quien la lanzó ya se haya cancelado.
 */
class LibraryRepository {

    private object appContext { val filesDir = appFilesDir }
    private val json = Json { ignoreUnknownKeys = true }
    private val parser = EpubParser()
    private val writeMutex = Mutex()

    private val booksDir: File get() = File(appContext.filesDir, "books").apply { mkdirs() }
    private val coversDir: File get() = File(appContext.filesDir, "covers").apply { mkdirs() }
    private val generatedImagesDir: File get() = File(appContext.filesDir, "generated_images").apply { mkdirs() }
    private val bookImagesRoot: File get() = File(appContext.filesDir, "book_images")
    private fun imagesDirFor(bookId: String) = File(bookImagesRoot, bookId)
    private val referenceImagesDir: File get() = File(appContext.filesDir, "reference_images").apply { mkdirs() }
    private val indexFile: File get() = File(appContext.filesDir, "library_index.json")

    private suspend fun loadIndex(): LibraryIndex = withContext(Dispatchers.Default) {
        if (!indexFile.exists()) return@withContext LibraryIndex()
        runCatching {
            json.decodeFromString(LibraryIndex.serializer(), indexFile.readText())
        }.getOrDefault(LibraryIndex())
    }

    suspend fun loadLibrary(): List<BookMeta> = loadIndex().books

    /** Libros borrados en ESTE dispositivo que aún no se han propagado al resto (ver [deleteBook]). */
    suspend fun loadDeletedBooks(): List<DeletedBookMeta> = loadIndex().deletedBooks

    private suspend fun saveIndex(index: LibraryIndex) = withContext(Dispatchers.Default) {
        indexFile.writeText(json.encodeToString(LibraryIndex.serializer(), index))
    }

    /**
     * Lee el índice completo, le aplica [transform] y vuelve a escribirlo -- todo bajo
     * [writeMutex] (para que no se solape con ninguna otra escritura en curso) y bajo
     * `NonCancellable` (para que, una vez empezada, termine de escribirse en disco aunque quien
     * la lanzó se haya cancelado mientras tanto, p. ej. al salir de la pantalla de lectura justo
     * después de guardar una nota). Punto de entrada único para toda modificación del índice.
     */
    private suspend fun mutateIndex(transform: (LibraryIndex) -> LibraryIndex) {
        withContext(NonCancellable + Dispatchers.Default) {
            writeMutex.withLock {
                saveIndex(transform(loadIndex()))
            }
        }
    }

    private suspend fun mutateLibrary(transform: (List<BookMeta>) -> List<BookMeta>) {
        mutateIndex { it.copy(books = transform(it.books)) }
    }

    /**
     * Importa un EPUB ya descargado en memoria -- usado por la sección Tienda (ver
     * [com.readriffle.app.data.store.GutenbergStoreClient]) para dar de alta un libro bajado de
     * Project Gutenberg. Mismo flujo que [importBook], solo que el origen son bytes que ya se
     * tienen en memoria en vez de una Uri del selector de archivos del sistema.
     */
    suspend fun importBookFromBytes(bytes: ByteArray, sourceKey: String? = null): BookMeta = withContext(Dispatchers.Default) {
        val id = UUID.randomUUID().toString()
        val destFile = File(booksDir, "$id.epub")
        destFile.writeBytes(bytes)
        finishImport(id, destFile, sourceKey)
    }

    private suspend fun finishImport(id: String, destFile: File, sourceKey: String? = null): BookMeta {
        val parsed = runCatching { parser.parse(destFile, imagesDirFor(id)) }.getOrElse {
            destFile.delete()
            imagesDirFor(id).deleteRecursively()
            throw it
        }

        val coverPath = parsed.coverBytes?.let { bytes ->
            val coverFile = File(coversDir, "$id.jpg")
            coverFile.writeBytes(bytes)
            coverFile.absolutePath
        }

        val totalWordCount = parsed.chapters.sumOf { WordTokenizer.tokenize(it.text).size }

        val meta = BookMeta(
            id = id,
            title = parsed.title,
            author = parsed.author,
            coverPath = coverPath,
            epubFileName = destFile.name,
            chapterCount = parsed.chapters.size,
            totalWordCount = totalWordCount,
            chapterScheme = CURRENT_CHAPTER_SCHEME,
            series = parsed.series,
            sourceKey = sourceKey
        )

        mutateLibrary { it + meta }
        Vfs.evict(destFile.path)
        return meta
    }

    /**
     * Rellena [BookMeta.totalWordCount] para un libro importado antes de que existiera ese campo
     * (valor 0 heredado) -- lo llama `ReadingViewModel.loadBook()` la primera vez que abre un
     * libro así, reutilizando el recuento por capítulo que ya calcula para sí mismo
     * (`chapterWordCounts.sum()`), para no tener que volver a tokenizar nada aquí.
     */
    suspend fun updateTotalWordCount(bookId: String, totalWordCount: Int) = mutateLibrary { books ->
        books.map { if (it.id == bookId && it.totalWordCount != totalWordCount) it.copy(totalWordCount = totalWordCount) else it }
    }

    /** Cambia el título y el autor mostrados (el EPUB original no se toca). */
    suspend fun updateBookInfo(bookId: String, title: String, author: String) = mutateLibrary { books ->
        books.map {
            if (it.id == bookId) it.copy(
                title = title.trim().ifBlank { it.title },
                author = author.trim().ifBlank { it.author },
                lastModifiedMillis = System.currentTimeMillis()
            ) else it
        }
    }

    /** Portada original que trae el EPUB (guardada al importar), si la tiene. */
    fun originalCoverPath(bookId: String): String? = File(coversDir, "$bookId.jpg").takeIf { it.exists() }?.absolutePath

    /**
     * Pone como portada una imagen elegida por el usuario: la reduce (máx. 1400 px de alto), la
     * guarda como JPEG con nombre nuevo (así la caché de imágenes no enseña la antigua) y la deja
     * como portada del libro. La portada original del EPUB no se borra, por si quiere volver a ella.
     * Devuelve false si los bytes no son una imagen legible.
     */
    suspend fun setCustomCover(bookId: String, imageBytes: ByteArray): Boolean = withContext(Dispatchers.Default) {
        val jpeg = ImageTools.reencode(imageBytes, 1400, byHeight = true) ?: return@withContext false
        val file = File(coversDir, "${bookId}_custom_${System.currentTimeMillis()}.jpg")
        file.writeBytes(jpeg)
        applyCoverPath(bookId, file.absolutePath)
        true
    }

    /** Vuelve a la portada que traía el EPUB (o a ninguna si no traía). */
    suspend fun useOriginalCover(bookId: String) = withContext(Dispatchers.Default) {
        applyCoverPath(bookId, originalCoverPath(bookId))
    }

    private suspend fun applyCoverPath(bookId: String, newPath: String?) {
        mutateLibrary { books ->
            books.map {
                if (it.id != bookId) it else {
                    // Borra la portada personalizada anterior (nunca la original del EPUB).
                    it.coverPath?.let { old -> if (old != newPath && File(old).name.contains("_custom_")) File(old).delete() }
                    it.copy(coverPath = newPath)
                }
            }
        }
    }

    suspend fun loadShelfNames(): List<String> = loadIndex().shelfNames

    suspend fun createShelf(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        mutateIndex { idx -> if (idx.shelfNames.any { it.equals(clean, true) }) idx else idx.copy(shelfNames = idx.shelfNames + clean) }
    }

    suspend fun renameShelf(old: String, new: String) {
        val clean = new.trim()
        if (clean.isEmpty() || clean == old) return
        mutateIndex { idx ->
            idx.copy(
                shelfNames = (idx.shelfNames.map { if (it == old) clean else it }).distinct(),
                books = idx.books.map { b -> if (old in b.shelves) b.copy(shelves = b.shelves.map { if (it == old) clean else it }.distinct()) else b }
            )
        }
    }

    suspend fun deleteShelf(name: String) {
        mutateIndex { idx ->
            idx.copy(
                shelfNames = idx.shelfNames.filterNot { it == name },
                books = idx.books.map { b -> if (name in b.shelves) b.copy(shelves = b.shelves - name) else b }
            )
        }
    }

    /** Estantes propios de un libro (reemplaza la lista completa). */
    suspend fun setBookShelves(bookId: String, shelves: List<String>) = mutateLibrary { books ->
        books.map { if (it.id == bookId) it.copy(shelves = shelves.map(String::trim).filter(String::isNotEmpty).distinct()) else it }
    }

    suspend fun loadBookContent(meta: BookMeta): Book = withContext(Dispatchers.Default) {
        val epubFile = File(booksDir, meta.epubFileName)
        Vfs.ensureLoaded(epubFile.path)
        val parsed = parser.parse(epubFile, imagesDirFor(meta.id))
        if (meta.series == null && parsed.series != null) {
            mutateLibrary { books -> books.map { if (it.id == meta.id && it.series == null) it.copy(series = parsed.series) else it } }
        }
        if (meta.chapterScheme < CURRENT_CHAPTER_SCHEME) migrateChapterScheme(meta, epubFile, parsed.chapters)
        Vfs.evict(epubFile.path)
        Book(
            id = meta.id,
            title = meta.title,
            author = meta.author,
            coverPath = meta.coverPath,
            chapters = parsed.chapters
        )
    }

    /**
     * Libros importados con el reparto antiguo (un archivo del EPUB = un capítulo): al abrirlos
     * por primera vez con el reparto por índice, la posición de lectura, subrayados, notas,
     * marcadores y páginas de consulta guardados se trasladan por posición absoluta en el texto,
     * para que nada apunte a un capítulo que ya no existe.
     */
    private suspend fun migrateChapterScheme(meta: BookMeta, epubFile: File, chapters: List<com.readriffle.app.data.model.Chapter>) {
        val legacy = runCatching { parser.legacyWordCounts(epubFile) }.getOrDefault(emptyList())
        val remap = if (legacy.isEmpty()) null else ChapterRemapper(legacy, chapters)
        mutateIndex { index ->
            index.copy(books = index.books.map { b ->
                if (b.id != meta.id || b.chapterScheme >= CURRENT_CHAPTER_SCHEME) return@map b
                val m = remap
                if (m == null) {
                    return@map b.copy(chapterCount = chapters.size, chapterScheme = CURRENT_CHAPTER_SCHEME)
                }
                val (lc, lw) = m.map(b.lastChapterIndex, b.lastWordIndex)
                b.copy(
                    chapterCount = chapters.size,
                    chapterScheme = CURRENT_CHAPTER_SCHEME,
                    lastChapterIndex = lc,
                    lastWordIndex = lw,
                    highlights = b.highlights.map { h ->
                        val (c, w) = m.map(h.chapterIndex, h.startWordIndex)
                        val (c2, w2) = m.map(h.chapterIndex, h.endWordIndex)
                        h.copy(
                            chapterIndex = c,
                            chapterTitle = chapters.getOrNull(c)?.title ?: h.chapterTitle,
                            startWordIndex = w,
                            endWordIndex = if (c2 == c) maxOf(w, w2) else maxOf(w, m.lastTokenIndex(c))
                        )
                    },
                    notes = b.notes.map { n ->
                        val (c, w) = m.map(n.chapterIndex, n.wordIndex)
                        n.copy(chapterIndex = c, chapterTitle = chapters.getOrNull(c)?.title ?: n.chapterTitle, wordIndex = w)
                    },
                    bookmarks = b.bookmarks.map { k ->
                        val (c, w) = m.map(k.chapterIndex, k.wordIndex)
                        k.copy(chapterIndex = c, chapterTitle = chapters.getOrNull(c)?.title ?: k.chapterTitle, wordIndex = w)
                    },
                    pinnedPages = b.pinnedPages.map { p ->
                        if (p.imagePath != null) p else {
                            val (c, w) = m.map(p.chapterIndex, p.wordIndex)
                            p.copy(chapterIndex = c, chapterTitle = chapters.getOrNull(c)?.title ?: p.chapterTitle, wordIndex = w)
                        }
                    },
                    generatedImages = b.generatedImages.map { g ->
                        val (c, w) = m.map(g.chapterIndex, g.wordIndex)
                        g.copy(chapterIndex = c, chapterTitle = chapters.getOrNull(c)?.title ?: g.chapterTitle, wordIndex = w)
                    },
                    lastModifiedMillis = System.currentTimeMillis()
                )
            })
        }
    }

    suspend fun updateProgress(bookId: String, chapterIndex: Int, wordIndex: Int, progressFraction: Float) =
        mutateLibrary { books ->
            books.map {
                if (it.id == bookId) {
                    it.copy(
                        lastChapterIndex = chapterIndex,
                        lastWordIndex = wordIndex,
                        progressFraction = progressFraction.coerceIn(0f, 1f),
                        lastOpenedAtMillis = System.currentTimeMillis(),
                        lastModifiedMillis = System.currentTimeMillis()
                    )
                } else it
            }
        }

    /**
     * Guardado inmediato y síncrono del progreso (web): se usa cuando la app pasa a segundo plano
     * o se cierra, donde no da tiempo a que corra una corrutina.
     */
    fun updateProgressNow(bookId: String, chapterIndex: Int, wordIndex: Int, progressFraction: Float) {
        val index = if (!indexFile.exists()) LibraryIndex() else runCatching {
            json.decodeFromString(LibraryIndex.serializer(), indexFile.readText())
        }.getOrDefault(LibraryIndex())
        val now = System.currentTimeMillis()
        val updated = index.copy(books = index.books.map {
            if (it.id == bookId) it.copy(
                lastChapterIndex = chapterIndex, lastWordIndex = wordIndex,
                progressFraction = progressFraction.coerceIn(0f, 1f),
                lastOpenedAtMillis = now, lastModifiedMillis = now
            ) else it
        })
        indexFile.writeText(json.encodeToString(LibraryIndex.serializer(), updated))
    }

    suspend fun addHighlight(bookId: String, highlight: HighlightMeta) = mutateLibrary { books ->
        books.map {
            if (it.id == bookId) {
                it.copy(highlights = it.highlights + highlight, lastModifiedMillis = System.currentTimeMillis())
            } else it
        }
    }

    suspend fun removeHighlight(bookId: String, highlightId: String) = mutateLibrary { books ->
        books.map {
            if (it.id == bookId) {
                it.copy(
                    highlights = it.highlights.filterNot { h -> h.id == highlightId },
                    deletedItemIds = it.deletedItemIds + highlightId,
                    lastModifiedMillis = System.currentTimeMillis()
                )
            } else it
        }
    }

    /** Añade o edita el comentario de un subrayado ya existente (null = quitar el comentario). */
    suspend fun setHighlightNote(bookId: String, highlightId: String, note: String?) = mutateLibrary { books ->
        books.map { meta ->
            if (meta.id != bookId) return@map meta
            meta.copy(
                highlights = meta.highlights.map { h -> if (h.id == highlightId) h.copy(note = note) else h },
                lastModifiedMillis = System.currentTimeMillis()
            )
        }
    }

    suspend fun addBookmark(bookId: String, bookmark: BookmarkMeta) = mutateLibrary { books ->
        books.map {
            if (it.id == bookId) it.copy(bookmarks = it.bookmarks + bookmark, lastModifiedMillis = System.currentTimeMillis()) else it
        }
    }

    suspend fun removeBookmarks(bookId: String, ids: Set<String>) = mutateLibrary { books ->
        books.map {
            if (it.id == bookId) {
                it.copy(bookmarks = it.bookmarks.filterNot { b -> b.id in ids }, deletedItemIds = it.deletedItemIds + ids, lastModifiedMillis = System.currentTimeMillis())
            } else it
        }
    }

    suspend fun addPinnedPage(bookId: String, page: PinnedPageMeta) = mutateLibrary { books ->
        books.map {
            if (it.id == bookId) it.copy(pinnedPages = it.pinnedPages + page, lastModifiedMillis = System.currentTimeMillis()) else it
        }
    }

    /**
     * Copia una imagen elegida por el usuario (cualquier formato que Android sepa leer) al
     * almacenamiento interno, reducida a un máximo de 4096 px por lado para no agotar memoria al
     * hacer zoom. Devuelve la ruta del archivo guardado.
     */
    suspend fun saveReferenceImage(imageBytes: ByteArray): String = withContext(Dispatchers.Default) {
        val png = ImageTools.hasAlpha(imageBytes)
        val out = ImageTools.reencode(imageBytes, 4096, png = png) ?: throw IllegalArgumentException("No se pudo leer la imagen")
        val file = File(referenceImagesDir, "${UUID.randomUUID()}.${if (png) "png" else "jpg"}")
        file.writeBytes(out)
        file.absolutePath
    }

    suspend fun removePinnedPage(bookId: String, id: String) = mutateLibrary { books ->
        books.find { it.id == bookId }?.pinnedPages?.find { it.id == id }?.imagePath?.let { File(it).delete() }
        books.map {
            if (it.id == bookId) {
                it.copy(pinnedPages = it.pinnedPages.filterNot { p -> p.id == id }, deletedItemIds = it.deletedItemIds + id, lastModifiedMillis = System.currentTimeMillis())
            } else it
        }
    }

    suspend fun addNote(bookId: String, note: NoteMeta) = mutateLibrary { books ->
        books.map {
            if (it.id == bookId) it.copy(notes = it.notes + note, lastModifiedMillis = System.currentTimeMillis()) else it
        }
    }

    suspend fun updateNote(bookId: String, noteId: String, text: String) = mutateLibrary { books ->
        books.map { meta ->
            if (meta.id != bookId) return@map meta
            meta.copy(
                notes = meta.notes.map { n -> if (n.id == noteId) n.copy(text = text) else n },
                lastModifiedMillis = System.currentTimeMillis()
            )
        }
    }

    suspend fun removeNote(bookId: String, noteId: String) = mutateLibrary { books ->
        books.map {
            if (it.id == bookId) {
                it.copy(notes = it.notes.filterNot { n -> n.id == noteId }, deletedItemIds = it.deletedItemIds + noteId, lastModifiedMillis = System.currentTimeMillis())
            } else it
        }
    }

    /**
     * Guarda los bytes de una imagen ya generada por IA en el almacenamiento interno de la app
     * (sin ligarla aún a ningún libro -- eso lo hace [addGeneratedImage] solo si el usuario
     * decide guardarla, para no dejar archivos "huérfanos" por cada imagen generada que luego se
     * descarta).
     */
    suspend fun saveGeneratedImageFile(bytes: ByteArray): String = withContext(Dispatchers.Default) {
        val file = File(generatedImagesDir, "${UUID.randomUUID()}.png")
        file.writeBytes(bytes)
        file.absolutePath
    }

    suspend fun deleteGeneratedImageFile(path: String) = withContext(Dispatchers.Default) {
        runCatching { File(path).delete() }
    }

    suspend fun addGeneratedImage(bookId: String, image: GeneratedImageMeta) = mutateLibrary { books ->
        books.map { if (it.id == bookId) it.copy(generatedImages = it.generatedImages + image) else it }
    }

    suspend fun removeGeneratedImage(bookId: String, imageId: String) = mutateLibrary { books ->
        books.map {
            if (it.id == bookId) it.copy(generatedImages = it.generatedImages.filterNot { img -> img.id == imageId }) else it
        }
    }

    /**
     * Bytes crudos del EPUB ya importado de este libro -- para subirlo a Drive en la
     * sincronización (ver [com.readriffle.app.data.sync.DriveSyncManager]). El propio archivo
     * nunca cambia tras importarlo, así que solo se lee una vez por libro en toda su vida (las
     * veces siguientes ya tiene `driveFileId` y no hace falta volver a leerlo).
     */
    suspend fun readEpubBytes(meta: BookMeta): ByteArray = withContext(Dispatchers.Default) {
        val f = File(booksDir, meta.epubFileName)
        Vfs.ensureLoaded(f.path)
        f.readBytes().also { Vfs.evict(f.path) }
    }

    /**
     * Da de alta, en ESTE dispositivo, un libro que ya existía en otro (llegado por
     * sincronización): escribe el EPUB descargado en el almacenamiento interno local y vuelve a
     * extraer su portada con el mismo parser que usa [importBook] -- nunca se sincroniza la
     * portada en sí, cada dispositivo la regenera a partir del EPUB. Las imágenes generadas con
     * IA de [remote] se descartan a propósito: no se sincronizan (quedan solo en el dispositivo
     * donde se generaron), así que el resultado siempre empieza con `generatedImages` vacío.
     * No toca el índice de la biblioteca -- quien llama decide cuándo guardar el resultado
     * (normalmente junto con el resto de libros fusionados, de una sola vez).
     */
    suspend fun adoptSyncedBook(remote: BookMeta, epubBytes: ByteArray): BookMeta = withContext(Dispatchers.Default) {
        val epubFileName = "${remote.id}.epub"
        val destFile = File(booksDir, epubFileName)
        destFile.writeBytes(epubBytes)

        val coverPath = runCatching { parser.parse(destFile) }.getOrNull()?.coverBytes?.let { bytes ->
            val coverFile = File(coversDir, "${remote.id}.jpg")
            coverFile.writeBytes(bytes)
            coverFile.absolutePath
        }

        remote.copy(
            epubFileName = epubFileName,
            coverPath = coverPath,
            generatedImages = emptyList()
        )
    }

    /** Borra del almacenamiento interno los archivos de un libro (EPUB, portada, imágenes de IA)
     *  sin tocar el índice -- usado por [deleteBook] y por el propio
     *  [com.readriffle.app.data.sync.DriveSyncManager] cuando, al sincronizar, descubre que este
     *  libro se ha borrado en OTRO dispositivo y hay que limpiarlo también aquí. */
    suspend fun deleteBookFiles(meta: BookMeta) = withContext(Dispatchers.Default) {
        File(coversDir, "${meta.id}.jpg").delete()
        File(booksDir, meta.epubFileName).delete()
        meta.coverPath?.let { File(it).delete() }
        meta.generatedImages.forEach { File(it.imagePath).delete() }
        meta.pinnedPages.forEach { p -> p.imagePath?.let { File(it).delete() } }
        imagesDirFor(meta.id).deleteRecursively()
    }

    /**
     * Sustituye la biblioteca entera por el resultado ya fusionado de una sincronización (ver
     * [com.readriffle.app.data.sync.DriveSyncManager.syncAll]) -- mismo mecanismo de exclusión
     * mutua y atomicidad que cualquier otra escritura del índice, solo que aquí la lista
     * sustituida ya viene completa en vez de derivarse de la que había antes. [deletedBooks]
     * sustituye también la lista de lápidas (ver [deleteBook]): una vez un borrado ha quedado
     * reflejado en el manifiesto de Drive por todos los dispositivos relevantes, no hay por qué
     * seguir arrastrándolo para siempre, pero por simplicidad aquí simplemente se guarda la
     * fusión completa que ya calculó el propio sincronizador.
     */
    suspend fun applyMergedLibrary(books: List<BookMeta>, deletedBooks: List<DeletedBookMeta>) =
        mutateIndex { current ->
            // Si mientras sincronizaba el usuario borró un subrayado/nota, esa lápida ya está en el
            // índice actual pero no en `books` (calculado antes): se vuelve a aplicar aquí.
            val reconciled = books.map { b ->
                val extra = current.books.find { it.id == b.id }?.deletedItemIds.orEmpty()
                if (extra.isEmpty()) b else {
                    val all = (b.deletedItemIds + extra).distinct()
                    val gone = all.toSet()
                    b.copy(
                        deletedItemIds = all,
                        highlights = b.highlights.filter { it.id !in gone },
                        notes = b.notes.filter { it.id !in gone },
                        bookmarks = b.bookmarks.filter { it.id !in gone },
                        pinnedPages = b.pinnedPages.filter { it.id !in gone }
                    )
                }
            }
            current.copy(books = reconciled, deletedBooks = deletedBooks)
        }

    /**
     * Borra un libro EN ESTE DISPOSITIVO y dejar una "lápida" (ver [DeletedBookMeta]) -- bug
     * corregido: antes solo se quitaba de la lista local, así que en la siguiente sincronización
     * volvía a aparecer en cuanto cualquier OTRO dispositivo que todavía lo tuviera subía su
     * propia copia del manifiesto de Drive ("parece que se borra, pero cuando se sincroniza
     * vuelve a aparecer"). La lápida viaja en el mismo manifiesto y le dice al sincronizador que
     * ese id está borrado a propósito, no "todavía no descargado".
     */
    suspend fun deleteBook(bookId: String) = withContext(NonCancellable + Dispatchers.Default) {
        writeMutex.withLock {
            val current = loadIndex()
            val meta = current.books.find { it.id == bookId } ?: return@withLock
            File(booksDir, meta.epubFileName).delete()
            meta.coverPath?.let { File(it).delete() }
            File(coversDir, "${meta.id}.jpg").delete()
            meta.generatedImages.forEach { File(it.imagePath).delete() }
            meta.pinnedPages.forEach { p -> p.imagePath?.let { File(it).delete() } }
            imagesDirFor(bookId).deleteRecursively()
            saveIndex(
                current.copy(
                    books = current.books.filterNot { it.id == bookId },
                    deletedBooks = current.deletedBooks.filterNot { it.id == bookId } + DeletedBookMeta(bookId)
                )
            )
        }
    }
}

/** Versión actual del reparto en capítulos (ver BookMeta.chapterScheme). */
internal const val CURRENT_CHAPTER_SCHEME = 2

/** Traslada posiciones (capítulo antiguo, palabra) al reparto nuevo contando palabras de texto. */
internal class ChapterRemapper(legacyCounts: List<Int>, private val chapters: List<com.readriffle.app.data.model.Chapter>) {
    private val legacy = legacyCounts
    private val legacyStarts = IntArray(legacyCounts.size).also {
        var run = 0
        for (i in legacyCounts.indices) { it[i] = run; run += legacyCounts[i] }
    }
    private val imageFlags: List<BooleanArray> = chapters.map { ch ->
        WordTokenizer.tokenize(ch.text).map { t -> t.isImage }.toBooleanArray()
    }
    private val textCounts = imageFlags.map { f -> f.count { !it } }

    fun lastTokenIndex(chapter: Int): Int = ((imageFlags.getOrNull(chapter)?.size ?: 1) - 1).coerceAtLeast(0)

    fun map(oldChapter: Int, oldWord: Int): Pair<Int, Int> {
        if (chapters.isEmpty()) return 0 to 0
        val oc = oldChapter.coerceIn(0, legacy.lastIndex)
        val g = legacyStarts[oc] + oldWord.coerceIn(0, legacy[oc])
        var cumulative = 0
        for (c in chapters.indices) {
            if (g < cumulative + textCounts[c]) return c to tokenIndexOfText(c, g - cumulative)
            cumulative += textCounts[c]
        }
        val last = chapters.lastIndex
        return last to lastTokenIndex(last)
    }

    private fun tokenIndexOfText(chapter: Int, r: Int): Int {
        if (r == 0) return 0
        var seen = 0
        val flags = imageFlags[chapter]
        for (i in flags.indices) {
            if (!flags[i]) {
                if (seen == r) return i
                seen++
            }
        }
        return lastTokenIndex(chapter)
    }
}
