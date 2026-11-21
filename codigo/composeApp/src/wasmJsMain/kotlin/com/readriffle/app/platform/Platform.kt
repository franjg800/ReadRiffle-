package com.readriffle.app.platform

import com.readriffle.app.platform.System
import kotlinx.coroutines.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Sustituto de java.lang.System para el código portado. */
object System {
    fun currentTimeMillis(): Long = jsNow().toLong()
}

/** Sustituto mínimo de java.util.UUID. */
class UUID private constructor(private val value: String) {
    override fun toString(): String = value
    companion object {
        fun randomUUID(): UUID = UUID(jsUuid())
    }
}

object URLDecoder {
    fun decode(s: String, @Suppress("UNUSED_PARAMETER") enc: String = "UTF-8"): String = jsDecodeUri(s.replace('+', ' '))
}

object URLEncoder {
    fun encode(s: String, @Suppress("UNUSED_PARAMETER") enc: String = "UTF-8"): String = jsEncodeUri(s)
}

fun stripAccents(s: String): String = jsStripAccents(s)

// ------------------------------------------------------------------------------------------- Bytes

/** Un carácter por byte: es como cruzan los binarios la frontera Kotlin <-> navegador. */
fun ByteArray.toLatin1(): String {
    val chars = CharArray(size)
    for (i in indices) chars[i] = (this[i].toInt() and 0xFF).toChar()
    return chars.concatToString()
}

fun String.latin1ToBytes(): ByteArray {
    val out = ByteArray(length)
    for (i in indices) out[i] = this[i].code.toByte()
    return out
}

// ------------------------------------------------------------------------------------------- Red

class HttpResponse(val code: Int, val bytes: ByteArray, val error: String) {
    val ok: Boolean get() = code in 200..299
    fun text(): String = bytes.decodeToString()
}

/**
 * Petición HTTP con fetch del navegador. [body] en texto (JSON) o [bodyBytes] en binario.
 * Las cabeceras se pasan como pares. Nunca lanza por un código HTTP de error: se mira [HttpResponse.code];
 * sí lanza si ni siquiera hubo respuesta (sin conexión, CORS...).
 */
suspend fun httpRequest(
    method: String,
    url: String,
    headers: Map<String, String> = emptyMap(),
    body: String? = null,
    bodyBytes: ByteArray? = null
): HttpResponse {
    val headersJson = if (headers.isEmpty()) null else buildString {
        append('{')
        headers.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) append(',')
            append(jsonString(k)).append(':').append(jsonString(v))
        }
        append('}')
    }
    val r = when {
        bodyBytes != null -> jsFetch(method, url, headersJson, bodyBytes.toLatin1(), false)
        body != null -> jsFetch(method, url, headersJson, body, true)
        else -> jsFetch(method, url, headersJson, null, false)
    }.await<JsFetchResult>()
    if (r.status == 0) throw IllegalStateException(r.error.ifBlank { "Sin conexión" })
    return HttpResponse(r.status, r.body.latin1ToBytes(), r.error)
}

private fun jsonString(s: String): String = buildString {
    append('"')
    for (c in s) when (c) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (c < ' ') append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
    }
    append('"')
}

// ------------------------------------------------------------------------------------------- Preferencias simples

/** Sustituto de SharedPreferences sobre localStorage (claves con prefijo propio). */
class SimplePrefs(private val name: String) {
    private fun k(key: String) = "br_${name}_$key"
    fun getString(key: String, def: String?): String? = jsLsGet(k(key)) ?: def
    fun getBoolean(key: String, def: Boolean): Boolean = jsLsGet(k(key))?.toBooleanStrictOrNull() ?: def
    fun getInt(key: String, def: Int): Int = jsLsGet(k(key))?.toIntOrNull() ?: def
    fun getLong(key: String, def: Long): Long = jsLsGet(k(key))?.toLongOrNull() ?: def
    fun getFloat(key: String, def: Float): Float = jsLsGet(k(key))?.toFloatOrNull() ?: def
    fun contains(key: String): Boolean = jsLsGet(k(key)) != null
    fun putString(key: String, v: String?) { if (v == null) jsLsRemove(k(key)) else jsLsSet(k(key), v) }
    fun putBoolean(key: String, v: Boolean) = jsLsSet(k(key), v.toString())
    fun putInt(key: String, v: Int) = jsLsSet(k(key), v.toString())
    fun putLong(key: String, v: Long) = jsLsSet(k(key), v.toString())
    fun putFloat(key: String, v: Float) = jsLsSet(k(key), v.toString())
    fun remove(key: String) = jsLsRemove(k(key))
}

// ------------------------------------------------------------------------------------------- Archivos del usuario

class PickedFile(val name: String, val bytes: ByteArray)

