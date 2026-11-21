package com.readriffle.app.ui.read

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.readriffle.app.R
import com.readriffle.app.data.ratings.ReadEntry
import com.readriffle.app.ui.common.BookRoomDialog
import com.readriffle.app.ui.common.desktopCentered
import com.readriffle.app.ui.common.BookRoomDialogText
import com.readriffle.app.viewmodel.ReadBookViewModel
import com.readriffle.app.platform.File
import com.readriffle.app.platform.SimpleDateFormat
import com.readriffle.app.platform.Date
import com.readriffle.app.platform.dateParts

private val readDateFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy")

/** Portada de un leído (de la biblioteca o apuntado a mano), con un degradado si no hay imagen. */
@Composable
fun ReadCover(title: String, coverPath: String?, modifier: Modifier = Modifier) {
    val palette = listOf(
        0xFF3D5A80 to 0xFF1F2F46, 0xFF8E4A49 to 0xFF4A2424, 0xFF4F6F52 to 0xFF273A29,
        0xFF7A5C8E to 0xFF3D2D4A, 0xFFB5833A to 0xFF5E4219, 0xFF2F6F73 to 0xFF173A3C
    )
    val (c1, c2) = palette[(title.hashCode() and 0x7fffffff) % palette.size]
    Box(modifier.background(Brush.linearGradient(listOf(Color(c1), Color(c2))))) {
        if (coverPath != null && File(coverPath).exists()) {
            AsyncImage(model = File(coverPath), contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                title, color = Color.White.copy(alpha = 0.92f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge, maxLines = 5, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadBookScreen(viewModel: ReadBookViewModel, onBack: () -> Unit, onReread: (String) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.removed) { if (state.removed) onBack() }

    val entry = state.entry
    if (entry == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            if (state.isLoading) CircularProgressIndicator() else Text("Este libro ya no está en Leídos", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var editing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val record = entry.record
    val primary = MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier.fillMaxWidth().height(420.dp).background(
                Brush.verticalGradient(listOf(primary.copy(alpha = 0.26f), primary.copy(alpha = 0.05f), Color.Transparent))
            )
        )
        val wide = com.readriffle.app.ui.common.LocalWide.current
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(bottom = 32.dp)
                .then(Modifier.desktopCentered(1120.dp))
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                CircleIcon(R.drawable.ic_arrow_back, "Volver", onBack)
                CircleIcon(R.drawable.ic_edit, "Editar opinión") { editing = true }
            }


            if (!wide) {
            // La portada entra girando un poco y se asienta con un muelle.
            val pop = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(Unit) {
                pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 260f))
            }
            ReadCover(
                entry.title, entry.coverPath,
                Modifier.align(Alignment.CenterHorizontally).width(if (wide) 260.dp else 158.dp).aspectRatio(2f / 3f)
                    .graphicsLayer {
                        val p = pop.value
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = 0.78f + 0.22f * p; scaleY = 0.78f + 0.22f * p
                        rotationZ = (1f - p) * -7f
                        translationY = (1f - p) * 60f
                    }
                    .shadow(18.dp, RoundedCornerShape(14.dp), ambientColor = primary, spotColor = primary)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.height(18.dp))
            Text(
                entry.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).appearIn(2)
            )
            Text(
                entry.author, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).appearIn(3)
            )
            Text(
                "Leído el ${readDateFormat.format(Date(record.finishedAtMillis))}",
                style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = primary,
                modifier = Modifier.align(Alignment.CenterHorizontally).appearIn(4).padding(top = 10.dp).clip(RoundedCornerShape(50))
                    .clickable { pickDate = true }.padding(horizontal = 12.dp, vertical = 6.dp)
            )


            Column(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Tu nota: se cambia tocando las estrellas.
                Column(
                    Modifier.fillMaxWidth().appearIn(5).clip(RoundedCornerShape(24.dp))
                        .background(Brush.verticalGradient(listOf(primary.copy(alpha = 0.14f), MaterialTheme.colorScheme.surface)))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReadLabel("Tu nota")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        StarRating(record.rating, size = 36.dp, spacing = 8.dp, appearDelayMs = 350, onChange = viewModel::setRating)
                        if (record.rating > 0) Column {
                            Text(formatHalfStars(record.rating), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = primary)
                            Text(ratingWord(record.rating), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (record.review.isNotBlank()) {
                        Text(record.review, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        Text(
                            "Añade tu opinión", style = MaterialTheme.typography.bodyMedium, color = primary,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { editing = true }.padding(vertical = 4.dp)
                        )
                    }
                }

                if (record.favoriteQuote.isNotBlank()) {
                    Column(
                        Modifier.fillMaxWidth().appearIn(6).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ReadLabel("Frase favorita")
                        Text(
                            "“${record.favoriteQuote}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                CommunityCard(
                    modifier = Modifier.appearIn(7),
                    record = record,
                    loading = state.loadingRatings,
                    onRefresh = viewModel::refreshRatings,
                    onOpenUrl = { url -> com.readriffle.app.platform.openUrl(url) }
                )

                entry.libraryBook?.let { book ->
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(primary).clickable { onReread(book.id) }.padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Volver a leerlo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary) }
                }
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                        .clickable { confirmRemove = true }.padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (entry.libraryBook != null) "Quitar de Leídos" else "Eliminar de Leídos",
                        fontSize = 15.sp, color = MaterialTheme.colorScheme.error
                    )
                }
            }
            } else {
            // Ordenador: portada, datos y botones a la izquierda; tu nota y la de los lectores a la derecha.
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // La portada entra girando un poco y se asienta con un muelle.
            val pop = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(Unit) {
                pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 260f))
            }
            ReadCover(
                entry.title, entry.coverPath,
                Modifier.align(Alignment.CenterHorizontally).width(if (wide) 260.dp else 158.dp).aspectRatio(2f / 3f)
                    .graphicsLayer {
                        val p = pop.value
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = 0.78f + 0.22f * p; scaleY = 0.78f + 0.22f * p
                        rotationZ = (1f - p) * -7f
                        translationY = (1f - p) * 60f
                    }
                    .shadow(18.dp, RoundedCornerShape(14.dp), ambientColor = primary, spotColor = primary)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.height(18.dp))
            Text(
                entry.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).appearIn(2)
            )
            Text(
                entry.author, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).appearIn(3)
            )
            Text(
                "Leído el ${readDateFormat.format(Date(record.finishedAtMillis))}",
                style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = primary,
                modifier = Modifier.align(Alignment.CenterHorizontally).appearIn(4).padding(top = 10.dp).clip(RoundedCornerShape(50))
                    .clickable { pickDate = true }.padding(horizontal = 12.dp, vertical = 6.dp)
            )


                    Spacer(Modifier.height(10.dp))
                entry.libraryBook?.let { book ->
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(primary).clickable { onReread(book.id) }.padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Volver a leerlo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary) }
                }
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                        .clickable { confirmRemove = true }.padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (entry.libraryBook != null) "Quitar de Leídos" else "Eliminar de Leídos",
                        fontSize = 15.sp, color = MaterialTheme.colorScheme.error
                    )
                }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Tu nota: se cambia tocando las estrellas.
                Column(
                    Modifier.fillMaxWidth().appearIn(5).clip(RoundedCornerShape(24.dp))
                        .background(Brush.verticalGradient(listOf(primary.copy(alpha = 0.14f), MaterialTheme.colorScheme.surface)))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReadLabel("Tu nota")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        StarRating(record.rating, size = 36.dp, spacing = 8.dp, appearDelayMs = 350, onChange = viewModel::setRating)
                        if (record.rating > 0) Column {
                            Text(formatHalfStars(record.rating), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = primary)
                            Text(ratingWord(record.rating), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (record.review.isNotBlank()) {
                        Text(record.review, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        Text(
                            "Añade tu opinión", style = MaterialTheme.typography.bodyMedium, color = primary,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { editing = true }.padding(vertical = 4.dp)
                        )
                    }
                }

                if (record.favoriteQuote.isNotBlank()) {
                    Column(
                        Modifier.fillMaxWidth().appearIn(6).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ReadLabel("Frase favorita")
                        Text(
                            "“${record.favoriteQuote}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                CommunityCard(
                    modifier = Modifier.appearIn(7),
                    record = record,
                    loading = state.loadingRatings,
                    onRefresh = viewModel::refreshRatings,
                    onOpenUrl = { url -> com.readriffle.app.platform.openUrl(url) }
                )


                }
            }
            }
        }
    }

    if (editing) {
        FinishBookSheet(
            heading = entry.title,
            initial = record,
            quotes = state.quotes,
            saveText = "Guardar",
            onSave = { r, _, _ -> viewModel.save(r); editing = false },
            onDismiss = { editing = false }
        )
    }

    if (confirmRemove) {
        BookRoomDialog(
            title = if (entry.libraryBook != null) "¿Quitar de Leídos?" else "¿Eliminar de Leídos?",
            onDismiss = { confirmRemove = false },
            confirmText = if (entry.libraryBook != null) "Quitar" else "Eliminar", destructive = true,
            onConfirm = { confirmRemove = false; viewModel.remove() }
        ) {
            BookRoomDialogText(
                if (entry.libraryBook != null) "El libro vuelve a «Leyendo» y se pierde tu nota y tu opinión."
                else "Se borrará este libro de tu lista de leídos, con tu nota y tu opinión."
            )
        }
    }

    if (pickDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = record.finishedAtMillis)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { sel ->
                        // El selector devuelve medianoche UTC: se conserva la hora del día original.
                        val day = 86_400_000L
                        val newMillis = sel + (record.finishedAtMillis % day)
                        viewModel.save(record.copy(finishedAtMillis = newMillis))
                    }
                    pickDate = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancelar") } }
        ) { DatePicker(state = pickerState, title = { Text("¿Cuándo lo terminaste?", modifier = Modifier.padding(start = 24.dp, top = 20.dp)) }) }
    }
}

@Composable
private fun CircleIcon(iconRes: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(iconRes), description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
    }
}

/** Estadística de la cabecera de Leídos. */
data class ReadShelfStats(val thisYear: Int, val total: Int, val averageHalfStars: Float?)

fun readShelfStats(entries: List<ReadEntry>): ReadShelfStats {
    val year = dateParts(com.readriffle.app.platform.System.currentTimeMillis()).year
    val thisYear = entries.count { dateParts(it.record.finishedAtMillis).year == year }
    val rated = entries.filter { it.record.rating > 0 }
    return ReadShelfStats(thisYear, entries.size, rated.takeIf { it.isNotEmpty() }?.map { it.record.rating }?.average()?.toFloat())
}
