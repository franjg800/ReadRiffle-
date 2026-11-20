package com.readriffle.app.data.dictionary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.readriffle.app.platform.httpRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import com.readriffle.app.platform.URLEncoder

/** Una acepción: número (si el diccionario la numera), ámbito ("Astrología"...), texto y notas. */
data class DictDefinition(
    val number: Int?,
    val tag: String,
    val text: String,
    val notes: List<String>
)

/** Un bloque por categoría gramatical ("Sustantivo femenino", "Verbo transitivo"...). */
data class DictSection(
    val heading: String,
    val intro: String,
    val definitions: List<DictDefinition>
)

sealed interface DictionaryResult {
    data class Found(val word: String, val sections: List<DictSection>, val source: String) : DictionaryResult
    data object NotFound : DictionaryResult
    data object Offline : DictionaryResult
}

/**
 * Diccionario en línea sin claves ni cuentas: usa la API pública de Wiktionary (primero en
 * español, que también recoge palabras de otros idiomas explicadas en español, y como respaldo en
 * inglés). Pide el artículo como texto plano y lo trocea por categorías y acepciones.
 */
private class IOException(msg: String) : Exception(msg)

object DictionaryClient {
    private val cache = HashMap<String, DictionaryResult.Found>()

    private val skipEs = listOf(
        "etimolog", "pronunciaci", "locucion", "refran", "información adicional", "véase", "traduccion",
        "conjugaci", "referencias", "descendient", "anagrama", "cognado", "derivad", "relacionad", "proverbio"
    )
    private val skipEn = listOf(
        "etymology", "pronunciation", "derived", "related", "translations", "see also", "references",
        "descendants", "anagrams", "further reading", "alternative forms", "conjugation", "usage notes", "quotations"
    )

    suspend fun lookup(rawWord: String): DictionaryResult = withContext(Dispatchers.Default) {
        val word = clean(rawWord)
        if (word.isEmpty()) return@withContext DictionaryResult.NotFound
        cache[word]?.let { return@withContext it }
        var offline = false
        for ((host, label) in listOf("es.wiktionary.org" to "Wikcionario", "en.wiktionary.org" to "Wiktionary")) {
            for (candidate in listOf(word, word.replaceFirstChar { it.uppercase() }).distinct()) {
                val extract = try {
                    fetchExtract(host, candidate)
                } catch (_: IOException) {
                    offline = true
                    null
                } catch (_: Exception) {
                    null
                } ?: continue
                val sections = parse(extract, spanish = host.startsWith("es"))
                if (sections.isNotEmpty()) {
                    val found = DictionaryResult.Found(word, sections, label)
                    cache[word] = found
                    return@withContext found
                }
            }
        }
        if (offline) DictionaryResult.Offline else DictionaryResult.NotFound
    }

    private fun clean(raw: String): String {
        val first = raw.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        return first.trim { !it.isLetterOrDigit() }.lowercase()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun fetchExtract(host: String, title: String): String? {
        // origin=* : la API de Wikimedia permite así las peticiones desde cualquier web (CORS).
        val url = "https://$host/w/api.php?action=query&prop=extracts&explaintext=1&exsectionformat=plain" +
            "&redirects=1&format=json&origin=*&titles=" + URLEncoder.encode(title, "UTF-8")
        val r = try { httpRequest("GET", url) } catch (e: Exception) { throw IOException(e.message ?: "sin conexión") }
        if (!r.ok) throw IOException("HTTP ${r.code}")
        val pages = json.parseToJsonElement(r.text()).jsonObject["query"]?.jsonObject?.get("pages")?.jsonObject ?: return null
        val page = pages.values.firstOrNull()?.jsonObject ?: return null
        if (page.containsKey("missing")) return null
        return page["extract"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }

    private fun parse(extract: String, spanish: Boolean): List<DictSection> {
        val skip = if (spanish) skipEs else skipEn
        val sections = ArrayList<DictSection>()
        var seenLanguage = false
        val blocks = extract.trim().split(Regex("\\n\\s*\\n\\s*\\n"))
            .map { blk -> blk.trim().lines().map { it.trim() }.filter { it.isNotEmpty() } }
            .filter { it.isNotEmpty() }
        for ((i, lines) in blocks.withIndex()) {
            val heading = lines.first()
            val lower = heading.lowercase()
            val nextHeading = blocks.getOrNull(i + 1)?.first()?.lowercase().orEmpty()
            // Un título suelto que precede a "Etimología"/"Etymology" es el nombre de un idioma
            // ("Español", "Catalán"...): nos quedamos solo con el primero.
            val isLanguage = lines.size == 1 && (i == 0 ||
                nextHeading.startsWith("etimolog") || nextHeading.startsWith("etymology"))
            if (isLanguage) {
                if (seenLanguage && sections.isNotEmpty()) break
                seenLanguage = true
                continue
            }
            if (skip.any { lower.startsWith(it) }) continue
            if (lines.size == 1) continue // subtítulo vacío ("Forma flexiva"...)
            sections += buildSection(heading, lines.drop(1))
        }
        return sections.filter { it.definitions.isNotEmpty() || it.intro.isNotBlank() }.take(6)
    }

    private fun buildSection(heading: String, body: List<String>): DictSection {
        val marker = Regex("^(\\d+)(?:\\s+(.+))?$")
        var intro = ""
        val defs = ArrayList<DictDefinition>()
        var expected = 1
        var number: Int? = null
        var tag = ""
        val text = StringBuilder()
        val notes = ArrayList<String>()

        fun flush() {
            if (number != null || text.isNotEmpty()) {
                defs += DictDefinition(number, tag, text.toString().trim(), notes.toList())
            }
            number = null; tag = ""; text.clear(); notes.clear()
        }

        for (line in body) {
            val m = marker.matchEntire(line)
            if (m != null && m.groupValues[1].toInt() == expected) {
                flush()
                number = expected
                expected++
                tag = m.groupValues[2]
                continue
            }
            if (number == null && defs.isEmpty() && text.isEmpty() && intro.isEmpty() && body.firstOrNull() == line &&
                (line.contains('¦') || line.contains("plural"))
            ) {
                intro = line.replace(' ', ' ').replace("¦", "·").replace(Regex("\\s+"), " ")
                continue
            }
            val isNote = line.contains(':') && line.substringBefore(':').length < 24 &&
                line.substringBefore(':').first().isUpperCase()
            if (isNote && (number != null)) notes += line
            else {
                if (text.isNotEmpty()) text.append(' ')
                text.append(line)
            }
        }
        flush()
        return DictSection(heading, intro, defs.filter { it.text.isNotBlank() }.take(14))
    }
}
