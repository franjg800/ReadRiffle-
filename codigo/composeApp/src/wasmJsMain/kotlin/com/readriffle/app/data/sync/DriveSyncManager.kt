package com.readriffle.app.data.sync

import com.readriffle.app.platform.System
import com.readriffle.app.data.model.BookMeta
import com.readriffle.app.data.model.DeletedBookMeta
import com.readriffle.app.data.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.readriffle.app.platform.httpRequest
import com.readriffle.app.platform.URLEncoder

sealed class SyncResult {
    data class Success(val syncedAtMillis: Long) : SyncResult()
    object NotSignedIn : SyncResult()
    data class Failure(val message: String) : SyncResult()
}

/** Instantánea de toda la biblioteca que se guarda como un único archivo JSON en Drive. */
@Serializable
private data class SyncManifest(
    val books: List<BookMeta> = emptyList(),
    val deletedBooks: List<DeletedBookMeta> = emptyList()
)

/**
 * Sincroniza biblioteca + progreso (capítulo, palabra, resaltados, notas) entre dispositivos
 * usando el Drive del propio usuario como almacén -- una carpeta propia y normal llamada
 * [APP_FOLDER_NAME] (antes era la carpeta oculta `appDataFolder`; se cambió porque el OAuth
 * "device flow" que usa el plugin de KOReader en el Kindle no admite el scope `drive.appdata`,
 * solo `drive.file`, y `drive.file` no da acceso a `appDataFolder`), gratis, sin tarjeta ni
 * servidor propio, pedido explícito: "tener sincronizados mis libros... y el progreso que llevo
 * los libros en todos los dispositivos donde esté la app". Las imágenes generadas con IA NO se
 * sincronizan (se quedan solo en el dispositivo donde se generaron) para no complicar el primer
 * alcance de esta función.
 *
 * Fusión por libro: `lastModifiedMillis` decide qué copia de los datos de progreso (capítulo,
 * palabra, título/autor) es la más reciente -- pero los subrayados y las notas se UNEN por id en
 * vez de sobrescribirse, para que anotar algo en un dispositivo y algo distinto en otro, sin
 * haber sincronizado entre medias, no haga perder ninguna de las dos cosas. El EPUB en sí nunca
 * cambia tras importarlo, así que cada libro solo se sube una vez en toda su vida
 * (`BookMeta.driveFileId` recuerda que ya se subió).
 *
 * Llamadas crudas a la API REST de Drive v3 con `HttpURLConnection` (mismo estilo que
 * [com.readriffle.app.data.ai.GeminiImageClient]), para no añadir la librería completa
 * `google-api-client` solo para esto.
 */
