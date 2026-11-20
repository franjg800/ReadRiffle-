package com.readriffle.app.data.stats

import com.readriffle.app.platform.appFilesDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.readriffle.app.platform.File
import com.readriffle.app.platform.LocalDate

/** Lo leído en un día concreto (día = días desde 1970 en la zona horaria del móvil). */
@Serializable
data class DayStat(val epochDay: Long, val seconds: Int = 0, val words: Int = 0)

@Serializable
private data class StatsFile(val days: List<DayStat> = emptyList())

/** Resumen ya calculado para pintar la pantalla de estadísticas y la tarjeta de "Hoy". */
data class StatsSummary(
    val days: Map<Long, DayStat> = emptyMap(),
    val todaySeconds: Int = 0,
    val todayWords: Int = 0,
    val weekSeconds: Int = 0,
    val weekWords: Int = 0,
    val totalSeconds: Int = 0,
    val totalWords: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    /** Palabras por minuto medias de toda la historia (0 si aún no hay datos suficientes). */
    val averageWpm: Int = 0,
    val activeDays: Int = 0
)

/**
 * Registro local de actividad de lectura: tiempo y palabras por día. Se guarda en un JSON propio
 * (no en el índice de la biblioteca) y no se sincroniza con Drive.
 */
class StatsRepository {
    private val file = File(appFilesDir, "reading_stats.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private fun read(): StatsFile =
        if (!file.exists()) StatsFile() else runCatching { json.decodeFromString<StatsFile>(file.readText()) }.getOrDefault(StatsFile())

    suspend fun add(seconds: Int, words: Int) {
        if (seconds <= 0 && words <= 0) return
        withContext(NonCancellable + Dispatchers.Default) {
            mutex.withLock {
                val today = LocalDate.now().toEpochDay()
                val current = read().days
                val existing = current.find { it.epochDay == today } ?: DayStat(today)
                val updated = existing.copy(seconds = existing.seconds + seconds, words = existing.words + words)
                file.writeText(json.encodeToString(StatsFile(current.filterNot { it.epochDay == today } + updated)))
            }
        }
    }

    suspend fun summary(): StatsSummary = withContext(Dispatchers.Default) {
        mutex.withLock { summarize(read().days) }
    }

    companion object {
        fun summarize(list: List<DayStat>, today: Long = LocalDate.now().toEpochDay()): StatsSummary {
            val map = list.associateBy { it.epochDay }
            fun active(d: Long) = (map[d]?.seconds ?: 0) >= 60 || (map[d]?.words ?: 0) >= 50
            // Racha: días seguidos con lectura hasta hoy; si hoy aún no has leído, cuenta desde ayer.
            var streak = 0
            var d = if (active(today)) today else today - 1
            while (active(d)) { streak++; d-- }
            var best = 0
            var run = 0
            var prev = Long.MIN_VALUE
            for (day in map.keys.filter { active(it) }.sorted()) {
                run = if (day == prev + 1) run + 1 else 1
                best = maxOf(best, run)
                prev = day
            }
            val week = (0..6).mapNotNull { map[today - it] }
            val totalSec = list.sumOf { it.seconds }
            val totalWords = list.sumOf { it.words }
            return StatsSummary(
                days = map,
                todaySeconds = map[today]?.seconds ?: 0,
                todayWords = map[today]?.words ?: 0,
                weekSeconds = week.sumOf { it.seconds },
                weekWords = week.sumOf { it.words },
                totalSeconds = totalSec,
                totalWords = totalWords,
                streak = streak,
                bestStreak = best,
                averageWpm = if (totalSec >= 300) (totalWords * 60f / totalSec).toInt() else 0,
                activeDays = map.keys.count { active(it) }
            )
        }
    }
}
