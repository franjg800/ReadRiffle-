package com.readriffle.app.ui.reading

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import kotlin.math.max
import kotlin.math.min

/**
 * Texto de un párrafo del modo normal con selección de palabras por gesto: mantener pulsado
 * marca la palabra bajo el dedo, y arrastrar sin soltar la extiende palabra a palabra (el mismo
 * gesto que seleccionar texto de forma nativa). Al soltar, [onSelectionFinished] entrega el
 * rango de palabras (índices locales a este párrafo) para que quien lo use decida qué hacer --
 * saltar a RSVP desde ahí o resaltarlo, ver [NormalReaderContent].
 *
 * Los subrayados ya guardados ([highlightedRanges], rangos de palabras locales al párrafo) se
 * pintan con un fondo tenue del color de acento; la selección en curso, con un fondo algo más
 * marcado para dar feedback mientras se arrastra. [currentPositionWordIndex] (índice local, o
 * null si no cae en este párrafo) marca "dónde nos hemos quedado" al venir de RSVP: se pinta
 * con el propio color de acento en negrita, en vez de fondo, para que se lea igual que la
 * palabra resaltada en RSVP en vez de parecer un subrayado más.
 */
@Composable
internal fun SelectableParagraphText(
    paragraph: String,
    style: TextStyle,
    accentColor: Color,
    highlightedRanges: List<IntRange>,
    currentPositionWordIndex: Int? = null,
    pendingRange: IntRange? = null,
    onSelectionFinished: (startWordIndex: Int, endWordIndex: Int, selectedText: String, anchor: androidx.compose.ui.geometry.Rect) -> Unit
) {
    var coords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var layoutResult by remember(paragraph) { mutableStateOf<TextLayoutResult?>(null) }
    var liveSelection by remember(paragraph) { mutableStateOf<IntRange?>(null) }
    val spans = remember(paragraph) { wordSpans(paragraph) }

    val annotated = remember(paragraph, highlightedRanges, liveSelection, pendingRange, accentColor, currentPositionWordIndex) {
        buildAnnotatedString {
            append(paragraph)
            for (range in highlightedRanges) {
                val startSpan = spans.getOrNull(range.first)
                val endSpan = spans.getOrNull(range.last)
                if (startSpan != null && endSpan != null) {
                    addStyle(
                        SpanStyle(background = accentColor.copy(alpha = 0.22f)),
                        startSpan.start,
                        endSpan.endExclusive
                    )
                }
            }
            (liveSelection ?: pendingRange)?.let { sel ->
                val startSpan = spans.getOrNull(sel.first)
                val endSpan = spans.getOrNull(sel.last)
                if (startSpan != null && endSpan != null) {
                    addStyle(
                        SpanStyle(background = accentColor.copy(alpha = 0.42f)),
                        startSpan.start,
                        endSpan.endExclusive
                    )
                }
            }
            currentPositionWordIndex?.let { idx ->
                val span = spans.getOrNull(idx)
                if (span != null) {
                    addStyle(
                        SpanStyle(color = accentColor, fontWeight = FontWeight.Bold),
                        span.start,
                        span.endExclusive
                    )
                }
            }
        }
    }

    Text(
        text = annotated,
        style = style,
        onTextLayout = { layoutResult = it },
        modifier = Modifier.onGloballyPositioned { coords = it }.pointerInput(paragraph) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val charOffset = layoutResult?.getOffsetForPosition(offset)
                    val idx = charOffset?.let { wordIndexAtChar(spans, it) }
                    liveSelection = idx?.let { it..it }
                },
                onDrag = { change, _ ->
                    change.consume()
                    val charOffset = layoutResult?.getOffsetForPosition(change.position) ?: return@detectDragGesturesAfterLongPress
                    val idx = wordIndexAtChar(spans, charOffset) ?: return@detectDragGesturesAfterLongPress
                    val anchor = liveSelection?.first ?: idx
                    liveSelection = min(anchor, idx)..max(anchor, idx)
                },
                onDragEnd = {
                    val sel = liveSelection
                    if (sel != null) {
                        val text = (sel.first..sel.last).joinToString(" ") { i ->
                            val s = spans[i]
                            paragraph.substring(s.start, s.endExclusive)
                        }
                        // Rectángulo de la selección en coordenadas de ventana, para colocar la
                        // barra contextual pegada al texto.
                        val lr = layoutResult
                        val c = coords
                        val anchor = if (lr != null && c != null && c.isAttached) {
                            val b = lr.getPathForRange(spans[sel.first].start, spans[sel.last].endExclusive).getBounds()
                            androidx.compose.ui.geometry.Rect(c.localToRoot(b.topLeft), c.localToRoot(b.bottomRight))
                        } else androidx.compose.ui.geometry.Rect.Zero
                        onSelectionFinished(sel.first, sel.last, text, anchor)
                    }
                    liveSelection = null
                },
                onDragCancel = { liveSelection = null }
            )
        }
    )
}
