package com.readriffle.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.readriffle.app.R
import com.readriffle.app.data.model.BookMeta
import kotlin.math.ceil

/**
 * Formatea minutos restantes de lectura como "1 h 30 min" (si hay al menos una hora) o
 * "45 min" (si no la hay) -- pedido explícito del usuario, igual que el tiempo restante de un
 * audiolibro. Un único punto compartido entre la pantalla RSVP y la Biblioteca para que ambas
 * formateen exactamente igual.
 */
@Composable
fun formatRemainingTime(totalMinutes: Int): String {
    val minutes = totalMinutes.coerceAtLeast(0)
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) {
        stringResource(R.string.time_remaining_hours_minutes_format, hours, mins)
    } else {
        stringResource(R.string.time_remaining_minutes_format, mins)
    }
}

/**
 * Minutos restantes de un libro de la Biblioteca (sin tener el `ReadingViewModel` cargado, que
 * solo existe mientras se está leyendo) -- a partir del total de palabras ya persistido en
 * [BookMeta.totalWordCount] (ver `LibraryRepository.finishImport`/`updateTotalWordCount`), su
 * fracción de progreso del libro completo, y la última velocidad (PPM) usada, pedida
 * explícitamente: "el tiempo que queda total del libro a la velocidad a la que estamos yendo".
 * 0 en libros aún no retocados desde que existe este campo (`totalWordCount == 0`) hasta que se
 * abran una vez y se rellene solo.
 */
fun bookMinutesRemaining(meta: BookMeta, wpm: Int): Int {
    if (meta.totalWordCount <= 0 || wpm <= 0) return 0
    val wordsRemaining = (meta.totalWordCount * (1f - meta.progressFraction.coerceIn(0f, 1f))).toInt().coerceAtLeast(0)
    if (wordsRemaining == 0) return 0
    return ceil(wordsRemaining.toFloat() / wpm.toFloat()).toInt()
}
