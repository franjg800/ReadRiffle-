package com.readriffle.app.viewmodel

import com.readriffle.app.data.model.PunctuationPause
import com.readriffle.app.data.model.RsvpToken

/**
 * Cálculo del tiempo (delay) de cada palabra en la reproducción RSVP: un tiempo base a partir
 * del WPM, más tiempo extra para palabras largas y para las distintas pausas de puntuación,
 * todo escalado por la "intensidad de ralentizado" que el usuario ajusta en Ajustes.
 */
object RsvpTiming {

    private const val LONG_WORD_THRESHOLD = 8
    private const val LONG_WORD_MS_PER_CHAR = 18f
    private const val MAX_LONG_WORD_EXTRA_MS = 220f

    private const val CHAPTER_END_EXTRA_MS = 950f

    private const val MIN_INTENSITY_FACTOR = 0.15f
    private const val MAX_INTENSITY_FACTOR = 0.80f

    /** Mapea el slider 0f (Sutil) .. 1f (Marcado) al factor real aplicado a los tiempos extra. */
    fun intensityFactor(intensity: Float): Float {
        val clamped = intensity.coerceIn(0f, 1f)
        return MIN_INTENSITY_FACTOR + clamped * (MAX_INTENSITY_FACTOR - MIN_INTENSITY_FACTOR)
    }

    /**
     * [paragraphPauseMs], [commaPauseMs] y [sentenceEndPauseMs] son las pausas de fin de
     * párrafo, coma y fin de frase tal y como las fija el usuario en Ajustes -- cada una un
     * valor absoluto en milisegundos, aplicado tal cual y NO escalado por [intensity], así el
     * usuario tiene control directo y total sobre cada una, independientes entre sí y del resto
     * de tiempos extra (palabra larga y fin de capítulo, que sí siguen escalados por la
     * intensidad general).
     */
    fun delayMillis(
        token: RsvpToken,
        wpm: Int,
        intensity: Float,
        paragraphPauseMs: Int,
        commaPauseMs: Int,
        sentenceEndPauseMs: Int
    ): Long {
        val safeWpm = wpm.coerceAtLeast(60)
        val base = 60_000f / safeWpm
        val factor = intensityFactor(intensity)

        var extra = 0f

        val letters = token.display.count { it.isLetterOrDigit() }
        if (letters > LONG_WORD_THRESHOLD) {
            extra += ((letters - LONG_WORD_THRESHOLD) * LONG_WORD_MS_PER_CHAR).coerceAtMost(MAX_LONG_WORD_EXTRA_MS)
        }

        extra += when (token.pauseAfter) {
            PunctuationPause.NONE -> 0f
            PunctuationPause.COMMA -> 0f
            PunctuationPause.SENTENCE_END -> 0f
            PunctuationPause.PARAGRAPH_END -> 0f
            PunctuationPause.CHAPTER_END -> CHAPTER_END_EXTRA_MS
        }

        val absoluteExtra = when (token.pauseAfter) {
            PunctuationPause.COMMA -> commaPauseMs.toFloat()
            PunctuationPause.SENTENCE_END -> sentenceEndPauseMs.toFloat()
            PunctuationPause.PARAGRAPH_END -> paragraphPauseMs.toFloat()
            else -> 0f
        }

        return (base + extra * factor + absoluteExtra).toLong().coerceAtLeast(20L)
    }
}
