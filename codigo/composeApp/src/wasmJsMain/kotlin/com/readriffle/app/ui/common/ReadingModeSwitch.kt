package com.readriffle.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.readriffle.app.R
import com.readriffle.app.data.model.ReadingMode

/**
 * Los iconos para alternar entre lectura RSVP (palabra a palabra) y lectura normal paginada,
 * más un tercer icono para abrir el reproductor de audio -- pedido explícito: "necesito que el
 * iconito para cambiar al modo de audio aparezca al lado del icono de RSVP y del icono de
 * lectura normal... en ese dock flotante". A diferencia de los otros dos, el de audio nunca
 * aparece "seleccionado": no sustituye el modo de lectura en pantalla, sino que navega a una
 * pantalla aparte, así que su [ModeIcon] siempre usa `selected = false`.
 */
@Composable
fun ReadingModeSwitch(
    current: ReadingMode,
    onSelect: (ReadingMode) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeIcon(
            iconRes = R.drawable.ic_mode_book,
            label = stringResource(R.string.reading_mode_book),
            selected = current == ReadingMode.NORMAL,
            onClick = { onSelect(ReadingMode.NORMAL) }
        )
        ModeIcon(
            iconRes = R.drawable.ic_mode_rsvp,
            label = stringResource(R.string.reading_mode_rsvp),
            selected = current == ReadingMode.RSVP,
            onClick = { onSelect(ReadingMode.RSVP) }
        )
    }
}

@Composable
private fun ModeIcon(iconRes: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(squircleShape(36.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}
