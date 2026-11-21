package com.readriffle.app.data.ratings

import com.readriffle.app.data.model.CommunityRating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.readriffle.app.platform.URLEncoder
import com.readriffle.app.platform.httpRequest
import com.readriffle.app.platform.jsonpRequest
import com.readriffle.app.platform.stripAccents

/**
 * Nota media de los lectores en webs de referencia, sin IA y sin cuentas: Apple Books (tienda de
 * España, y la de EE. UU. si en España no tiene votos), Open Library y Google Books. Busca primero
 * por ISBN y, si no, por título y autor, y solo acepta fichas cuyo título y autor se parezcan de
 * verdad al libro (para no enseñar la nota de otro).
 *
 * Goodreads no aparece porque cerró su API a desarrolladores nuevos en 2020.
 */
object CommunityRatingsClient {

    suspend fun fetch(title: String, author: String, isbn: String?): List<CommunityRating> = coroutineScope {
        val jobs = listOf(
            async { runCatching { appleBooks(title, author, isbn) }.getOrNull() },
            async { runCatching { openLibrary(title, author, isbn) }.getOrNull() },
            async { runCatching { googleBooks(title, author, isbn) }.getOrNull() }
        )
        jobs.mapNotNull { it.await() }.filter { it.count > 0 && it.average > 0f }
    }

    /** Media de todas las fuentes, ponderada por el número de votos de cada una. */
    fun combined(ratings: List<CommunityRating>): Pair<Float, Int>? {
        val total = ratings.sumOf { it.count }
        if (total <= 0) return null
        val avg = ratings.sumOf { it.average.toDouble() * it.count } / total
        return avg.toFloat() to total
    }

    // --- Apple Books -----------------------------------------------------------------------------

    private suspend fun appleBooks(title: String, author: String, isbn: String?): CommunityRating? {
        for (country in listOf("es", "us")) {
            val results = buildList {
                if (isbn != null) jsonp("https://itunes.apple.com/lookup?isbn=$isbn&country=$country")
                    ?.let { JSONObject(it).optJSONArray("results") }?.let { a -> for (i in 0 until a.length()) add(a.getJSONObject(i)) }
                if (isEmpty()) jsonp("https://itunes.apple.com/search?term=${enc("$title $author")}&media=ebook&entity=ebook&country=$country&limit=15")
                    ?.let { JSONObject(it).optJSONArray("results") }?.let { a -> for (i in 0 until a.length()) add(a.getJSONObject(i)) }
            }
            val best = results
                .filter { matches(title, author, it.optString("trackName"), it.optString("artistName")) || (isbn != null && results.size == 1) }
                .filter { it.optInt("userRatingCount", 0) > 0 }
                .maxByOrNull { it.optInt("userRatingCount", 0) }
                ?: continue
            return CommunityRating(
                source = if (country == "es") "Apple Books" else "Apple Books (EE. UU.)",
                average = best.optDouble("averageUserRating", 0.0).toFloat(),
                count = best.optInt("userRatingCount", 0),
                url = best.optString("trackViewUrl").ifBlank { null },
                matchedTitle = "${best.optString("trackName")} · ${best.optString("artistName")}"
            )
        }
        return null
    }

    // --- Open Library ----------------------------------------------------------------------------

    private suspend fun openLibrary(title: String, author: String, isbn: String?): CommunityRating? {
        val fields = "&fields=key,title,author_name,ratings_average,ratings_count&limit=10"
        val urls = buildList {
            if (isbn != null) add("https://openlibrary.org/search.json?isbn=$isbn$fields")
            add("https://openlibrary.org/search.json?title=${enc(cleanTitle(title))}" + (if (author.isNotBlank()) "&author=${enc(author)}" else "") + fields)
        }
        for (u in urls) {
            val docs = get(u)?.let { JSONObject(it).optJSONArray("docs") } ?: continue
            var best: JSONObject? = null
            for (i in 0 until docs.length()) {
                val d = docs.getJSONObject(i)
                val docAuthor = d.optJSONArray("author_name")?.let { a -> (0 until a.length()).joinToString(", ") { a.optString(it) } }.orEmpty()
                if (!u.contains("isbn=") && !matches(title, author, d.optString("title"), docAuthor)) continue
                if (d.optInt("ratings_count", 0) <= 0) continue
                if (best == null || d.optInt("ratings_count", 0) > best.optInt("ratings_count", 0)) best = d
            }
            val b = best ?: continue
            return CommunityRating(
                source = "Open Library",
                average = b.optDouble("ratings_average", 0.0).toFloat(),
                count = b.optInt("ratings_count", 0),
                url = b.optString("key").takeIf { it.isNotBlank() }?.let { "https://openlibrary.org$it" },
                matchedTitle = b.optString("title")
            )
        }
        return null
    }

