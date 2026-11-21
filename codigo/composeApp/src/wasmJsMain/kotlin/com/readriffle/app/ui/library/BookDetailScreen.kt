package com.readriffle.app.ui.library

import com.readriffle.app.platform.System
import com.readriffle.app.platform.shareText
import com.readriffle.app.ui.common.desktopCentered
import com.readriffle.app.platform.formatInt
import com.readriffle.app.platform.SimpleDateFormat
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.readriffle.app.R
import com.readriffle.app.data.model.BookMeta
import com.readriffle.app.theme.TitleDisplayFontFamily
import com.readriffle.app.ui.common.BookRoomDialog
import com.readriffle.app.ui.common.BookRoomDialogText
import com.readriffle.app.ui.common.BookRoomTextField
import com.readriffle.app.ui.common.bookMinutesRemaining
import com.readriffle.app.ui.common.formatRemainingTime
import com.readriffle.app.ui.common.squircleShape
import com.readriffle.app.viewmodel.BookDetailViewModel
import com.readriffle.app.platform.File
import com.readriffle.app.platform.Date
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * La "pantalla principal" de cada libro: portada grande, título, autor, progreso y estadísticas.
 * Pensada también para hacer una captura de pantalla bonita y compartirla.
 */
@Composable
fun BookDetailScreen(
    viewModel: BookDetailViewModel,
    onBack: () -> Unit,
    onRead: (String) -> Unit,
    onOpenRead: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var markingRead by remember { mutableStateOf(false) }
    val picker by viewModel.picker.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    val book = state.book
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            book == null -> LaunchedEffect(Unit) { onBack() }
            else -> BookDetailContent(
                book, state.wpm, onBack, onRead, viewModel::editBook, viewModel::openCoverPicker,
                onMarkRead = { if (book.isFinished) onOpenRead("lib:${book.id}") else markingRead = true }
            ) { viewModel.deleteBook(onBack) }
        }
        if (markingRead && book != null) {
            com.readriffle.app.ui.read.FinishBookSheet(
                heading = book.title,
                celebrate = true,
                initial = book.finished?.copy(finishedAtMillis = System.currentTimeMillis()),
                quotes = book.highlights.sortedBy { it.createdAtMillis }.map { it.text.trim() }.filter { it.isNotEmpty() },
                onSave = { record, _, _ -> viewModel.markRead(record); markingRead = false; onOpenRead("lib:${book.id}") },
                onDismiss = { markingRead = false }
            )
        }
        val p = picker
        if (p != null && book != null) {
            CoverPickerOverlay(
                state = p, bookTitle = book.title,
                onQueryChange = viewModel::setCoverQuery,
                onSearch = { viewModel.searchCovers(it) },
                onApply = viewModel::applyCover,
                onClose = viewModel::closeCoverPicker
            )
        }
    }
}

