package com.readriffle.app.data.model

/** Tipo de pausa que corresponde tras esta palabra, usado por el motor RSVP para calcular el delay. */
enum class PunctuationPause {
    NONE,
    COMMA,          // coma, punto y coma, dos puntos
    SENTENCE_END,   // punto, exclamación, interrogación
    PARAGRAPH_END,  // última palabra de un párrafo
    CHAPTER_END     // última palabra del capítulo
}

/**
 * Una palabra tokenizada lista para mostrarse en la pantalla de lectura RSVP.
 * [display] conserva la puntuación pegada (ej. "casa,", "¿Qué?") para que se vea natural.
 */
data class RsvpToken(
    val display: String,
    val pauseAfter: PunctuationPause,
    /** True si este "token" es una imagen del libro (se enseña como ▣ en RSVP y no se narra). */
    val isImage: Boolean = false
)