    // --- Google Books ----------------------------------------------------------------------------

    private suspend fun googleBooks(title: String, author: String, isbn: String?): CommunityRating? {
        val queries = buildList {
            if (isbn != null) add("isbn:$isbn")
            add("intitle:${enc(cleanTitle(title))}" + if (author.isNotBlank()) "+inauthor:${enc(author)}" else "")
        }
        for (q in queries) {
            val items = get("https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=15&printType=books")
                ?.let { JSONObject(it).optJSONArray("items") } ?: continue
            var best: JSONObject? = null
            for (i in 0 until items.length()) {
                val v = items.getJSONObject(i).optJSONObject("volumeInfo") ?: continue
                val authors = v.optJSONArray("authors")?.let { a -> (0 until a.length()).joinToString(", ") { a.optString(it) } }.orEmpty()
                if (!q.startsWith("isbn:") && !matches(title, author, v.optString("title"), authors)) continue
                if (v.optInt("ratingsCount", 0) <= 0) continue
                if (best == null || v.optInt("ratingsCount", 0) > best.optInt("ratingsCount", 0)) best = v
            }
            val b = best ?: continue
            return CommunityRating(
                source = "Google Books",
                average = b.optDouble("averageRating", 0.0).toFloat(),
                count = b.optInt("ratingsCount", 0),
                url = b.optString("infoLink").ifBlank { null },
                matchedTitle = b.optString("title")
            )
        }
        return null
    }

    // --- Comparar títulos -----------------------------------------------------------------------

    private fun norm(s: String): String =
        stripAccents(s).lowercase()
            .replace(Regex("[^a-z0-9ñ ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun cleanTitle(t: String): String =
        t.replace(Regex("\\(.*?\\)|\\[.*?]"), " ").substringBefore(":").replace(Regex("\\s+"), " ").trim()

    private val stopWords = setOf("el", "la", "los", "las", "un", "una", "de", "del", "y", "the", "a", "an", "of", "and", "en")

    /** ¿La ficha encontrada es este libro? Título parecido y, si hay autor, algún apellido en común. */
    internal fun matches(title: String, author: String, candTitle: String, candAuthor: String): Boolean {
        val t = norm(cleanTitle(title)).split(' ').filter { it.length > 1 && it !in stopWords }.toSet()
        val c = norm(cleanTitle(candTitle)).split(' ').filter { it.length > 1 && it !in stopWords }.toSet()
        if (t.isEmpty() || c.isEmpty()) return false
        val common = t.intersect(c).size
        val titleOk = common.toFloat() / minOf(t.size, c.size) >= 0.75f
        if (!titleOk) return false
        val a = norm(author).split(' ').filter { it.length > 2 }.toSet()
        if (a.isEmpty() || candAuthor.isBlank()) return true
        val ca = norm(candAuthor).split(' ').filter { it.length > 2 }.toSet()
        return a.intersect(ca).isNotEmpty()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun get(url: String): String? =
        runCatching { httpRequest("GET", url).takeIf { it.ok }?.text() }.getOrNull()

    /** Apple no deja pedir su API desde otras webs (CORS): se usa JSONP. */
    private suspend fun jsonp(url: String): String? = runCatching { jsonpRequest(url).ifBlank { null } }.getOrNull()
}
