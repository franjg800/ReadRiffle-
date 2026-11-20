package com.readriffle.app.ui.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readriffle.app.R
import com.readriffle.app.data.model.BookmarkMeta
import com.readriffle.app.data.model.Chapter
import com.readriffle.app.data.model.HighlightMeta

/**
 * El índice de capítulos ("las tres rayitas"), ahora con una segunda pestaña de "Subrayados":
 * el usuario pidió explícitamente poder ver ahí las frases o palabras que ha resaltado en modo
 * normal, sin tener que buscarlas de nuevo por el capítulo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterIndexSheet(
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    highlights: List<HighlightMeta>,
    bookmarks: List<BookmarkMeta> = emptyList(),
    onSelectBookmark: (BookmarkMeta) -> Unit = {},
    onDeleteBookmark: (String) -> Unit = {},
    onSelectChapter: (Int) -> Unit,
    onSelectHighlight: (HighlightMeta) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onEditNote: (HighlightMeta) -> Unit,
    onShareHighlight: (HighlightMeta) -> Unit = {},
    onSearch: (suspend (String) -> List<com.readriffle.app.data.search.SearchHit>)? = null,
    onSelectHit: (com.readriffle.app.data.search.SearchHit) -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val primary = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Text(
            "Índice",
            fontFamily = com.readriffle.app.theme.TitleDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
        )
        // Pestañas en una píldora segmentada que se desplaza si no caben.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IndexTabLabel(stringResource(R.string.reading_chapters_title), selectedTab == 0) { selectedTab = 0 }
            IndexTabLabel(stringResource(R.string.reading_highlights_tab) + if (highlights.isNotEmpty()) " · ${highlights.size}" else "", selectedTab == 1) { selectedTab = 1 }
            IndexTabLabel(stringResource(R.string.reading_bookmarks_tab) + if (bookmarks.isNotEmpty()) " · ${bookmarks.size}" else "", selectedTab == 2) { selectedTab = 2 }
            if (onSearch != null) IndexTabLabel("Buscar", selectedTab == 3) { selectedTab = 3 }
        }

        if (selectedTab == 0) {
            // La lista se abre ya situada en el capítulo actual (en colecciones largas, sin esto
            // habría que bajar a mano hasta él).
            val startIndex = remember(chapters, currentChapterIndex) {
                var n = 0
                for (i in 0 until currentChapterIndex.coerceIn(0, chapters.size)) {
                    if (chapters[i].group.isNotBlank() && chapters[i].group != chapters.getOrNull(i - 1)?.group.orEmpty()) n++
                    n++
                }
                val cur = chapters.getOrNull(currentChapterIndex)
                if (cur != null && cur.group.isNotBlank() && cur.group != chapters.getOrNull(currentChapterIndex - 1)?.group.orEmpty()) n++
                (n - 2).coerceAtLeast(0)
            }
            val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
            LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                chapters.forEachIndexed { i, chapter ->
                    val previousGroup = chapters.getOrNull(i - 1)?.group.orEmpty()
                    if (chapter.group.isNotBlank() && chapter.group != previousGroup) {
                        item(key = "group-$i") {
                            Text(
                                text = chapter.group.uppercase(),
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
                                color = primary,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 6.dp)
                            )
                        }
                    }
                    item(key = chapter.index) {
                        val selected = chapter.index == currentChapterIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) primary.copy(alpha = 0.13f) else Color.Transparent)
                                .clickable { onSelectChapter(chapter.index) }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (chapter.index + 1).toString(),
                                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(34.dp)
                            )
                            Text(
                                text = chapter.title,
                                fontSize = 16.sp,
                                color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(primary))
                        }
                    }
                }
            }
        } else if (selectedTab == 3 && onSearch != null) {
            SearchTab(onSearch = onSearch, onSelectHit = onSelectHit)
        } else if (selectedTab == 2) {
            if (bookmarks.isEmpty()) {
                IndexEmpty(stringResource(R.string.reading_bookmarks_empty))
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    items(bookmarks.sortedWith(compareBy({ it.chapterIndex }, { it.wordIndex })), key = { it.id }) { bookmark ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .clickable { onSelectBookmark(bookmark) }
                                .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_bookmark_filled),
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(
                                    text = chapters.getOrNull(bookmark.chapterIndex)?.title ?: bookmark.chapterTitle,
                                    fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1
                                )
                                Text(
                                    text = stringResource(R.string.reading_bookmark_item, bookmark.pageNumber, bookmark.totalPages),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            CardIconButton(R.drawable.ic_close, stringResource(R.string.reading_bookmark_remove)) { onDeleteBookmark(bookmark.id) }
                        }
                    }
                }
            }
        } else {
            if (highlights.isEmpty()) {
                IndexEmpty(stringResource(R.string.reading_highlights_empty))
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    items(highlights.sortedByDescending { it.createdAtMillis }, key = { it.id }) { highlight ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .clickable { onSelectHighlight(highlight) }
                                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)
                        ) {
                            Row {
                                Box(Modifier.width(3.dp).height(IntrinsicSize.Min).heightIn(min = 40.dp).clip(RoundedCornerShape(2.dp)).background(primary))
                                Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                                    Text(
                                        text = chapters.getOrNull(highlight.chapterIndex)?.title.orEmpty().uppercase(),
                                        fontSize = 10.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                                    )
                                    Text(
                                        text = highlight.text,
                                        fontSize = 15.sp, lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 4, modifier = Modifier.padding(top = 4.dp)
                                    )
                                    highlight.note?.let { note ->
                                        Text(
                                            text = note,
                                            fontSize = 13.sp, lineHeight = 18.sp,
                                            color = primary,
                                            maxLines = 3, modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                CardIconButton(R.drawable.ic_share, "Compartir como imagen") { onShareHighlight(highlight) }
                                CardIconButton(R.drawable.ic_note_simple, stringResource(R.string.notes_edit_note)) { onEditNote(highlight) }
                                CardIconButton(R.drawable.ic_close, stringResource(R.string.reading_highlight_delete)) { onDeleteHighlight(highlight.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardIconButton(iconRes: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(iconRes), description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun IndexEmpty(text: String) {
    Text(
        text = text,
        fontSize = 15.sp, lineHeight = 22.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp).padding(bottom = 24.dp)
    )
}

@Composable
private fun IndexTabLabel(label: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = label, fontSize = 14.sp,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun SearchTab(
    onSearch: suspend (String) -> List<com.readriffle.app.data.search.SearchHit>,
    onSelectHit: (com.readriffle.app.data.search.SearchHit) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.readriffle.app.data.search.SearchHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); searched = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        results = onSearch(query)
        searched = true
    }
    val accent = MaterialTheme.colorScheme.primary
    com.readriffle.app.ui.common.BookRoomTextField(
        value = query, onValueChange = { query = it },
        placeholder = "Buscar en el libro…",
        fieldColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
    )
    if (searched && results.isEmpty()) {
        Text(
            "Sin resultados",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
    } else if (results.isNotEmpty()) {
        Text(
            if (results.size >= 150) "Primeros 150 resultados" else "${results.size} resultados",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
    LazyColumn(modifier = Modifier.heightIn(max = 420.dp).padding(bottom = 24.dp)) {
        items(results.size) { i ->
            val hit = results[i]
            Column(
                modifier = Modifier.fillMaxWidth().clickable { onSelectHit(hit) }.padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    hit.chapterTitle,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                )
                Text(
                    text = androidx.compose.ui.text.buildAnnotatedString {
                        append("…" + hit.before + " ")
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = accent, fontWeight = FontWeight.Bold))
                        append(hit.match)
                        pop()
                        append(" " + hit.after + "…")
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 3
                )
            }
        }
    }
}
