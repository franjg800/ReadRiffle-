package com.readriffle.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.readriffle.app.platform.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.readriffle.app.R
import com.readriffle.app.data.covers.CoverCandidate
import com.readriffle.app.theme.TitleDisplayFontFamily
import com.readriffle.app.ui.common.BookRoomTextField
import com.readriffle.app.viewmodel.CoverPickerState
import com.readriffle.app.platform.File

private fun CoverCandidate.model(thumb: Boolean): Any =
    if (localPath != null) File(localPath) else if (thumb) thumbUrl else fullUrl

/**
 * Galería "Cambiar portada": pantalla completa con el estilo de la app. Busca por título (editable),
 * enseña una cuadrícula de portadas y, al tocar una, la ve en grande para decidir si se queda.
 */
@Composable
fun CoverPickerOverlay(
    state: CoverPickerState,
    bookTitle: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onApply: (CoverCandidate) -> Unit,
    onClose: () -> Unit
) {
    var preview by remember { mutableStateOf<CoverCandidate?>(null) }
    BackHandler { if (preview != null && !state.applying) preview = null else if (!state.applying) onClose() }
    val primary = MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { Icon(painterResource(R.drawable.ic_arrow_back), "Volver", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Cambiar portada", fontFamily = TitleDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text(bookTitle, fontSize = 13.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }

            BookRoomTextField(
                value = state.query, onValueChange = onQueryChange,
                placeholder = "Título o autor",
                fieldColor = MaterialTheme.colorScheme.surface,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                onAction = { onSearch(state.query) },
                trailing = {
                    Icon(
                        painterResource(R.drawable.ic_search), "Buscar", tint = primary,
                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onSearch(state.query) }.padding(5.dp)
                    )
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(14.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = primary)
                        Text("Buscando portadas…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
                    }
                    state.searched && state.results.isEmpty() -> Text(
                        "No he encontrado portadas. Prueba con otro título o solo con el apellido del autor, y comprueba que tienes conexión.",
                        fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 36.dp)
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize().navigationBarsPadding()
                    ) {
                        itemsIndexed(state.results, key = { i, c -> "$i-${c.fullUrl}" }) { _, c ->
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface).clickable { preview = c }
                            ) {
                                SubcomposeAsyncImage(
                                    model = c.model(thumb = true), contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                    loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = primary.copy(alpha = 0.6f)) } },
                                    error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sin imagen", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                                )
                                if (c.localPath != null) {
                                    Text(
                                        "Original", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(50))
                                            .background(primary).padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        if (state.results.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "Portadas de Apple Books, Google Books y Open Library",
                                fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Vista previa grande: decidir si es esa o elegir otra.
        preview?.let { c ->
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                            .clickable(enabled = !state.applying) { preview = null },
                        contentAlignment = Alignment.Center
                    ) { Icon(painterResource(R.drawable.ic_arrow_back), "Volver a la galería", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
                    Text(c.source, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                    SubcomposeAsyncImage(
                        model = c.model(thumb = false), contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = primary) } },
                        // Si la versión grande no existe, se enseña la miniatura.
                        error = {
                            AsyncImage(model = c.model(thumb = true), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        }
                    )
                }
                state.error?.let {
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 10.dp))
                }
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (state.applying) primary.copy(alpha = 0.5f) else primary)
                        .clickable(enabled = !state.applying) { onApply(c) }.padding(vertical = 17.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.applying) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Usar esta portada", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                }
                Text(
                    "Elegir otra", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).clickable(enabled = !state.applying) { preview = null }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }
}
