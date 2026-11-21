package com.readriffle.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import com.readriffle.app.platform.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

/** Las cuatro secciones principales de la app, accesibles desde el dock flotante inferior. */
enum class DockDestination { LIBRARY, NOTES, STORE, SETTINGS }

/**
 * Dock flotante inferior con las cuatro secciones principales de la app: Biblioteca, Notas
 * (subrayados y anotaciones de todos los libros), Tienda (catálogo de libros de dominio público
 * gratis y legales) y Ajustes (donde también vive el conversor PDF a EPUB). Sustituye el icono de ajustes que antes
 * vivía en la barra superior de la Biblioteca, para que la navegación entre estas pantallas sea
 * consistente y esté siempre a mano, sin ocupar una barra superior completa.
 */
@Composable
fun MainDock(
    current: DockDestination,
    onLibrary: () -> Unit,
    onNotes: () -> Unit,
    onStore: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // En ordenador la navegación va en la barra lateral (ver DesktopSidebar).
    if (LocalWide.current) return

    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 10.dp)
            .shadow(16.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(1.dp, onSurface.copy(alpha = 0.08f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockItem(R.drawable.ic_nav_library, stringResource(R.string.dock_library), current == DockDestination.LIBRARY, onLibrary)
        DockItem(R.drawable.ic_nav_notes, stringResource(R.string.dock_notes), current == DockDestination.NOTES, onNotes)
        DockItem(R.drawable.ic_nav_store, stringResource(R.string.dock_store), current == DockDestination.STORE, onStore)
        DockItem(R.drawable.ic_nav_settings, stringResource(R.string.dock_settings), current == DockDestination.SETTINGS, onSettings)
    }
}

/**
 * Elemento del dock: icono de línea; el activo se "expande" en una píldora con el acento del
 * tema y su nombre al lado (patrón actual de navegación flotante), el resto son solo iconos.
 */
@Composable
private fun DockItem(iconRes: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (selected) primary.copy(alpha = 0.16f) else Color.Transparent, label = "dock-bg")
    val tint by animateColorAsState(if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "dock-tint")
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = if (selected) 18.dp else 15.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(painterResource(iconRes), contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        if (selected) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1, softWrap = false)
        }
    }
}
