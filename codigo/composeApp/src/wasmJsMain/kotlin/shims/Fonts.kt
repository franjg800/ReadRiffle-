package androidx.compose.ui.text.font

import com.readriffle.app.FONT_FILES

/** Bytes de las tipografías de la app, cargados una vez al arrancar (ver Main.kt). */
object PreloadedFonts {
    val bytes = HashMap<Int, ByteArray>()
}

/** Equivalente web de Font(R.font.x, peso): misma llamada que en Android. */
fun Font(resId: Int, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal): Font {
    val data = PreloadedFonts.bytes[resId] ?: error("Fuente no cargada: ${FONT_FILES[resId]}")
    return androidx.compose.ui.text.platform.Font(FONT_FILES[resId] + weight.weight + style, data, weight, style)
}
