package com.readriffle.app.platform

import kotlinx.coroutines.await

/**
 * Sistema de archivos de la app en el navegador. Mantiene en memoria los archivos pequeños (índice
 * de la biblioteca, portadas, imágenes) y guarda cada escritura en IndexedDB, para que el código
 * portado de Android pueda seguir usando File de forma síncrona.
 *
 * - Los EPUB ("/files/books/...") son "perezosos": solo se traen de IndexedDB cuando hacen falta
 *   ([ensureLoaded]) y se sueltan de memoria al terminar ([evict]).
 * - Las imágenes extraídas de los libros ("/files/book_images/...") viven solo en memoria: se
 *   vuelven a sacar del EPUB cada vez que se abre.
 */
object Vfs {
    private val data = HashMap<String, ByteArray>()
    /** Archivos que existen en IndexedDB pero aún no se han cargado en memoria. */
    private val lazyKeys = HashSet<String>()

    private val lazyPrefixes = listOf("/files/books/")
    private val memoryOnlyPrefixes = listOf("/files/book_images/")

    private fun persisted(path: String) = memoryOnlyPrefixes.none { path.startsWith(it) }

    suspend fun init() {
        val n = jsIdbLoadAll(lazyPrefixes.joinToString(",")).await<JsNumber>().toInt()
        for (i in 0 until n) {
            val key = jsLoadedKey(i)
            if (jsLoadedIsLazy(i)) lazyKeys += key
            else jsLoadedValue(i)?.let { data[key] = it.latin1ToBytes() }
        }
        jsLoadedClear()
    }

    fun exists(path: String) = path in data || path in lazyKeys || isDir(path)

    fun isDir(path: String): Boolean {
        val prefix = if (path.endsWith("/")) path else "$path/"
        return data.keys.any { it.startsWith(prefix) } || lazyKeys.any { it.startsWith(prefix) }
    }

    fun read(path: String): ByteArray =
        data[path] ?: if (path in lazyKeys) error("Archivo no cargado: $path") else throw NoSuchElementException("No existe: $path")

    fun write(path: String, bytes: ByteArray) {
        data[path] = bytes
        lazyKeys -= path
        if (persisted(path)) jsIdbPut(path, bytes.toLatin1())
    }

    fun delete(path: String): Boolean {
        val had = (data.remove(path) != null) or lazyKeys.remove(path)
        if (had && persisted(path)) jsIdbDelete(path)
        return had
    }

    fun deleteTree(path: String): Boolean {
        val prefix = if (path.endsWith("/")) path else "$path/"
        val victims = (data.keys + lazyKeys).filter { it == path || it.startsWith(prefix) }
        victims.forEach { delete(it) }
        return true
    }

    fun list(dir: String): List<String> {
        val prefix = if (dir.endsWith("/")) dir else "$dir/"
        return (data.keys + lazyKeys).filter { it.startsWith(prefix) }
            .map { prefix + it.removePrefix(prefix).substringBefore('/') }
            .distinct()
    }

    fun size(path: String): Long = data[path]?.size?.toLong() ?: 0L

    /** Trae a memoria un archivo perezoso (EPUB) antes de leerlo. */
    suspend fun ensureLoaded(path: String) {
        if (path in data || path !in lazyKeys) return
        val s = jsIdbGet(path).await<JsString?>()?.toString() ?: return
        data[path] = s.latin1ToBytes()
    }

    /** Suelta de memoria un archivo perezoso ya guardado (sigue en IndexedDB). */
    fun evict(path: String) {
        if (lazyPrefixes.any { path.startsWith(it) } && data.remove(path) != null) lazyKeys += path
    }
}

/** Sustituto de java.io.File sobre [Vfs]. */
class File(path: String) {
    constructor(parent: File, child: String) : this(parent.path.trimEnd('/') + "/" + child)
    constructor(parent: String, child: String) : this(parent.trimEnd('/') + "/" + child)

    val path: String = normalize(path)
    val absolutePath: String get() = path
    val name: String get() = path.substringAfterLast('/')
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val extension: String get() = name.substringAfterLast('.', "")
    val parentFile: File? get() = path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }?.let { File(it) }

    fun exists(): Boolean = Vfs.exists(path)
    fun isFile(): Boolean = exists() && !Vfs.isDir(path)
    fun isDirectory(): Boolean = Vfs.isDir(path)
    fun length(): Long = Vfs.size(path)
    fun mkdirs(): Boolean = true
    fun delete(): Boolean = Vfs.delete(path)
    fun deleteRecursively(): Boolean = Vfs.deleteTree(path)
    fun listFiles(): Array<File> = Vfs.list(path).map { File(it) }.toTypedArray()

    fun readBytes(): ByteArray = Vfs.read(path)
    fun readText(): String = readBytes().decodeToString()
    fun writeBytes(bytes: ByteArray) = Vfs.write(path, bytes)
    fun writeText(text: String) = Vfs.write(path, text.encodeToByteArray())
    fun outputStream(): FileOutput = FileOutput(this)
    fun inputStream(): ByteInput = ByteInput(readBytes())

    override fun equals(other: Any?) = other is File && other.path == path
    override fun hashCode() = path.hashCode()
    override fun toString() = path

    private companion object {
        fun normalize(p: String): String {
            val parts = ArrayDeque<String>()
            for (seg in p.split('/')) when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(seg)
            }
            return "/" + parts.joinToString("/")
        }
    }
}

/** Flujo de entrada en memoria (lo que devuelve un ZIP o un archivo). */
class ByteInput(private val bytes: ByteArray) : AutoCloseable {
    fun readBytes(): ByteArray = bytes
    fun copyTo(out: FileOutput): Long { out.write(bytes); return bytes.size.toLong() }
    override fun close() {}
}

/** Flujo de salida que escribe el archivo al cerrarse. */
class FileOutput(private val file: File) : AutoCloseable {
    private val chunks = ArrayList<ByteArray>()
    fun write(b: ByteArray) { chunks += b }
    override fun close() {
        val total = chunks.sumOf { it.size }
        val out = ByteArray(total)
        var pos = 0
        for (c in chunks) { c.copyInto(out, pos); pos += c.size }
        file.writeBytes(out)
    }
}

/** Raíz de los archivos internos de la app (equivalente a context.filesDir). */
val appFilesDir: File = File("/files")
