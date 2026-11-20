package com.readriffle.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Las dos líneas horizontales que marcan por encima y por debajo dónde cae la palabra RSVP --
 * compartidas por [BlackLinesBackground], [WhiteBackground] y [FlowingWordFocusBackdrop] (este
 * último para el modo "Texto fluido", ver más abajo). [horizontalPadding] controla cuánto se
 * recortan los extremos de la línea respecto al ancho del contenedor. [lineOffset] es la
 * distancia de cada línea al centro -- escalada con el zoom de texto (ver cada función de más
 * abajo) para que el hueco entre líneas crezca o encoja junto con la palabra.
 */
@Composable
private fun WordFocusLines(lineColor: Color, horizontalPadding: Dp, lineOffset: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        val line = @Composable { offsetY: Dp ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = offsetY)
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .height(1.dp)
                    .background(lineColor.copy(alpha = 0.35f))
            )
        }
        line(-lineOffset)
        line(lineOffset)
    }
}

/**
 * Fondo "Pantalla negra" del modo RSVP: sin texto de fondo fluyendo, con dos líneas horizontales
 * marcando por encima y por debajo dónde cae la palabra (para que el ojo sepa siempre dónde
 * mirar).
 *
 * OJO: al principio esto forzaba negro puro ([Color.Black]) pase lo que pase, deliberadamente
 * independiente del tema activo. El usuario pidió lo contrario tras probarlo con la variante
 * "Oscuro profundo": el fondo tiene que seguir el tema igual que ya hacía "Texto fluido"
 * ("eso funciona perfecto cuando ponemos texto fluido, pero cuando ponemos la pantalla limpia,
 * sigue en blanco... mismo caso para el negro, tampoco cambia el color de la letra de blanco a
 * gris"). Como desde la v23 este modo ya solo se activa junto a un tema oscuro (el menú de tema
 * los sincroniza, ver `ReadingViewModel.setThemeChoice`), usar aquí directamente los colores del
 * tema activo es seguro y da exactamente el matiz esperado (oscuro estándar o profundo).
 * [textScale] escala el hueco entre líneas junto con el tamaño de la palabra.
 */
@Composable
fun BlackLinesBackground(lineColor: Color, textScale: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WordFocusLines(
            lineColor = lineColor,
            horizontalPadding = 56.dp,
            lineOffset = 34.dp * textScale,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Fondo "Pantalla blanca" del modo RSVP -- sustituye a la opción "Pulso" original. Mismo
 * planteamiento que [BlackLinesBackground] (ver su comentario: ya no fuerza blanco puro, sigue
 * el fondo del tema activo -- claro estándar o claro cálido según corresponda).
 */
@Composable
fun WhiteBackground(lineColor: Color, textScale: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WordFocusLines(
            lineColor = lineColor,
            horizontalPadding = 56.dp,
            lineOffset = 34.dp * textScale,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Para el modo "Texto fluido": una franja opaca (del color de fondo del tema activo) detrás de
 * la palabra, con las mismas dos líneas de marca que ya usan "Pantalla negra"/"Pantalla blanca"
 * -- pedido explícito del usuario porque ver el texto de fondo fluyendo justo debajo de la
 * palabra fija "marea un poco". No tapa toda la pantalla (el texto de fondo sigue fluyendo
 * visible alrededor): solo esta franja alrededor de la palabra queda opaca, con esquinas
 * redondeadas para que no se note como un recorte brusco. [textScale] escala tanto la altura de
 * la franja como el hueco entre líneas, para que sigan ajustadas a la palabra a cualquier zoom.
 */
@Composable
fun FlowingWordFocusBackdrop(lineColor: Color, textScale: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(88.dp * textScale)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.background)
    ) {
        // horizontalPadding más grande que el de las líneas a pantalla completa (56.dp) para que
        // dentro de esta franja, más estrecha, las líneas también queden más cortas en
        // proporción -- pedido explícito: "que las dos líneas sean un poquito más estrechas".
        WordFocusLines(
            lineColor = lineColor,
            horizontalPadding = 36.dp,
            lineOffset = 34.dp * textScale,
            modifier = Modifier.fillMaxSize()
        )
    }
}
