package com.readriffle.app.ui.read

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readriffle.app.data.model.ReadingRecord
import com.readriffle.app.data.ratings.CommunityRatingsClient
import com.readriffle.app.ui.common.BookRoomTextField
import kotlin.math.cos
import kotlin.math.sin

/** "4,5" a partir de medias estrellas (9). */
fun formatHalfStars(half: Int): String =
    if (half % 2 == 0) "${half / 2}" else "${half / 2},5"

/** "4,3" con un decimal y coma. */
fun formatOneDecimal(v: Float): String = androidx.compose.ui.res.fixed(v.toDouble(), 1).replace('.', ',')

/** "1.250" con punto de miles. */
fun formatCount(n: Int): String = com.readriffle.app.platform.formatInt(n)

private fun starPath(cx: Float, cy: Float, outer: Float): Path {
    val inner = outer * 0.48f
    val p = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = toRad((-90 + i * 36).toDouble())
        val x = cx + (r * cos(a)).toFloat()
        val y = cy + (r * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

/**
 * Cinco estrellas con medias, animadas. [halfStars] de 0 a 10. Si [onChange] no es null se pueden
 * tocar: la mitad izquierda de una estrella pone media, la derecha la entera; tocar la misma nota
 * la quita. Al aparecer se rellenan una tras otra; al cambiar, cada estrella que se enciende da un
 * pequeño salto con brillo y la tocada suelta una chispa.
 */
@Composable
fun StarRating(
    halfStars: Int,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    spacing: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    emptyColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
    animateOnAppear: Boolean = true,
    appearDelayMs: Int = 0,
    onChange: ((Int) -> Unit)? = null
) {
    var tapped by remember { mutableIntStateOf(-1) }
    var burst by remember { mutableIntStateOf(0) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
        for (i in 0 until 5) {
            val target = (halfStars - i * 2).coerceIn(0, 2) / 2f
            val fill = remember { Animatable(if (animateOnAppear) 0f else target) }
            val scale = remember { Animatable(1f) }
            val spark = remember { Animatable(1f) }
            var first by remember { mutableStateOf(true) }
            LaunchedEffect(target) {
                val growing = target > fill.value
                val wait = if (first) appearDelayMs + i * 85 else if (tapped >= 0) kotlin.math.abs(i - tapped) * 35 else i * 35
                first = false
                kotlinx.coroutines.delay(wait.toLong())
                launch { fill.animateTo(target, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) }
                if (growing) {
                    scale.animateTo(1.32f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
                    scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow))
                } else {
                    scale.animateTo(0.86f, tween(90))
                    scale.animateTo(1f, spring(dampingRatio = 0.5f))
                }
            }
            LaunchedEffect(burst) {
                if (burst > 0 && tapped == i) {
                    spark.snapTo(0f)
                    spark.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
                }
            }
            Canvas(
                Modifier.size(size).graphicsLayer { scaleX = scale.value; scaleY = scale.value }.then(
                    if (onChange == null) Modifier else Modifier.pointerInput(i, halfStars) {
                        detectTapGestures { pos ->
                            val v = i * 2 + if (pos.x < this.size.width / 2f) 1 else 2
                            tapped = i
                            burst++
                            onChange(if (v == halfStars) 0 else v)
                        }
                    }
                )
            ) {
                val w = this.size.width
                val h = this.size.height
                val r = this.size.minDimension / 2f
                val center = Offset(w / 2f, h / 2f + h * 0.04f)
                val f = fill.value.coerceIn(0f, 1f)
                // Brillo detrás de las estrellas encendidas.
                if (f > 0f) {
                    drawCircle(
                        Brush.radialGradient(listOf(color.copy(alpha = 0.30f * f), Color.Transparent), center = center, radius = r * 1.5f),
                        radius = r * 1.5f, center = center
                    )
                }
                val path = starPath(center.x, center.y, r)
                drawPath(path, emptyColor, style = Stroke(width = r * 0.13f, join = StrokeJoin.Round))
                if (f > 0f) {
                    clipRect(right = w * f) {
                        drawPath(path, Brush.verticalGradient(listOf(lerp(color, Color.White, 0.35f), color), startY = center.y - r, endY = center.y + r))
                        drawPath(path, color, style = Stroke(width = r * 0.13f, join = StrokeJoin.Round))
                    }
                }
                // Chispa al tocar: rayitas que salen del centro y se desvanecen.
                val sp = spark.value
                if (sp < 1f) {
                    val a = 1f - sp
                    for (k in 0 until 8) {
                        val ang = toRad((k * 45 - 90).toDouble())
                        val r1 = r * (0.9f + sp * 0.9f)
                        val r2 = r * (1.05f + sp * 1.25f)
                        drawLine(
                            color.copy(alpha = a), Offset(center.x + (r1 * cos(ang)).toFloat(), center.y + (r1 * sin(ang)).toFloat()),
                            Offset(center.x + (r2 * cos(ang)).toFloat(), center.y + (r2 * sin(ang)).toFloat()),
                            strokeWidth = r * 0.14f, cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

/** Palabra para cada nota (en medias estrellas). */
fun ratingWord(half: Int): String = when (half) {
    0 -> ""
    1, 2 -> "No me gustó"
    3, 4 -> "Flojo"
    5, 6 -> "Está bien"
    7 -> "Bueno"
    8 -> "Muy bueno"
    9 -> "Buenísimo"
    else -> "Una joya"
}

/** Etiqueta en versalitas con punto de acento (mismo estilo que el resto de la app). */
@Composable
fun ReadLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Text(
            text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PillButton(text: String, primary: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (primary) MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f)
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(50)).background(bg).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 15.sp, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Hoja para marcar un libro como leído (o editar su ficha): estrellas, una opinión corta y,
 * si hay subrayados, la frase favorita. Con [askTitleAuthor] sirve para apuntar un libro leído
 * fuera de la app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishBookSheet(
    heading: String,
    initial: ReadingRecord? = null,
    quotes: List<String> = emptyList(),
    askTitleAuthor: Boolean = false,
    saveText: String = "Guardar en Leídos",
    celebrate: Boolean = false,
    onSave: (record: ReadingRecord, title: String, author: String) -> Unit,
    onDismiss: () -> Unit
) {
    var rating by remember { mutableIntStateOf(initial?.rating ?: 0) }
    var review by remember { mutableStateOf(initial?.review ?: "") }
    var quote by remember { mutableStateOf(initial?.favoriteQuote ?: "") }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    val canSave = !askTitleAuthor || title.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ReadLabel(if (askTitleAuthor) "Apuntar un libro leído" else "Libro terminado", Modifier.appearIn(0))
            Text(
                heading, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.appearIn(1)
            )

            if (askTitleAuthor) {
                BookRoomTextField(value = title, onValueChange = { title = it }, placeholder = "Título", fieldColor = MaterialTheme.colorScheme.background)
                BookRoomTextField(value = author, onValueChange = { author = it }, placeholder = "Autor", fieldColor = MaterialTheme.colorScheme.background)
            }

            Column(
                Modifier.fillMaxWidth().appearIn(2).clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), MaterialTheme.colorScheme.primary.copy(alpha = 0.03f))
                        )
                    )
                    .padding(vertical = 20.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("¿Qué te ha parecido?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StarRating(rating, size = 46.dp, spacing = 10.dp, appearDelayMs = 250, onChange = { rating = it })
                AnimatedContent(
                    targetState = rating,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(spring(dampingRatio = 0.7f)) { if (up) it else -it } + fadeIn(tween(180))) togetherWith
                            (slideOutVertically(tween(160)) { if (up) -it else it } + fadeOut(tween(120)))
                    },
                    label = "nota"
                ) { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(30.dp)) {
                        if (r == 0) {
                            Text("Toca una estrella · la mitad izquierda pone media", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(formatHalfStars(r), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(ratingWord(r), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            BookRoomTextField(
                value = review, onValueChange = { review = it },
                placeholder = "Tu opinión en dos líneas (opcional)",
                singleLine = false, maxLines = 6,
                fieldColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
            )

            if (quotes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Frase favorita (de tus subrayados)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quotes) { q ->
                            val selected = q == quote
                            Text(
                                "“$q”", maxLines = 4, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(220.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
                                    .clickable { quote = if (selected) "" else q }.padding(12.dp)
                            )
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().appearIn(5), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Ahora no", primary = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                PillButton(saveText, primary = true, modifier = Modifier.weight(1.4f), enabled = canSave) {
                    val base = initial ?: ReadingRecord()
                    onSave(base.copy(rating = rating, review = review.trim(), favoriteQuote = quote, active = true), title.trim(), author.trim())
                }
            }
        }
        if (celebrate) {
            ConfettiBurst(
                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary, Color(0xFFF4EEE3)),
                modifier = Modifier.matchParentSize()
            )
        }
        }
    }
}

/** Texto que compara tu nota con la de los lectores. */
fun comparisonText(myHalfStars: Int, community: Float): String {
    val mine = myHalfStars / 2f
    val diff = mine - community
    return when {
        diff >= 1f -> "Te gustó bastante más que a la mayoría"
        diff >= 0.4f -> "Te gustó algo más que a la mayoría"
        diff <= -1f -> "Te gustó bastante menos que a la mayoría"
        diff <= -0.4f -> "Te gustó algo menos que a la mayoría"
        else -> "Coincides con la mayoría de lectores"
    }
}

/** Tarjeta "Tú vs. lectores": las dos notas contando, una escala con las dos marcas y cada web. */
@Composable
fun CommunityCard(
    record: ReadingRecord,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ratings = record.communityRatings
    val combined = CommunityRatingsClient.combined(ratings)
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val mine = record.rating / 2f
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ReadLabel("Tú frente a los lectores")
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("TÚ", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val shown = animatedNumber(mine)
                Text(
                    if (record.rating == 0) "—" else formatOneDecimal(shown).removeSuffix(",0"),
                    fontSize = 40.sp, fontWeight = FontWeight.Bold, color = primary
                )
                StarRating(record.rating, size = 15.dp, spacing = 3.dp, appearDelayMs = 150)
            }
            Column(Modifier.weight(1f)) {
                Text("LECTORES", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val shown = animatedNumber(combined?.first ?: 0f, delayMs = 120)
                Text(combined?.let { formatOneDecimal(shown) } ?: "—", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = onSurface)
                StarRating(
                    combined?.let { kotlin.math.round(it.first * 2).toInt() } ?: 0, size = 15.dp, spacing = 3.dp,
                    color = onSurface.copy(alpha = 0.75f), appearDelayMs = 300
                )
            }
        }

        if (combined != null && record.rating > 0) {
            ComparisonScale(mine = mine, readers = combined.first)
            Text(
                comparisonText(record.rating, combined.first),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = onSurface,
                modifier = Modifier.appearIn(3)
            )
        }
        when {
            combined == null && loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = primary)
                Text("Buscando la nota de los lectores…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            combined == null && record.ratingsFetchedAtMillis > 0 -> Text(
                "No he encontrado notas de lectores para este libro en Apple Books, Open Library ni Google Books.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            combined == null -> Text("Aún no se ha buscado la nota de los lectores.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        ratings.forEachIndexed { i, r ->
            val bar = remember(r) { Animatable(0f) }
            LaunchedEffect(r) {
                kotlinx.coroutines.delay(250L + i * 120L)
                bar.animateTo(r.average / 5f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
            }
            Column(
                Modifier.fillMaxWidth().appearIn(4 + i).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.background)
                    .clickable(enabled = r.url != null) { r.url?.let(onOpenUrl) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.source, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = onSurface)
                        Text(
                            "${formatCount(r.count)} valoraciones" + if (r.matchedTitle.isNotBlank()) " · ${r.matchedTitle}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(formatOneDecimal(bar.value * 5f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    Text(" / 5", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(onSurface.copy(alpha = 0.08f))) {
                    Box(
                        Modifier.fillMaxWidth(bar.value.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(primary.copy(alpha = 0.55f), primary)))
                    )
                }
            }
        }
        Text(
            if (loading) "Actualizando…" else "Actualizar notas de lectores",
            style = MaterialTheme.typography.labelLarge, color = primary,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = !loading, onClick = onRefresh).padding(vertical = 6.dp)
        )
    }
}

/**
 * Escala de 0 a 5 con dos marcas que se deslizan hasta su sitio: la tuya (color de acento) y la de
 * los lectores; el tramo entre ambas se ilumina.
 */
@Composable
private fun ComparisonScale(mine: Float, readers: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val a = remember { Animatable(0f) }
    val b = remember { Animatable(0f) }
    LaunchedEffect(mine, readers) {
        launch { a.animateTo(mine / 5f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessVeryLow)) }
        kotlinx.coroutines.delay(120)
        b.animateTo(readers / 5f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessVeryLow))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val y = size.height * 0.62f
            val pad = 12.dp.toPx()
            val w = size.width - pad * 2
            drawLine(onSurface.copy(alpha = 0.10f), Offset(pad, y), Offset(pad + w, y), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
            for (k in 0..5) {
                val x = pad + w * k / 5f
                drawCircle(onSurface.copy(alpha = 0.25f), radius = 1.6.dp.toPx(), center = Offset(x, y))
            }
            val xa = pad + w * a.value
            val xb = pad + w * b.value
            drawLine(
                Brush.horizontalGradient(listOf(primary.copy(alpha = 0.25f), primary.copy(alpha = 0.6f)), startX = minOf(xa, xb), endX = maxOf(xa, xb) + 1f),
                Offset(minOf(xa, xb), y), Offset(maxOf(xa, xb), y), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round
            )
            // Lectores
            drawCircle(surface, radius = 9.dp.toPx(), center = Offset(xb, y))
            drawCircle(onSurface.copy(alpha = 0.85f), radius = 6.5.dp.toPx(), center = Offset(xb, y))
            // Tú
            drawCircle(primary.copy(alpha = 0.25f), radius = 14.dp.toPx(), center = Offset(xa, y))
            drawCircle(surface, radius = 9.dp.toPx(), center = Offset(xa, y))
            drawCircle(primary, radius = 7.dp.toPx(), center = Offset(xa, y))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(primary))
                Text("Tú", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.size(8.dp).clip(CircleShape).background(onSurface.copy(alpha = 0.85f)))
                Text("Lectores", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("0 — 5", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


private fun toRad(deg: Double): Double = deg * kotlin.math.PI / 180.0
