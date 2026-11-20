package com.readriffle.app.ui.reading

/** Rango de caracteres (inicio inclusive, fin exclusive) de una palabra dentro de un texto. */
internal data class WordSpan(val start: Int, val endExclusive: Int)

/**
 * Divide [text] en palabras separadas por espacios, igual que
 * [com.readriffle.app.data.tokenizer.WordTokenizer] -- así el índice de palabra que se obtiene
 * aquí (por ejemplo al tocar un párrafo en modo normal) coincide exactamente con el índice de
 * palabra que usa el motor RSVP, sin desajustes entre ambos modos.
 */
internal fun wordSpans(text: String): List<WordSpan> =
    Regex("\\S+").findAll(text).map { WordSpan(it.range.first, it.range.last + 1) }.toList()

/**
 * Encuentra qué palabra de [spans] contiene (o está más cerca de) [charOffset] -- por ejemplo si
 * el toque cae justo sobre un espacio entre dos palabras, se elige la más próxima en vez de no
 * seleccionar nada.
 */
internal fun wordIndexAtChar(spans: List<WordSpan>, charOffset: Int): Int? {
    if (spans.isEmpty()) return null
    for ((idx, s) in spans.withIndex()) {
        if (charOffset >= s.start && charOffset < s.endExclusive) return idx
    }
    return spans.indices.minByOrNull { i ->
        val s = spans[i]
        if (charOffset < s.start) s.start - charOffset else charOffset - s.endExclusive + 1
    }
}
