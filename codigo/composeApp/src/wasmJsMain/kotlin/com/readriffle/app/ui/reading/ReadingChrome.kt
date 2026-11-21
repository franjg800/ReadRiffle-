package com.readriffle.app.ui.reading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.readriffle.app.platform.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.readriffle.app.R
import com.readriffle.app.data.model.ReadingMode
import com.readriffle.app.theme.DarkBackground
import com.readriffle.app.theme.DarkOnBackground
import com.readriffle.app.theme.DarkOnBackgroundDim
import com.readriffle.app.theme.LightBackground
import com.readriffle.app.theme.LightOnBackground
import com.readriffle.app.theme.LightWarmBackground
import com.readriffle.app.theme.LightWarmOnBackground
import com.readriffle.app.theme.ReaderFontOption
import com.readriffle.app.theme.RsvpBackgroundMode
import com.readriffle.app.theme.ThemeMode
import com.readriffle.app.theme.ThemeVariant
import com.readriffle.app.ui.common.ProgressScrubBar
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Rediseño de la "cromática" de la pantalla de lectura. Lenguaje común: superficies flotantes
 * tipo cápsula, casi opacas, con un filo fino y una sombra suave (sirven igual en claro y en
 * oscuro), iconos de 22 dp sin caja propia y un solo color de acento para lo activo. Nada de
 * botones sueltos por la pantalla: arriba dos cápsulas, abajo un panel, y los ajustes de texto
 * en una hoja con vista previa real (como hacen los lectores actuales).
 */

/** Superficie flotante: relleno del tema casi opaco + filo de 1 dp + sombra suave. */
internal fun Modifier.glass(shape: Shape, elevation: Int = 10): Modifier = composed {
    val c = MaterialTheme.colorScheme
    this
        .shadow(elevation.dp, shape, ambientColor = Color.Black.copy(alpha = 0.30f), spotColor = Color.Black.copy(alpha = 0.30f))
        .clip(shape)
        .background(c.surface.copy(alpha = 0.95f))
        .border(1.dp, c.onSurface.copy(alpha = 0.09f), shape)
}

@Composable
internal fun ChromeCapsule(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.glass(RoundedCornerShape(50)).padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
internal fun ChromeIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (active) primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Una acción del menú "más" (los tres puntos). */
internal data class ChromeMenuAction(val iconRes: Int, val label: String, val onClick: () -> Unit)

/**
 * Barra superior: a la izquierda la cápsula de salir; a la derecha la cápsula con marcador
 * (solo en modo normal), índice, ajustes de texto ("Aa") y el menú de los tres puntos con el resto
 * de acciones, ahora con su nombre en vez de iconos sueltos.
 */
@Composable
internal fun ReadingTopBar(
    isBookmarked: Boolean?,
    onExit: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenIndex: () -> Unit,
    onOpenSettings: () -> Unit,
    menuActions: List<ChromeMenuAction>,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChromeCapsule { ChromeIconButton(R.drawable.ic_exit, "Salir", onExit) }
        ChromeCapsule {
            if (isBookmarked != null) {
                ChromeIconButton(
                    if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                    if (isBookmarked) "Quitar marcador" else "Marcar página",
                    onToggleBookmark, active = isBookmarked
                )
            }
            ChromeIconButton(R.drawable.ic_index, "Índice", onOpenIndex)
            ChromeIconButton(R.drawable.ic_text_aa, "Texto y tema", onOpenSettings)
            Box {
                ChromeIconButton(R.drawable.ic_more, "Más opciones", { menuOpen = true }, active = menuOpen)
                ChromeMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, actions = menuActions)
            }
        }
    }
}

