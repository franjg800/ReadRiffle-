package com.readriffle.app.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.readriffle.app.theme.TitleDisplayFontFamily

/**
 * Título grande de cabecera de pantalla (Biblioteca, Notas, Convertir, Ajustes): tipografía
 * propia y exclusiva (ver [TitleDisplayFontFamily], nunca una de las 6 de lectura), más grande
 * que el resto del texto, y en el color de acento activo -- pedido explícito del usuario: si en
 * Ajustes se elige, por ejemplo, azul, ese título también cambia a azul.
 *
 * OJO con `fontSize`/`lineHeight`: al principio esto solo sobreescribía `fontSize` sobre
 * `headlineMedium.copy(...)`, heredando su `lineHeight` (~40sp, pensado para un `fontSize` de
 * 28sp). Con un `fontSize` mayor que ese `lineHeight` original, Compose recorta el texto dentro
 * de la caja de línea heredada -- exactamente el "título cortado, se corta por la parte inferior
 * y a media palabra" que reportó el usuario en el conversor. Por eso aquí `lineHeight` se fija
 * explícitamente en proporción al nuevo `fontSize`, nunca heredado.
 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = TitleDisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            lineHeight = 48.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        softWrap = true,
        modifier = modifier
    )
}
