package com.readriffle.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import com.readriffle.app.R
import kotlin.math.roundToInt

/**
 * Barra contextual de selección: una cápsula oscura (clara en tema oscuro) que flota justo
 * encima del texto seleccionado -- o debajo, si arriba no cabe -- como el menú de selección de
 * los lectores actuales. Mientras está visible, el texto sigue marcado (ver
 * [SelectableParagraphText]). [anchor] es el rectángulo de la selección en coordenadas de ventana.
 * Acciones de siempre: pasar a RSVP desde aquí, subrayar, generar imagen, diccionario y cancelar.
 */
@Composable
internal fun WordSelectionActionBar(
    anchor: Rect,
    onReadFromHere: () -> Unit,
    onHighlight: () -> Unit,
    onGenerateImage: () -> Unit,
    onDictionary: () -> Unit,
    onDismiss: () -> Unit
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }
    ) {
        val containerW = constraints.maxWidth
        val gap = with(density) { 14.dp.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val topLimit = statusTop + with(density) { 76.dp.toPx() } - origin.y
        val a = anchor.translate(-origin.x, -origin.y)
        val x = (a.center.x - size.width / 2f).coerceIn(margin, (containerW - size.width - margin).coerceAtLeast(margin))
        val above = a.top - size.height - gap
        val y = if (above >= topLimit) above else a.bottom + gap

        val bg = MaterialTheme.colorScheme.inverseSurface
        val fg = MaterialTheme.colorScheme.inverseOnSurface
        Row(
            modifier = Modifier
                .onSizeChanged { size = it }
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .alpha(if (size == IntSize.Zero) 0f else 1f)
                .shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(RoundedCornerShape(22.dp))
                .background(bg)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            SelectionAction(R.drawable.ic_mode_rsvp, stringResource(R.string.selection_read_from_here), fg, onReadFromHere)
            SelectionAction(R.drawable.ic_highlight, stringResource(R.string.selection_highlight), fg, onHighlight)
            SelectionAction(R.drawable.ic_image, stringResource(R.string.selection_generate_image), fg, onGenerateImage)
            SelectionAction(R.drawable.ic_dictionary, stringResource(R.string.selection_dictionary), fg, onDictionary)
            SelectionAction(R.drawable.ic_close, stringResource(R.string.selection_dismiss), fg.copy(alpha = 0.7f), onDismiss)
        }
    }
}

@Composable
private fun SelectionAction(iconRes: Int, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = label, fontSize = 10.5.sp, lineHeight = 12.sp, color = tint,
            textAlign = TextAlign.Center, maxLines = 2
        )
    }
}
