package com.readriffle.app.data.search

import com.readriffle.app.data.model.Chapter
import com.readriffle.app.data.tokenizer.ImageMarker

/** Una coincidencia de la búsqueda dentro del libro, ya situada por capítulo y palabra. */
data class SearchHit(
    val chapterIndex: Int,
    val chapterTitle: String,
    val wordIndex: Int,
    val before: String,
    val match: String,
    val after: String
)

/** Búsqueda de texto en todo el libro, sin distinguir mayúsculas ni tildes. */
object BookSearch {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")

    private fun norm(s: String) = com.readriffle.app.platform.stripAccents(s).lowercase()

    fun search(chapters: List<Chapter>, rawQuery: String, limit: Int = 150): List<SearchHit> {
        val queryWords = rawQuery.trim().split(whitespace).filter { it.isNotBlank() }
        val query = norm(queryWords.joinToString(" "))
        if (query.length < 2) return emptyList()
        val hits = mutableListOf<SearchHit>()
        for (chapter in chapters) {
            val words = chapter.text.split(whitespace).filter { it.isNotBlank() }
            if (words.isEmpty()) continue
            val normalized = words.map { if (ImageMarker.isMarker(it)) "" else norm(it) }
            val starts = IntArray(words.size)
            val joined = StringBuilder()
            normalized.forEachIndexed { i, w ->
                if (i > 0) joined.append(' ')
                starts[i] = joined.length
                joined.append(w)
            }
            var from = 0
            while (hits.size < limit) {
                val pos = joined.indexOf(query, from)
                if (pos < 0) break
                // palabra que contiene la posición: última cuyo inicio <= pos
                var lo = 0
                var hi = starts.size - 1
                while (lo < hi) {
                    val mid = (lo + hi + 1) / 2
                    if (starts[mid] <= pos) lo = mid else hi = mid - 1
                }
                val w = lo
                val end = (w + queryWords.size).coerceAtMost(words.size)
                hits += SearchHit(
                    chapterIndex = chapter.index,
                    chapterTitle = chapter.title,
                    wordIndex = w,
                    before = words.subList((w - 6).coerceAtLeast(0), w).joinToString(" "),
                    match = words.subList(w, end).joinToString(" "),
                    after = words.subList(end, (end + 9).coerceAtMost(words.size)).joinToString(" ")
                )
                from = pos + query.length
            }
            if (hits.size >= limit) break
        }
        return hits
    }
}
