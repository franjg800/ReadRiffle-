package com.readriffle.app.ui.store

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.readriffle.app.R
import com.readriffle.app.platform.openUrl
import com.readriffle.app.platform.rememberFilePicker
import com.readriffle.app.data.store.SavedStore
import com.readriffle.app.data.store.StoreLinksRepository
import com.readriffle.app.ui.common.BookRoomDialog
import com.readriffle.app.ui.common.BookRoomDialogText
import com.readriffle.app.ui.common.BookRoomTextField
import com.readriffle.app.ui.common.ScreenTitle
import kotlinx.coroutines.launch

/** Las dos vistas de la sección Tienda en la web: tarjetas y añadir enlace. */
private sealed interface StoreMode {
    data object Home : StoreMode
    data object Add : StoreMode
}

/**
 * Sección Tienda (versión web). Tarjetas con las tiendas guardadas; al tocar una se abre en el
 * navegador (Safari en el iPhone). Lo que descargues allí queda en la app Archivos, y con
 * «Importar EPUB descargado» pasa a la Biblioteca. Una web no puede meter otra web dentro con su
 * propio bloqueador de anuncios como hacía la app Android: para eso, Safari ya tiene los suyos.
 *
 * @param onImportEpub guarda los bytes de un EPUB en la biblioteca.
 * @param dock el dock flotante inferior (`MainDock` con Tienda activa).
 */
@Composable
fun WebStoreScreen(
    links: StoreLinksRepository,
    onImportEpub: suspend (ByteArray) -> Unit,
    dock: @Composable BoxScope.() -> Unit
) {
    var stores by remember { mutableStateOf(links.savedStores()) }
    var mode by remember { mutableStateOf<StoreMode>(if (stores.isEmpty()) StoreMode.Add else StoreMode.Home) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val importPicker = rememberFilePicker(".epub,application/epub+zip", multiple = true) { files ->
        scope.launch {
            var ok = 0
            var failed = 0
            for (f in files) runCatching { onImportEpub(f.bytes) }.onSuccess { ok++ }.onFailure { failed++ }
            message = when {
                failed == 0 && ok == 1 -> "Libro añadido a tu biblioteca"
                failed == 0 -> "$ok libros añadidos a tu biblioteca"
                ok == 0 -> "No se pudo importar: ¿es un EPUB?"
                else -> "$ok añadidos, $failed con error"
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (mode) {
                StoreMode.Home -> StoreHome(
                    stores = stores,
                    onOpen = { openUrl(it.url) },
                    onAdd = { mode = StoreMode.Add },
                    onImport = importPicker,
                    onRemove = { links.remove(it.url); stores = links.savedStores() }
                )
                StoreMode.Add -> AddStoreContent(
                    canCancel = stores.isNotEmpty(),
                    onCancel = { mode = StoreMode.Home },
                    onSubmit = { url ->
                        links.save(url, StoreLinksRepository.defaultName(url))
                        stores = links.savedStores()
                        mode = StoreMode.Home
                        openUrl(url)
                    }
                )
            }
            dock()
        }
    }

    message?.let { text ->
        BookRoomDialog(title = text, onDismiss = { message = null }, confirmText = "Vale", onConfirm = { message = null }, dismissText = null) {}
    }
}

@Composable
private fun StoreHeader(subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp)) {
        ScreenTitle(text = stringResource(R.string.store_title))
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoreHome(
    stores: List<SavedStore>,
    onOpen: (SavedStore) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onRemove: (SavedStore) -> Unit
) {
    var toRemove by remember { mutableStateOf<SavedStore?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            StoreHeader(stringResource(R.string.store_home_subtitle))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(onClick = onImport).padding(18.dp)
            ) {
                Text(
                    "Importar EPUB descargado", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Las tiendas se abren en el navegador. Cuando descargues un libro, vuelve aquí y elígelo en Archivos.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(stores, key = { it.url }) { store ->
            StoreCard(
                modifier = Modifier.combinedClickable(onClick = { onOpen(store) }, onLongClick = { toRemove = store }),
                content = {
                    StoreBadge(store)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        store.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        StoreLinksRepository.host(store.url), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
        item {
            StoreCard(modifier = Modifier.clickable(onClick = onAdd), content = {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) { Text("+", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.store_add_card), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.store_add_card_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            })
        }
    }

    toRemove?.let { store ->
        BookRoomDialog(
            title = stringResource(R.string.store_remove_title, store.name),
            onDismiss = { toRemove = null },
            confirmText = stringResource(R.string.store_remove_confirm), destructive = true,
            dismissText = stringResource(R.string.store_link_cancel),
            onConfirm = { onRemove(store); toRemove = null }
        ) {}
    }
}

@Composable
private fun StoreCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface).padding(18.dp)
    ) { content() }
}

/** Icono de la tienda: su favicon si carga; si no, una inicial sobre el color de acento. */
@Composable
private fun StoreBadge(store: SavedStore) {
    val shape = RoundedCornerShape(18.dp)
    val monogram: @Composable () -> Unit = {
        Box(
            Modifier.size(56.dp).clip(shape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                store.name.firstOrNull()?.uppercase() ?: "?", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    monogram()
}

@Composable
private fun AddStoreContent(canCancel: Boolean, onCancel: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val submit = {
        val url = StoreLinksRepository.normalize(text)
        if (url == null) error = true else onSubmit(url)
    }
    val accent = MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        StoreHeader(stringResource(R.string.store_link_subtitle))
        Spacer(Modifier.height(20.dp))
        StoreTextField(
            value = text, onValueChange = { text = it; error = false },
            placeholder = stringResource(R.string.store_link_placeholder),
            keyboardType = KeyboardType.Uri, onGo = { submit() }
        )
        if (error) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.store_link_invalid), fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(24.dp)).background(accent).clickable { submit() }
                    .padding(horizontal = 26.dp, vertical = 12.dp)
            ) {
                Text(stringResource(R.string.store_link_open), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
            }
            if (canCancel) TextButton(onClick = onCancel) { Text(stringResource(R.string.store_link_cancel)) }
        }
    }
}

@Composable
private fun StoreTextField(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text, onGo: () -> Unit = {}
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface).padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

