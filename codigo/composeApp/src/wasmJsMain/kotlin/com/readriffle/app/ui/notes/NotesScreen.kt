package com.readriffle.app.ui.notes

import com.readriffle.app.ui.common.desktopCentered
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.readriffle.app.R
import com.readriffle.app.platform.File
import com.readriffle.app.ui.common.DockDestination
import com.readriffle.app.ui.common.MainDock
import com.readriffle.app.ui.common.ScreenTitle
import com.readriffle.app.viewmodel.NoteEntry
import com.readriffle.app.viewmodel.NotesViewModel
import com.readriffle.app.platform.SimpleDateFormat
import com.readriffle.app.platform.Date

/**
 * Notas: cuarto icono del dock flotante. Junta en una sola lista cronológica los subrayados y
 * las notas sueltas de TODOS los libros de la biblioteca -- lo que pidió el usuario ("una
 * sección nueva que serán estás notas y las frases resaltadas") -- cada entrada referida a
 * libro, capítulo, página y fecha. Toca una entrada para saltar directamente a esa posición del
 * libro (RSVP o modo normal, el que tuviera activo esa lectura).
 */
@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    onOpenLibrary: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAt: (bookId: String, chapterIndex: Int, wordIndex: Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // El dock inferior navega entre Biblioteca/Notas/Convertir/Ajustes con `saveState`/
    // `restoreState` (ver `AppNavigation.navigateTopLevel`), así que este `NotesViewModel` se
    // crea UNA SOLA VEZ y se reutiliza en cada visita -- su `init{}` solo llama a `refresh()` la
    // primera vez. Sin esto, una nota guardada desde la pantalla de lectura nunca aparecía aquí
    // hasta reiniciar la app, porque la lista en memoria nunca se recargaba (mismo patrón que ya
    // usa `LibraryScreen` para refrescar la portada destacada al volver).
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).desktopCentered(900.dp)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
                uiState.entries.isEmpty() -> EmptyNotes(modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item { NotesHeader() }
                    items(uiState.entries, key = { it.id }) { entry ->
                        NoteCard(
                            entry = entry,
                            onClick = { onOpenAt(entry.bookId, entry.chapterIndex, entry.jumpToWordIndex) },
                            onDelete = { viewModel.deleteEntry(entry) }
                        )
                    }
                }
            }

            MainDock(
                current = DockDestination.NOTES,
                onLibrary = onOpenLibrary,
                onNotes = {},
                onStore = onOpenStore,
                onSettings = onOpenSettings,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun NotesHeader() {
    Column {
        ScreenTitle(text = stringResource(R.string.notes_title))
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.notes_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EmptyNotes(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.notes_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.notes_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private val dateFormatter = SimpleDateFormat("d MMM yyyy")

/**
 * Tarjeta de una entrada: una etiqueta pequeña en versalitas (Subrayado/Nota, mismo lenguaje
 * visual que [com.readriffle.app.ui.settings.SettingsScreen]'s SectionLabel), el texto citado
 * (si es un subrayado) en cursiva, el comentario del usuario si lo hay, y la referencia
 * libro · capítulo · página · fecha al pie, en el color secundario.
 */
@Composable
private fun NoteCard(entry: NoteEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    val dateLabel = dateFormatter.format(Date(entry.createdAtMillis))
    val reference = if (entry.totalPages > 0) {
        stringResource(
            R.string.note_reference_format,
            entry.bookTitle,
            entry.chapterTitle,
            entry.pageNumber,
            entry.totalPages,
            dateLabel
        )
    } else {
        stringResource(R.string.note_reference_format_no_pages, entry.bookTitle, entry.chapterTitle, dateLabel)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeTag(
                label = stringResource(
                    when {
                        entry.isImage -> R.string.notes_type_image
                        entry.isHighlight -> R.string.notes_type_highlight
                        else -> R.string.notes_type_note
                    }
                )
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.notes_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Imagen generada con IA: se muestra por encima de la descripción/prompt (que ya viaja
        // en [NoteEntry.quotedText], igual que la cita de un subrayado) -- pedido explícito:
        // "que se guarde la imagen y la descripción... en la sección de notas".
        if (entry.isImage && entry.imagePath != null) {
            AsyncImage(
                model = File(entry.imagePath),
                contentDescription = entry.quotedText,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.height(10.dp))
        }

        entry.quotedText?.let { quoted ->
            Text(
                text = "“$quoted”",
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 4
            )
        }

        entry.noteText?.let { note ->
            if (entry.quotedText != null) Spacer(Modifier.height(8.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.quotedText != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 6
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = reference,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Misma etiqueta en versalitas con punto de acento que ya usa la sección de Ajustes. */
@Composable
private fun TypeTag(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
