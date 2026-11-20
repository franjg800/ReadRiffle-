package com.readriffle.app.ui.reading

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.readriffle.app.util.opticalRecognitionPointIndex

/**
 * La palabra RSVP actual, con la letra del punto de fijación óptimo marcada en el color
 * de acento y en negrita.
 *
 * Clave del RSVP: esa letra (el punto de fijación) tiene que caer siempre en el mismo
 * lugar de la pantalla, sea la palabra corta o larga -- si no, el texto se "descuadra"
 * en cada palabra y la lectura se siente brusca. Por eso la palabra no se centra como
 * bloque: se parte en tres trozos (antes / letra ORP / después) y un Layout a medida
 * coloca la letra ORP siempre en el centro horizontal, dejando que el prefijo crezca
 * hacia la izquierda y el sufijo hacia la derecha.
 */
@Composable
fun RsvpWordDisplay(
    word: String,
    fontFamily: FontFamily,
    accentColor: Color,
    textScale: Float,
    modifier: Modifier = Modifier
) = BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val density = LocalDensity.current
    val availableWidthPx = with(density) { maxWidth.toPx() }
    val textMeasurer = rememberTextMeasurer()
    val baseFontSize = 36 * textScale

    Crossfade(
        targetState = word,
        animationSpec = tween(90),
        label = "rsvp-word",
        modifier = Modifier.fillMaxWidth()
    ) { current ->
        if (current.isEmpty()) return@Crossfade

        val orpIndex = remember(current) { opticalRecognitionPointIndex(current) }
        val prefix = current.substring(0, orpIndex)
        val orpChar = current.substring(orpIndex, orpIndex + 1)
        val suffix = current.substring(orpIndex + 1)

        val onColor = MaterialTheme.colorScheme.onBackground

        // En pantallas estrechas (formato vertical) una palabra larga a un zoom alto podía no
        // caber en el ancho disponible y quedaba recortada en vez de encogerse ("en formato
        // vertical se corta el texto") -- más visible todavía en los fondos "Pantalla negra"/
        // "Pantalla blanca", donde no hay texto de fondo que disimule el recorte. Se mide la
        // palabra completa sin límite de ancho y, si no cabe, se reduce el tamaño de letra en
        // proporción (con un mínimo para no encogerla hasta ser ilegible).
        val measuredWidthPx = remember(current, fontFamily, baseFontSize) {
            textMeasurer.measure(
                text = current,
                style = TextStyle(fontFamily = fontFamily, fontSize = baseFontSize.sp)
            ).size.width
        }
        val fitScale = if (measuredWidthPx > 0 && measuredWidthPx > availableWidthPx) {
            (availableWidthPx / measuredWidthPx).coerceIn(0.45f, 1f)
        } else {
            1f
        }
        val fontSize = (baseFontSize * fitScale).sp

        Layout(
            content = {
                Text(text = prefix, fontFamily = fontFamily, fontSize = fontSize, color = onColor, maxLines = 1, softWrap = false)
                Text(
                    text = orpChar,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1,
                    softWrap = false
                )
                Text(text = suffix, fontFamily = fontFamily, fontSize = fontSize, color = onColor, maxLines = 1, softWrap = false)
            },
            modifier = Modifier.fillMaxWidth()
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val prefixPlaceable = measurables[0].measure(loose)
            val orpPlaceable = measurables[1].measure(loose)
            val suffixPlaceable = measurables[2].measure(loose)

            val width = constraints.maxWidth
            val height = maxOf(prefixPlaceable.height, orpPlaceable.height, suffixPlaceable.height)
            val centerX = width / 2

            layout(width, height) {
                val orpX = centerX - orpPlaceable.width / 2
                val prefixX = orpX - prefixPlaceable.width
                val suffixX = orpX + orpPlaceable.width

                prefixPlaceable.place(prefixX, (height - prefixPlaceable.height) / 2)
                orpPlaceable.place(orpX, (height - orpPlaceable.height) / 2)
                suffixPlaceable.place(suffixX, (height - suffixPlaceable.height) / 2)
            }
        }
    }
}