/** Abre el selector de archivos del sistema (Archivos en el iPhone). Vacío si se cancela. */
suspend fun pickFiles(accept: String, multiple: Boolean): List<PickedFile> = suspendCancellableCoroutine { cont ->
    jsPickFiles(accept, multiple) { n ->
        val out = (0 until n).map { PickedFile(jsPickedName(it), jsPickedData(it).latin1ToBytes()) }
        jsPickedClear()
        if (cont.isActive) cont.resume(out)
    }
}

fun downloadFile(name: String, bytes: ByteArray, mime: String) = jsDownload(name, bytes.toLatin1(), mime)
fun openUrl(url: String) = jsOpenUrl(url)
suspend fun shareText(text: String, title: String? = null): String = jsShareText(text, title).await<JsString>().toString()
suspend fun shareImage(name: String, png: ByteArray, text: String? = null): String = jsShareImage(name, png.toLatin1(), text).await<JsString>().toString()
fun setThemeColor(hex: String) = jsThemeColor(hex)
fun keepScreenOn(on: Boolean) = jsKeepAwake(on)
fun isInstalledApp(): Boolean = jsIsStandalone()
fun appOrigin(): String = jsOrigin()

suspend fun storageEstimate(): String = jsStorageEstimate().await<JsString>().toString()

// ------------------------------------------------------------------------------------------- Fechas

/** Sustituto mínimo de java.time.LocalDate (día local del usuario). */
class LocalDate private constructor(private val epochDay: Long) {
    fun toEpochDay(): Long = epochDay
    /** 1 = lunes ... 7 = domingo, como DayOfWeek.value. */
    val dayOfWeek: DayOfWeekValue get() = DayOfWeekValue((((epochDay + 3) % 7 + 7) % 7 + 1).toInt())

    class DayOfWeekValue(val value: Int)

    companion object {
        fun now(): LocalDate {
            val ms = jsNow()
            val local = ms + jsTzOffsetMinutes(ms) * 60_000.0
            return LocalDate(kotlin.math.floor(local / 86_400_000.0).toLong())
        }
        fun ofEpochDay(d: Long) = LocalDate(d)
    }
}

/** Fecha local desglosada a partir de milisegundos. */
class DateParts(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int)

fun dateParts(millis: Long): DateParts {
    val local = millis + jsTzOffsetMinutes(millis.toDouble()) * 60_000L
    val days = local.floorDiv(86_400_000L)
    val msOfDay = local - days * 86_400_000L
    // Algoritmo de días civiles (Howard Hinnant).
    val z = days + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    return DateParts((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt(), (msOfDay / 3_600_000L).toInt(), ((msOfDay / 60_000L) % 60).toInt())
}

private val MONTHS_SHORT = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")
private val MONTHS_LONG = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

/** Sustituto de java.util.Date (solo guarda los milisegundos). */
class Date(val time: Long = System.currentTimeMillis())

/** Sustituto mínimo de SimpleDateFormat en español: d, dd, MMM, MMMM, MM, yyyy, HH, mm y texto literal. */
class SimpleDateFormat(private val pattern: String, @Suppress("UNUSED_PARAMETER") locale: Any? = null) {
    fun format(date: Date): String {
        val p = dateParts(date.time)
        val sb = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\'') {
                val end = pattern.indexOf('\'', i + 1).let { if (it < 0) pattern.length else it }
                sb.append(pattern, i + 1, end); i = end + 1; continue
            }
            var j = i
            while (j < pattern.length && pattern[j] == c) j++
            val n = j - i
            when (c) {
                'd' -> sb.append(if (n >= 2) p.day.toString().padStart(2, '0') else p.day.toString())
                'M' -> sb.append(when { n >= 4 -> MONTHS_LONG[p.month - 1]; n == 3 -> MONTHS_SHORT[p.month - 1]; n == 2 -> p.month.toString().padStart(2, '0'); else -> p.month.toString() })
                'y' -> sb.append(if (n == 2) (p.year % 100).toString().padStart(2, '0') else p.year.toString())
                'H' -> sb.append(p.hour.toString().padStart(n, '0'))
                'm' -> sb.append(p.minute.toString().padStart(n, '0'))
                else -> repeat(n) { sb.append(c) }
            }
            i = j
        }
        return sb.toString()
    }
}

/** Número entero con separador de miles español (1.234). */
fun formatInt(n: Number): String = androidx.compose.ui.res.groupThousands(n.toLong())

/** Abre el tutorial para instalar la web en la pantalla de inicio. */
fun openInstallGuide() = jsOpenInstallGuide()

/** Funciones que se llaman cuando la app pasa a segundo plano o se cierra (para guardar). */
object AppVisibility {
    private val listeners = mutableListOf<() -> Unit>()
    private var bound = false
    fun add(l: () -> Unit) {
        if (!bound) { bound = true; jsOnHidden { listeners.toList().forEach { runCatching { it() } } } }
        listeners += l
    }
    fun remove(l: () -> Unit) { listeners -= l }
}

/** Petición JSONP (para Apple Books, que no admite CORS). Texto JSON o vacío si falla. */
suspend fun jsonpRequest(url: String): String = jsJsonp(url).await<JsString>().toString()
