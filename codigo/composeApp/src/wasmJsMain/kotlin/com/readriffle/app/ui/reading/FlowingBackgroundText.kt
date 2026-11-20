package com.readriffle.app.ui.reading

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * La "capa de fondo" de la pantalla de lectura: todo el texto del capítulo, muy tenue,
 * fluyendo en scroll vertical continuo. No está pensada para leerse — solo aporta la
 * sensación de "libro en movimiento" detrás de la palabra RSVP.
 *
 * [active] indica si debe fluir en este momento (modo automático + reproduciendo,
 * o modo "mantener pulsado" + dedo sobre la pantalla).
 */
@Composable
fun FlowingBackgroundText(
    text: String,
    fontFamily: FontFamily,
    active: Boolean,
    wpm: Int,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current

    var offsetPx by remember { mutableFloatStateOf(0f) }
    // El capítulo se mide en bloques (~1500 caracteres) en vez de como un único texto enorme:
    // así en cada frame solo se dibujan los bloques que están a la vista, en lugar de repintar
    // el capítulo entero tres veces (lo que hacía que el texto de fondo no fluyera con suavidad).
    var chunks by remember(text, fontFamily) { mutableStateOf<List<MeasuredChunk>>(emptyList()) }
    var totalHeightPx by remember(text, fontFamily) { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)

        // IMPORTANTE: hay que pasar `constraints` con el ancho de pantalla -- sin él, `measure`
        // usa un ancho no acotado (Constraints() = infinito) y cada párrafo se mide como una
        // única línea kilométrica en vez de envolver el texto. Con capítulos largos o párrafos
        // muy largos, eso producía un layout de texto enorme que había que volver a dibujar en
        // el Canvas en cada frame de la animación -- el origen de que "algunos archivos EPUB"
        // (los de capítulos/párrafos más largos) fueran a tirones ("va como a cachito, se queda
        // pillado") en vez de fluir con suavidad. También se mueve el cálculo fuera del hilo
        // principal (es el texto del capítulo entero) para no bloquear la UI al entrar en RSVP.
        LaunchedEffect(text, fontFamily, widthPx) {
            val style = TextStyle(fontFamily = fontFamily, fontSize = 20.sp, lineHeight = 32.sp, color = textColor)
            val measured = withContext(Dispatchers.Default) {
                val parts = mutableListOf<String>()
                val sb = StringBuilder()
                for (line in text.split('\n')) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(line)
                    if (sb.length >= 1500) { parts += sb.toString(); sb.clear() }
                }
                if (sb.isNotEmpty()) parts += sb.toString()
                var top = 0f
                parts.map { part ->
                    val r = textMeasurer.measure(text = part, style = style, constraints = Constraints(maxWidth = widthPx))
                    MeasuredChunk(r, top).also { top += r.size.height }
                }.let { list -> list to top }
            }
            chunks = measured.first
            totalHeightPx = measured.second
        }

        LaunchedEffect(active, wpm) {
            if (!active) return@LaunchedEffect
            val pxPerSecond = with(density) { (24 + wpm * 0.12f).dp.toPx() }
            var lastNanos = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { now ->
                    val deltaSeconds = ((now - lastNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
                    lastNanos = now
                    val totalHeight = totalHeightPx.coerceAtLeast(1f)
                    offsetPx = (offsetPx + pxPerSecond * deltaSeconds) % totalHeight
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
        ) {
            val totalHeight = totalHeightPx
            if (totalHeight <= 0f || chunks.isEmpty()) return@Canvas
            val startY = -offsetPx
            val viewH = size.height
            // Tres copias en vertical para el scroll infinito, pero solo se dibuja lo visible.
            for (k in -1..1) {
                for (c in chunks) {
                    val y = startY + k * totalHeight + c.top
                    if (y + c.layout.size.height < 0f || y > viewH) continue
                    drawText(c.layout, topLeft = Offset(0f, y))
                }
            }
        }
    }
}

/** Un bloque del capítulo ya medido y su posición vertical dentro del texto completo. */
private class MeasuredChunk(val layout: TextLayoutResult, val top: Float)
