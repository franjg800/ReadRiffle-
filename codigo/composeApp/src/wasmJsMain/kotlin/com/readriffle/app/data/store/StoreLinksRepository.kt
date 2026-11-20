package com.readriffle.app.data.store

import com.readriffle.app.platform.SimplePrefs

/** Una tienda guardada por el usuario: enlace de entrada + nombre que se muestra en su tarjeta. */
data class SavedStore(val url: String, val name: String)

/**
 * Guarda las tiendas favoritas del usuario (SharedPreferences, 100% local), en el orden en que
 * se fueron añadiendo. Formato: una línea por tienda, `url<TAB>nombre` (las líneas antiguas, que
 * solo tenían la url, se siguen leyendo y toman un nombre derivado del dominio).
 */
class StoreLinksRepository {

    private val prefs = SimplePrefs("store_links")

    fun savedStores(): List<SavedStore> =
        prefs.getString(KEY_LIST, "").orEmpty().split('\n').filter { it.isNotBlank() }.map { line ->
            val parts = line.split('\t', limit = 2)
            SavedStore(url = parts[0], name = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: defaultName(parts[0]))
        }

    fun isSaved(url: String): Boolean = savedStores().any { it.url == url }

    /** Guarda (o renombra, si ya existía esa url) una tienda. Las nuevas van al final. */
    fun save(url: String, name: String) {
        val clean = name.trim().replace('\t', ' ').replace('\n', ' ').ifBlank { defaultName(url) }
        val current = savedStores()
        val updated = if (current.any { it.url == url }) current.map { if (it.url == url) it.copy(name = clean) else it }
        else current + SavedStore(url, clean)
        write(updated)
    }

    fun remove(url: String) = write(savedStores().filter { it.url != url })

    private fun write(list: List<SavedStore>) {
        prefs.putString(KEY_LIST, list.joinToString("\n") { "${it.url}\t${it.name}" })
    }

    companion object {
        private const val KEY_LIST = "stores"

        /** Normaliza lo que pega el usuario: añade https:// si falta y rechaza esquemas raros. */
        fun normalize(raw: String): String? {
            val t = raw.trim()
            if (t.isEmpty() || t.contains(' ')) return null
            val withScheme = if ("://" in t) t else "https://$t"
            val scheme = withScheme.substringBefore("://").lowercase()
            if (scheme != "https" && scheme != "http") return null
            val host = hostOf(withScheme).ifBlank { return null }
            if ('.' !in host) return null
            return withScheme
        }

        fun hostOf(url: String): String = url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#').substringAfter('@').substringBefore(':').lowercase()

        fun host(url: String): String = hostOf(url).removePrefix("www.")

        /** Nombre por defecto a partir del dominio: "www.casadellibro.com" -> "Casadellibro". */
        fun defaultName(url: String): String =
            host(url).substringBefore('.').replaceFirstChar { it.uppercase() }.ifBlank { url }
    }
}