@Composable
private fun BookDetailContent(
    book: BookMeta,
    wpm: Int,
    onBack: () -> Unit,
    onRead: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    onChangeCover: () -> Unit,
    onMarkRead: () -> Unit = {},
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val pct = (book.progressFraction.coerceIn(0f, 1f) * 100).roundToInt()
    val finished = book.progressFraction >= 0.99f
    val started = book.progressFraction > 0f || book.lastOpenedAtMillis > 0L
    val primary = MaterialTheme.colorScheme.primary
    val wide = com.readriffle.app.ui.common.LocalWide.current

    // Fondo: un resplandor del color de acento que se funde con el fondo.
    Box(
        Modifier.fillMaxWidth().height(460.dp).background(
            Brush.verticalGradient(listOf(primary.copy(alpha = 0.28f), primary.copy(alpha = 0.06f), Color.Transparent))
        )
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(bottom = 32.dp).then(Modifier.desktopCentered(1120.dp))
    ) {
        if (!wide) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundIconButton(R.drawable.ic_arrow_back, stringResource(R.string.book_detail_back), onBack)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIconButton(R.drawable.ic_edit, "Editar título y autor") { editing = true }
                    RoundIconButton(R.drawable.ic_share, stringResource(R.string.book_detail_share)) {
                        val text = context.getString(R.string.book_detail_share_text, book.title, book.author, pct)
                        scope.launch { shareText(text, book.title) }
                    }
                }
            }

            // Portada grande
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxWidth(if (wide) 1f else 0.58f).aspectRatio(2f / 3f)
                        .shadow(28.dp, RoundedCornerShape(18.dp), ambientColor = primary, spotColor = primary)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onChangeCover)
                ) {
                    if (book.coverPath != null) {
                        AsyncImage(
                            model = File(book.coverPath), contentDescription = book.title,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(primary.copy(alpha = 0.5f), primary.copy(alpha = 0.15f))))
                                .padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                book.title, fontFamily = TitleDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp,
                                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground, maxLines = 5, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .clickable(onClick = onChangeCover).padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_cover_image), null, tint = primary, modifier = Modifier.size(16.dp))
                    Text("Cambiar portada", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    book.title, modifier = Modifier.fillMaxWidth(), fontFamily = TitleDisplayFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 30.sp, lineHeight = 36.sp, textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground, maxLines = 4, overflow = TextOverflow.Ellipsis
                )
                Text(
                    book.author, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (wide) TextAlign.Start else TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            // Progreso
            Column(Modifier.padding(horizontal = 28.dp).padding(top = 28.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$pct%", fontFamily = TitleDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, color = primary)
                    Text(
                        if (finished) stringResource(R.string.book_detail_finished) else stringResource(R.string.book_detail_read_pct),
                        fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Box(
                    Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                ) {
                    Box(Modifier.fillMaxWidth(book.progressFraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(primary))
                }
                Text(
                    stringResource(R.string.book_detail_chapter_of, (book.lastChapterIndex + 1).coerceAtMost(book.chapterCount.coerceAtLeast(1)), book.chapterCount),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp)
                )
            }

            // Estadísticas
            val remaining = bookMinutesRemaining(book, wpm)
            val totalMinutes = if (book.totalWordCount > 0 && wpm > 0) ceil(book.totalWordCount.toFloat() / wpm).toInt() else 0
            val stats = listOf(
                stringResource(R.string.book_detail_stat_remaining) to if (finished) "—" else if (remaining > 0) formatRemainingTime(remaining) else "—",
                stringResource(R.string.book_detail_stat_total) to if (totalMinutes > 0) formatRemainingTime(totalMinutes) else "—",
                stringResource(R.string.book_detail_stat_chapters) to book.chapterCount.toString(),
                stringResource(R.string.book_detail_stat_words) to if (book.totalWordCount > 0) formatInt(book.totalWordCount) else "—",
                stringResource(R.string.book_detail_stat_highlights) to book.highlights.size.toString(),
                stringResource(R.string.book_detail_stat_notes) to book.notes.size.toString(),
                stringResource(R.string.book_detail_stat_bookmarks) to book.bookmarks.size.toString(),
                stringResource(R.string.book_detail_stat_speed) to stringResource(R.string.book_detail_wpm_value, wpm)
            )
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.chunked(if (wide) 4 else 2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
                    }
                }
            }

            // Fechas
            val dateFmt = remember { SimpleDateFormat("d MMM yyyy") }
            Column(Modifier.padding(horizontal = 28.dp).padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.book_detail_added, dateFmt.format(Date(book.addedAtMillis))),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (book.lastOpenedAtMillis > 0L) Text(
                    stringResource(R.string.book_detail_last_read, dateFmt.format(Date(book.lastOpenedAtMillis))),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Acciones
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(primary)
                        .clickable { onRead(book.id) }.padding(vertical = 17.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(if (started && !finished) R.string.book_detail_continue else if (finished) R.string.book_detail_continue else R.string.book_detail_start),
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                // Leídos: marcar como leído o, si ya lo está, ver su ficha (tu nota frente a la de los lectores).
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(if (book.isFinished) primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
                        .clickable(onClick = onMarkRead).padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (book.isFinished) {
                        Text("Leído", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primary)
                        val stars = book.finished?.rating ?: 0
                        if (stars > 0) {
                            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                            com.readriffle.app.ui.read.StarRating(stars, size = 16.dp, spacing = 2.dp)
                        }
                        Text("  ·  Ver ficha", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Marcar como leído", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                            .clickable { confirmDelete = true }.padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.book_detail_delete), fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

        } else {
            // Ordenador: portada y acciones en una columna fija a la izquierda; datos a la derecha.
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundIconButton(R.drawable.ic_arrow_back, stringResource(R.string.book_detail_back), onBack)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIconButton(R.drawable.ic_edit, "Editar título y autor") { editing = true }
                    RoundIconButton(R.drawable.ic_share, stringResource(R.string.book_detail_share)) {
                        val text = context.getString(R.string.book_detail_share_text, book.title, book.author, pct)
                        scope.launch { shareText(text, book.title) }
                    }
                }
            }


            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                Column(Modifier.width(320.dp)) {
                    // Portada grande
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.fillMaxWidth(if (wide) 1f else 0.58f).aspectRatio(2f / 3f)
                                .shadow(28.dp, RoundedCornerShape(18.dp), ambientColor = primary, spotColor = primary)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onChangeCover)
                        ) {
                            if (book.coverPath != null) {
                                AsyncImage(
                                    model = File(book.coverPath), contentDescription = book.title,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(primary.copy(alpha = 0.5f), primary.copy(alpha = 0.15f))))
                                        .padding(18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        book.title, fontFamily = TitleDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp,
                                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground, maxLines = 5, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                .clickable(onClick = onChangeCover).padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_cover_image), null, tint = primary, modifier = Modifier.size(16.dp))
                            Text("Cambiar portada", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Acciones
                    Column(Modifier.padding(horizontal = 20.dp).padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(primary)
                                .clickable { onRead(book.id) }.padding(vertical = 17.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(if (started && !finished) R.string.book_detail_continue else if (finished) R.string.book_detail_continue else R.string.book_detail_start),
                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        // Leídos: marcar como leído o, si ya lo está, ver su ficha (tu nota frente a la de los lectores).
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(if (book.isFinished) primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
                                .clickable(onClick = onMarkRead).padding(horizontal = 18.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (book.isFinished) {
                                Text("Leído", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primary)
                                val stars = book.finished?.rating ?: 0
                                if (stars > 0) {
                                    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                                    com.readriffle.app.ui.read.StarRating(stars, size = 16.dp, spacing = 2.dp)
                                }
                                Text("  ·  Ver ficha", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("Marcar como leído", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                                    .clickable { confirmDelete = true }.padding(vertical = 15.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.book_detail_delete), fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                }
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            book.title, modifier = Modifier.fillMaxWidth(), fontFamily = TitleDisplayFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 30.sp, lineHeight = 36.sp, textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground, maxLines = 4, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            book.author, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = if (wide) TextAlign.Start else TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }

                    // Progreso
                    Column(Modifier.padding(horizontal = 28.dp).padding(top = 28.dp)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$pct%", fontFamily = TitleDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, color = primary)
                            Text(
                                if (finished) stringResource(R.string.book_detail_finished) else stringResource(R.string.book_detail_read_pct),
                                fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Box(
                            Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                        ) {
                            Box(Modifier.fillMaxWidth(book.progressFraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(primary))
                        }
                        Text(
                            stringResource(R.string.book_detail_chapter_of, (book.lastChapterIndex + 1).coerceAtMost(book.chapterCount.coerceAtLeast(1)), book.chapterCount),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    // Estadísticas
                    val remaining = bookMinutesRemaining(book, wpm)
                    val totalMinutes = if (book.totalWordCount > 0 && wpm > 0) ceil(book.totalWordCount.toFloat() / wpm).toInt() else 0
                    val stats = listOf(
                        stringResource(R.string.book_detail_stat_remaining) to if (finished) "—" else if (remaining > 0) formatRemainingTime(remaining) else "—",
                        stringResource(R.string.book_detail_stat_total) to if (totalMinutes > 0) formatRemainingTime(totalMinutes) else "—",
                        stringResource(R.string.book_detail_stat_chapters) to book.chapterCount.toString(),
                        stringResource(R.string.book_detail_stat_words) to if (book.totalWordCount > 0) formatInt(book.totalWordCount) else "—",
                        stringResource(R.string.book_detail_stat_highlights) to book.highlights.size.toString(),
                        stringResource(R.string.book_detail_stat_notes) to book.notes.size.toString(),
                        stringResource(R.string.book_detail_stat_bookmarks) to book.bookmarks.size.toString(),
                        stringResource(R.string.book_detail_stat_speed) to stringResource(R.string.book_detail_wpm_value, wpm)
                    )
                    Column(Modifier.padding(horizontal = 20.dp).padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        stats.chunked(if (wide) 4 else 2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
                            }
                        }
                    }

                    // Fechas
                    val dateFmt = remember { SimpleDateFormat("d MMM yyyy") }
                    Column(Modifier.padding(horizontal = 28.dp).padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.book_detail_added, dateFmt.format(Date(book.addedAtMillis))),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (book.lastOpenedAtMillis > 0L) Text(
                            stringResource(R.string.book_detail_last_read, dateFmt.format(Date(book.lastOpenedAtMillis))),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }


                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (editing) {
        EditBookDialog(book.title, book.author, onDismiss = { editing = false }, onSave = { t, a -> onEdit(t, a); editing = false })
    }

    if (confirmDelete) {
        BookRoomDialog(
            title = stringResource(R.string.book_detail_delete_title, book.title),
            onDismiss = { confirmDelete = false },
            confirmText = stringResource(R.string.book_detail_delete_confirm),
            destructive = true,
            dismissText = stringResource(R.string.book_detail_delete_cancel),
            onConfirm = { confirmDelete = false; onDelete() }
        ) { BookRoomDialogText(stringResource(R.string.book_detail_delete_text)) }
    }
}

@Composable
private fun EditBookDialog(title: String, author: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var t by remember { mutableStateOf(title) }
    var a by remember { mutableStateOf(author) }
    BookRoomDialog(
        title = "Editar libro",
        onDismiss = onDismiss,
        confirmText = "Guardar",
        confirmEnabled = t.isNotBlank(),
        onConfirm = { onSave(t, a) }
    ) {
        BookRoomTextField(value = t, onValueChange = { t = it }, label = "Título", singleLine = false, maxLines = 4)
        BookRoomTextField(value = a, onValueChange = { a = it }, label = "Autor", onAction = { if (t.isNotBlank()) onSave(t, a) })
    }
}

@Composable
private fun RoundIconButton(iconRes: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(squircleShape(46.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(iconRes), description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label.uppercase(), fontSize = 10.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, fontFamily = TitleDisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 21.sp,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
