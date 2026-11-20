package com.readriffle.app.ui.library

import com.readriffle.app.platform.rememberFilePicker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateListOf
import com.readriffle.app.data.stats.StatsSummary
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.readriffle.app.R
import com.readriffle.app.data.model.BookMeta
import com.readriffle.app.ui.common.BookRoomDialog
import com.readriffle.app.ui.common.BookRoomDialogText
import com.readriffle.app.ui.common.BookRoomTextField
import com.readriffle.app.ui.common.DockDestination
import com.readriffle.app.ui.common.MainDock
import com.readriffle.app.ui.common.ScreenTitle
import com.readriffle.app.ui.common.bookMinutesRemaining
import com.readriffle.app.ui.common.formatRemainingTime
import com.readriffle.app.viewmodel.LibraryViewModel
import com.readriffle.app.viewmodel.LibrarySection
import com.readriffle.app.viewmodel.SyncViewModel
import com.readriffle.app.platform.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    syncViewModel: SyncViewModel,
    onOpenBook: (String) -> Unit,
    onContinue: (String) -> Unit,
    onOpenNotes: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onLockCover: (BookMeta) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncState by syncViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<BookMeta?>(null) }
    var actionsFor by remember { mutableStateOf<BookMeta?>(null) }

    // Selector de archivos: en el iPhone abre la app Archivos (iCloud, descargas...). Se pueden
    // elegir varios EPUB a la vez.
    val importPicker = rememberFilePicker(".epub,application/epub+zip", multiple = true) { files ->
        viewModel.importFiles(files)
    }

    // Refresca al volver a la Biblioteca (p. ej. tras cerrar un libro): la portada destacada, las
    // estadísticas y el último subrayado se actualizan al momento.
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    // Sincroniza con Drive al entrar (no hace nada si no hay cuenta conectada).
    LaunchedEffect(Unit) {
        syncViewModel.syncNow(interactive = false)
    }
    LaunchedEffect(syncState.lastSyncedAtMillis) {
        if (syncState.lastSyncedAtMillis > 0L) viewModel.refresh()
    }

    val addFile = { importPicker() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading && uiState.books.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.books.isEmpty() -> {
                    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                        LibraryHeader(
                            onAddFile = addFile
                        )
                    }
                    EmptyLibrary(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    val finishedBooks = uiState.books.filter { it.progressFraction >= 0.99f }
                    val unfinishedBooks = uiState.books.filter { it.progressFraction < 0.99f }
                    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = full) {
                            LibraryHeader(
                                onAddFile = addFile
                            )
                        }
                        // --- "Hoy": continuar leyendo, ritmo y último subrayado -----------------
                        uiState.currentBook?.let { current ->
                            item(span = full) {
                                CurrentlyReadingHero(
                                    meta = current,
                                    wpm = uiState.lastWpm,
                                    onContinue = { onContinue(current.id) },
                                    onLock = { onLockCover(current) }
                                )
                            }
                        }
                        item(span = full) { StatsStrip(uiState.stats, onClick = onOpenStats) }
                        item(span = full) {
                            LibrarySectionLabel(
                                text = stringResource(R.string.library_section_all),
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }
                        items(unfinishedBooks, key = { it.id }) { meta ->
                            CoverTile(meta, onClick = { onOpenBook(meta.id) }, onLongClick = { actionsFor = meta })
                        }
                        if (finishedBooks.isNotEmpty()) {
                            item(span = full) {
                                LibrarySectionLabel(
                                    text = stringResource(R.string.library_section_finished),
                                    modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                                )
                            }
                            items(finishedBooks, key = { it.id }) { meta ->
                                CoverTile(meta, onClick = { onOpenBook(meta.id) }, onLongClick = { actionsFor = meta })
                            }
                        }
                    }
                }
            }

            if (uiState.isImporting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            MainDock(
                current = DockDestination.LIBRARY,
                onLibrary = {},
                onNotes = onOpenNotes,
                onStore = onOpenStore,
                onSettings = onOpenSettings,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    uiState.importError?.let { error ->
        BookRoomDialog(
            title = "No se pudo importar", onDismiss = viewModel::dismissError,
            confirmText = "Vale", onConfirm = viewModel::dismissError, dismissText = null
        ) { BookRoomDialogText(error) }
    }
    uiState.folderImportMessage?.let { message ->
        BookRoomDialog(
            title = "Carpeta del móvil", onDismiss = viewModel::dismissFolderMessage,
            confirmText = "Vale", onConfirm = viewModel::dismissFolderMessage, dismissText = null
        ) { BookRoomDialogText(message) }
    }

    // Menú de acciones de un libro (pulsación larga)
    actionsFor?.let { meta ->
        BookRoomDialog(
            title = meta.title, onDismiss = { actionsFor = null },
            confirmText = "Eliminar de la biblioteca", destructive = true,
            onConfirm = { pendingDelete = meta; actionsFor = null },
            dismissText = "Cerrar"
        ) {}
    }

    pendingDelete?.let { meta ->
        BookRoomDialog(
            title = "¿Eliminar \"${meta.title}\"?", onDismiss = { pendingDelete = null },
            confirmText = "Eliminar", destructive = true,
            onConfirm = { viewModel.deleteBook(meta.id); pendingDelete = null }
        ) { BookRoomDialogText("Se borrará de tu biblioteca junto con su progreso de lectura.") }
    }
}

/** Cabecera: título grande + botón de añadir con menú (archivo, carpeta vigilada). */
@Composable
private fun LibraryHeader(
    onAddFile: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScreenTitle(text = stringResource(R.string.library_title), modifier = Modifier.weight(1f))
        val addBookDescription = stringResource(R.string.library_add_book)
        Box {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = addBookDescription,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAddFile() }
                    .semantics { contentDescription = addBookDescription }
            )
        }
    }
}