/** Menú de los tres puntos: panel flotante con una fila por acción (icono + nombre). */
@Composable
private fun ChromeMenu(expanded: Boolean, onDismiss: () -> Unit, actions: List<ChromeMenuAction>) {
    if (!expanded) return
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.TopEnd,
        offset = with(density) { IntOffset(0, 52.dp.roundToPx()) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        val state = remember { MutableTransitionState(false) }
        state.targetState = true
        AnimatedVisibility(
            visibleState = state,
            enter = fadeIn(tween(130)) + scaleIn(tween(160), initialScale = 0.92f, transformOrigin = TransformOrigin(1f, 0f)),
            exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.95f, transformOrigin = TransformOrigin(1f, 0f))
        ) {
            Column(
                modifier = Modifier.width(250.dp).glass(RoundedCornerShape(22.dp), elevation = 16).padding(vertical = 8.dp)
            ) {
                actions.forEach { a ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onDismiss(); a.onClick() }
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(painterResource(a.iconRes), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Text(a.label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/**
 * Panel inferior (el "dock" de la lectura), más grande que antes: arriba la barra de progreso
 * con su texto, debajo el selector de modo con iconos y nombre (Libro / RSVP / Escuchar).
 */
@Composable
internal fun ReadingBottomPanel(
    readingMode: ReadingMode,
    onSelectMode: (ReadingMode) -> Unit,
    onOpenPlayer: () -> Unit,
    currentPage: Int,
    totalPages: Int,
    onSeekPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(30.dp), elevation = 14)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProgressScrubBar(currentPage = currentPage, totalPages = totalPages, onSeekPage = onSeekPage)
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeSegment(R.drawable.ic_mode_page, "Libro", readingMode == ReadingMode.NORMAL, Modifier.weight(1f)) { onSelectMode(ReadingMode.NORMAL) }
            ModeSegment(R.drawable.ic_mode_rsvp, "RSVP", readingMode == ReadingMode.RSVP, Modifier.weight(1f)) { onSelectMode(ReadingMode.RSVP) }
        }
    }
}

@Composable
private fun ModeSegment(iconRes: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent, label = "seg-bg")
    val tint by animateColorAsState(if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "seg-tint")
    Row(
        modifier = modifier.height(50.dp).clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(iconRes), null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = tint, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
//  Hoja "Texto y tema"
// ---------------------------------------------------------------------------------------------

/**
 * Ajustes de lectura en una sola hoja inferior: tamaño de texto (cinco pasos), tipografía (cada
 * una escrita en su propia fuente), tema (cuatro muestras reales) y, en RSVP, el fondo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingSettingsSheet(
    textScale: Float,
    zoomPresets: List<Float>,
    onSetTextScale: (Float) -> Unit,
    fontOption: ReaderFontOption,
    onSelectFont: (ReaderFontOption) -> Unit,
    themeMode: ThemeMode,
    themeVariant: ThemeVariant,
    onSetTheme: (ThemeMode, ThemeVariant) -> Unit,
    isDark: Boolean,
    rsvpBackground: RsvpBackgroundMode?,
    onSetRsvpBackground: (RsvpBackgroundMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // --- Tamaño ---
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SheetLabel("Tamaño del texto")
                val idx = zoomPresets.indexOfFirst { abs(it - textScale) < 0.01f }.let { if (it < 0) 1 else it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = idx.toFloat(),
                        onValueChange = { v ->
                            val i = v.roundToInt().coerceIn(0, zoomPresets.lastIndex)
                            if (i != idx) onSetTextScale(zoomPresets[i])
                        },
                        valueRange = 0f..zoomPresets.lastIndex.toFloat(),
                        steps = (zoomPresets.size - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                    )
                    Text("A", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // --- Tipografía ---
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetLabel("Tipografía")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReaderFontOption.entries.forEach { option ->
                        val selected = option == fontOption
                        Column(
                            modifier = Modifier
                                .width(96.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(18.dp))
                                .clickable { onSelectFont(option) }
                                .padding(vertical = 14.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Aa", fontFamily = option.fontFamily, fontSize = 28.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(
                                option.label, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- Tema ---
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetLabel("Tema")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeSwatch("Claro", LightBackground, LightOnBackground, themeMode == ThemeMode.LIGHT && themeVariant == ThemeVariant.STANDARD, Modifier.weight(1f)) { onSetTheme(ThemeMode.LIGHT, ThemeVariant.STANDARD) }
                    ThemeSwatch("Cálido", LightWarmBackground, LightWarmOnBackground, themeMode == ThemeMode.LIGHT && themeVariant == ThemeVariant.ALT, Modifier.weight(1f)) { onSetTheme(ThemeMode.LIGHT, ThemeVariant.ALT) }
                    ThemeSwatch("Oscuro", DarkBackground, DarkOnBackground, themeMode == ThemeMode.DARK && themeVariant == ThemeVariant.STANDARD, Modifier.weight(1f)) { onSetTheme(ThemeMode.DARK, ThemeVariant.STANDARD) }
                    ThemeSwatch("Profundo", DarkBackground, DarkOnBackgroundDim, themeMode == ThemeMode.DARK && themeVariant == ThemeVariant.ALT, Modifier.weight(1f)) { onSetTheme(ThemeMode.DARK, ThemeVariant.ALT) }
                }
            }

            // --- Fondo (solo RSVP) ---
            if (rsvpBackground != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetLabel("Fondo RSVP")
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val flowing = rsvpBackground == RsvpBackgroundMode.FLOWING
                        BackgroundSegment("Texto fluido", flowing, Modifier.weight(1f)) { onSetRsvpBackground(RsvpBackgroundMode.FLOWING) }
                        BackgroundSegment("Pantalla limpia", !flowing, Modifier.weight(1f)) {
                            onSetRsvpBackground(if (isDark) RsvpBackgroundMode.BLACK_LINES else RsvpBackgroundMode.WHITE_SCREEN)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ThemeSwatch(label: String, bg: Color, fg: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                    RoundedCornerShape(16.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Text("Aa", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = fg) }
        Text(
            label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BackgroundSegment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(44.dp).clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