class DriveSyncManager(
    private val libraryRepository: LibraryRepository,
    private val getAccessToken: suspend () -> String?
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val manifestName = "manifest.json"

    companion object {
        /** Carpeta propia y visible en el Drive del usuario donde vive todo lo de ReadRiffle. */
        private const val APP_FOLDER_NAME = "ReadRiffle"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }

    suspend fun syncAll(): SyncResult = withContext(Dispatchers.Default) {
        val token = getAccessToken() ?: return@withContext SyncResult.NotSignedIn
        try {
            val folderId = resolveAppFolderId(token)
            val manifestFileId = findFileId(token, manifestName, folderId)
            val remoteManifest = manifestFileId?.let { id ->
                runCatching { json.decodeFromString(SyncManifest.serializer(), downloadText(token, id)) }.getOrNull()
            } ?: SyncManifest()

            val localBooks = libraryRepository.loadLibrary()
            val localDeleted = libraryRepository.loadDeletedBooks()

            // Une las lápidas de ambos lados -- si el mismo id tiene más de una (no debería
            // pasar, pero por si acaso), se queda con la fecha de borrado más reciente.
            val mergedDeleted = (localDeleted + remoteManifest.deletedBooks)
                .groupBy { it.id }
                .map { (_, versions) -> versions.maxBy { it.deletedAtMillis } }
                .toMutableList()

            val remoteById = remoteManifest.books.associateBy { it.id }
            val localById = localBooks.associateBy { it.id }
            val allIds = localById.keys + remoteById.keys

            val merged = allIds.mapNotNull { id ->
                val local = localById[id]
                val remote = remoteById[id]

                // Si hay una lápida para este id posterior al último cambio real que se conoce
                // en cualquiera de los dos lados, el libro se queda borrado -- así, borrarlo en
                // un dispositivo se propaga al resto en la siguiente sincronización, en vez de
                // "revivir" en cuanto otro dispositivo que aún no se había enterado sube su
                // propia copia (bug corregido: "se borra, pero al sincronizar vuelve a aparecer").
                val tombstone = mergedDeleted.find { it.id == id }
                val lastRealChange = maxOf(local?.lastModifiedMillis ?: 0L, remote?.lastModifiedMillis ?: 0L)
                if (tombstone != null && tombstone.deletedAtMillis >= lastRealChange) {
                    return@mapNotNull null
                }

                when {
                    local == null && remote != null -> adoptRemoteOnlyBook(token, remote, folderId)
                    remote == null -> local
                    else -> mergeBook(local!!, remote!!)
                }
            }

            // Libros con el mismo título+autor pero id distinto: pasa cuando el mismo libro se
            // importó por separado en dos dispositivos ANTES de activar la sincronización (lo
            // que le pasó al usuario) -- en la práctica "el mismo libro" duplicado, y sin esto no
            // había forma de que dejaran de estarlo. Se queda el que se importó antes (criterio
            // estable, igual en cualquier dispositivo), se le pasa el progreso más avanzado y se
            // unen los resaltados/notas de todas las copias; el resto se marca como borrado.
            val deduped = mutableListOf<BookMeta>()
            merged.groupBy { "${it.title.trim().lowercase()}|${it.author.trim().lowercase()}" }.forEach { (_, group) ->
                if (group.size <= 1) {
                    deduped += group
                    return@forEach
                }
                val primary = group.minBy { it.addedAtMillis }
                val mostAdvanced = group.maxBy { it.lastModifiedMillis }
                deduped += primary.copy(
                    lastChapterIndex = mostAdvanced.lastChapterIndex,
                    lastWordIndex = mostAdvanced.lastWordIndex,
                    progressFraction = mostAdvanced.progressFraction,
                    lastOpenedAtMillis = mostAdvanced.lastOpenedAtMillis,
                    lastModifiedMillis = maxOf(group.maxOf { it.lastModifiedMillis }, System.currentTimeMillis()),
                    deletedItemIds = group.flatMap { it.deletedItemIds }.distinct(),
                    highlights = group.flatMap { it.highlights }.distinctBy { it.id }.filter { h -> group.none { g -> h.id in g.deletedItemIds } },
                    notes = group.flatMap { it.notes }.distinctBy { it.id }.filter { h -> group.none { g -> h.id in g.deletedItemIds } },
                    bookmarks = group.flatMap { it.bookmarks }.distinctBy { it.id }.filter { h -> group.none { g -> h.id in g.deletedItemIds } },
                    pinnedPages = group.flatMap { it.pinnedPages }.distinctBy { it.id }.filter { h -> group.none { g -> h.id in g.deletedItemIds } }
                )
                group.filterNot { it.id == primary.id }.forEach { duplicate ->
                    mergedDeleted.removeAll { it.id == duplicate.id }
                    mergedDeleted += DeletedBookMeta(duplicate.id)
                }
            }

            // Borra en ESTE dispositivo los archivos de cualquier libro que se haya quedado fuera
            // (por lápida o por deduplicación), tanto si el borrado se originó aquí como en otro.
            val remainingIds = deduped.map { it.id }.toSet()
            localBooks.filter { it.id !in remainingIds }.forEach { libraryRepository.deleteBookFiles(it) }

            // Sube el EPUB de cualquier libro que no exista ya en la carpeta actual de Drive.
            // Importante: no basta con mirar si `meta.driveFileId` es null -- un libro
            // sincronizado con la versión anterior (carpeta `appDataFolder`) ya tenía un
            // driveFileId guardado, pero apunta a un archivo que no existe en esta carpeta nueva.
            // Por eso se comprueba siempre contra esta carpeta por nombre, y solo se sube si de
            // verdad no está aquí.
            val withUploads = deduped.map { meta ->
                val existingId = findFileId(token, epubName(meta.id), folderId)
                if (existingId != null) {
                    meta.copy(driveFileId = existingId)
                } else {
                    val bytes = libraryRepository.readEpubBytes(meta)
                    val fileId = uploadFile(token, null, epubName(meta.id), "application/epub+zip", bytes, folderId)
                    meta.copy(driveFileId = fileId)
                }
            }

            libraryRepository.applyMergedLibrary(withUploads, mergedDeleted)

            val manifestBytes = json.encodeToString(SyncManifest.serializer(), SyncManifest(withUploads, mergedDeleted))
                .encodeToByteArray()
            uploadFile(token, manifestFileId, manifestName, "application/json", manifestBytes, folderId)

            SyncResult.Success(System.currentTimeMillis())
        } catch (e: Exception) {
            SyncResult.Failure(e.message ?: "Error de sincronización")
        }
    }

    /** Un libro que existe en Drive pero todavía no en este dispositivo: lo descarga y adopta. */
    private suspend fun adoptRemoteOnlyBook(token: String, remote: BookMeta, folderId: String): BookMeta? {
        // No usar remote.driveFileId directamente: puede venir de un manifest viejo de la carpeta
        // appDataFolder (ya no existe aquí). Siempre se busca por nombre en la carpeta actual.
        val fileId = findFileId(token, epubName(remote.id), folderId) ?: return null
        val bytes = downloadBytes(token, fileId)
        return libraryRepository.adoptSyncedBook(remote, bytes).copy(driveFileId = fileId)
    }

    private fun epubName(bookId: String) = "book_$bookId.epub"

    private fun mergeBook(local: BookMeta, remote: BookMeta): BookMeta {
        // Si el otro dispositivo usa otro reparto en capítulos, sus posiciones (capítulo, palabra)
        // no valen aquí: se conserva todo lo local hasta que ambos estén en la misma versión.
        val deleted = (local.deletedItemIds + remote.deletedItemIds).distinct()
        val deletedSet = deleted.toSet()
        if (remote.chapterScheme != local.chapterScheme) {
            return local.copy(
                deletedItemIds = deleted,
                highlights = local.highlights.filter { it.id !in deletedSet },
                notes = local.notes.filter { it.id !in deletedSet },
                bookmarks = local.bookmarks.filter { it.id !in deletedSet },
                pinnedPages = local.pinnedPages.filter { it.id !in deletedSet },
                lastModifiedMillis = maxOf(local.lastModifiedMillis, remote.lastModifiedMillis),
                driveFileId = local.driveFileId ?: remote.driveFileId
            )
        }
        val remoteWins = remote.lastModifiedMillis > local.lastModifiedMillis
        val progressSource = if (remoteWins) remote else local
        return local.copy(
            title = progressSource.title,
            author = progressSource.author,
            lastChapterIndex = progressSource.lastChapterIndex,
            lastWordIndex = progressSource.lastWordIndex,
            progressFraction = progressSource.progressFraction,
            lastOpenedAtMillis = progressSource.lastOpenedAtMillis,
            lastModifiedMillis = maxOf(local.lastModifiedMillis, remote.lastModifiedMillis),
            deletedItemIds = deleted,
            highlights = mergeById(local.highlights, remote.highlights) { it.id }.filter { it.id !in deletedSet },
            notes = mergeById(local.notes, remote.notes) { it.id }.filter { it.id !in deletedSet },
            bookmarks = mergeById(local.bookmarks, remote.bookmarks) { it.id }.filter { it.id !in deletedSet },
            pinnedPages = mergeById(local.pinnedPages, remote.pinnedPages) { it.id }.filter { it.id !in deletedSet },
            driveFileId = local.driveFileId ?: remote.driveFileId
            // coverPath, epubFileName y generatedImages se quedan los de `local` (ya vienen de
            // `local.copy(...)`): son específicos de este dispositivo y nunca se toman del otro.
        )
    }

    private fun <T> mergeById(a: List<T>, b: List<T>, idOf: (T) -> String): List<T> {
        val map = LinkedHashMap<String, T>()
        a.forEach { map[idOf(it)] = it }
        b.forEach { val k = idOf(it); if (!map.containsKey(k)) map[k] = it }
        return map.values.toList()
    }

    // --- API REST de Drive v3 -----------------------------------------------------------------

    /**
     * Busca la carpeta [APP_FOLDER_NAME] en el Drive del usuario y la crea si no existe todavía
     * (primera sincronización de este dispositivo, o migración desde la antigua `appDataFolder`).
     * Con el scope `drive.file` la app solo ve carpetas/archivos que ella misma ha creado, así
     * que basta con buscar por nombre cada vez en lugar de guardar el id entre sesiones.
     */
    private fun auth(token: String) = mapOf("Authorization" to "Bearer $token")

    private suspend fun getJson(token: String, url: String): kotlinx.serialization.json.JsonObject {
        val r = httpRequest("GET", url, auth(token))
        if (!r.ok) error("Drive respondió ${r.code}: ${r.text().take(200)}")
        return json.parseToJsonElement(r.text()).jsonObject
    }

    private suspend fun resolveAppFolderId(token: String): String {
        val query = URLEncoder.encode("name='$APP_FOLDER_NAME' and mimeType='$FOLDER_MIME_TYPE' and trashed=false", "UTF-8")
        val files = getJson(token, "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id)")["files"]?.jsonArray
        val existingId = files?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
        if (existingId != null) return existingId

        val metadata = """{"name":"$APP_FOLDER_NAME","mimeType":"$FOLDER_MIME_TYPE"}"""
        val r = httpRequest(
            "POST", "https://www.googleapis.com/drive/v3/files?fields=id",
            auth(token) + ("Content-Type" to "application/json; charset=UTF-8"), body = metadata
        )
        if (!r.ok) error("Drive respondió ${r.code} al crear la carpeta")
        return json.parseToJsonElement(r.text()).jsonObject["id"]?.jsonPrimitive?.content
            ?: error("Drive no devolvió el id de la carpeta $APP_FOLDER_NAME creada")
    }

    private suspend fun findFileId(token: String, name: String, folderId: String): String? {
        val query = URLEncoder.encode("name='$name' and '$folderId' in parents and trashed=false", "UTF-8")
        val files = getJson(token, "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id)")["files"]?.jsonArray ?: return null
        return files.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    private suspend fun downloadText(token: String, fileId: String): String = downloadBytes(token, fileId).decodeToString()

    private suspend fun downloadBytes(token: String, fileId: String): ByteArray {
        val r = httpRequest("GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media", auth(token))
        if (!r.ok) error("Drive respondió ${r.code} al descargar")
        return r.bytes
    }

    /** Subida multipart (metadatos + contenido) en una sola petición, según la API de Drive v3. */
    private suspend fun uploadFile(token: String, existingFileId: String?, name: String, mimeType: String, bytes: ByteArray, folderId: String): String {
        val boundary = "readriffle_sync_${System.currentTimeMillis()}"
        val metadataJson = if (existingFileId == null) {
            """{"name":"$name","parents":["$folderId"]}"""
        } else {
            """{"name":"$name"}"""
        }
        val head = ("--$boundary\r\n" +
            "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
            metadataJson +
            "\r\n--$boundary\r\n" +
            "Content-Type: $mimeType\r\n\r\n").encodeToByteArray()
        val tail = "\r\n--$boundary--".encodeToByteArray()
        val body = head + bytes + tail

        val baseUrl = "https://www.googleapis.com/upload/drive/v3/files"
        val url = if (existingFileId == null) "$baseUrl?uploadType=multipart" else "$baseUrl/$existingFileId?uploadType=multipart"
        val r = httpRequest(
            if (existingFileId == null) "POST" else "PATCH", url,
            auth(token) + ("Content-Type" to "multipart/related; boundary=$boundary"), bodyBytes = body
        )
        if (!r.ok) error("Drive respondió ${r.code} al subir $name")
        val id = json.parseToJsonElement(r.text()).jsonObject["id"]?.jsonPrimitive?.content
        return id ?: existingFileId ?: error("Drive no devolvió el id del archivo subido")
    }
}
