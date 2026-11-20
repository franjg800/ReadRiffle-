package com.readriffle.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.readriffle.app.R
import kotlin.math.roundToInt

/**
 * Barra de progreso del capítulo actual, en páginas: pista gruesa con el tramo leído en el acento
 * y un tirador. Encima, "Pág. X de Y" a la izquierda y las páginas que QUEDAN a la derecha. Se
 * puede tocar o arrastrar para saltar a cualquier página; el texto se actualiza en vivo al
 * arrastrar y [onSeekPage] solo se llama al soltar el dedo.
 */
@Composable
fun ProgressScrubBar(
    currentPage: Int,
    totalPages: Int,
    onSeekPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxIndex = (totalPages - 1).coerceAtLeast(0)
    var draggingPage by remember { mutableStateOf<Int?>(null) }
    val displayedPage = (draggingPage ?: currentPage).coerceIn(0, maxIndex)
    val pagesLeft = maxIndex - displayedPage
    val description = stringResource(R.string.reading_progress_bar)

    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val ring = MaterialTheme.colorScheme.surface

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Pág. ${displayedPage + 1} de $totalPages",
                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when (pagesLeft) {
                    0 -> stringResource(R.string.reading_last_page)
                    1 -> stringResource(R.string.reading_pages_left_one)
                    else -> stringResource(R.string.reading_pages_left_many, pagesLeft)
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp) // zona táctil cómoda aunque la pista dibujada sea fina
                .semantics { contentDescription = description }
                .pointerInput(maxIndex) {
                    if (maxIndex == 0) return@pointerInput
                    val pad = 12.dp.toPx()
                    fun pageAt(x: Float): Int {
                        val usable = (size.width - 2 * pad).coerceAtLeast(1f)
                        return (((x - pad) / usable) * maxIndex).roundToInt().coerceIn(0, maxIndex)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        draggingPage = pageAt(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) { change.consume(); break }
                            draggingPage = pageAt(change.position.x)
                            change.consume()
                        }
                        val final = draggingPage
                        draggingPage = null
                        if (final != null) onSeekPage(final)
                    }
                }
        ) {
            val pad = 12.dp.toPx()
            val usable = (size.width - 2 * pad).coerceAtLeast(1f)
            val cy = size.height / 2f
            val trackW = 5.dp.toPx()
            val x = if (maxIndex == 0) size.width / 2f else pad + usable * displayedPage / maxIndex

            drawLine(track, Offset(pad, cy), Offset(size.width - pad, cy), trackW, StrokeCap.Round)
            if (maxIndex > 0) drawLine(accent, Offset(pad, cy), Offset(x, cy), trackW, StrokeCap.Round)
            // Tirador: anillo del color de la superficie + núcleo en el acento.
            drawCircle(Color.Black.copy(alpha = 0.18f), 11.dp.toPx(), Offset(x, cy + 1.dp.toPx()))
            drawCircle(ring, 10.dp.toPx(), Offset(x, cy))
            drawCircle(accent, 6.5.dp.toPx(), Offset(x, cy))
        }
    }
}
