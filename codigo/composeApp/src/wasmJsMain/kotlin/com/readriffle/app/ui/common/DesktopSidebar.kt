package com.readriffle.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readriffle.app.R
import com.readriffle.app.theme.TitleDisplayFontFamily

/**
 * Barra lateral del diseño de ordenador (sustituye al dock flotante del móvil): logo, las cuatro
 * secciones con una pastilla que se desliza hasta la elegida, efecto al pasar el ratón y, abajo,
 * el estado de la sincronización y los atajos de teclado.
 */
@Composable
fun DesktopSidebar(
    current: DockDestination?,
    onSelect: (DockDestination) -> Unit,
    syncEmail: String?,
    isSyncing: Boolean,
    onSync: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val items = listOf(
        Triple(DockDestination.LIBRARY, "Biblioteca", R.drawable.ic_nav_library),
        Triple(DockDestination.NOTES, "Notas", R.drawable.ic_nav_notes),
        Triple(DockDestination.STORE, "Tienda", R.drawable.ic_nav_store),
        Triple(DockDestination.SETTINGS, "Ajustes", R.drawable.ic_nav_settings)
    )
    val selectedIndex = items.indexOfFirst { it.first == current }
    val itemHeight = 52.dp
    val gap = 6.dp
    val indicatorY by animateDpAsState(
        (itemHeight + gap) * selectedIndex.coerceAtLeast(0),
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "indicador-lateral"
    )
    val indicatorAlpha by animateFloatAsState(if (selectedIndex >= 0) 1f else 0f, label = "indicador-alfa")

    Column(
        Modifier.width(264.dp).fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 18.dp, vertical = 26.dp)
    ) {
        // Logo
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 6.dp, bottom = 34.dp)) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground), null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(42.dp).shadow(8.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                    .graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }
            )
            Text("Book Room", fontFamily = TitleDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        }

        Box {
            // Pastilla deslizante bajo la sección elegida.
            Box(
                Modifier.offset(y = indicatorY).fillMaxWidth().height(itemHeight)
                    .graphicsLayer { alpha = indicatorAlpha }
                    .shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = primary, spotColor = primary)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(lerp(primary, Color.White, 0.16f), primary)))
            )
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                items.forEachIndexed { i, (dest, label, icon) ->
                    SidebarItem(label, icon, selected = i == selectedIndex, height = itemHeight) { onSelect(dest) }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Sincronización
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                .clickable(onClick = onSync).handCursor().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isSyncing) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = primary)
                else Box(Modifier.size(8.dp).clip(CircleShape).background(if (syncEmail != null) Color(0xFF3FB27F) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
                Text(
                    if (syncEmail != null) "Sincronizado con Drive" else "Sin sincronizar",
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                syncEmail ?: "Pulsa para conectar Google Drive",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "Atajos: ← → pasar página · Espacio leer/pausar · Esc volver",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 12.dp, start = 4.dp)
        )
    }
}

@Composable
private fun SidebarItem(label: String, icon: Int, selected: Boolean, height: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else if (hovered) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(220), label = "color-item"
    )
    val hoverBg by animateFloatAsState(if (hovered && !selected) 1f else 0f, label = "hover-item")
    val shift by animateDpAsState(if (hovered && !selected) 4.dp else 0.dp, label = "desplazar-item")
    Row(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f * hoverBg))
            .hoverable(interaction).handCursor()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 16.dp + shift, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(21.dp))
        Text(label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = content)
    }
}
