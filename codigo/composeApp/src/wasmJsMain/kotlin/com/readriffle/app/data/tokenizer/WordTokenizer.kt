package com.readriffle.app.data.tokenizer

import com.readriffle.app.data.model.PunctuationPause
import com.readriffle.app.data.model.RsvpToken

/**
 * Convierte el texto plano de un capítulo en la lista de palabras que el motor RSVP
 * va a reproducir, clasificando qué pausa corresponde tras cada una (ver [PunctuationPause]).
 *
 * Reconoce abreviaturas comunes en español para no confundirlas con un fin de frase real.
 */
object WordTokenizer {

    private val ABBREVIATIONS = setOf(
        "sr.", "sra.", "srta.", "dr.", "dra.", "d.", "dña.", "prof.", "profa.",
        "pág.", "págs.", "vol.", "vols.", "etc.", "p.ej.", "ej.", "ud.", "uds.",
        "aprox.", "art.", "cap.", "núm.", "s.a.", "s.l.", "a.c.", "d.c.",
        "izq.", "der.", "tel.", "fig.", "ref.", "trad.", "ed.", "eds.", "cf.", "vs.", "gral."
    )

    private val SENTENCE_END_CHARS = setOf('.', '!', '?', '…')
    private val INTERMEDIATE_PAUSE_CHARS = setOf(',', ';', ':')
    private val TRIM_CHARS = charArrayOf('"', '\'', '”', '’', '“', '‘', ')', '(', '«', '»')

    fun tokenize(chapterText: String): List<RsvpToken> {
        if (chapterText.isBlank()) return emptyList()

        val tokens = mutableListOf<RsvpToken>()
        val paragraphs = chapterText.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }

        for ((pIndex, paragraph) in paragraphs.withIndex()) {
            val words = paragraph.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val isLastParagraph = pIndex == paragraphs.lastIndex

            for ((wIndex, rawWord) in words.withIndex()) {
                val isLastWordOfParagraph = wIndex == words.lastIndex
                val isChapterEnd = isLastWordOfParagraph && isLastParagraph
                val isParagraphEnd = isLastWordOfParagraph && !isLastParagraph

                if (ImageMarker.isMarker(rawWord)) {
                    tokens.add(RsvpToken(display = "▣", pauseAfter = PunctuationPause.PARAGRAPH_END, isImage = true))
                    continue
                }
                val pause = classifyPause(rawWord, isParagraphEnd, isChapterEnd)
                tokens.add(RsvpToken(display = rawWord, pauseAfter = pause))
            }
        }
        return tokens
    }

    private fun classifyPause(rawWord: String, isParagraphEnd: Boolean, isChapterEnd: Boolean): PunctuationPause {
        val normalized = rawWord.lowercase().trim(*TRIM_CHARS)
        if (normalized in ABBREVIATIONS) {
            return when {
                isChapterEnd -> PunctuationPause.CHAPTER_END
                isParagraphEnd -> PunctuationPause.PARAGRAPH_END
                else -> PunctuationPause.NONE
            }
        }

        val lastMeaningfulChar = rawWord.trimEnd(*TRIM_CHARS).lastOrNull()
        val base = when (lastMeaningfulChar) {
            in SENTENCE_END_CHARS -> PunctuationPause.SENTENCE_END
            in INTERMEDIATE_PAUSE_CHARS -> PunctuationPause.COMMA
            else -> PunctuationPause.NONE
        }

        return when {
            isChapterEnd -> PunctuationPause.CHAPTER_END
            isParagraphEnd -> PunctuationPause.PARAGRAPH_END
            else -> base
        }
    }
}

/**
 * Las imágenes del libro viajan dentro del texto del capítulo como un párrafo propio con la forma
 * `[[IMG:/ruta/absoluta.jpg]]` (sin espacios), que cuenta como UNA palabra: así los índices de
 * palabra de RSVP, la paginación y los subrayados siguen siendo coherentes.
 */
object ImageMarker {
    private val regex = Regex("^\\[\\[IMG:(.+)\\]\\]$")

    fun isMarker(text: String): Boolean = text.startsWith("[[IMG:") && text.endsWith("]]")

    /** Ruta de la imagen si [paragraph] es solo un marcador; null si es texto normal. */
    fun pathOf(paragraph: String): String? = regex.matchEntire(paragraph.trim())?.groupValues?.get(1)

    fun make(path: String) = "[[IMG:$path]]"

    /** El texto sin los marcadores, para fondos decorativos o búsquedas. */
    fun strip(text: String): String =
        text.split(Regex("\\n\\s*\\n")).filterNot { pathOf(it) != null }.joinToString("\n\n")
}
