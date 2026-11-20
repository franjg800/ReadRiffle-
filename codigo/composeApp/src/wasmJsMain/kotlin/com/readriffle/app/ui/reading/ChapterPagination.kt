package com.readriffle.app.ui.reading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import com.readriffle.app.data.tokenizer.WordTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resultado de paginar el capítulo actual: las páginas ya repartidas (usadas por el modo normal
 * para renderizarlas) y la palabra de inicio de cada una (para saber a qué página corresponde la
 * posición de lectura actual y para poder saltar a una página exacta).
 *
 * Se calcula UNA SOLA VEZ en [ReadingScreen] -- el enrutador que envuelve tanto RSVP como el modo
 * normal -- en vez de dentro de cada modo por separado: así "cuántas páginas tiene el capítulo"
 * es el mismo número se mire desde el menú de pausa de RSVP o desde el modo normal, necesario
 * para que la barra de progreso de ambos modos pueda mostrar "página X de Y" y saltar a una
 * página concreta de forma consistente.
 */
class ChapterPagination(
    val pages: List<List<String>>,
    val pageWordOffsets: IntArray,
    val pageIndex: Int
) {
    val totalPages: Int get() = pages.size

    /** Palabra (índice dentro del capítulo) con la que empieza [page], acotada a un rango válido. */
    fun wordOffsetForPage(page: Int): Int =
        pageWordOffsets[page.coerceIn(0, pages.lastIndex.coerceAtLeast(0))]
}

@Composable
fun rememberChapterPagination(
    chapterText: String,
    style: TextStyle,
    textMeasurer: TextMeasurer,
    maxWidthPx: Int,
    maxHeightPx: Int,
    paragraphGapPx: Int,
    wordIndex: Int
): ChapterPagination? {
    val paragraphs = remember(chapterText) {
        chapterText.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    val pages by produceState<List<List<String>>?>(initialValue = null, paragraphs, style, maxWidthPx, maxHeightPx, paragraphGapPx) {
        value = null
        value = withContext(Dispatchers.Default) {
            paginateParagraphs(paragraphs, textMeasurer, style, maxWidthPx, maxHeightPx, paragraphGapPx)
        }
    }
    val safePages = pages ?: return null

    // Palabra con la que empieza cada página: se re-tokeniza el texto de cada página por
    // separado -- como las páginas son un reparto exacto del mismo texto (sin perder ni
    // fusionar palabras), la suma de estos recuentos coincide con el recuento total de tokens
    // del capítulo.
    val pageWordOffsets = remember(safePages) {
        val offsets = IntArray(safePages.size + 1)
        var running = 0
        for (i in safePages.indices) {
            offsets[i] = running
            running += WordTokenizer.tokenize(safePages[i].joinToString("\n\n")).size
        }
        offsets[safePages.size] = running
        offsets
    }

    val pageIndex = remember(safePages, wordIndex) {
        var idx = 0
        for (i in safePages.indices) {
            if (pageWordOffsets[i] <= wordIndex) idx = i else break
        }
        idx.coerceIn(0, safePages.lastIndex.coerceAtLeast(0))
    }

    return remember(safePages, pageIndex) { ChapterPagination(safePages, pageWordOffsets, pageIndex) }
}

/**
 * Reparte los párrafos de un capítulo en páginas que quepan en [maxHeightPx] (con ancho
 * [maxWidthPx]): mide cada párrafo entero primero; si no cabe completo en lo que queda de
 * página, corta por la última línea que sí quepa y continúa el resto en la página siguiente.
 */
internal fun paginateParagraphs(
    paragraphs: List<String>,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    maxWidthPx: Int,
    maxHeightPx: Int,
    paragraphGapPx: Int
): List<List<String>> {
    if (paragraphs.isEmpty() || maxWidthPx <= 0 || maxHeightPx <= 0) return listOf(emptyList())

    val pages = mutableListOf<List<String>>()
    var currentPage = mutableListOf<String>()
    var usedHeight = 0
    val constraints = Constraints(maxWidth = maxWidthPx)

    for (paragraph in paragraphs) {
        // Una imagen ocupa una página entera, ella sola.
        if (com.readriffle.app.data.tokenizer.ImageMarker.pathOf(paragraph) != null) {
            if (currentPage.isNotEmpty()) {
                pages.add(currentPage)
                currentPage = mutableListOf()
                usedHeight = 0
            }
            pages.add(listOf(paragraph))
            continue
        }
        var remaining = paragraph
        var safetyCounter = 0
        while (remaining.isNotEmpty() && safetyCounter < 500) {
            safetyCounter++
            val layout = textMeasurer.measure(remaining, style, constraints = constraints)
            val gap = if (currentPage.isNotEmpty()) paragraphGapPx else 0
            val available = maxHeightPx - usedHeight - gap

            if (layout.size.height <= available) {
                currentPage.add(remaining)
                usedHeight += layout.size.height + gap
                remaining = ""
            } else {
                var fitLines = 0
                for (lineIndex in 0 until layout.lineCount) {
                    if (layout.getLineBottom(lineIndex) <= available) fitLines = lineIndex + 1 else break
                }
                if (fitLines == 0) {
                    if (currentPage.isNotEmpty()) {
                        pages.add(currentPage)
                        currentPage = mutableListOf()
                        usedHeight = 0
                        continue
                    }
                    fitLines = 1
                }
                val cutOffset = layout.getLineEnd(fitLines - 1, visibleEnd = true).coerceIn(1, remaining.length)
                val pageText = remaining.substring(0, cutOffset).trimEnd()
                val restText = remaining.substring(cutOffset).trimStart()
                if (pageText.isNotEmpty()) currentPage.add(pageText)
                pages.add(currentPage)
                currentPage = mutableListOf()
                usedHeight = 0
                remaining = if (restText == remaining) "" else restText
            }
        }
    }
    if (currentPage.isNotEmpty()) pages.add(currentPage)
    return pages.ifEmpty { listOf(emptyList()) }
}
