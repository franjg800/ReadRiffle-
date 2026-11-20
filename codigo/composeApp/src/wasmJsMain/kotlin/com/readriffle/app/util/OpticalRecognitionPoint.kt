package com.readriffle.app.util

/**
 * Calcula el índice de la letra "óptima" de fijación dentro de una palabra (técnica estándar
 * de RSVP): una letra cercana al tercio izquierdo, ligeramente más adentro cuanto más larga
 * es la palabra, para que el ojo no tenga que moverse al leer.
 *
 * Ignora tanto la puntuación inicial (¿, ¡, comillas de apertura...) como la final, para que
 * el índice siempre caiga sobre una letra real de la palabra y no sobre un signo pegado.
 */
fun opticalRecognitionPointIndex(word: String): Int {
    if (word.isEmpty()) return 0
    val lastIndex = word.length - 1
    val leadingTrim = word.indexOfFirst { it.isLetterOrDigit() }.let { if (it == -1) 0 else it }
    val core = word.substring(leadingTrim).trimEnd { !it.isLetterOrDigit() }
    val len = core.length
    if (len <= 1) return leadingTrim.coerceIn(0, lastIndex)
    val orp = when {
        len <= 4 -> 1
        len <= 8 -> 2
        len <= 12 -> 3
        else -> 4
    }
    return (leadingTrim + orp.coerceAtMost(len - 1)).coerceIn(0, lastIndex)
}
