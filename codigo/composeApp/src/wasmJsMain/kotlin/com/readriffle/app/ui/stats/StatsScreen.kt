package com.readriffle.app.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readriffle.app.R
import com.readriffle.app.data.stats.StatsRepository
import com.readriffle.app.data.stats.StatsSummary
import com.readriffle.app.ui.common.ScreenTitle
import com.readriffle.app.ui.library.formatMinutes
import com.readriffle.app.platform.LocalDate

/** Estadísticas de lectura: racha, tiempo, palabras, ritmo medio y calendario de actividad. */
@Composable
fun StatsScreen(repository: StatsRepository, onBack: () -> Unit) {
    val stats by produceState(StatsSummary()) { value = repository.summary() }
    BackHandler(onBack = onBack)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.padding(top = 12.dp).size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), "Volver", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
            }
            ScreenTitle("Estadísticas")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat("Racha actual", if (stats.streak == 1) "1 día" else "${stats.streak} días", Modifier.weight(1f), highlight = true)
                BigStat("Mejor racha", if (stats.bestStreak == 1) "1 día" else "${stats.bestStreak} días", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat("Hoy", formatMinutes(stats.todaySeconds), Modifier.weight(1f))
                BigStat("Últimos 7 días", formatMinutes(stats.weekSeconds), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat("Palabras leídas", number(stats.totalWords), Modifier.weight(1f))
                BigStat("Tiempo total", formatMinutes(stats.totalSeconds), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat("Ritmo medio", if (stats.averageWpm > 0) "${stats.averageWpm} ppm" else "—", Modifier.weight(1f))
                BigStat("Días leyendo", "${stats.activeDays}", Modifier.weight(1f))
            }

            Text("ÚLTIMAS SEMANAS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Heatmap(stats)
            Text(
                "Cada cuadrito es un día; cuanto más intenso, más has leído. Cuenta solo el tiempo con el lector abierto y actividad real.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun number(n: Int): String = com.readriffle.app.platform.formatInt(n)

@Composable
private fun BigStat(label: String, value: String, modifier: Modifier, highlight: Boolean = false) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(20.dp))
            .background(if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Calendario de 18 semanas (columnas) × 7 días (filas); la última columna es la semana actual. */
@Composable
private fun Heatmap(stats: StatsSummary) {
    val weeks = 18
    val accent = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val today = LocalDate.now()
    val todayEpoch = today.toEpochDay()
    // Lunes de la semana actual
    val mondayThis = todayEpoch - (today.dayOfWeek.value - 1)
    val maxSeconds = (stats.days.values.maxOfOrNull { it.seconds } ?: 0).coerceAtLeast(600)
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val gap = 4.dp.toPx()
            val cell = ((size.width - gap * (weeks - 1)) / weeks).coerceAtMost((size.height - gap * 6) / 7)
            val totalW = cell * weeks + gap * (weeks - 1)
            val startX = (size.width - totalW) / 2f
            for (w in 0 until weeks) {
                for (d in 0 until 7) {
                    val day = mondayThis - (weeks - 1 - w) * 7L + d
                    if (day > todayEpoch) continue
                    val secs = stats.days[day]?.seconds ?: 0
                    val color = if (secs <= 0) empty else {
                        val t = (secs.toFloat() / maxSeconds).coerceIn(0f, 1f)
                        accent.copy(alpha = 0.25f + 0.75f * t)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(startX + w * (cell + gap), d * (cell + gap)),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(cell * 0.28f)
                    )
                }
            }
        }
    }
}
