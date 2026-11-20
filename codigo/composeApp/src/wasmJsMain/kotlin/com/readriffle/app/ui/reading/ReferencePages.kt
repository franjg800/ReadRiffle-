package com.readriffle.app.ui.reading

import com.readriffle.app.platform.rememberFilePicker
import androidx.compose.ui.res.formatAndroid
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.readriffle.app.R
import com.readriffle.app.platform.File
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.readriffle.app.data.model.Chapter
import com.readriffle.app.data.model.PinnedPageMeta

/** Medidas con las que se pagina un capítulo; las comparten la lectura y la ventana de consulta. */
class PageGeometry(
    val style: TextStyle,
    val textMeasurer: TextMeasurer,
    val maxWidthPx: Int,
    val maxHeightPx: Int,
    val paragraphGapPx: Int
)

private fun PinnedPageMeta.displayLabel(defaultLabel: (Int, Int) -> String, imageLabel: String = "Imagen"): String =
    label.ifBlank { if (imagePath != null) imageLabel else defaultLabel(chapterIndex + 1, pageNumber) }

/**
 * Hoja de consultas DE ESTE LIBRO, con dos pestañas bien separadas: "Mis consultas" (ver y abrir
 * lo ya guardado) y "Añadir" (fijar una página del libro o subir una imagen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferencePagesSheet(
    bookTitle: String,
    currentPage: Int,
    totalPages: Int,
    pages: List<PinnedPageMeta>,
    onPinCurrent: (String) -> Unit,
    onAddImage: (String, ByteArray) -> Unit,
    onOpen: (PinnedPageMeta) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableIntStateOf(if (pages.isEmpty()) 1 else 0) }
    var pageName by remember { mutableStateOf("") }
    var imageName by remember { mutableStateOf("") }
    val imageLabel = stringResource(R.string.reference_image_default)
    val pickImage = rememberFilePicker("image/*") { files ->
        files.firstOrNull()?.let { onAddImage(imageName, it.bytes); imageName = ""; tab = 0 }
    }
    val template = stringResource(R.string.reference_default_label)
    val defaultLabel: (Int, Int) -> String = { ch, pg -> formatAndroid(template, arrayOf(ch, pg)) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.reference_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.reference_only_this_book, bookTitle),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Selector de pestaña
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    stringResource(R.string.reference_tab_view, pages.size),
                    stringResource(R.string.reference_tab_add)
                ).forEachIndexed { i, label ->
                    val sel = tab == i
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                            .background(if (sel) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { tab = i }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label, fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (tab == 0) {
                if (pages.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.reference_empty),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.reference_go_add), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { tab = 1 }.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(pages.sortedBy { it.createdAtMillis }, key = { it.id }) { page ->
                            val isImage = page.imagePath != null
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpen(page) }
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isImage && File(page.imagePath!!).exists()) {
                                        AsyncImage(
                                            model = File(page.imagePath), contentDescription = null,
                                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(painterResource(R.drawable.ic_reference_page), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(page.displayLabel(defaultLabel, imageLabel), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                    Text(
                                        if (isImage) stringResource(R.string.reference_kind_image)
                                        else stringResource(R.string.reference_kind_page) + " · ${page.chapterTitle} · " + stringResource(R.string.reading_bookmark_item, page.pageNumber, page.totalPages),
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                                    )
                                }
                                Box(Modifier.clickable { onRemove(page.id) }.padding(8.dp)) {
                                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.reference_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                AddCard(
                    title = stringResource(R.string.reference_add_page_title),
                    hint = stringResource(R.string.reference_add_page_hint, currentPage, totalPages),
                    name = pageName, onNameChange = { pageName = it },
                    buttonLabel = stringResource(R.string.reference_pin_current)
                ) { onPinCurrent(pageName); pageName = ""; tab = 0 }
                AddCard(
                    title = stringResource(R.string.reference_add_image_title),
                    hint = stringResource(R.string.reference_add_image_hint),
                    name = imageName, onNameChange = { imageName = it },
                    buttonLabel = stringResource(R.string.reference_add_image)
                ) { pickImage() }
            }
        }
    }
}

@Composable
private fun AddCard(
    title: String,
    hint: String,
    name: String,
    onNameChange: (String) -> Unit,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.07f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(hint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = name, onValueChange = { onNameChange(it.take(40)) }, singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (name.isEmpty()) Text(
                        stringResource(R.string.reference_name_hint), fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    inner()
                }
            )
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick).padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(buttonLabel, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/**
 * La ventanita: enseña la página fijada (con selector si hay varias y flechas para ver las
 * páginas contiguas) encima de la lectura, sin mover la posición de lectura.
 */
