package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.readriffle.app.DRAWABLE_TABLE
import com.readriffle.app.STRING_TABLE

/**
 * Equivalentes web de stringResource/painterResource de Android con ids de R.*: así las pantallas
 * portadas no cambian. Los textos salen de la tabla generada desde strings.xml.
 */
@Composable
fun stringResource(id: Int): String = STRING_TABLE[id]

@Composable
fun stringResource(id: Int, vararg formatArgs: Any?): String = formatAndroid(STRING_TABLE[id], formatArgs)

@Composable
fun painterResource(id: Int): Painter = org.jetbrains.compose.resources.painterResource(DRAWABLE_TABLE[id])

/** Texto de recurso fuera de Compose (equivalente a context.getString). */
fun appString(id: Int, vararg formatArgs: Any?): String =
    if (formatArgs.isEmpty()) STRING_TABLE[id] else formatAndroid(STRING_TABLE[id], formatArgs)

private val FORMAT_REGEX = Regex("%(\\d+\\$)?([-#+ 0,(]*)?(\\d+)?(\\.\\d+)?([dsfx%])")

/** Formato estilo String.format para las cadenas de la app (%s, %d, %1\$s, %.1f, %%). */
fun formatAndroid(template: String, args: Array<out Any?>): String {
    var auto = 0
    return FORMAT_REGEX.replace(template) { m ->
        val conv = m.groupValues[5]
        if (conv == "%") return@replace "%"
        val idx = m.groupValues[1].takeIf { it.isNotEmpty() }?.dropLast(1)?.toIntOrNull()?.minus(1) ?: auto++
        val arg = args.getOrNull(idx)
        val flags = m.groupValues[2]
        val width = m.groupValues[3].toIntOrNull() ?: 0
        val precision = m.groupValues[4].drop(1).toIntOrNull()
        val body = when (conv) {
            "d" -> {
                val n = (arg as? Number)?.toLong() ?: arg.toString().toLongOrNull() ?: 0L
                if (flags.contains(',')) groupThousands(n) else n.toString()
            }
            "f" -> fixed((arg as? Number)?.toDouble() ?: 0.0, precision ?: 6)
            "x" -> ((arg as? Number)?.toLong() ?: 0L).toString(16)
            else -> arg.toString()
        }
        when {
            body.length >= width -> body
            flags.contains('-') -> body.padEnd(width)
            flags.contains('0') -> body.padStart(width, '0')
            else -> body.padStart(width)
        }
    }
}

fun fixed(v: Double, decimals: Int): String {
    if (decimals <= 0) return kotlin.math.round(v).toLong().toString()
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    val r = kotlin.math.round(kotlin.math.abs(v) * factor).toLong()
    val intPart = r / factor.toLong()
    val frac = (r % factor.toLong()).toString().padStart(decimals, '0')
    return (if (v < 0 && r != 0L) "-" else "") + intPart + "." + frac
}

fun groupThousands(n: Long, sep: Char = '.'): String {
    val s = kotlin.math.abs(n).toString()
    val sb = StringBuilder()
    for ((i, c) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(sep)
        sb.append(c)
    }
    return (if (n < 0) "-" else "") + sb
}