/**
 * "Hoy": el libro que estás leyendo. Tarjeta compacta: la portada ocupa todo el alto (pegada
 * arriba, a la izquierda y abajo), y "Continuar" retoma la lectura donde la dejaste.
 */
@Composable
private fun CurrentlyReadingHero(meta: BookMeta, wpm: Int, onContinue: () -> Unit, onLock: () -> Unit) {
    val progress = meta.progressFraction.coerceIn(0f, 1f)
    val percent = (progress * 100).roundToInt()
    val minutesRemaining = bookMinutesRemaining(meta, wpm)
    val info = buildString {
        append("$percent%")
        if (minutesRemaining > 0) append(" · ").append(formatRemainingTime(minutesRemaining))
        append(" · Cap. ").append(meta.lastChapterIndex + 1)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(184.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), MaterialTheme.colorScheme.surface)
                )
            )
            .clickable(onClick = onContinue)
    ) {
        BookCoverImage(meta = meta, modifier = Modifier.width(123.dp).fillMaxHeight())
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.library_hero_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = meta.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Column {
                Text(
                    text = info,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(progress).height(5.dp)
                            .clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary)
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                            .clickable(onClick = onContinue).padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        Icon(painterResource(R.drawable.ic_play), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                        Text(
                            "Continuar",
                            color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            style = androidx.compose.ui.text.TextStyle(
                                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                                )
                            )
                        )
                    }
                    Box(
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f))
                            .clickable(onClick = onLock).padding(11.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_lock), "Bloquear con la portada", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Tres cifras de ritmo (hoy / racha / semana); al tocarlas se abren las estadísticas completas. */
@Composable
private fun StatsStrip(stats: StatsSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCell("Hoy", formatMinutes(stats.todaySeconds), Modifier.weight(1f))
        StatCell("Racha", if (stats.streak == 1) "1 día" else "${stats.streak} días", Modifier.weight(1f))
        StatCell("Semana", formatWords(stats.weekWords), Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

internal fun formatMinutes(seconds: Int): String {
    val m = seconds / 60
    return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
}

internal fun formatWords(words: Int): String = when {
    words >= 10_000 -> "${words / 1000}k palabras"
    words >= 1000 -> androidx.compose.ui.res.fixed(words / 1000.0, 1) + "k palabras"
    else -> "$words palabras"
}

/** Portada del libro; si no tiene, un degradado con el título (distinto por libro). */
@Composable
internal fun BookCoverImage(meta: BookMeta, modifier: Modifier = Modifier) {
    val palette = listOf(
        0xFF3D5A80 to 0xFF1F2F46, 0xFF8E4A49 to 0xFF4A2424, 0xFF4F6F52 to 0xFF273A29,
        0xFF7A5C8E to 0xFF3D2D4A, 0xFFB5833A to 0xFF5E4219, 0xFF2F6F73 to 0xFF173A3C
    )
    val (c1, c2) = palette[(meta.title.hashCode() and 0x7fffffff) % palette.size]
    Box(modifier = modifier.background(Brush.linearGradient(listOf(Color(c1), Color(c2))))) {
        if (meta.coverPath != null && File(meta.coverPath).exists()) {
            AsyncImage(
                model = File(meta.coverPath),
                contentDescription = meta.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = meta.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Una portada de la cuadrícula, con barra de progreso encima y título centrado debajo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverTile(meta: BookMeta, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))) {
            BookCoverImage(meta, Modifier.fillMaxSize())
            if (meta.progressFraction > 0.005f) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Box(Modifier.fillMaxWidth(meta.progressFraction.coerceIn(0f, 1f)).height(4.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Text(
            text = meta.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
        )
    }
}

@Composable
private fun LibrarySectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.library_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