@Composable
fun ReferencePageWindow(
    pages: List<PinnedPageMeta>,
    initialId: String,
    chapters: List<Chapter>,
    geometry: PageGeometry,
    onDismiss: () -> Unit
) {
    val ordered = remember(pages) { pages.sortedBy { it.createdAtMillis } }
    var selectedId by remember { mutableStateOf(initialId) }
    var offset by remember(selectedId) { mutableIntStateOf(0) }
    var zoomPath by remember { mutableStateOf<String?>(null) }
    val page = ordered.firstOrNull { it.id == selectedId } ?: ordered.firstOrNull()
    if (page == null) { onDismiss(); return }
    val template = stringResource(R.string.reference_default_label)
    val defaultLabel: (Int, Int) -> String = { ch, pg -> formatAndroid(template, arrayOf(ch, pg)) }
    val imageLabel = stringResource(R.string.reference_image_default)
    val isImage = page.imagePath != null
    val maxHeight = (LocalConfiguration.current.screenHeightDp * (if (isImage) 0.86f else 0.74f)).dp

    val pagination = rememberChapterPagination(
        chapterText = if (page.imagePath != null) "" else chapters.getOrNull(page.chapterIndex)?.text.orEmpty(),
        style = geometry.style,
        textMeasurer = geometry.textMeasurer,
        maxWidthPx = geometry.maxWidthPx,
        maxHeightPx = geometry.maxHeightPx,
        paragraphGapPx = geometry.paragraphGapPx,
        wordIndex = page.wordIndex
    )

    zoomPath?.let { ImageZoomDialog(it) { zoomPath = null } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = maxHeight)
        ) {
            Column(Modifier.padding(top = 18.dp, bottom = 12.dp)) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(page.displayLabel(defaultLabel, imageLabel), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                        Text(if (isImage) stringResource(R.string.reference_image_hint) else page.chapterTitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (isImage) 2 else 1)
                    }
                    Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onDismiss).padding(10.dp)) {
                        Icon(painterResource(R.drawable.ic_close), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
                if (ordered.size > 1) {
                    Row(
                        Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ordered.forEach { p ->
                            val sel = p.id == page.id
                            Text(
                                p.displayLabel(defaultLabel, imageLabel), fontSize = 13.sp, maxLines = 1,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clip(RoundedCornerShape(50))
                                    .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                    .clickable { selectedId = p.id }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
                Box(Modifier.height(12.dp))
                if (isImage) {
                    ZoomableImage(
                        path = page.imagePath!!,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp)
                    )
                } else Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    if (pagination == null) {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        val shownIndex = (pagination.pageIndex + offset).coerceIn(0, pagination.pages.lastIndex.coerceAtLeast(0))
                        Column(
                            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pagination.pages.getOrNull(shownIndex).orEmpty().forEach { paragraph ->
                                val imagePath = com.readriffle.app.data.tokenizer.ImageMarker.pathOf(paragraph)
                                if (imagePath != null) {
                                    PageImageBlock(imagePath, Modifier.fillMaxWidth().height(360.dp)) { zoomPath = imagePath }
                                } else {
                                    Text(paragraph, style = geometry.style)
                                }
                            }
                        }
                    }
                }
                if (pagination != null && !isImage) {
                    val shownIndex = (pagination.pageIndex + offset).coerceIn(0, pagination.pages.lastIndex.coerceAtLeast(0))
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp, start = 20.dp, end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("‹", fontSize = 26.sp, color = if (shownIndex > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = shownIndex > 0) { offset-- }.padding(horizontal = 16.dp))
                        Text(
                            stringResource(R.string.reference_page_of, shownIndex + 1, pagination.totalPages),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("›", fontSize = 26.sp, color = if (shownIndex < pagination.pages.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = shownIndex < pagination.pages.lastIndex) { offset++ }.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

/** Imagen con zoom por pellizco, arrastre y doble toque (amplía / vuelve al tamaño original). */
@Composable
internal fun ZoomableImage(path: String, modifier: Modifier = Modifier) {
    val file = remember(path) { File(path) }
    if (!file.exists()) {
        Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.reference_image_missing),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp
            )
        }
        return
    }
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = box.width * (s - 1f) / 2f
        val maxY = box.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .onSizeChanged { box = it }
            .pointerInput(path) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 8f)
                    scale = newScale
                    offset = clamp(offset + pan * newScale, newScale)
                }
            }
            .pointerInput(path) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1.05f) { scale = 1f; offset = Offset.Zero } else { scale = 3f }
                })
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(file).size(Size(4096, 4096)).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            }
        )
    }
}
