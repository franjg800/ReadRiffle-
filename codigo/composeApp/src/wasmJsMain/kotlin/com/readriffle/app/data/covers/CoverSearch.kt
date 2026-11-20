package com.readriffle.app.data.covers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.readriffle.app.platform.httpRequest
import com.readriffle.app.platform.URLEncoder

/**
 * Una portada candidata. [thumbUrl] es la miniatura de la cuadrícula; [fullUrl] la versión grande
 * (vista previa y, al elegirla, la que se guarda). Si [fullUrl] falla se usa [thumbUrl].
 * [localPath] != null = es una imagen que ya está en el móvil (la portada del propio EPUB).
 */
data class CoverCandidate(
    val thumbUrl: String,
    val fullUrl: String,
    val source: String,
    val localPath: String? = null
)

/**
 * Busca portadas de un libro por título (y autor) en varios servicios gratuitos a la vez y las
 * mezcla sin repetir: Open Library (incluye otras ediciones del mismo libro), Google Books y
 * Apple Books. Si un servicio falla o no contesta, los demás siguen valiendo.
 */
class CoverSearch {

    suspend fun search(title: String, author: String): List<CoverCandidate> = coroutineScope {
        val t = cleanTitle(title)
        val a = author.trim()
        val jobs = listOf(
            async { runCatching { appleBooks(t, a) }.getOrDefault(emptyList()) },
            async { runCatching { googleBooks(t, a) }.getOrDefault(emptyList()) },
            async { runCatching { openLibrary(t, a) }.getOrDefault(emptyList()) }
        )
        val lists = jobs.map { it.await() }
        // Reparto alterno para que lo primero que se ve mezcle fuentes.
        val merged = ArrayList<CoverCandidate>()
        val seen = HashSet<String>()
        var i = 0
        while (lists.any { i < it.size }) {
            lists.forEach { l -> l.getOrNull(i)?.let { if (seen.add(it.fullUrl)) merged.add(it) } }
            i++
        }
        merged.take(60)
    }

    private fun cleanTitle(t: String): String =
        t.replace(Regex("\\(.*?\\)|\\[.*?]"), " ").replace(Regex("\\s+"), " ").trim()

    // ---- Apple Books (iTunes Search) -----------------------------------------------------------
    private suspend fun appleBooks(title: String, author: String): List<CoverCandidate> {
        val term = enc("$title $author".trim())
        val body = get("https://itunes.apple.com/search?term=$term&media=ebook&entity=ebook&country=es&limit=25") ?: return emptyList()
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<CoverCandidate>()
        for (i in 0 until results.length()) {
            val art = results.getJSONObject(i).optString("artworkUrl100").ifBlank { null } ?: continue
            out += CoverCandidate(
                thumbUrl = art.replace(Regex("\\d+x\\d+bb"), "300x300bb"),
                fullUrl = art.replace(Regex("\\d+x\\d+bb"), "1000x1000bb"),
                source = "Apple Books"
            )
        }
        return out
    }

    // ---- Google Books ---------------------------------------------------------------------------
    private suspend fun googleBooks(title: String, author: String): List<CoverCandidate> {
        val q = buildString {
            append("intitle:").append(enc(title))
            if (author.isNotBlank()) append("+inauthor:").append(enc(author))
        }
        val body = get("https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=25&printType=books") ?: return emptyList()
        val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
        val out = ArrayList<CoverCandidate>()
        for (i in 0 until items.length()) {
            val links = items.getJSONObject(i).optJSONObject("volumeInfo")?.optJSONObject("imageLinks") ?: continue
            val thumb = (links.optString("thumbnail").ifBlank { links.optString("smallThumbnail") }).ifBlank { null } ?: continue
            val base = thumb.replace("http://", "https://").replace("&edge=curl", "")
            out += CoverCandidate(
                thumbUrl = base,
                fullUrl = base.replace("zoom=1", "zoom=0"),
                source = "Google Books"
            )
        }
        return out
    }

    // ---- Open Library ---------------------------------------------------------------------------
    private suspend fun openLibrary(title: String, author: String): List<CoverCandidate> {
        val url = buildString {
            append("https://openlibrary.org/search.json?title=").append(enc(title))
            if (author.isNotBlank()) append("&author=").append(enc(author))
            append("&limit=12&fields=key,cover_i")
        }
        val docs = JSONObject(get(url) ?: return emptyList()).optJSONArray("docs") ?: return emptyList()
        val ids = LinkedHashSet<Long>()
        val works = ArrayList<String>()
        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            if (d.has("cover_i")) ids.add(d.getLong("cover_i"))
            if (works.size < 2) d.optString("key").takeIf { it.startsWith("/works/") }?.let { works.add(it) }
        }
        // Otras ediciones de las primeras obras: suelen traer portadas distintas del mismo libro.
        works.forEach { key ->
            val ed = get("https://openlibrary.org$key/editions.json?limit=40") ?: return@forEach
            val entries = JSONObject(ed).optJSONArray("entries") ?: return@forEach
            for (i in 0 until entries.length()) {
                val covers = entries.getJSONObject(i).optJSONArray("covers") ?: continue
                for (j in 0 until covers.length()) covers.optLong(j).takeIf { it > 0 }?.let { ids.add(it) }
            }
        }
        return ids.take(30).map {
            CoverCandidate(
                thumbUrl = "https://covers.openlibrary.org/b/id/$it-M.jpg",
                fullUrl = "https://covers.openlibrary.org/b/id/$it-L.jpg",
                source = "Open Library"
            )
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun get(url: String): String? =
        runCatching { httpRequest("GET", url).takeIf { it.ok }?.text() }.getOrNull()

    companion object {
        /** Descarga una imagen; null si falla o no es una imagen razonable. */
        suspend fun downloadImage(url: String): ByteArray? =
            runCatching { httpRequest("GET", url).takeIf { it.ok }?.bytes?.takeIf { it.size > 2_000 } }.getOrNull()
    }
}
